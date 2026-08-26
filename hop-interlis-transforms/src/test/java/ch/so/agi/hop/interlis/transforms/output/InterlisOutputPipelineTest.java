package ch.so.agi.hop.interlis.transforms.output;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
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

class InterlisOutputPipelineTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void input_output_roundtrip_writes_a_readable_xtf() throws Exception {
    Path outputFile = tempDir.resolve("roundtrip.xtf");

    // INTERLIS Input (geometry XTF) -> INTERLIS Output -> Rows to result
    InterlisInputMeta inputMeta = new InterlisInputMeta();
    inputMeta.setFileName(TestData.path("/data/HopIli_Geometry_V1_valid.xtf").toString());
    inputMeta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    inputMeta.setModelDirectories(TestData.path("/models").toString());
    inputMeta.setClassName("HopIli_Geometry_V1.Data.TestObject");
    inputMeta.setIncludeTid(true);
    inputMeta.setIncludeBid(true);

    InterlisOutputMeta outputMeta = new InterlisOutputMeta();
    outputMeta.setFileName(outputFile.toString());
    outputMeta.setModelNames("HopIli_Geometry_V1");
    outputMeta.setModelDirectories(TestData.path("/models").toString());
    outputMeta.setClassName("HopIli_Geometry_V1.Data.TestObject");
    outputMeta.setObjectIdField("_ili_tid");
    outputMeta.setBasketIdField("_ili_bid");
    outputMeta.setBasketId("b1");
    outputMeta.setOverwrite(true);

    List<RowMetaAndData> rows = run(inputMeta, outputMeta).getResultRows();
    assertThat(rows).hasSize(2);

    // Re-read the written transfer and compare the objects semantically.
    ch.so.agi.hop.interlis.core.model.CompiledInterlisModel model =
        new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl()
            .compile(
                new ch.so.agi.hop.interlis.core.model.ModelSource(
                    List.of(TestData.path("/models/HopIli_Geometry_V1.ili")),
                    List.of(),
                    List.of()),
                ch.so.agi.hop.interlis.core.model.ModelCompileOptions.defaults());
    List<InterlisObjectEnvelope> events = readAll(outputFile, model.transferDescription());
    List<InterlisObjectEnvelope> objects = events.stream()
        .filter(e -> e.eventType() == InterlisEventType.OBJECT)
        .toList();
    assertThat(objects).hasSize(2);

    InterlisObjectEnvelope o1 = objects.get(0);
    assertThat(o1.objectId()).isEqualTo("o1");
    assertThat(o1.basketId()).isEqualTo("b1");
    assertThat(o1.object().getattrvalue("Name")).isEqualTo("A");
    assertThat(o1.object().getattrobj("Center", 0).getobjecttag()).isEqualTo("COORD");
    assertThat(o1.object().getattrobj("Axis", 0).getobjecttag()).isEqualTo("POLYLINE");

