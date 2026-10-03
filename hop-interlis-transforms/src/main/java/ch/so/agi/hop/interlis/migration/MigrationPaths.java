package ch.so.agi.hop.interlis.migration;

import java.net.URI;
import java.nio.file.Path;

/** Hop's internal directory variables may resolve to local VFS file URIs. */
final class MigrationPaths {
  private MigrationPaths() {}

  static Path local(String value) {
    if (value.contains("${")) throw new IllegalArgumentException("Unresolved variable: " + value);
    if (value.startsWith("file:")) return Path.of(URI.create(value));
    if (value.contains("://"))
      throw new IllegalArgumentException("A local file is required: " + value);
    return Path.of(value);
  }
}
