package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
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
    return result == null ? List.of() : result.schema().classes();
  }

  /** Formats the projected Hop schema for the preview area, including warnings. */
  public String formatSchemaPreview(InterlisProjectionResult result) {
    StringBuilder preview = new StringBuilder();
    preview.append("Projected Hop schema\n");
    preview.append("--------------------\n");
    try {
      IRowMeta rowMeta = schemaFactory.createRowMeta(result.plan());
      for (int i = 0; i < rowMeta.size(); i++) {
        org.apache.hop.core.row.IValueMeta valueMeta = rowMeta.getValueMeta(i);
        var field = result.plan().fields().get(i);
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
        preview.append(
            String.format("%-26s %-14s %s%n", valueMeta.getName(), valueMeta.getTypeDesc(), source));
      }
    } catch (HopTransformException e) {
      preview.append("Schema preview failed: ").append(e.getMessage()).append('\n');
    }
    for (String warning : result.plan().warnings()) {
      preview.append("! ").append(warning).append('\n');
    }
    return preview.toString();
  }

  /** The most relevant cause message for display in the dialog. */
  public static String rootCauseMessage(Throwable throwable) {
    return InterlisStructureDialogSupport.rootCauseMessage(throwable);
  }
}
