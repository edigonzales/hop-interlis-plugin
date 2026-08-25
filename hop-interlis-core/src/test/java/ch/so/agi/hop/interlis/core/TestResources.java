package ch.so.agi.hop.interlis.core;

import java.net.URISyntaxException;
import java.nio.file.Path;

/** Locates committed test resources. */
public final class TestResources {

  private TestResources() {}

  public static Path path(String resource) {
    try {
      return Path.of(TestResources.class.getResource(resource).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Resource not found: " + resource, e);
    }
  }
}
