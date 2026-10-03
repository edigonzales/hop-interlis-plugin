package ch.so.agi.hop.interlis.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** UTF-8 source with atomic saves and external-edit detection. */
public final class MappingFile {
  private Path path;
  private byte[] original;

  public MappingFile(Path path) throws IOException {
    this.path = path.toAbsolutePath().normalize();
    original = Files.exists(this.path) ? Files.readAllBytes(this.path) : null;
  }

  public Path path() {
    return path;
  }

  public String text() {
    return original == null
        ? "mapping v2 \"Migration\" {\n}\n"
        : new String(original, StandardCharsets.UTF_8);
  }

  public void save(String text) throws IOException {
    byte[] current = Files.exists(path) ? Files.readAllBytes(path) : null;
    if (!Arrays.equals(current, original))
      throw new IOException(
          "Mapping was changed outside the editor. Reopen it before saving: " + path);
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    Path temporary = Files.createTempFile(path.getParent(), ".ilimap-", ".tmp");
    try {
      Files.write(temporary, bytes);
      Files.move(
          temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      original = bytes;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
