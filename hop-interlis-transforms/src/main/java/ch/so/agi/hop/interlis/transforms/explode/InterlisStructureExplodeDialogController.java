package ch.so.agi.hop.interlis.transforms.explode;

import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisPreviewRow;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreviewSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureProbeResult;
import java.util.List;
import org.apache.hop.core.variables.IVariables;

/**
 * SWT-free controller for the INTERLIS Structure Explode dialog.
 *
 * <p>The dialog only renders widgets; all model probing and schema preview formatting lives here
 * so it can be unit-tested without a display.
 */
public final class InterlisStructureExplodeDialogController {

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
    InterlisSchemaPreview schemaPreview = createSchemaPreview(plan);
    for (InterlisPreviewRow row : schemaPreview.rows()) {
      preview.append(
          String.format("%-26s %-14s %s%n", row.fieldName(), row.hopType(), row.source()));
    }
    if (schemaPreview.hasError()) {
      preview.append(schemaPreview.errorMessage()).append('\n');
    }
    if (!schemaPreview.warnings().isEmpty()) {
      preview.append('\n');
      for (String warning : schemaPreview.warnings()) {
        preview.append("Warning: ").append(warning).append('\n');
      }
    }
    return preview.toString();
  }

  /** Builds the structured preview consumed by the SWT table. */
  public InterlisSchemaPreview createSchemaPreview(InterlisStructurePlan plan) {
    return InterlisSchemaPreviewSupport.createStructurePreview(plan);
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
