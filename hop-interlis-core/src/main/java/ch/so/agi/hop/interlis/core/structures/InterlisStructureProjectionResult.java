package ch.so.agi.hop.interlis.core.structures;

import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import java.util.List;

/**
 * Result of projecting a multi-valued structure attribute: the compiled model, the extracted
 * schema and the structure plan.
 *
 * @param model the compiled INTERLIS model
 * @param schema the extracted schema
 * @param plan the structure projection
 * @param modelNames resolved model names in request order
 */
public record InterlisStructureProjectionResult(
    CompiledInterlisModel model,
    InterlisSchemaDescriptor schema,
    InterlisStructurePlan plan,
    List<String> modelNames) {

  public InterlisStructureProjectionResult {
    modelNames = modelNames == null ? List.of() : List.copyOf(modelNames);
  }
}
