package ch.so.agi.hop.interlis.core.io;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.buffer.SpillOptions;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class GroupedObjectStoreTest {
  @TempDir Path temp;

  @Test
  void disk_and_memory_group_identically_and_reject_topic_conflicts() throws Exception {
    for (long memory : new long[] {1, 1024 * 1024}) {
      try (var store = new GroupedObjectStore(new SpillOptions(memory, temp, 0))) {
        store.add("M.T", "b1", new Iom_jObject("M.T.A", "a1"));
        store.add("M.T", "b2", new Iom_jObject("M.T.B", "b1"));
        store.add("M.T", "b1", new Iom_jObject("M.T.B", "b2"));
        assertThat(store.spilled()).isEqualTo(memory == 1);
        var ordered = new ArrayList<String>();
        store.iterator().forEachRemaining(o -> ordered.add(o.object().getobjectoid()));
        assertThat(ordered).containsExactly("a1", "b2", "b1");
        assertThatThrownBy(() -> store.add("M.Other", "b1", new Iom_jObject("M.Other.A", "x")))
            .hasMessageContaining("both");
      }
    }
    try (var files = Files.list(temp)) {
      assertThat(files.toList()).isEmpty();
    }
  }
}
