package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import java.util.Arrays;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;

/** Shared metadata and value layout for both streaming and basket-buffered projections. */
public record InterlisObjectToRowOutputPlan(
    IRowMeta rowMeta, boolean append, int[] targetIndexes, int inputFieldCount) {
  public static InterlisObjectToRowOutputPlan create(
      IRowMeta input, InterlisRowMappingPlan plan, boolean append) throws HopTransformException {
    IRowMeta typed = new HopRowSchemaFactory().createRowMeta(plan);
    IRowMeta output = append ? input.clone() : typed;
    int[] targets = new int[typed.size()];
    for (var field : plan.fields()) {
      int i = field.outputIndex();
      var value = typed.getValueMeta(i);
      if (!append) {
        targets[i] = i;
        continue;
      }
      int existing =
          ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding.find(
              output, value.getName(), "INTERLIS Object to Row append");
      if (existing >= 0) {
        boolean identity =
            switch (field.source()) {
              case OBJECT_ID, BASKET_ID, CLASS_NAME, TOPIC_NAME, OPERATION -> true;
              default -> false;
            };
        if (!identity || output.getValueMeta(existing).getType() != value.getType()) {
          throw new HopTransformException(
              "Append projection field <"
                  + value.getName()
                  + "> collides with an input field. Only technical identity fields with matching"
                  + " types can be reused.");
        }
        targets[i] = existing;
      } else {
        targets[i] = output.size();
        output.addValueMeta(value);
      }
    }
    return new InterlisObjectToRowOutputPlan(output, append, targets, input.size());
  }

  public Object[] values(Object[] input, Object[] typed) {
    if (!append) return typed;
    Object[] output = Arrays.copyOf(input, rowMeta.size());
    for (int i = 0; i < typed.length; i++) {
      // Existing technical fields belong to the envelope and retain their original value.
      if (targetIndexes[i] >= inputFieldCount) output[targetIndexes[i]] = typed[i];
    }
    return output;
  }
}
