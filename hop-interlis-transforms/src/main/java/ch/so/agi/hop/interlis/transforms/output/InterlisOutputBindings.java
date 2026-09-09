package ch.so.agi.hop.interlis.transforms.output;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;

/**
 * The output's configured identities and name-bound attributes, resolved once at initialization.
 */
public record InterlisOutputBindings(
    ch.so.agi.hop.interlis.transforms.mapping.InterlisRowBindings fields,
    int basketIndex,
    String constantBasket,
    ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding carrier,
    ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding operation) {
  public static String sourceName(
      InterlisFieldPlan field, InterlisOutputMeta meta, IVariables vars) {
    String name =
        switch (field.source()) {
          case OBJECT_ID -> meta.getObjectIdField();
          case BASKET_ID -> meta.getBasketIdField();
          default -> field.hopFieldName();
        };
    return name == null ? "" : vars.resolve(name).trim();
  }

  public static InterlisOutputBindings bind(
      IRowMeta input, InterlisRowMappingPlan plan, InterlisOutputMeta meta, IVariables vars)
      throws HopTransformException {
    var fields =
        new java.util.ArrayList<ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding>();
    var factory = new ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory();
    int basketIndex = -1;
    String context = "INTERLIS Output " + plan.root().scopedName();
    for (var field : plan.fields()) {
      String name = sourceName(field, meta, vars);
      boolean constant = field.source() == InterlisFieldSource.BASKET_ID && name.isBlank();
      fields.add(
          constant
              ? ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding.constant(
                  context, field.hopFieldName(), field.outputIndex(), null)
              : ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding.bind(
                  input,
                  context,
                  name,
                  field.hopFieldName(),
                  field.outputIndex(),
                  factory.createValueMeta(field),
                  true));
      if (field.source() == InterlisFieldSource.BASKET_ID) basketIndex = field.outputIndex();
    }
    String basket = meta.getBasketId() == null ? "" : vars.resolve(meta.getBasketId());
    String source =
        ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding.resolve(
            vars, meta.getSourceObjectField());
    String op =
        ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding.resolve(
            vars, meta.getOperationField());
    return new InterlisOutputBindings(
        new ch.so.agi.hop.interlis.transforms.mapping.InterlisRowBindings(fields),
        basketIndex,
        basket,
        ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding.bind(
            input,
            context,
            source,
            "source object",
            0,
            new ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject(source),
            !source.isBlank()),
        ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding.bind(
            input,
            context,
            op,
            "operation",
            0,
            new org.apache.hop.core.row.value.ValueMetaString(op),
            !op.isBlank()));
  }

  public Object[] values(Object[] row) throws HopTransformException {
    Object[] values = fields.values(row);
    if (basketIndex >= 0
        && (values[basketIndex] == null || values[basketIndex].toString().isBlank())) {
      values[basketIndex] = constantBasket;
    }
    return values;
  }
}
