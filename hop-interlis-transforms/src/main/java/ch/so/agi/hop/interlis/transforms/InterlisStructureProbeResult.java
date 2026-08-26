package ch.so.agi.hop.interlis.transforms;

import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionResult;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import java.util.List;

/**
 * Result of probing the model configuration of INTERLIS Structure Explode/Collect dialogs.
 *
 * @param ok whether the probe succeeded; failures are returned as a friendly message and never
 *     make the dialog unusable
 * @param message diagnostic or success message
 * @param projection the structure projection, or {@code null} when not yet resolvable
 * @param classes all transferable classes of the resolved models
 * @param structurePaths multi-valued structure paths of the selected class in model order
 */
public record InterlisStructureProbeResult(
    boolean ok,
    String message,
    InterlisStructureProjectionResult projection,
    List<InterlisClassDescriptor> classes,
    List<String> structurePaths) {

  public InterlisStructureProbeResult {
    classes = classes == null ? List.of() : List.copyOf(classes);
    structurePaths = structurePaths == null ? List.of() : List.copyOf(structurePaths);
  }
}
