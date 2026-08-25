package ch.so.agi.hop.interlis.core.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Where INTERLIS models come from.
 *
 * <p>Phase 0 supports local {@code .ili} files and model directories. Models referenced by name
 * only are resolved by looking for {@code <modelName>.ili} inside the given directories. Model
 * repository URLs are added in a later phase.
 *
 * @param iliFiles local model files to compile
 * @param modelNames model names to resolve through the model directories
 * @param modelDirectories directories (or {@code .ili} files) searched for referenced models
 */
public record ModelSource(
    List<Path> iliFiles, List<String> modelNames, List<String> modelDirectories) {

  public ModelSource {
    iliFiles = iliFiles == null ? List.of() : List.copyOf(iliFiles);
    modelNames = modelNames == null ? List.of() : List.copyOf(modelNames);
    modelDirectories =
        modelDirectories == null ? List.of() : List.copyOf(modelDirectories);
  }
}
