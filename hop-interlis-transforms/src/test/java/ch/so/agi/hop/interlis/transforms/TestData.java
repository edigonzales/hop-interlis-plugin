package ch.so.agi.hop.interlis.transforms;

import java.net.URISyntaxException;
import java.nio.file.Path;

/** Locates committed test resources of the transforms module. */
public final class TestData {

  private TestData() {}

  public static Path path(String resource) {
    try {
      return Path.of(TestData.class.getResource(resource).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Resource not found: " + resource, e);
    }
  }
}
