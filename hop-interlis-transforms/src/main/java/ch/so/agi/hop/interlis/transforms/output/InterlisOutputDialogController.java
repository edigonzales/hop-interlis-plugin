package ch.so.agi.hop.interlis.transforms.output;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.transforms.InterlisProbeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.variables.IVariables;

/**
 * SWT-free controller for the INTERLIS Output dialog.
 *
 * <p>Provides the model probe and the field mapping grid contents; the dialog only renders
 * widgets, all model interpretation lives here and is unit-tested without a display.
 */
public final class InterlisOutputDialogController {

  public InterlisProbeResult probe(InterlisOutputMeta meta, IVariables variables) {
    Optional<InterlisProjectionResult> projection;
    try {
      projection = meta.tryProject(variables);
    } catch (Exception e) {
      return new InterlisProbeResult(false, rootCauseMessage(e), null, List.of());
    }
    if (projection.isEmpty()) {
      return new InterlisProbeResult(
          false,
          "Mapping preview unavailable: the configuration is incomplete "
              + "(missing output file, models, class or unresolved variables).",
          null,
          List.of());
    }
    InterlisProjectionResult result = projection.get();
    return new InterlisProbeResult(
        true,
        "Model loaded: "
            + result.modelNames()
            + "; "
            + result.schema().classes().size()
            + " classes",
        result,
        result.schema().classes());
  }

  /** Builds the mapping grid rows for the projected plan. */
  public List<InterlisFieldMapping> mapping(InterlisRowMappingPlan plan) {
    List<InterlisFieldMapping> mappings = new ArrayList<>();
    for (InterlisFieldPlan field : plan.fields()) {
      String property = propertyLabel(field);
      String type =
          field.attributeDescriptor() == null
              ? "String"
              : field.attributeDescriptor().typeName();
      mappings.add(
          new InterlisFieldMapping(property, field.hopFieldName(), type, "auto-map by name"));
    }
    return mappings;
  }

  private String propertyLabel(InterlisFieldPlan field) {
    if (field.source() == InterlisFieldSource.OBJECT_ID) {
      return "@TID";
    }
    if (field.source() == InterlisFieldSource.BASKET_ID) {
      return "@BID";
    }
    if (field.source() == InterlisFieldSource.CLASS_NAME) {
      return "@CLASS";
    }
    if (field.source() == InterlisFieldSource.TOPIC_NAME) {
      return "@TOPIC";
    }
    if (field.source() == InterlisFieldSource.OPERATION) {
      return "@OPERATION";
    }
    if (field.source() == InterlisFieldSource.ROLE_REFERENCE) {
      return field.propertyPath().leafName()
          + " -> "
          + (field.roleDescriptor() == null ? "?" : field.roleDescriptor().targetClassScopedName());
    }
    return field.propertyPath().dotted();
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
