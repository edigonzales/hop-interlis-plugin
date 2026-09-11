package ch.so.agi.hop.interlis.transforms;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Locates committed test resources of the transforms module. */
public final class TestData {

  private static final List<String> MODEL_VERSIONS = List.of("2.3", "2.4");
  private static volatile Path allModels;
  private static volatile Path allData;

  private TestData() {}

  public static Path path(String resource) {
    if ("/models".equals(resource)) {
      return allModelsDirectory();
    }
    if ("/data".equals(resource)) {
      return allDataDirectory();
    }
    if (resource.startsWith("/models/")) {
      return modelPath(resource.substring("/models/".length()));
    }
    if (resource.startsWith("/data/")) {
      return dataPath(resource.substring("/data/".length()));
    }
    return resourcePath(resource);
  }

  public static Path modelDirectory(String version) {
    if (!MODEL_VERSIONS.contains(version)) {
      throw new IllegalArgumentException("Unsupported INTERLIS model version: " + version);
    }
    return resourcePath("/fixtures/models/" + version);
  }

  public static Path dataDirectory(String version) {
    if (!MODEL_VERSIONS.contains(version)) {
      throw new IllegalArgumentException("Unsupported XTF version: " + version);
    }
    return resourcePath("/fixtures/data/" + version);
  }

  public static List<Path> modelDirectories() {
    return MODEL_VERSIONS.stream().map(TestData::modelDirectory).toList();
  }

  public static String modelDirectoriesValue() {
    return modelDirectories().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
  }

  private static Path modelPath(String fileName) {
    for (String version : MODEL_VERSIONS) {
      Path candidate = modelDirectory(version).resolve(fileName);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    if (fileName.startsWith("repo/")) {
      return resourcePath("/fixtures/models/repository/" + fileName.substring("repo/".length()));
    }
    throw new IllegalStateException("Model fixture not found: " + fileName);
  }

  private static Path dataPath(String fileName) {
    for (String version : MODEL_VERSIONS) {
      Path candidate = dataDirectory(version).resolve(fileName);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Transfer fixture not found: " + fileName);
  }

  private static Path allModelsDirectory() {
    Path result = allModels;
    if (result != null) {
      return result;
    }
    synchronized (TestData.class) {
      if (allModels == null) {
        allModels = createAggregateDirectory("hop-interlis-models");
        for (String version : MODEL_VERSIONS) {
          copyDirectory(modelDirectory(version), allModels);
        }
      }
      return allModels;
    }
  }

  private static Path allDataDirectory() {
    Path result = allData;
    if (result != null) {
      return result;
    }
    synchronized (TestData.class) {
      if (allData == null) {
        allData = createAggregateDirectory("hop-interlis-data");
        for (String version : MODEL_VERSIONS) {
          copyDirectory(dataDirectory(version), allData);
        }
      }
      return allData;
    }
  }

  private static Path createAggregateDirectory(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new IllegalStateException("Could not create aggregate test resource directory", e);
    }
  }

  private static void copyDirectory(Path source, Path target) {
    try {
      try (var paths = Files.walk(source)) {
        paths.forEach(
            path -> {
              try {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                  Files.createDirectories(destination);
                } else {
                  Files.createDirectories(destination.getParent());
                  Files.copy(path, destination);
                }
              } catch (IOException e) {
                throw new IllegalStateException("Could not copy test resource " + path, e);
              }
            });
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not read test resource directory " + source, e);
    }
  }

  private static Path resourcePath(String resource) {
    try {
      var url = TestData.class.getResource(resource);
      if (url == null) {
        throw new IllegalStateException("Resource not found: " + resource);
      }
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Resource not found: " + resource, e);
    }
  }
}
