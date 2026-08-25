package ch.so.agi.hop.interlis.core.model;

import ch.interlis.ili2c.metamodel.TransferDescription;
import java.util.List;

/**
 * The result of compiling a set of INTERLIS models.
 *
 * <p>The wrapped {@link TransferDescription} is treated as read-only once handed out; consumers
 * must not modify it.
 *
 * @param transferDescription compiled models
 * @param compiledModelNames names of the models that were explicitly requested
 */
public record CompiledInterlisModel(
    TransferDescription transferDescription, List<String> compiledModelNames) {

  public CompiledInterlisModel {
    compiledModelNames =
        compiledModelNames == null ? List.of() : List.copyOf(compiledModelNames);
  }
}
