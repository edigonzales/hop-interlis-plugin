package ch.so.agi.hop.interlis.transforms;

import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import java.util.List;

/**
 * Result of a design-time model/schema probe.
 *
 * @param configured whether the model configuration was complete enough to load; a model-only
 * probe is configured even when no class has been selected yet
 * @param message status or error message for the preview area
 * @param projection the projection result; {@code null} for a model-only probe or if probing failed
 * @param classes all transferable classes of the resolved models; empty if the model could not be loaded
 * @param associations all transferable associations of the resolved models
 */
public record InterlisProbeResult(
    boolean configured,
    String message,
    InterlisProjectionResult projection,
    List<InterlisClassDescriptor> classes,
    List<InterlisAssociationDescriptor> associations) {

  public InterlisProbeResult {
    classes = classes == null ? List.of() : List.copyOf(classes);
    associations = associations == null ? List.of() : List.copyOf(associations);
  }

  /** Backwards-compatible constructor without associations. */
  public InterlisProbeResult(
      boolean configured,
      String message,
      InterlisProjectionResult projection,
      List<InterlisClassDescriptor> classes) {
    this(configured, message, projection, classes, List.of());
  }

  public boolean successful() {
    return projection != null;
  }
}
