package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import java.util.List;

/**
 * The immutable result of resolving and compiling an INTERLIS model source.
 *
 * <p>The context deliberately contains the extracted schema as well as the compiled model. The
 * schema is used by design-time consumers such as the class selector, while the compiled model is
 * retained for projections and transfer readers/writers that need the original
 * {@code TransferDescription}.
 *
 * @param model compiled INTERLIS model
 * @param schema transfer-relevant schema extracted from the compiled model
 * @param modelNames model names actually used by the compilation
 */
public record InterlisModelContext(
    CompiledInterlisModel model, InterlisSchemaDescriptor schema, List<String> modelNames) {

  public InterlisModelContext {
    modelNames = modelNames == null ? List.of() : List.copyOf(modelNames);
  }
}
