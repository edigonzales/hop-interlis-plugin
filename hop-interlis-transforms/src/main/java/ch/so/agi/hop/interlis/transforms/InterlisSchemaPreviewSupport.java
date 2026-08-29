package ch.so.agi.hop.interlis.transforms;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource;
import ch.so.agi.hop.interlis.core.mapping.InterlisPropertyPath;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;

/** Shared SWT-free construction of preview rows from the central Hop schema factory. */
public final class InterlisSchemaPreviewSupport {

  private static final String GEOMETRY_PLUGIN_FAILURE_MESSAGE =
      "Schema preview failed: Geometry Type Plugin fehlt, ist inkompatibel oder Hop muss neu "
          + "gestartet werden.";

  @FunctionalInterface
  public interface RowMetaCreator {
    IRowMeta create(InterlisRowMappingPlan plan) throws HopTransformException;
  }

  private InterlisSchemaPreviewSupport() {}

  public static InterlisSchemaPreview create(InterlisRowMappingPlan plan) {
    HopRowSchemaFactory factory = new HopRowSchemaFactory();
    return create(plan, factory::createRowMeta);
  }

  public static InterlisSchemaPreview create(
      InterlisRowMappingPlan plan, RowMetaCreator rowMetaCreator) {
    List<InterlisPreviewRow> rows = new ArrayList<>();
    try {
      IRowMeta rowMeta = rowMetaCreator.create(plan);
      for (int i = 0; i < rowMeta.size(); i++) {
        IValueMeta valueMeta = rowMeta.getValueMeta(i);
        rows.add(toPreviewRow(valueMeta, plan.fields().get(i)));
      }
      return new InterlisSchemaPreview(rows, plan.warnings(), null);
    } catch (HopTransformException e) {
      return new InterlisSchemaPreview(
          rows, plan.warnings(), "Schema preview failed: " + message(e));
    } catch (LinkageError e) {
      return new InterlisSchemaPreview(rows, plan.warnings(), GEOMETRY_PLUGIN_FAILURE_MESSAGE);
    }
  }

  /** Creates preview rows for structure-child fields and optional technical/prefix rows. */
  public static InterlisSchemaPreview createFieldPreview(
      List<InterlisPreviewRow> prefixRows,
      List<InterlisFieldPlan> fields,
      List<String> warnings) {
    List<InterlisPreviewRow> rows =
        prefixRows == null ? new ArrayList<>() : new ArrayList<>(prefixRows);
    try {
      HopRowSchemaFactory factory = new HopRowSchemaFactory();
      for (InterlisFieldPlan field : fields) {
        rows.add(toPreviewRow(factory.createValueMeta(field), field));
      }
      return new InterlisSchemaPreview(rows, warnings, null);
    } catch (HopTransformException e) {
      return new InterlisSchemaPreview(rows, warnings, "Schema preview failed: " + message(e));
    } catch (LinkageError e) {
      return new InterlisSchemaPreview(rows, warnings, GEOMETRY_PLUGIN_FAILURE_MESSAGE);
    }
  }

  /** Builds the child-row preview used by Structure Explode. */
  public static InterlisSchemaPreview createStructurePreview(InterlisStructurePlan plan) {
    List<InterlisPreviewRow> prefixRows =
        List.of(
            new InterlisPreviewRow(plan.parentClass().scopedName(), "", "parent class"),
            new InterlisPreviewRow(
                plan.attributeName(),
                plan.ordered() ? "LIST" : "BAG",
                plan.structure().scopedName()),
            new InterlisPreviewRow("_ili_parent_tid", "String", "parent TID"),
            new InterlisPreviewRow("_ili_parent_bid", "String", "parent BID"),
            new InterlisPreviewRow(
                "_ili_index",
                "Integer",
                plan.ordered() ? "LIST order (semantic)" : "technical index"));
    return createFieldPreview(prefixRows, plan.childFields(), plan.warnings());
  }

  public static InterlisPreviewRow toPreviewRow(IValueMeta valueMeta, InterlisFieldPlan field) {
    return new InterlisPreviewRow(valueMeta.getName(), valueMeta.getTypeDesc(), source(field));
  }

  public static String source(InterlisFieldPlan field) {
    return switch (field.source()) {
      case OBJECT_ID -> "@TID";
      case BASKET_ID -> "@BID";
      case CLASS_NAME -> "@CLASS";
      case TOPIC_NAME -> "@TOPIC";
      case OPERATION -> "@OPERATION";
      case ROLE_REFERENCE -> "-> " + field.roleDescriptor().targetClassScopedName();
      default ->
          field.attributeDescriptor() == null ? "" : field.attributeDescriptor().typeName();
    };
  }

  /** Creates a field plan suitable for previews of target-class fields such as Role Join. */
  public static InterlisFieldPlan attributeFieldPlan(
      String outputName, InterlisAttributeDescriptor attribute) {
    InterlisFieldSource source =
        attribute.kind().isGeometry()
            ? InterlisFieldSource.GEOMETRY_ATTRIBUTE
            : InterlisFieldSource.PRIMITIVE_ATTRIBUTE;
    return new InterlisFieldPlan(
        0,
        outputName,
        source,
        InterlisPropertyPath.root(attribute.name()),
        attribute,
        null,
        null);
  }

  private static String message(Throwable throwable) {
    String message = throwable.getMessage();
    return message == null || message.isBlank()
        ? throwable.getClass().getSimpleName()
        : message;
  }
}
