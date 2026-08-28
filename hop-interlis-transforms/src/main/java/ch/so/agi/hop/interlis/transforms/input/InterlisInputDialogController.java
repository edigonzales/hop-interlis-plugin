package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelContext;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisProbeResult;
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

  private final HopRowSchemaFactory schemaFactory = new HopRowSchemaFactory();

  /**
   * Probes the current configuration. Failures are returned as a friendly message; the dialog
   * stays usable.
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
          context.schema().classes(),
          context.schema().associations());
    }
    return new InterlisProbeResult(
        true,
        modelLoadedMessage(context),
        result,
        context.schema().classes(),
        context.schema().associations());
  }

  private InterlisProbeResult modelOnlyResult(InterlisModelContext context) {
    return new InterlisProbeResult(
        true,
        modelLoadedMessage(context) + ". Select an INTERLIS class.",
        null,
        context.schema().classes(),
        context.schema().associations());
  }

  private InterlisProbeResult incompleteConfiguration() {
    return new InterlisProbeResult(
        false,
        "Schema preview unavailable: the configuration is incomplete "
            + "(missing transfer file, model directories or unresolved variables).",
        null,
        List.of());
  }

  private String modelLoadedMessage(InterlisModelContext model) {
    return "Model loaded: "
        + model.modelNames()
        + "; "
        + model.schema().classes().size()
        + " classes, "
        + model.schema().associations().size()
        + " associations";
  }

  /** Formats the projected Hop schema for the preview area, including warnings. */
  public String formatSchemaPreview(InterlisRowMappingPlan plan) {
    StringBuilder preview = new StringBuilder();
    preview.append("Projected Hop schema\n");
    preview.append("--------------------\n");
    try {
      IRowMeta rowMeta = schemaFactory.createRowMeta(plan);
      for (int i = 0; i < rowMeta.size(); i++) {
        org.apache.hop.core.row.IValueMeta valueMeta = rowMeta.getValueMeta(i);
        InterlisFieldPlan field = plan.fields().get(i);
        String source =
            switch (field.source()) {
              case OBJECT_ID -> "@TID";
              case BASKET_ID -> "@BID";
              case CLASS_NAME -> "@CLASS";
              case TOPIC_NAME -> "@TOPIC";
              case OPERATION -> "@OPERATION";
              case ROLE_REFERENCE -> "-> " + field.roleDescriptor().targetClassScopedName();
              default -> field.attributeDescriptor() == null
                  ? ""
                  : field.attributeDescriptor().typeName();
            };
        preview.append(String.format("%-26s %-14s %s%n", valueMeta.getName(), valueMeta.getTypeDesc(), source));
      }
    } catch (HopTransformException e) {
      preview.append("Schema preview failed: ").append(e.getMessage()).append('\n');
    }
    if (plan.hasWarnings()) {
      preview.append('\n');
      for (String warning : plan.warnings()) {
        preview.append("! ").append(warning).append('\n');
      }
    }
    return preview.toString();
  }

  private static String rootCauseMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null || message.isBlank()
        ? current.getClass().getSimpleName()
        : message;
  }
}
