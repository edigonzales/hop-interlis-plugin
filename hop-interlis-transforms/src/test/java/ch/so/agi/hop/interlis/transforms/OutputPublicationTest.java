package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.transforms.output.*;
import ch.so.agi.hop.interlis.transforms.testutil.*;
import ch.so.agi.hop.interlis.transforms.transferoutput.*;
import ch.so.agi.hop.interlis.transforms.validate.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.config.*;
import org.apache.hop.pipeline.engine.*;
import org.apache.hop.pipeline.engines.local.*;
import org.apache.hop.pipeline.transform.*;
import org.apache.hop.pipeline.transforms.rowstoresult.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class OutputPublicationTest {
  @TempDir Path temp;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  RegressionRowsMeta rows(String... bids) {
    var rows = new RegressionRowsMeta();
    rows.schema = new RowMeta();
    rows.schema.addValueMeta(new ValueMetaString("_ili_tid"));
    rows.schema.addValueMeta(new ValueMetaString("_ili_bid"));
    rows.schema.addValueMeta(new ValueMetaString("Name"));
    for (int i = 0; i < bids.length; i++)
      rows.rows.add(new Object[] {"i" + i, bids[i], "item" + i});
    return rows;
  }

  InterlisOutputMeta writer(Path target) {
    var writer = new InterlisOutputMeta();
    writer.setDefault();
    writer.setFileName(target.toString());
    writer.setOverwrite(true);
    writer.setModelNames("HopIli_Collections_V1");
    writer.setModelDirectories(TestData.path("/models").toString());
    writer.setClassName("HopIli_Collections_V1.Data.Target");
    return writer;
  }

  Pipeline prepare(PipelineMeta pm) throws Exception {
    var config = new PipelineRunConfiguration();
    var local = new LocalPipelineRunConfiguration();
    local.setEnginePluginId("Local");
    local.setRowSetSize("1");
    config.setEngineRunConfiguration(local);
    var engine = (Pipeline) PipelineEngineFactory.createPipelineEngine(config, pm);
    engine.prepareExecution();
    return engine;
  }

  @Test
  void late_branch_errors_stop_and_deferred_validation_preserve_previous_file() throws Exception {
    for (String outcome : List.of("success", "error", "stop", "deferred", "validate")) {
      var target = temp.resolve(outcome + ".xtf");
      Files.writeString(target, "previous");
      var pm = new PipelineMeta();
      var source = new TransformMeta("source", rows("b1"));
      var writer = new TransformMeta("writer", writer(target));
      var sink = new TransformMeta("writer result", new RowsToResultMeta());
      pm.addTransform(source);
      pm.addTransform(writer);
      pm.addTransform(sink);
      pm.addPipelineHop(new PipelineHopMeta(source, writer));
      pm.addPipelineHop(new PipelineHopMeta(writer, sink));
      ITransformMeta lateMeta = rows("b1");
      if (outcome.equals("validate")) {
        var validate = new InterlisValidateMeta();
        validate.setDefault();
        validate.setFailOnErrors(true);
        validate.setFileName(TestData.path("/data/HopIli_Enums_V1_invalid.xtf").toString());
        validate.setModelNames("%DATA");
        validate.setModelDirectories(TestData.path("/models").toString());
        lateMeta = validate;
      }
      var late = new TransformMeta("late", lateMeta);
      var lateSink = new TransformMeta("late result", new RowsToResultMeta());
      pm.addTransform(late);
      pm.addTransform(lateSink);
      pm.addPipelineHop(new PipelineHopMeta(late, lateSink));
      var engine = prepare(pm);
      var finished = new CountDownLatch(1);
      engine
          .getTransform("writer", 0)
          .addTransformFinishedListener((pipeline, meta, transform) -> finished.countDown());
      var observed = new java.util.concurrent.atomic.AtomicBoolean();
      engine
          .getTransform("late result", 0)
          .addRowListener(
              new RowAdapter() {
                @Override
                public void rowReadEvent(IRowMeta meta, Object[] row)
                    throws org.apache.hop.core.exception.HopTransformException {
                  try {
                    if (!finished.await(5, TimeUnit.SECONDS))
                      throw new AssertionError("writer did not finish");
                    assertThat(Files.readString(target)).isEqualTo("previous");
                    if (observed.compareAndSet(false, true)) {
                      if (outcome.equals("error"))
                        throw new org.apache.hop.core.exception.HopTransformException(
                            "late branch failure");
                      if (outcome.equals("stop")) engine.stopAll();
                      if (outcome.equals("deferred"))
                        InterlisPipelineCompletion.forPipeline(engine)
                            .deferFailure((BaseTransform<?, ?>) engine.getTransform("late", 0), 1);
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new org.apache.hop.core.exception.HopTransformException(e);
                  } catch (java.io.IOException e) {
                    throw new org.apache.hop.core.exception.HopTransformException(e);
                  }
                }
              });
      engine.startThreads();
      engine.waitUntilFinished();
      assertThat(observed.get()).isTrue();
      if (outcome.equals("success")) {
        assertThat(engine.getErrors()).isZero();
        assertThat(Files.readString(target)).contains("item0");
      } else {
        assertThat(Files.readString(target)).isEqualTo("previous");
        if (!outcome.equals("stop")) assertThat(engine.getErrors()).isPositive();
      }
    }
    try (var files = Files.list(temp)) {
      assertThat(files.toList())
          .noneMatch(file -> file.getFileName().toString().contains("pending"));
    }
  }

  @Test
  void mapping_validation_and_reappearing_basket_errors_do_not_replace_target() throws Exception {
    for (String failure : List.of("mapping", "validation", "basket")) {
      var target = temp.resolve(failure + ".xtf");
      Files.writeString(target, "previous");
      var source = rows("b1");
      var writer = writer(target);
      if (failure.equals("mapping")) source.rows.set(0, new Object[] {"i1", "b1", null});
      if (failure.equals("validation")) {
        writer.setValidateBeforePublish(true);
        source.rows.add(new Object[] {"i0", "b1", "item0"}); // duplicate identity
      }
      if (failure.equals("basket")) source = rows("b1", "b2", "b1");
      var pm = new PipelineMeta();
      var a = new TransformMeta("source", source);
      var b = new TransformMeta("writer", writer);
      pm.addTransform(a);
      pm.addTransform(b);
      pm.addPipelineHop(new PipelineHopMeta(a, b));
      var engine = prepare(pm);
      engine.startThreads();
      engine.waitUntilFinished();
      assertThat(engine.getErrors()).as(failure).isPositive();
      assertThat(Files.readString(target)).isEqualTo("previous");
    }
  }

  @Test
  void no_overwrite_publication_race_is_a_pipeline_error_and_keeps_concurrent_file()
      throws Exception {
    var target = temp.resolve("publication-race.xtf");
    var writer = writer(target);
    writer.setOverwrite(false);
    var pm = new PipelineMeta();
    var source = new TransformMeta("source", rows("b1"));
    var output = new TransformMeta("writer", writer);
    var late = new TransformMeta("late", rows("b1"));
    var sink = new TransformMeta("late result", new RowsToResultMeta());
    for (var transform : List.of(source, output, late, sink)) pm.addTransform(transform);
    pm.addPipelineHop(new PipelineHopMeta(source, output));
    pm.addPipelineHop(new PipelineHopMeta(late, sink));
    var engine = prepare(pm);
    var finished = new CountDownLatch(1);
    engine
        .getTransform("writer", 0)
        .addTransformFinishedListener((pipeline, meta, transform) -> finished.countDown());
    engine
        .getTransform("late result", 0)
        .addRowListener(
            new RowAdapter() {
              @Override
              public void rowReadEvent(IRowMeta meta, Object[] row)
                  throws org.apache.hop.core.exception.HopTransformException {
                try {
                  if (!finished.await(5, TimeUnit.SECONDS))
                    throw new AssertionError("writer did not finish");
                  assertThat(target).doesNotExist();
                  Files.writeString(target, "concurrent", StandardOpenOption.CREATE_NEW);
                } catch (Exception e) {
                  throw new org.apache.hop.core.exception.HopTransformException(e);
                }
              }
            });
    engine.startThreads();
    engine.waitUntilFinished();
    assertThat(engine.getErrors()).isPositive();
    assertThat(Files.readString(target)).isEqualTo("concurrent");
    try (var files = Files.list(temp)) {
      assertThat(files.toList())
          .noneMatch(file -> file.getFileName().toString().contains("pending"));
    }
  }

  @Test
  void generic_object_and_event_modes_reject_reused_baskets_and_keep_previous_output()
      throws Exception {
    for (boolean events : List.of(false, true)) {
      var target = temp.resolve("generic-" + events + ".xtf");
      Files.writeString(target, "previous");
      var source = new RegressionRowsMeta();
      source.schema = InterlisEnvelopeSchemaFactory.createRowMeta();
      if (events)
        source.rows.add(
            event(ch.so.agi.hop.interlis.core.io.InterlisEventType.START_TRANSFER, null));
      for (String bid : List.of("b1", "b2", "b1")) {
        if (events)
          source.rows.add(
              event(ch.so.agi.hop.interlis.core.io.InterlisEventType.START_BASKET, bid));
        var object =
            new ch.interlis.iom_j.Iom_jObject(
                "HopIli_Collections_V1.Data.Target", "t" + source.rows.size());
        object.setattrvalue("Name", "target");
        source.rows.add(
            ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout.objectRow(
                object,
                bid,
                "HopIli_Collections_V1.Data",
                ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.NONE));
        if (events)
          source.rows.add(event(ch.so.agi.hop.interlis.core.io.InterlisEventType.END_BASKET, bid));
      }
      if (events)
        source.rows.add(event(ch.so.agi.hop.interlis.core.io.InterlisEventType.END_TRANSFER, null));
      var writer = new InterlisTransferOutputMeta();
      writer.setDefault();
      writer.setFileName(target.toString());
      writer.setOverwrite(true);
      writer.setEventMode(events);
      writer.setModelNames("HopIli_Collections_V1");
      writer.setModelDirectories(TestData.path("/models").toString());
      var pm = new PipelineMeta();
      var a = new TransformMeta("source", source);
      var b = new TransformMeta("writer", writer);
      pm.addTransform(a);
      pm.addTransform(b);
      pm.addPipelineHop(new PipelineHopMeta(a, b));
      var engine = prepare(pm);
      engine.startThreads();
      engine.waitUntilFinished();
      assertThat(engine.getErrors()).isPositive();
      assertThat(Files.readString(target)).isEqualTo("previous");
    }
    try (var files = Files.list(temp)) {
      assertThat(files.toList())
          .noneMatch(file -> file.getFileName().toString().contains("pending"));
    }
  }

  private Object[] event(ch.so.agi.hop.interlis.core.io.InterlisEventType type, String bid) {
    return ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout.toRow(
        new ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope(
            type,
            "HopIli_Collections_V1",
            "HopIli_Collections_V1.Data",
            bid,
            null,
            null,
            ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.NONE,
            null));
  }
}
