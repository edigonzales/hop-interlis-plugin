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
public record InterlisOutputBindings(int[] indexes, int basketIndex, String constantBasket) {
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
    int[] indexes = new int[plan.fieldCount()];
    int basketIndex = -1;
    for (InterlisFieldPlan field : plan.fields()) {
      String name = sourceName(field, meta, vars);
      boolean constant = field.source() == InterlisFieldSource.BASKET_ID && name.isBlank();
      int index = constant ? -1 : input.indexOfValue(name);
      if (!constant && index < 0) {
        throw new HopTransformException(
            "Incoming row is missing field <"
                + name
                + "> required by "
                + plan.root().scopedName()
                + " ("
                + field.source()
                + ")");
      }
      indexes[field.outputIndex()] = index;
      if (field.source() == InterlisFieldSource.BASKET_ID) basketIndex = field.outputIndex();
    }
    String basket = meta.getBasketId() == null ? "" : vars.resolve(meta.getBasketId());
    return new InterlisOutputBindings(indexes, basketIndex, basket);
  }

  public Object[] values(Object[] row) {
    Object[] values = new Object[indexes.length];
    for (int i = 0; i < indexes.length; i++) values[i] = indexes[i] < 0 ? null : row[indexes[i]];
    if (basketIndex >= 0
        && (values[basketIndex] == null || values[basketIndex].toString().isBlank())) {
      values[basketIndex] = constantBasket;
    }
    return values;
  }
}
