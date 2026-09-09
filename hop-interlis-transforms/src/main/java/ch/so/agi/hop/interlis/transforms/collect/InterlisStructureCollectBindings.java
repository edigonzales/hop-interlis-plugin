package ch.so.agi.hop.interlis.transforms.collect;

import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import java.util.ArrayList;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.IVariables;

final class InterlisStructureCollectBindings {
  record Parent(InterlisFieldBinding key, InterlisFieldBinding carrier, IRowMeta output) {
    Parent {
      output = output.clone();
    }

    @Override
    public IRowMeta output() {
      return output.clone();
    }
  }

  record Child(InterlisFieldBinding key, InterlisFieldBinding index, InterlisRowBindings values) {}

  static Parent parent(IRowMeta input, InterlisStructureCollectMeta meta, IVariables vars)
      throws HopTransformException {
    String key = InterlisFieldBinding.resolve(vars, meta.getParentKeyField());
    String carrier = InterlisFieldBinding.resolve(vars, meta.getSourceObjectField());
    return new Parent(
        InterlisFieldBinding.bind(
            input,
            "INTERLIS Structure Collect parent",
            key,
            "parent key",
            0,
            new ValueMetaString(key),
            true),
        InterlisFieldBinding.bind(
            input,
            "INTERLIS Structure Collect parent",
            carrier,
            "source object",
            0,
            new ValueMetaInterlisObject(carrier),
            true),
        output(input, meta, vars));
  }

  static IRowMeta output(IRowMeta input, InterlisStructureCollectMeta meta, IVariables vars)
      throws HopTransformException {
    var output = input.clone();
    String name = InterlisFieldBinding.resolve(vars, meta.getSourceObjectField());
    var carrier =
        InterlisFieldBinding.bind(
            input,
            "INTERLIS Structure Collect parent",
            name,
            "source object",
            0,
            new ValueMetaInterlisObject(name),
            false);
    if (carrier.present()) {
      var field = output.getValueMeta(carrier.sourceIndex());
      field.setStorageType(org.apache.hop.core.row.IValueMeta.STORAGE_TYPE_NORMAL);
      field.setStorageMetadata(null);
      field.setIndex(null);
    }
    return output;
  }

  static Child child(
      IRowMeta input,
      InterlisStructurePlan plan,
      InterlisStructureCollectMeta meta,
      IVariables vars)
      throws HopTransformException {
    String context = "INTERLIS Structure Collect child";
    String key = InterlisFieldBinding.resolve(vars, meta.getChildParentKeyField());
    String index = InterlisFieldBinding.resolve(vars, meta.getChildIndexField());
    var fields = new ArrayList<InterlisFieldBinding>();
    var factory = new HopRowSchemaFactory();
    for (var field : plan.childFields())
      fields.add(
          InterlisFieldBinding.bind(
              input,
              context,
              field.hopFieldName(),
              field.hopFieldName(),
              fields.size(),
              factory.createValueMeta(field),
              true));
    return new Child(
        InterlisFieldBinding.bind(
            input, context, key, "parent key", 0, new ValueMetaString(key), true),
        InterlisFieldBinding.bind(
            input, context, index, "index", 0, new ValueMetaInteger(index), plan.ordered()),
        new InterlisRowBindings(fields));
  }
}