    // The second object's axis must still contain an ARC segment.
    InterlisObjectEnvelope o2 = objects.get(1);
    ch.interlis.iom.IomObject axis = o2.object().getattrobj("Axis", 0);
    ch.interlis.iom.IomObject sequence = axis.getattrobj("sequence", 0);
    ch.interlis.iom.IomObject arcSegment = sequence.getattrobj("segment", 1);
    assertThat(arcSegment.getobjecttag()).isEqualTo("ARC");
    // Lexical formatting may differ; compare numerically.
    assertThat(Double.parseDouble(arcSegment.getattrvalue("A1"))).isEqualTo(2600050.0);
    assertThat(Double.parseDouble(arcSegment.getattrvalue("A2"))).isEqualTo(1200050.0);
  }

  @Test
  void structures_roles_and_inheritance_roundtrip() throws Exception {
    Path outputFile = tempDir.resolve("spike-roundtrip.xtf");

    InterlisInputMeta inputMeta = new InterlisInputMeta();
    inputMeta.setFileName(TestData.path("/data/HopIli_Spike_V1_valid.xtf").toString());
    inputMeta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    inputMeta.setModelDirectories(TestData.path("/models").toString());
    inputMeta.setClassName("HopIli_Spike_V1.Data.Building");
    inputMeta.setIncludeTid(true);
    inputMeta.setIncludeBid(true);

    InterlisOutputMeta outputMeta = new InterlisOutputMeta();
    outputMeta.setFileName(outputFile.toString());
    outputMeta.setModelNames("HopIli_Spike_V1");
    outputMeta.setModelDirectories(TestData.path("/models").toString());
    outputMeta.setClassName("HopIli_Spike_V1.Data.Building");
    outputMeta.setObjectIdField("_ili_tid");
    outputMeta.setBasketIdField("_ili_bid");
    outputMeta.setBasketId("b1");
    outputMeta.setOverwrite(true);

    run(inputMeta, outputMeta);

    ch.so.agi.hop.interlis.core.model.CompiledInterlisModel model =
        new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl()
            .compile(
                new ch.so.agi.hop.interlis.core.model.ModelSource(
                    List.of(TestData.path("/models/HopIli_Spike_V1.ili")),
                    List.of(),
                    List.of()),
                ch.so.agi.hop.interlis.core.model.ModelCompileOptions.defaults());
    List<InterlisObjectEnvelope> objects = readAll(outputFile, model.transferDescription()).stream()
        .filter(e -> e.eventType() == InterlisEventType.OBJECT)
        .toList();
    assertThat(objects).hasSize(1);

    ch.interlis.iom.IomObject building = objects.get(0).object();
    assertThat(building.getobjecttag()).isEqualTo("HopIli_Spike_V1.Data.Building");
    assertThat(building.getobjectoid()).isEqualTo("g1");
    assertThat(building.getattrvalue("Note")).isEqualTo("inherited note");
    assertThat(building.getattrvalue("Code")).isEqualTo("42");
    assertThat(building.getattrobj("Address", 0).getattrvalue("Street")).isEqualTo("Main Street");
    assertThat(building.getattrobj("Address", 0).getattrvalue("Number")).isEqualTo("10");
    assertThat(building.getattrobj("Municipality", 0).getobjectrefoid()).isEqualTo("m1");
  }

  @Test
  void existing_output_file_fails_without_overwrite() throws Exception {
    Path outputFile = tempDir.resolve("exists.xtf");
    java.nio.file.Files.writeString(outputFile, "x");

    InterlisInputMeta inputMeta = new InterlisInputMeta();
    inputMeta.setFileName(TestData.path("/data/HopIli_Spike_V1_valid.xtf").toString());
    inputMeta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    inputMeta.setModelDirectories(TestData.path("/models").toString());
    inputMeta.setClassName("HopIli_Spike_V1.Data.Building");
    inputMeta.setIncludeTid(true);
    inputMeta.setIncludeBid(true);

    InterlisOutputMeta outputMeta = new InterlisOutputMeta();
    outputMeta.setFileName(outputFile.toString());
    outputMeta.setModelNames("HopIli_Spike_V1");
    outputMeta.setModelDirectories(TestData.path("/models").toString());
    outputMeta.setClassName("HopIli_Spike_V1.Data.Building");
    outputMeta.setObjectIdField("_ili_tid");
    outputMeta.setBasketIdField("_ili_bid");
    outputMeta.setOverwrite(false);

    Pipeline pipeline = run(inputMeta, outputMeta);
    assertThat(pipeline.getErrors()).isEqualTo(1);
  }

  private static Pipeline run(
      InterlisInputMeta inputMeta, InterlisOutputMeta outputMeta) throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", inputMeta);
    source.setLocation(100, 100);
    TransformMeta output = new TransformMeta("INTERLIS_OUTPUT", "INTERLIS Output", outputMeta);
    output.setLocation(300, 100);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    sink.setLocation(500, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(output);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, output));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(output, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();
    return (Pipeline) engine;
  }

  private static List<InterlisObjectEnvelope> readAll(Path file) throws Exception {
    return readAll(file, null);
  }

  private static List<InterlisObjectEnvelope> readAll(
      Path file, ch.interlis.ili2c.metamodel.TransferDescription td) throws Exception {
    try (XtfTransferReader reader = XtfTransferReader.open(file, td)) {
      List<InterlisObjectEnvelope> events = new ArrayList<>();
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        events.add(event);
      }
      return events;
    }
  }
}
