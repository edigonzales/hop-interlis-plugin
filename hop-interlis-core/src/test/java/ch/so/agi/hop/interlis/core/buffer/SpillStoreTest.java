package ch.so.agi.hop.interlis.core.buffer;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SpillStoreTest {
  @TempDir Path temp;

  @Test
  void memory_and_disk_have_identical_lookup_sort_and_fifo_semantics() throws Exception {
    for (long memory : new long[] {1, 1024 * 1024}) {
      Path directory;
      try (var store =
          new SpillStore<String>(new JavaRecordCodec<>(), new SpillOptions(memory, temp, 0))) {
        assertThat(store.putIfAbsent("key", "value")).isTrue();
        assertThat(store.putIfAbsent("key", "other")).isFalse();
        assertThat(store.get("key")).isEqualTo("value");
        assertThat(store.get("missing")).isNull();
        store.appendOrdered(4, "four");
        store.appendOrdered(2, "two");
        store.appendOrdered(2, "second-two");
        var sorted = new ArrayList<String>();
        store.iterator(true).forEachRemaining(sorted::add);
        assertThat(sorted).containsExactly("value", "two", "second-two", "four");
        assertThat(store.poll()).isEqualTo("value");
        assertThat(store.containsKey("key")).isFalse();
        assertThat(store.poll()).isEqualTo("four");
        assertThat(store.poll()).isEqualTo("two");
        assertThat(store.poll()).isEqualTo("second-two");
        assertThat(store.poll()).isNull();
        assertThat(store.spilled()).isEqualTo(memory == 1);
        directory = store.workDirectory();
        store.close();
        store.close();
        assertThatThrownBy(() -> store.get("key")).hasMessageContaining("closed");
      }
      if (directory != null) assertThat(directory).doesNotExist();
    }
    try (var files = Files.list(temp)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  @Test
  void disk_cap_is_shared_across_stores_and_released_after_group_cleanup() throws Exception {
    var options = new SpillOptions(1, temp, 700 * 1024);
    try (var first = new SpillStore<byte[]>(new JavaRecordCodec<>(), options.divided(2));
        var second = new SpillStore<byte[]>(new JavaRecordCodec<>(), options.divided(2))) {
      first.append(new byte[400 * 1024]);
      assertThatThrownBy(() -> second.append(new byte[400 * 1024]))
          .hasMessageContaining("combined")
          .hasMessageContaining("disk limit");
    }
    try (var next = new SpillStore<byte[]>(new JavaRecordCodec<>(), options)) {
      next.append(new byte[400 * 1024]);
      assertThat(next.poll()).hasSize(400 * 1024);
    }
  }

  @Test
  void disk_limit_and_codec_errors_are_actionable_and_leave_no_files() throws Exception {
    try (var store =
        new SpillStore<byte[]>(new JavaRecordCodec<>(), new SpillOptions(1, temp, 1))) {
      assertThatThrownBy(() -> store.append(new byte[10000]))
          .hasMessageContaining("disk limit")
          .hasMessageContaining(temp.toString());
    }
    var bad =
        new RecordCodec<String>() {
          public byte[] encode(String value) throws Exception {
            throw new Exception("broken codec");
          }

          public String decode(byte[] bytes) {
            return "";
          }
        };
    try (var store = new SpillStore<>(bad, new SpillOptions(1, temp, 0))) {
      assertThatThrownBy(() -> store.append("bad")).hasMessageContaining("broken codec");
    }
    try (var files = Files.list(temp)) {
      assertThat(files.toList()).isEmpty();
    }
  }
}
