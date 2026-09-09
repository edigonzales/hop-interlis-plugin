package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.transforms.*;
import ch.so.agi.hop.interlis.transforms.input.*;
import ch.so.agi.hop.interlis.transforms.objecttorow.*;
import ch.so.agi.hop.interlis.transforms.transferinput.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(40)
class BufferLifecyclePipelineTest {
  @TempDir Path temp;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void releases_input_and_object_projection_buffers_after_success_error_and_abort()
      throws Exception {
    for (boolean objectProjection : new boolean[] {false, true}) {
      for (String mode : List.of("success", "error", "abort")) {
        String original =
            Files.readString(TestData.path("/data/HopIli_Associations_V1_mapping.xtf"));
        int start = original.indexOf("<HopIli_Associations_V1:Data ");
        int end =
            original.indexOf("</HopIli_Associations_V1:Data>")
                + "</HopIli_Associations_V1:Data>".length();
        String basket = original.substring(start, end);
        var expanded = new StringBuilder(original.substring(0, start));
        for (int i = 0; i < 8; i++)
          expanded.append(
              basket
                  .replace("ili:bid=\"b1\"", "ili:bid=\"basket" + i + "\"")
                  .replace("ili:tid=\"", "ili:tid=\"b" + i + "_")
                  .replace("ili:ref=\"", "ili:ref=\"b" + i + "_"));
        expanded.append(original.substring(end));
        String text = expanded.toString();
        if (mode.equals("error"))
          text = text.replaceAll("(<HopIli_Associations_V1:Share>)[^<]+", "$1not-a-decimal");
        var file = temp.resolve("baskets-" + objectProjection + "-" + mode + ".xtf");
        Files.writeString(file, text);
        var pm = new PipelineMeta();
        String dirs = TestData.path("/models").toString();
        TransformMeta target;
        if (objectProjection) {
          var source = new InterlisTransferInputMeta();
          source.setDefault();
          source.setFileName(file.toString());
          source.setModelNames("HopIli_Associations_V1");
          source.setModelDirectories(dirs);
          source.setMode("EVENTS");
          var input = new TransformMeta("source", source);
          pm.addTransform(input);
          var projection = new InterlisObjectToRowMeta();
          projection.setDefault();
          projection.setModelNames("HopIli_Associations_V1");
          projection.setModelDirectories(dirs);
          projection.setClassName("HopIli_Associations_V1.Data.Person");
          projection.setAppendEnvelopeFields(true);
          target = new TransformMeta("target", projection);
          pm.addTransform(target);
          pm.addPipelineHop(new PipelineHopMeta(input, target));
        } else {
          var input = new InterlisInputMeta();
          input.setDefault();
          input.setFileName(file.toString());
          input.setModelNames("HopIli_Associations_V1");
          input.setModelDirectories(dirs);
          input.setClassName("HopIli_Associations_V1.Data.Person");
          input.setKeepSourceObject(true);
          target = new TransformMeta("target", input);
          pm.addTransform(target);
        }
        var sink =
            new TransformMeta(
                "result", new org.apache.hop.pipeline.transforms.rowstoresult.RowsToResultMeta());
        pm.addTransform(sink);
        pm.addPipelineHop(new PipelineHopMeta(target, sink));
        var config = new org.apache.hop.pipeline.config.PipelineRunConfiguration();
        var local = new org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration();
        local.setEnginePluginId("Local");
        local.setRowSetSize("2");
        config.setEngineRunConfiguration(local);
        var engine =
            (Pipeline)
                org.apache.hop.pipeline.engine.PipelineEngineFactory.createPipelineEngine(
                    config, pm);
        engine.prepareExecution();
        var consumed = new AtomicInteger();
        engine
            .getTransform("result", 0)
            .addRowListener(
                new RowAdapter() {
                  @Override
                  public void rowReadEvent(IRowMeta schema, Object[] row) {
                    consumed.incrementAndGet();
                    if (mode.equals("abort")) engine.stopAll();
                    try {
                      Thread.sleep(5);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  }
                });
        engine.startThreads();
        engine.waitUntilFinished();
        if (mode.equals("success")) {
          assertThat(engine.getErrors()).isZero();
          assertThat(engine.getResultRows()).hasSize(24);
          for (int i = 0; i < 8; i++) {
            String bid = "basket" + i;
            assertThat(
                    engine.getResultRows().stream()
                        .filter(
                            r -> bid.equals(r.getData()[r.getRowMeta().indexOfValue("_ili_bid")])))
                .hasSize(3);
          }
          assertThat(engine.getResultRows())
              .anySatisfy(
                  r ->
                      assertThat(r.getData()[r.getRowMeta().indexOfValue("Address_ref")])
                          .isEqualTo("b7_a1"));
        } else if (mode.equals("error")) assertThat(engine.getErrors()).isPositive();
        else assertThat(consumed.get()).isPositive();
        var transform = (BaseTransform<?, ?>) engine.getTransform("target", 0);
        Object data = transform.getData();
        for (String fieldName : List.of("basketBuffer", "pendingOutput")) {
          var field = data.getClass().getDeclaredField(fieldName);
          field.setAccessible(true);
          assertThat(field.get(data)).as(objectProjection + " " + mode + " " + fieldName).isNull();
        }
        transform.dispose(); // Cleanup remains idempotent.
      }
    }
  }
}
