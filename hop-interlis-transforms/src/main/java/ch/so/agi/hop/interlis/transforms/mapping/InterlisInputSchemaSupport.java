package ch.so.agi.hop.interlis.transforms.mapping;

import java.util.List;
import org.apache.hop.core.*;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Design-time lookup of separately configured streams; runtime always binds actual metadata. */
public final class InterlisInputSchemaSupport {
  private InterlisInputSchemaSupport() {}

  public static IRowMeta available(
      PipelineMeta pipeline,
      String configured,
      IVariables vars,
      String stream,
      List<ICheckResult> remarks,
      TransformMeta owner) {
    String name = InterlisFieldBinding.resolve(vars, configured);
    try {
      var source = pipeline == null ? null : pipeline.findTransform(name);
      if (source == null)
        throw new IllegalArgumentException("input transform <" + name + "> is unavailable");
      var fields = pipeline.getTransformFields(vars, source);
      if (fields == null || fields.isEmpty())
        throw new IllegalArgumentException("input schema is not available yet");
      return fields;
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              "Cannot check " + stream + " bindings yet: " + e.getMessage(),
              owner));
      return null;
    }
  }
}
