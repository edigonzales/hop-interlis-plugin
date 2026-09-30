package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import java.util.*;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;

final class InterlisRoleJoinBindings {
  record Main(InterlisFieldBinding reference, IRowMeta output) {
    Main {
      output = output.clone();
    }

    @Override
    public IRowMeta output() {
      return output.clone();
    }
  }

  record Lookup(InterlisFieldBinding tid, InterlisRowBindings fields) {}

  private static List<InterlisFieldPlan> fields(
      InterlisRoleJoinProbeResult probe, InterlisRoleJoinMeta meta, IVariables vars)
      throws HopTransformException {
    var selected = new java.util.HashSet<String>();
    for (String field : meta.effectiveLookupFields(probe))
      selected.add(InterlisFieldBinding.resolve(vars, field));
    try {
      var options =
          new ProjectionOptions(false, false, false, false, false, true, "_", null, selected);
      var fields =
          new InterlisRowSchemaBuilder()
              .build(probe.schema(), probe.target(), options).fields().stream()
                  .filter(field -> field.attributeDescriptor() != null)
                  .toList();
      if (meta.getLookupFields() != null && !meta.getLookupFields().isEmpty()) {
        for (String path : selected) {
          if (fields.stream()
              .noneMatch(
                  field ->
                      field.propertyPath().dotted().equals(path)
                          || field.propertyPath().dotted().startsWith(path + "."))) {
            throw new HopTransformException(
                "INTERLIS Role Join lookup: no supported scalar projection for "
                    + probe.target().scopedName()
                    + "."
                    + path
                    + "; collections require Structure Explode/Collect");
          }
        }
      }
      return fields;
    } catch (InterlisMappingException e) {
      throw new HopTransformException("INTERLIS Role Join lookup: " + e.getMessage(), e);
    }
  }

  static Main main(
      IRowMeta input, InterlisRoleJoinProbeResult probe, InterlisRoleJoinMeta meta, IVariables vars)
      throws HopTransformException {
    String name = InterlisFieldBinding.resolve(vars, meta.resolvedMainReferenceField(probe));
    var reference =
        InterlisFieldBinding.bind(
            input,
            "INTERLIS Role Join main",
            name,
            "reference",
            0,
            new ValueMetaString(name),
            true);
    return new Main(reference, output(input, probe, meta, vars));
  }

  static IRowMeta output(
      IRowMeta input, InterlisRoleJoinProbeResult probe, InterlisRoleJoinMeta meta, IVariables vars)
      throws HopTransformException {
    var output = input.clone();
    var factory = new HopRowSchemaFactory();
    String prefix = InterlisFieldBinding.resolve(vars, meta.resolvedPrefix(probe));
    for (var field : fields(probe, meta, vars)) {
      var value = factory.createValueMeta(field);
      value.setName(prefix + field.hopFieldName());
      InterlisFieldBinding.addUnique(output, value, "INTERLIS Role Join output");
    }
    return output;
  }

  static Lookup lookup(
      IRowMeta input, InterlisRoleJoinProbeResult probe, InterlisRoleJoinMeta meta, IVariables vars)
      throws HopTransformException {
    String context = "INTERLIS Role Join lookup";
    String name = InterlisFieldBinding.resolve(vars, meta.getLookupTidField());
    var tid =
        InterlisFieldBinding.bind(
            input, context, name, "lookup TID", 0, new ValueMetaString(name), true);
    var bindings = new ArrayList<InterlisFieldBinding>();
    var factory = new HopRowSchemaFactory();
    for (var field : fields(probe, meta, vars))
      bindings.add(
          InterlisFieldBinding.bind(
              input,
              context,
              field.hopFieldName(),
              InterlisFieldBinding.resolve(vars, meta.resolvedPrefix(probe)) + field.hopFieldName(),
              field.outputIndex(),
              factory.createValueMeta(field),
              true));
    return new Lookup(tid, new InterlisRowBindings(bindings));
  }
}
