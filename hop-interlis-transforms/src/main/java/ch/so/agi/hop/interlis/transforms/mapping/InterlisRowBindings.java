package ch.so.agi.hop.interlis.transforms.mapping;

import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;

/** A plan-ordered collection of immutable field bindings. No per-row schema lookups. */
public final class InterlisRowBindings {
  private final List<InterlisFieldBinding> fields;

  public InterlisRowBindings(List<InterlisFieldBinding> fields) {
    this.fields = List.copyOf(fields);
    for (int i = 0; i < fields.size(); i++)
      if (fields.get(i).targetIndex() != i)
        throw new IllegalArgumentException("Bindings must be in target order");
  }

  public static InterlisRowBindings bind(IRowMeta input, InterlisRowMappingPlan plan)
      throws HopTransformException {
    var fields = new ArrayList<InterlisFieldBinding>();
    var factory = new HopRowSchemaFactory();
    for (var field : plan.fields())
      fields.add(
          InterlisFieldBinding.bind(
              input,
              plan.root().scopedName(),
              field.hopFieldName(),
              field.hopFieldName(),
              field.outputIndex(),
              factory.createValueMeta(field),
              true));
    return new InterlisRowBindings(fields);
  }

  public Object[] values(Object[] row) throws HopTransformException {
    Object[] values = new Object[fields.size()];
    for (var field : fields) values[field.targetIndex()] = field.read(row);
    return values;
  }
}
