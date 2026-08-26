package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import java.util.List;

/**
 * The result of projecting a class: the compiled model, the extracted schema, the row mapping
 * plan and the resolved model names.
 *
 * @param model compiled model (carries the {@code TransferDescription} for transfer writers)
 * @param schema extracted schema of the compiled models
 * @param plan precomputed row projection
 * @param modelNames model names that were compiled
 */
public record InterlisProjectionResult(
    CompiledInterlisModel model,
    InterlisSchemaDescriptor schema,
    InterlisRowMappingPlan plan,
    List<String> modelNames) {

  public InterlisProjectionResult {
    modelNames = modelNames == null ? List.of() : List.copyOf(modelNames);
  }
}
