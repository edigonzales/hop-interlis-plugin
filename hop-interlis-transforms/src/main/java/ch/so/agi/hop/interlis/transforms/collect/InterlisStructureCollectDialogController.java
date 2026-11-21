package ch.so.agi.hop.interlis.transforms.collect;

import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisPreviewRow;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreviewSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureProbeResult;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.variables.IVariables;

/**
 * SWT-free controller for the INTERLIS Structure Collect dialog.
 *
 * <p>The dialog only renders widgets; all model probing and schema preview formatting lives here so
 * it can be unit-tested without a display.
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

  /** Builds the structured mapping preview shown below the collect configuration. */
  public InterlisSchemaPreview createSchemaPreview(
      InterlisStructurePlan plan,
      String parentTransform,
      String childTransform,
      String parentKeyField,
      String childParentKeyField,
      InterlisStructureCollectMeta meta) {
    var prefixRows =
        List.of(
            new InterlisPreviewRow("Parent input", "", safe(parentTransform)),
            new InterlisPreviewRow("Child input", "", safe(childTransform)),
            new InterlisPreviewRow(
                "Structure", plan.ordered() ? "LIST" : "BAG", plan.childTypeName()));
    var preview =
        InterlisSchemaPreviewSupport.createFieldPreview(
            prefixRows, plan.childFields(), plan.warnings());
    var rows = new ArrayList<>(preview.rows());
    var warnings = new ArrayList<>(preview.warnings());
    if (!plan.primitive()
        && meta.getCollectMode() == InterlisStructureCollectMeta.CollectMode.PRESERVE)
      rows.add(
          new InterlisPreviewRow(
              "_ili_child_object",
              "InterlisObject",
              "required; copy concrete child and overlay selected fields"));
    warnings.add(
        "Collect mode: "
            + meta.getCollectMode()
            + ". The child stream replaces the entire selected collection; filtered children are"
            + " removed.");
    warnings.add(
        "Parent identity: "
            + "("
            + safe(meta.getParentBidField())
            + ", "
            + parentKeyField
            + ")"
            + "; child identity: "
            + "("
            + safe(meta.getChildParentBidField())
            + ", "
            + childParentKeyField
            + ")");
    warnings.add("Sort both inputs by basket and parent key, and ordered children by index.");
    return new InterlisSchemaPreview(rows, warnings, preview.errorMessage());
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static List<String> resolveModelNames(
      InterlisStructureCollectMeta meta, IVariables variables) {
    String resolved = resolve(variables, meta.getModelNames());
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private static List<String> resolveModelDirectories(
      InterlisStructureCollectMeta meta, IVariables variables) {
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
