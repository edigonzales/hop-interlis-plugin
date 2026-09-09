package ch.so.agi.hop.interlis.transforms.explode;

import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import java.util.*;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.IVariables;

/** Shared input and output layout for structure expansion. */
record InterlisStructureExplodeBindings(
    InterlisFieldBinding carrier,
    InterlisFieldBinding tid,
    InterlisFieldBinding bid,
    List<InterlisFieldBinding> parentFields,
    IRowMeta output) {
  InterlisStructureExplodeBindings {
    parentFields = List.copyOf(parentFields);
    output = output.clone();
  }

  @Override
  public IRowMeta output() {
    return output.clone();
  }

  static InterlisStructureExplodeBindings bind(
      IRowMeta input,
      InterlisStructurePlan plan,
      InterlisStructureExplodeMeta meta,
      IVariables vars)
      throws HopTransformException {
    return create(input, plan, meta, vars, true);
  }

  static IRowMeta output(
      IRowMeta input,
      InterlisStructurePlan plan,
      InterlisStructureExplodeMeta meta,
      IVariables vars)
      throws HopTransformException {
    return create(input, plan, meta, vars, false).output();
  }

  private static InterlisStructureExplodeBindings create(
      IRowMeta input,
      InterlisStructurePlan plan,
      InterlisStructureExplodeMeta meta,
      IVariables vars,
      boolean bindInputs)
      throws HopTransformException {
    String context = "INTERLIS Structure Explode parent";
    String source = InterlisFieldBinding.resolve(vars, meta.getSourceObjectField());
    var carrier =
        InterlisFieldBinding.bind(
            input,
            context,
            source,
            "source object",
            0,
            new ValueMetaInterlisObject(source),
            bindInputs);
    var output = new RowMeta();
    String tidName = InterlisFieldBinding.resolve(vars, meta.resolvedParentKeyFieldName());
    InterlisFieldBinding.addUnique(output, new ValueMetaString(tidName), context);
    var tid =
        InterlisFieldBinding.bind(
            input,
            context,
            InterlisFieldBinding.resolve(vars, meta.getParentTidField()),
            tidName,
            0,
            new ValueMetaString(tidName),
            bindInputs);
    String bidSource =
        meta.isEmitParentBid() ? InterlisFieldBinding.resolve(vars, meta.getParentBidField()) : "";
    String bidName = InterlisFieldBinding.resolve(vars, meta.resolvedParentBidKeyFieldName());
    var bid =
        InterlisFieldBinding.bind(
            input,
            context,
            bidSource,
            bidName,
            1,
            new ValueMetaString(bidName),
            bindInputs && !bidSource.isBlank());
    if (meta.isEmitParentBid())
      InterlisFieldBinding.addUnique(output, new ValueMetaString(bidName), context);
    if (plan.ordered() || meta.isEmitIndexForBag())
      InterlisFieldBinding.addUnique(
          output,
          new ValueMetaInteger(InterlisFieldBinding.resolve(vars, meta.resolvedIndexFieldName())),
          context);
    var factory = new HopRowSchemaFactory();
    for (var field : plan.childFields())
      InterlisFieldBinding.addUnique(output, factory.createValueMeta(field), context);
    var parents = new ArrayList<InterlisFieldBinding>();
    for (String configured :
        meta.getIncludeParentFields() == null ? List.<String>of() : meta.getIncludeParentFields()) {
      String name = InterlisFieldBinding.resolve(vars, configured);
      var binding =
          InterlisFieldBinding.bind(input, context, name, name, output.size(), null, true);
      InterlisFieldBinding.addUnique(output, binding.sourceMeta(), context);
      parents.add(binding);
    }
    return new InterlisStructureExplodeBindings(carrier, tid, bid, parents, output);
  }
}
