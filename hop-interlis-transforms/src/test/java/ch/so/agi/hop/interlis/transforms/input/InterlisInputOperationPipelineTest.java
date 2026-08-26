package ch.so.agi.hop.interlis.transforms.input;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.output.InterlisOutputMeta;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.rowstoresult.RowsToResultMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterlisInputOperationPipelineTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void typed_input_exposes_delete_operations() throws Exception {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName(TestData.path("/data/HopIli_Associations_V1_delete.xtf").toString());
    meta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Associations_V1.Data.Person");
    meta.setIncludeTid(true);
    meta.setIncludeBid(true);
    meta.setIncludeOperation(true);

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", meta);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();

    assertThat(engine.getErrors()).isZero();
    List<RowMetaAndData> rows = ((Pipeline) engine).getResultRows();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getRowMeta().getFieldNames()).contains("_ili_operation");
    int operationIndex = rows.get(0).getRowMeta().indexOfValue("_ili_operation");
    assertThat(rows.get(0).getData()[operationIndex]).isEqualTo("DELETE");
  }

  @Test
  void typed_output_writes_the_operation_field() throws Exception {
    Path outputFile = tempDir.resolve("delete-roundtrip.xtf");

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(TestData.path("/data/HopIli_Associations_V1_delete.xtf").toString());
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(TestData.path("/models").toString());
    input.setClassName("HopIli_Associations_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    input.setIncludeOperation(true);

    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setFileName(outputFile.toString());
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(TestData.path("/models").toString());
    output.setClassName("HopIli_Associations_V1.Data.Person");
    output.setObjectIdField("_ili_tid");
    output.setBasketIdField("_ili_bid");
    output.setBasketId("b1");
    output.setOperationField("_ili_operation");
    output.setOverwrite(true);

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    TransformMeta sink = new TransformMeta("INTERLIS Output", output);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();

    assertThat(engine.getErrors()).isZero();

    CompiledInterlisModel model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(TestData.path("/models/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());
    List<InterlisObjectEnvelope> objects = new ArrayList<>();
    try (XtfTransferReader reader =
        XtfTransferReader.open(outputFile, model.transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        if (event.eventType() == InterlisEventType.OBJECT) {
          objects.add(event);
        }
      }
    }
    assertThat(objects).hasSize(1);
    assertThat(objects.get(0).operation())
        .isEqualTo(ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.DELETE);
  }
}
