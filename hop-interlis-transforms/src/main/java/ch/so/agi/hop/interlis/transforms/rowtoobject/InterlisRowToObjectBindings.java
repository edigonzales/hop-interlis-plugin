package ch.so.agi.hop.interlis.transforms.rowtoobject;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import java.util.*;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;

record InterlisRowToObjectBindings(
    InterlisRowBindings values,
    InterlisFieldBinding basket,
    InterlisFieldBinding source,
    InterlisFieldBinding operation,
    List<InterlisFieldBinding> basketMetadata) {
  InterlisRowToObjectBindings {
    basketMetadata = List.copyOf(basketMetadata);
  }

  static InterlisRowToObjectBindings bind(
      IRowMeta input, InterlisRowMappingPlan plan, InterlisRowToObjectMeta meta, IVariables vars)
      throws HopTransformException {
    String context = "INTERLIS Row to Object " + plan.root().scopedName();
    String basketName = InterlisFieldBinding.resolve(vars, meta.getBasketIdField());
    var basket =
        InterlisFieldBinding.bind(
            input,
            context,
            basketName,
            "basket",
            0,
            new ValueMetaString("basket"),
            !basketName.isBlank());
    var fields = new ArrayList<InterlisFieldBinding>();
    var factory = new HopRowSchemaFactory();
    for (var field : plan.fields()) {
      boolean bid = field.source() == InterlisFieldSource.BASKET_ID;
      String name = bid ? basketName : field.hopFieldName();
      fields.add(
          InterlisFieldBinding.bind(
              input,
              context,
              name,
              field.hopFieldName(),
              field.outputIndex(),
              factory.createValueMeta(field),
              !bid || !name.isBlank()));
    }
    String sourceName = InterlisFieldBinding.resolve(vars, meta.getSourceObjectField());
    String selected = sourceName;
    if (selected.isBlank())
      selected =
          InterlisFieldBinding.find(input, "_ili_source_object", context) >= 0
              ? "_ili_source_object"
              : "_ili_object";
    var source =
        InterlisFieldBinding.bind(
            input,
            context,
            selected,
            "source object",
            0,
            new ValueMetaInterlisObject(selected),
            !sourceName.isBlank());
    String op = InterlisFieldBinding.resolve(vars, meta.getOperationField());
    var operation =
        InterlisFieldBinding.bind(
            input,
            context,
            op.isBlank() ? "_ili_operation" : op,
            "operation",
            0,
            new ValueMetaString("operation"),
            !op.isBlank());
    String[] names = {
      InterlisEnvelopeRowLayout.BASKET_CONSISTENCY,
      InterlisEnvelopeRowLayout.BASKET_KIND,
      InterlisEnvelopeRowLayout.BASKET_START_STATE,
      InterlisEnvelopeRowLayout.BASKET_END_STATE
    };
    var metadata = new ArrayList<InterlisFieldBinding>();
    for (String name : names)
      metadata.add(
          InterlisFieldBinding.bind(
              input, context, name, name, metadata.size(), new ValueMetaString(name), false));
    return new InterlisRowToObjectBindings(
        new InterlisRowBindings(fields), basket, source, operation, metadata);
  }
}
