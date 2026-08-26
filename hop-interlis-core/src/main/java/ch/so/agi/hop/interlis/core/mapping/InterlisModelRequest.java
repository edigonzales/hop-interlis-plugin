package ch.so.agi.hop.interlis.core.mapping;

import java.nio.file.Path;
import java.util.List;

/**
 * A model resolution request: an optional transfer file plus explicit models and model
 * directories.
 *
 * <p>If {@code modelNames} is empty, the model names are detected from the transfer file header.
 * {@code %XTF_DIR} inside a model directory is resolved to the directory of {@code dataFile}.
 *
 * @param dataFile transfer file used for model inference and {@code %XTF_DIR} resolution
 * @param modelNames explicit model names; empty means detect from the transfer file
 * @param modelDirectories model directories/repositories; may contain {@code %XTF_DIR}
 */
public record InterlisModelRequest(
    Path dataFile, List<String> modelNames, List<String> modelDirectories) {

  public InterlisModelRequest {
    modelNames = modelNames == null ? List.of() : List.copyOf(modelNames);
    modelDirectories =
        modelDirectories == null ? List.of() : List.copyOf(modelDirectories);
  }
}
