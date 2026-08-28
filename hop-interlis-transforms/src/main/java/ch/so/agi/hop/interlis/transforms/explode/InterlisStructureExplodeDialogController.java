package ch.so.agi.hop.interlis.transforms.explode;

import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureProbeResult;
import java.util.List;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;

/**
 * SWT-free controller for the INTERLIS Structure Explode dialog.
 *
 * <p>The dialog only renders widgets; all model probing and schema preview formatting lives here
 * so it can be unit-tested without a display.
 */
public final class InterlisStructureExplodeDialogController {

  private final HopRowSchemaFactory schemaFactory = new HopRowSchemaFactory();

  /** Probes the current configuration. Failures are returned as a friendly message. */
  public InterlisStructureProbeResult probe(
      InterlisStructureExplodeMeta meta, IVariables variables) {
    return InterlisStructureDialogSupport.probe(
        resolveModelNames(meta, variables),
        resolveModelDirectories(meta, variables),
        resolve(variables, meta.getClassName()),
        resolve(variables, meta.getStructureAttributePath()),
        meta.projectionOptions());
  }

  /** Formats the child row schema for the preview area, including warnings. */
  public String formatSchemaPreview(InterlisStructurePlan plan) {
    StringBuilder preview = new StringBuilder();
    preview.append("Child row schema\n");
    preview.append("----------------\n");
    preview
        .append(String.format("%-26s %-14s %s%n",
            plan.parentClass().scopedName(), "", "parent class"))
        .append(String.format("%-26s %-14s %s%n",
            plan.attributeName(),
            plan.ordered() ? "LIST" : "BAG",
            plan.structure().scopedName()))
        .append('\n');
    preview
        .append(String.format("%-26s %-14s %s%n", "_ili_parent_tid", "String", "parent TID"))
        .append(String.format("%-26s %-14s %s%n", "_ili_parent_bid", "String", "parent BID"))
        .append(String.format("%-26s %-14s %s%n",
            "_ili_index", "Integer", plan.ordered() ? "LIST order (semantic)" : "technical index"));
    try {
      for (var field : plan.childFields()) {
        IRowMeta rowMeta = new org.apache.hop.core.row.RowMeta();
        var valueMeta = schemaFactory.createValueMeta(field);
        preview.append(
            String.format(
                "%-26s %-14s %s%n",
                valueMeta.getName(),
                valueMeta.getTypeDesc(),
                field.attributeDescriptor() == null
                    ? ""
                    : field.attributeDescriptor().typeName()));
      }
    } catch (HopTransformException e) {
      preview.append("Schema preview failed: ").append(e.getMessage()).append('\n');
    }
    if (!plan.warnings().isEmpty()) {
      preview.append('\n');
      for (String warning : plan.warnings()) {
        preview.append("Warning: ").append(warning).append('\n');
      }
    }
    return preview.toString();
  }

  private static List<String> resolveModelNames(
      InterlisStructureExplodeMeta meta, IVariables variables) {
    String resolved = resolve(variables, meta.getModelNames());
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private static List<String> resolveModelDirectories(
      InterlisStructureExplodeMeta meta, IVariables variables) {
    String resolved = resolve(variables, meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return InterlisModelSourceSupport.parseModelDirectories(resolved);
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return "";
    }
    return variables == null ? value.trim() : variables.resolve(value).trim();
  }
}
