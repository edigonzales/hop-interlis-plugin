package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.InterlisTransferWriter;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.io.XtfTransferWriter;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.output.InterlisOutputMeta;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Performance baseline: a large transfer (50k objects, ~4 MB) is read, mapped and written in a
 * streaming fashion. The fixture is generated on the fly (committed test data stays minimal); the
 * pipeline must finish without buffering the whole transfer.
 */
class LargeTransferStreamingTest {

  static final int OBJECT_COUNT = 50_000;

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void large_transfer_roundtrips_in_streaming_fashion() throws Exception {
    CompiledInterlisModel model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(
                        ch.so.agi.hop.interlis.transforms.TestData.path(
                            "/models/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());

    Path inputFile = tempDir.resolve("large-input.xtf");
    try (InterlisTransferWriter writer =
        XtfTransferWriter.open(
            inputFile, model.transferDescription(), List.of("HopIli_Associations_V1"))) {
      writer.startTransfer("hop-interlis-large-test");
      writer.startBasket("HopIli_Associations_V1.Data", "b1");
      for (int i = 0; i < OBJECT_COUNT; i++) {
        ch.interlis.iom_j.Iom_jObject person =
            new ch.interlis.iom_j.Iom_jObject("HopIli_Associations_V1.Data.Person", "p" + i);
        person.setattrvalue("Name", "Person " + i);
        writer.writeObject(person);
      }
      writer.endBasket();
      writer.endTransfer();
    }

    Path outputFile = tempDir.resolve("large-output.xtf");

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile.toString());
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(
        ch.so.agi.hop.interlis.transforms.TestData.path("/models").toString());
    input.setClassName("HopIli_Associations_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);

    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setFileName(outputFile.toString());
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(
        ch.so.agi.hop.interlis.transforms.TestData.path("/models").toString());
    output.setClassName("HopIli_Associations_V1.Data.Person");
    output.setObjectIdField("_ili_tid");
    output.setBasketIdField("_ili_bid");
    output.setBasketId("b1");
    output.setOverwrite(true);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("large-transfer-streaming");
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    TransformMeta sink = new TransformMeta("INTERLIS Output", output);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));

    long start = System.currentTimeMillis();
    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();
    long elapsed = System.currentTimeMillis() - start;

    assertThat(engine.getErrors()).isZero();
    System.out.println(
        "[LargeTransferStreamingTest] " + OBJECT_COUNT + " objects in " + elapsed + " ms");
    assertThat(elapsed)
        .as("streaming roundtrip of %d objects must stay well below the sanity bound", OBJECT_COUNT)
        .isLessThan(180_000L);

    long readBack = 0;
    try (XtfTransferReader reader = XtfTransferReader.open(outputFile, model.transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        if (event.eventType() == InterlisEventType.OBJECT) {
          readBack++;
        }
      }
    }
    assertThat(readBack).isEqualTo(OBJECT_COUNT);
  }
}
