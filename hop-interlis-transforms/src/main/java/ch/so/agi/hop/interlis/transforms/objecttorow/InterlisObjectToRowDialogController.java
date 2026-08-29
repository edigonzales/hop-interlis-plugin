package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisPreviewRow;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreviewSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.variables.IVariables;

/**
 * SWT-free controller for the INTERLIS Object to Row dialog: model probing and schema preview.
 */
public final class InterlisObjectToRowDialogController {

  private final HopRowSchemaFactory schemaFactory = new HopRowSchemaFactory();

  /** Probes the current configuration; failures are returned as a friendly message. */
  public InterlisProjectionResult probe(InterlisObjectToRowMeta meta, IVariables variables)
      throws Exception {
    Optional<InterlisProjectionResult> projection = meta.tryProject(variables);
    return projection.orElse(null);
  }

  /** All transferable classes of the resolved models. */
  public List<InterlisClassDescriptor> classes(InterlisProjectionResult result) {
    return result == null ? List.of() : result.schema().selectableClasses();
  }

  /** Formats the projected Hop schema for the preview area, including warnings. */
  public String formatSchemaPreview(InterlisProjectionResult result) {
    StringBuilder preview = new StringBuilder();
    preview.append("Projected Hop schema\n");
    preview.append("--------------------\n");
    InterlisSchemaPreview schemaPreview = createSchemaPreview(result);
    for (InterlisPreviewRow row : schemaPreview.rows()) {
      preview.append(
          String.format("%-26s %-14s %s%n", row.fieldName(), row.hopType(), row.source()));
    }
    if (schemaPreview.hasError()) {
      preview.append(schemaPreview.errorMessage()).append('\n');
    }
    for (String warning : schemaPreview.warnings()) {
      preview.append("! ").append(warning).append('\n');
    }
    return preview.toString();
  }

  /** Builds the structured preview consumed by the SWT table. */
  public InterlisSchemaPreview createSchemaPreview(InterlisProjectionResult result) {
    if (result == null) {
      return new InterlisSchemaPreview(List.of(), List.of(), null);
    }
    return InterlisSchemaPreviewSupport.create(result.plan(), schemaFactory::createRowMeta);
  }

  /** The most relevant cause message for display in the dialog. */
  public static String rootCauseMessage(Throwable throwable) {
    return InterlisStructureDialogSupport.rootCauseMessage(throwable);
  }
}
