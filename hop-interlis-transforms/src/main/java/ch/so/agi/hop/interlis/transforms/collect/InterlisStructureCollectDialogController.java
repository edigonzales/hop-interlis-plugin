package ch.so.agi.hop.interlis.transforms.collect;

import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureProbeResult;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.variables.IVariables;

/**
 * SWT-free controller for the INTERLIS Structure Collect dialog.
 *
 * <p>The dialog only renders widgets; all model probing and schema preview formatting lives here
 * so it can be unit-tested without a display.
 */
public final class InterlisStructureCollectDialogController {

  /** Probes the current configuration. Failures are returned as a friendly message. */
  public InterlisStructureProbeResult probe(
      InterlisStructureCollectMeta meta, IVariables variables) {
    return InterlisStructureDialogSupport.probe(
        resolveModelNames(meta, variables),
        resolveModelDirectories(meta, variables),
        resolve(variables, meta.getClassName()),
        resolve(variables, meta.getStructureAttributePath()),
        meta.projectionOptions());
  }

  /** Formats a description of the collected structure for the preview area. */
  public String formatCollectPreview(InterlisStructurePlan plan, String parentTransform,
      String childTransform, String parentKeyField, String childParentKeyField) {
    return String.join(
        "\n",
        "Collect preview",
        "--------------",
        "Parent stream  " + (parentTransform == null ? "" : parentTransform)
            + "  (key " + parentKeyField + ")",
        "Child stream   " + (childTransform == null ? "" : childTransform)
            + "  (parent key " + childParentKeyField + ")",
        "Structure      " + plan.attributeName() + " : "
            + (plan.ordered() ? "LIST OF " : "BAG OF ") + plan.structure().scopedName(),
        "Child fields   " + plan.childFields().stream()
            .map(f -> f.hopFieldName()).toList(),
        "Output         parent rows with updated " + InterlisStructureCollectMeta.DEFAULT_SOURCE_OBJECT_FIELD,
        "",
        "Both input streams must be sorted: the parent stream by parent key ascending and the",
        "child stream by (parent key, index) ascending.");
  }

  private static List<String> resolveModelNames(
      InterlisStructureCollectMeta meta, IVariables variables) {
    String resolved = resolve(variables, meta.getModelNames());
    if (resolved.isBlank()) {
      return List.of();
    }
    return Arrays.stream(resolved.split(","))
        .map(String::trim)
        .filter(n -> !n.isEmpty())
        .toList();
  }

  private static List<String> resolveModelDirectories(
      InterlisStructureCollectMeta meta, IVariables variables) {
    String resolved = resolve(variables, meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return Arrays.stream(resolved.split(";"))
        .map(String::trim)
        .filter(d -> !d.isEmpty())
        .toList();
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return "";
    }
    return variables == null ? value.trim() : variables.resolve(value).trim();
  }
}
