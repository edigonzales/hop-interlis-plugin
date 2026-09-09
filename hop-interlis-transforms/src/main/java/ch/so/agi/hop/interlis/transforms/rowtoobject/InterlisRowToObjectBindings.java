package ch.so.agi.hop.interlis.transforms.rowtoobject;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.*;
import org.apache.hop.core.variables.IVariables;

/** Shared design/runtime bindings for inverse projection and optional envelope context. */
record InterlisRowToObjectBindings(
    int[] values, int basket, int source, int operation, int[] basketMetadata) {
  static InterlisRowToObjectBindings bind(
      IRowMeta input, InterlisRowMappingPlan plan, InterlisRowToObjectMeta meta, IVariables vars)
      throws HopException {
    String basketName = resolve(vars, meta.getBasketIdField());
    int basket = index(input, basketName, "", IValueMeta.TYPE_STRING);
    int[] values = new int[plan.fieldCount()];
    for (var field : plan.fields()) {
      if (field.source() == InterlisFieldSource.BASKET_ID) values[field.outputIndex()] = basket;
      else {
        int index = input.indexOfValue(field.hopFieldName());
        if (index < 0)
          throw new HopException(
              "Incoming row is missing field <"
                  + field.hopFieldName()
                  + "> for "
                  + plan.root().scopedName());
        values[field.outputIndex()] = index;
      }
    }
    String sourceName = resolve(vars, meta.getSourceObjectField());
    int source =
        index(
            input, sourceName, "_ili_source_object", ValueMetaInterlisObject.TYPE_INTERLIS_OBJECT);
    if (source < 0 && sourceName.isBlank())
      source = index(input, "", "_ili_object", ValueMetaInterlisObject.TYPE_INTERLIS_OBJECT);
    int operation =
        index(
            input,
            resolve(vars, meta.getOperationField()),
            "_ili_operation",
            IValueMeta.TYPE_STRING);
    String[] basketNames = {
      InterlisEnvelopeRowLayout.BASKET_CONSISTENCY,
      InterlisEnvelopeRowLayout.BASKET_KIND,
      InterlisEnvelopeRowLayout.BASKET_START_STATE,
      InterlisEnvelopeRowLayout.BASKET_END_STATE
    };
    int[] metadata = new int[4];
    for (int i = 0; i < metadata.length; i++)
      metadata[i] = index(input, "", basketNames[i], IValueMeta.TYPE_STRING);
    return new InterlisRowToObjectBindings(values, basket, source, operation, metadata);
  }

  private static int index(IRowMeta input, String configured, String fallback, int type)
      throws HopException {
    String name = configured.isBlank() ? fallback : configured;
    int index = name.isBlank() ? -1 : input.indexOfValue(name);
    if (index < 0 && !configured.isBlank())
      throw new HopException("Configured field <" + name + "> not found");
    if (index >= 0 && input.getValueMeta(index).getType() != type)
      throw new HopException("Incompatible type for field <" + name + ">");
    return index;
  }

  private static String resolve(IVariables vars, String value) {
    return value == null ? "" : (vars == null ? value : vars.resolve(value)).trim();
  }
}
