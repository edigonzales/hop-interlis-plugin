package ch.so.agi.hop.interlis.transforms.mapping;

import java.util.List;
import org.apache.hop.pipeline.transform.*;
import org.apache.hop.pipeline.transform.stream.*;

/** INFO inputs keep heterogeneous row schemas separate, including in Hop Safe Mode. */
public final class MappedInputStreams {
  private MappedInputStreams() {}

  public static ITransformIOMeta create(List<InterlisMappedInput> inputs) {
    var io = new TransformIOMeta(true, false, false, false, true, false);
    for (var input : inputs)
      io.addStream(
          new Stream(
              IStream.StreamType.INFO,
              null,
              input.getClassName(),
              StreamIcon.INFO,
              input.getTransformName()));
    return io;
  }

  public static void resolve(
      ITransformIOMeta io, List<InterlisMappedInput> inputs, List<TransformMeta> transforms) {
    var streams = io.getInfoStreams();
    for (int i = 0; i < inputs.size(); i++) {
      var input = inputs.get(i);
      var stream = streams.get(i);
      // A live transform reference survives a rename. Never remove a missing mapping silently.
      var target = stream.getTransformMeta();
      if (target != null && transforms.contains(target)) input.setTransformName(target.getName());
      else target = TransformMeta.findTransform(transforms, input.getTransformName());
      stream.setTransformMeta(target);
      stream.setSubject(input.getTransformName());
    }
  }
}
