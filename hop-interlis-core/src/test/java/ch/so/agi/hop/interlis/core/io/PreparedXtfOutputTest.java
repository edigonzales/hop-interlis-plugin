package ch.so.agi.hop.interlis.core.io;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PreparedXtfOutputTest {
  @TempDir Path temp;

  @Test
  void preparation_preserves_existing_output_abort_and_publication_are_idempotent()
      throws Exception {
    var target = temp.resolve("output.xtf");
    Files.writeString(target, "old");
    try (var output = new PreparedXtfOutput(target, true)) {
      Files.writeString(output.temporary(), "new");
      output.prepare();
      assertThat(Files.readString(target)).isEqualTo("old");
      output.close();
      output.close();
      assertThat(output.temporary()).doesNotExist();
    }
    assertThat(Files.readString(target)).isEqualTo("old");
    try (var output = new PreparedXtfOutput(target, true)) {
      Files.writeString(output.temporary(), "new");
      output.prepare();
      output.publish();
      output.publish();
    }
    assertThat(Files.readString(target)).isEqualTo("new");
  }

  @Test
  void no_overwrite_publication_rejects_a_target_created_after_preparation() throws Exception {
    var target = temp.resolve("race.xtf");
    try (var output = new PreparedXtfOutput(target, false)) {
      Files.writeString(output.temporary(), "prepared");
      output.prepare();
      Files.writeString(target, "concurrent");
      assertThatThrownBy(output::publish).isInstanceOf(FileAlreadyExistsException.class);
    }
    assertThat(Files.readString(target)).isEqualTo("concurrent");
    Files.delete(target);
    try (var output = new PreparedXtfOutput(target, false)) {
      Files.writeString(output.temporary(), "new");
      output.prepare();
      output.publish();
    }
    assertThat(Files.readString(target)).isEqualTo("new");
    try (var files = Files.list(temp)) {
      assertThat(files.toList()).containsExactly(target);
    }
  }
}
