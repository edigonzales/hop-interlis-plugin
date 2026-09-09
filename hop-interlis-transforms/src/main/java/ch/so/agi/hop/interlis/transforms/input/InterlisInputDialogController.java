package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.mapping.InterlisModelContext;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisProbeResult;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreviewSupport;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;

/**
 * SWT-free controller for the INTERLIS Input dialog.
 *
 * <p>The dialog only renders widgets; all model probing, class discovery and schema preview
 * formatting lives here so it can be unit-tested without a display.
 */
public final class InterlisInputDialogController {

  @FunctionalInterface
  interface RowMetaCreator {
    IRowMeta create(InterlisRowMappingPlan plan) throws HopTransformException;
  }

  private final RowMetaCreator rowMetaCreator;

  public InterlisInputDialogController() {
    this(new HopRowSchemaFactory()::createRowMeta);
  }

  InterlisInputDialogController(RowMetaCreator rowMetaCreator) {
    this.rowMetaCreator = rowMetaCreator;
  }

  /**
   * Probes the current configuration. Failures are returned as a friendly message; the dialog stays
   * usable.
   */
  public InterlisProbeResult probe(InterlisInputMeta meta, IVariables variables) {
    String resolvedClass = meta.resolvedClassName(variables);
    Optional<InterlisModelContext> model;
    try {
      model = meta.tryLoadModel(variables);
    } catch (Exception e) {
      return new InterlisProbeResult(false, rootCauseMessage(e), null, List.of());
    }
    if (model.isEmpty()) {
      return incompleteConfiguration();
    }

    InterlisModelContext context = model.get();
    if (resolvedClass.isBlank() || resolvedClass.contains("${")) {
      return modelOnlyResult(context);
    }

    InterlisProjectionResult result;
    try {
      result =
          new InterlisProjectionService()
              .project(context, resolvedClass, meta.projectionOptions(variables));
    } catch (Exception e) {
      String message =
          context.schema().findPlanRoot(resolvedClass).isEmpty()
              ? modelLoadedMessage(context)
                  + ". Class "
                  + resolvedClass
                  + " was not found; select an INTERLIS class."
              : modelLoadedMessage(context) + ". Schema projection failed: " + rootCauseMessage(e);
      return new InterlisProbeResult(
              true,
              message,
              null,
              context.schema().selectableClasses(),
              context.schema().selectableAssociations())
          .withStatus(ch.so.agi.hop.interlis.transforms.InterlisProbeStatus.ERROR);
    }
    return new InterlisProbeResult(
        true,
        modelLoadedMessage(context),
        result,
        context.schema().selectableClasses(),
        context.schema().selectableAssociations());
  }

  private InterlisProbeResult modelOnlyResult(InterlisModelContext context) {
    return new InterlisProbeResult(
        true,
        modelLoadedMessage(context) + ". Select an INTERLIS class.",
        null,
        context.schema().selectableClasses(),
        context.schema().selectableAssociations());
  }

  private InterlisProbeResult incompleteConfiguration() {
    return new InterlisProbeResult(
            false,
            "Schema preview unavailable: the configuration is incomplete "
                + "(missing transfer file, model directories or unresolved variables).",
            null,
            List.of())
        .withStatus(ch.so.agi.hop.interlis.transforms.InterlisProbeStatus.INFO);
  }

  private String modelLoadedMessage(InterlisModelContext model) {
    return "Model loaded: "
        + model.modelNames()
        + "; "
        + model.schema().selectableClasses().size()
        + " classes, "
        + model.schema().selectableAssociations().size()
        + " associations";
  }

  /** Formats the projected Hop schema for the preview area, including warnings. */
  public String formatSchemaPreview(InterlisRowMappingPlan plan) {
    StringBuilder preview = new StringBuilder();
    preview.append("Projected Hop schema\n");
    preview.append("--------------------\n");
    InterlisSchemaPreview schemaPreview = createSchemaPreview(plan);
    for (var row : schemaPreview.rows()) {
      preview.append(
          String.format("%-26s %-14s %s%n", row.fieldName(), row.hopType(), row.source()));
    }
    if (schemaPreview.hasError()) {
      preview.append(schemaPreview.errorMessage()).append('\n');
    }
    if (!schemaPreview.warnings().isEmpty()) {
      preview.append('\n');
      for (String warning : schemaPreview.warnings()) {
        preview.append("! ").append(warning).append('\n');
      }
    }
    return preview.toString();
  }

  /** Builds the structured preview consumed by the SWT table. */
  public InterlisSchemaPreview createSchemaPreview(InterlisRowMappingPlan plan) {
    return InterlisSchemaPreviewSupport.create(plan, rowMetaCreator::create);
  }

  private static String rootCauseMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
  }
}
