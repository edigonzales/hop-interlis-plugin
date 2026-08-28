package ch.so.agi.hop.interlis.transforms;

import java.util.Arrays;
import java.util.List;

/** Shared parsing rules for the model source fields used by INTERLIS transforms. */
public final class InterlisModelSourceSupport {

  public static final String MODELS_FROM_DATA = "%DATA";
  public static final String DEFAULT_MODEL_DIRECTORIES =
      "%XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch";

  private InterlisModelSourceSupport() {}

  /**
   * Parses explicit model names. Semicolons are the documented separator; commas remain accepted
   * for backwards compatibility with existing pipeline metadata.
   */
  public static List<String> parseModelNames(String value) {
    if (value == null || value.isBlank() || MODELS_FROM_DATA.equals(value.trim())) {
      return List.of();
    }
    return Arrays.stream(value.split("[;,]"))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .toList();
  }

  /** Parses local model directories and repository URLs separated by semicolons. */
  public static List<String> parseModelDirectories(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(";"))
        .map(String::trim)
        .filter(directory -> !directory.isEmpty())
        .toList();
  }
}
