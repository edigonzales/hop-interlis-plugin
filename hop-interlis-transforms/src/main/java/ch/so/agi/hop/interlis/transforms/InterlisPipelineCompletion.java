package ch.so.agi.hop.interlis.transforms;

import ch.so.agi.hop.interlis.core.io.PreparedXtfOutput;
import java.util.*;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.transform.BaseTransform;

/** Per-pipeline ownership; diagnostic failures are applied before prepared files are published. */
public final class InterlisPipelineCompletion {
  private record Output(PreparedXtfOutput file, BaseTransform<?, ?> transform) {}

  private final List<Output> outputs = new ArrayList<>();
  private final Map<BaseTransform<?, ?>, Long> failures = new IdentityHashMap<>();
  private boolean finished;

  public static InterlisPipelineCompletion forPipeline(IPipelineEngine<PipelineMeta> pipeline) {
    synchronized (pipeline) {
      String key = InterlisPipelineCompletion.class.getName();
      var found = (InterlisPipelineCompletion) pipeline.getExtensionDataMap().get(key);
      if (found != null && !found.finished) return found;
      var coordinator = new InterlisPipelineCompletion();
      pipeline.getExtensionDataMap().put(key, coordinator);
      pipeline.addExecutionFinishedListener(engine -> coordinator.finish(pipeline));
      return coordinator;
    }
  }

  public synchronized void register(PreparedXtfOutput file, BaseTransform<?, ?> transform) {
    if (finished) throw new IllegalStateException("Pipeline already completed");
    if (outputs.stream().anyMatch(output -> output.file().target().equals(file.target())))
      throw new IllegalStateException(
          "Multiple INTERLIS writers target the same file: " + file.target());
    outputs.add(new Output(file, transform));
  }

  public synchronized void deferFailure(BaseTransform<?, ?> transform, long count) {
    failures.merge(transform, count, Long::sum);
  }

  private synchronized void finish(IPipelineEngine<PipelineMeta> pipeline) {
    if (finished) return;
    finished = true;
    failures.forEach((transform, count) -> transform.setErrors(transform.getErrors() + count));
    boolean success = !pipeline.isStopped() && pipeline.getResult().getNrErrors() == 0;
    HopException failure = null;
    if (success)
      for (var output : outputs)
        if (!output.file().ready()) {
          output.transform().setErrors(output.transform().getErrors() + 1);
          success = false;
          failure =
              new HopException(
                  "INTERLIS output was not completely prepared: " + output.file().target());
        }
    for (var output : outputs) {
      try {
        if (success && output.file().ready()) output.file().publish();
      } catch (Exception e) {
        success = false;
        output.transform().setErrors(output.transform().getErrors() + 1);
        failure = new HopException("Failed to publish INTERLIS output: " + e.getMessage(), e);
      } finally {
        try {
          output.file().close();
        } catch (Exception e) {
          success = false;
          output.transform().setErrors(output.transform().getErrors() + 1);
          if (failure == null) failure = new HopException("Failed to clean up INTERLIS output", e);
          else failure.addSuppressed(e);
        }
      }
    }
    outputs.clear();
    failures.clear();
    // Hop 2.19 does not signal waitUntilFinished when a completion listener throws.
    // Errors are already assigned to the writer; log here and allow Hop to finish.
    if (failure != null)
      pipeline.getLogChannel().logError("INTERLIS output completion failed", failure);
  }
}
