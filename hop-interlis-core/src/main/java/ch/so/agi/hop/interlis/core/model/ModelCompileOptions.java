package ch.so.agi.hop.interlis.core.model;

/**
 * Options controlling model compilation.
 *
 * @param iliLanguageVersion INTERLIS language version, e.g. {@code "2.3"} or {@code "2.4"};
 *     {@code null} lets ili2c auto-detect the version from each model file
 */
public record ModelCompileOptions(String iliLanguageVersion) {

  public static ModelCompileOptions defaults() {
    return new ModelCompileOptions(null);
  }

  public ModelCompileOptions {
    iliLanguageVersion =
        iliLanguageVersion == null || iliLanguageVersion.isBlank()
            ? null
            : iliLanguageVersion;
  }
}
