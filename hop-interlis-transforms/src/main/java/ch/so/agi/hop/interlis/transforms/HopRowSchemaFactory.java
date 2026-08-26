package ch.so.agi.hop.interlis.transforms;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisValueKind;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBigNumber;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;

/**
 * Turns an {@link InterlisRowMappingPlan} into Hop row metadata.
 *
 * <p>This is the only place where INTERLIS value kinds are mapped to Hop value metas; the runtime
 * mapper derives its Java value types from the same descriptors, so {@code getFields()} and the
 * runtime stay consistent by construction.
 */
public final class HopRowSchemaFactory {

  public org.apache.hop.core.row.IRowMeta createRowMeta(InterlisRowMappingPlan plan)
      throws HopTransformException {
    RowMeta rowMeta = new RowMeta();
    for (InterlisFieldPlan field : plan.fields()) {
      rowMeta.addValueMeta(createValueMeta(field));
    }
    return rowMeta;
  }

  public IValueMeta createValueMeta(InterlisFieldPlan field) throws HopTransformException {
    return switch (field.source()) {
      case OBJECT_ID, BASKET_ID, CLASS_NAME, TOPIC_NAME, OPERATION, ROLE_REFERENCE ->
          new ValueMetaString(field.hopFieldName());
      case PRIMITIVE_ATTRIBUTE, GEOMETRY_ATTRIBUTE, FLATTENED_STRUCTURE_ATTRIBUTE ->
          createAttributeValueMeta(field);
    };
  }

  private IValueMeta createAttributeValueMeta(InterlisFieldPlan field)
      throws HopTransformException {
    InterlisAttributeDescriptor attribute = field.attributeDescriptor();
    if (attribute == null) {
      throw new HopTransformException("Field " + field.hopFieldName() + " has no attribute descriptor");
    }
    return switch (attribute.kind()) {
      case TEXT, MTEXT, NAME, URI, ENUM, TIME -> {
        ValueMetaString meta = new ValueMetaString(field.hopFieldName());
        if (attribute.textMaxLength() > 0) {
          meta.setLength(attribute.textMaxLength());
        }
        yield meta;
      }
      case BOOLEAN -> new ValueMetaBoolean(field.hopFieldName());
      case INTEGER -> new ValueMetaInteger(field.hopFieldName());
      case DECIMAL -> {
        ValueMetaBigNumber meta = new ValueMetaBigNumber(field.hopFieldName());
        if (attribute.decimalPlaces() >= 0) {
          meta.setPrecision(attribute.decimalPlaces());
        }
        yield meta;
      }
      case DATE -> new ValueMetaDate(field.hopFieldName());
      case DATETIME -> new ValueMetaTimestamp(field.hopFieldName());
      case GEOMETRY -> new ValueMetaGeometry(field.hopFieldName());
      case STRUCTURE ->
          throw new HopTransformException(
              "Structure attribute " + field.hopFieldName() + " must be flattened before projection");
    };
  }

  /** Java runtime type produced by the mapper for a value kind. */
  static Class<?> runtimeType(InterlisValueKind kind) {
    return switch (kind) {
      case TEXT, MTEXT, NAME, URI, ENUM, TIME -> String.class;
      case BOOLEAN -> Boolean.class;
      case INTEGER -> Long.class;
      case DECIMAL -> BigDecimal.class;
      case DATE -> Date.class;
      case DATETIME -> Timestamp.class;
      case GEOMETRY -> org.locationtech.jts.geom.Geometry.class;
      case STRUCTURE -> Object.class;
    };
  }
}
