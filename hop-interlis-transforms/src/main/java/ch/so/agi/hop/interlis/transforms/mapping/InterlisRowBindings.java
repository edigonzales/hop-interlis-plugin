package ch.so.agi.hop.interlis.transforms.mapping;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;

/**
 * Binds an incoming Hop row to the field order of a {@link InterlisRowMappingPlan}.
 *
 * <p>The binding is computed once per transform initialization; the runtime mapper then works
 * with plan-ordered value arrays without any per-row name lookups.
 */
public final class InterlisRowBindings {

  private InterlisRowBindings() {}

  /**
   * Computes the input index for every plan field.
   *
   * @param inputRowMeta incoming row structure
   * @param plan the projection the values are mapped back with
   * @return input index per plan field, in plan field order
   * @throws HopTransformException if a required plan field is missing from the input
   */
  public static int[] bind(IRowMeta inputRowMeta, InterlisRowMappingPlan plan)
      throws HopTransformException {
    int[] inputIndexes = new int[plan.fieldCount()];
    for (InterlisFieldPlan field : plan.fields()) {
      int index = inputRowMeta.indexOfValue(field.hopFieldName());
      if (index < 0) {
        throw new HopTransformException(
            "Incoming row is missing field <"
                + field.hopFieldName()
                + "> required by "
                + plan.root().scopedName());
      }
      inputIndexes[field.outputIndex()] = index;
    }
    return inputIndexes;
  }

  /** Converts an incoming row to plan-ordered values. */
  public static Object[] values(Object[] row, int[] inputIndexes) {
    Object[] values = new Object[inputIndexes.length];
    for (int i = 0; i < inputIndexes.length; i++) {
      values[i] = row[inputIndexes[i]];
    }
    return values;
  }
}
