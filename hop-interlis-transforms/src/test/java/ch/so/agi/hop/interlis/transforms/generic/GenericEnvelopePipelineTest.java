package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.objecttorow.InterlisObjectToRowMeta;
import ch.so.agi.hop.interlis.transforms.rowtoobject.InterlisRowToObjectMeta;
import ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta;
import ch.so.agi.hop.interlis.transforms.transferinput.TransferInputMode;
import ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta;
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

/** Vertical tests of the generic envelope path: Transfer Input, Object to Row, Row to Object, Transfer Output. */
class GenericEnvelopePipelineTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisTransferInputMeta transferInput(TransferInputMode mode) {
    InterlisTransferInputMeta meta = new InterlisTransferInputMeta();
    meta.setFileName(TestData.path("/data/HopIli_Associations_V1_mapping.xtf").toString());
    meta.setModelNames(ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setMode(mode.name());
    return meta;
  }

  private IPipelineEngine<PipelineMeta> runPipeline(PipelineMeta pipelineMeta) throws Exception {
    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();
    return engine;
  }

  private List<InterlisObjectEnvelope> readObjects(Path file) throws Exception {
    CompiledInterlisModel model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(TestData.path("/models/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());
    List<InterlisObjectEnvelope> events = new ArrayList<>();
    try (XtfTransferReader reader = XtfTransferReader.open(file, model.transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        events.add(event);
      }
    }
    return events;
  }

  @Test
  void object_to_row_projects_typed_rows_from_the_envelope_stream() throws Exception {
    InterlisObjectToRowMeta objectToRow = new InterlisObjectToRowMeta();
    objectToRow.setDefault();
    objectToRow.setModelNames("HopIli_Associations_V1");
    objectToRow.setModelDirectories(TestData.path("/models").toString());
    objectToRow.setClassName("HopIli_Associations_V1.Data.Person");

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta(
            "INTERLIS_TRANSFER_INPUT",
            "INTERLIS Transfer Input",
            transferInput(TransferInputMode.EVENTS));
    TransformMeta middle = new TransformMeta("INTERLIS Object to Row", objectToRow);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(middle);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, middle));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(middle, sink));

    IPipelineEngine<PipelineMeta> engine = runPipeline(pipelineMeta);
    assertThat(engine.getErrors()).isZero();

    List<RowMetaAndData> rows = ((Pipeline) engine).getResultRows();
    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "Name", "Address_ref", "Address_Share");
    assertThat(rows.get(0).getData()[3]).isEqualTo("a1");
    assertThat(rows.get(0).getData()[4]).isEqualTo(new java.math.BigDecimal("0.5"));
  }

  @Test
  void row_to_object_emits_envelope_rows_including_link_objects() throws Exception {
    InterlisRowToObjectMeta rowToObject = new InterlisRowToObjectMeta();
    rowToObject.setDefault();
    rowToObject.setModelNames("HopIli_Associations_V1");
    rowToObject.setModelDirectories(TestData.path("/models").toString());
    rowToObject.setClassName("HopIli_Associations_V1.Data.Person");

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta(
            "INTERLIS_TRANSFER_INPUT",
            "INTERLIS Transfer Input",
            transferInput(TransferInputMode.EVENTS));
    InterlisObjectToRowMeta objectToRow = new InterlisObjectToRowMeta();
    objectToRow.setDefault();
    objectToRow.setModelNames("HopIli_Associations_V1");
    objectToRow.setModelDirectories(TestData.path("/models").toString());
    objectToRow.setClassName("HopIli_Associations_V1.Data.Person");
    TransformMeta middle = new TransformMeta("INTERLIS Object to Row", objectToRow);
    TransformMeta inverse = new TransformMeta("INTERLIS Row to Object", rowToObject);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(middle);
    pipelineMeta.addTransform(inverse);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, middle));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(middle, inverse));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(inverse, sink));

    IPipelineEngine<PipelineMeta> engine = runPipeline(pipelineMeta);
    assertThat(engine.getErrors()).isZero();

    List<RowMetaAndData> rows = ((Pipeline) engine).getResultRows();
    // 3 persons + 1 regenerated AddressOwnership link.
    assertThat(rows).hasSize(4);
    assertThat(rows).allSatisfy(
        row -> assertThat(row.getData()[InterlisEnvelopeRowLayout.EVENT_TYPE_INDEX])
            .isEqualTo("OBJECT"));
    assertThat(rows)
        .extracting(row -> row.getData()[InterlisEnvelopeRowLayout.CLASS_INDEX])
        .containsExactly(
            "HopIli_Associations_V1.Data.Person",
            "HopIli_Associations_V1.Data.AddressOwnership",
            "HopIli_Associations_V1.Data.Person",
            "HopIli_Associations_V1.Data.Person");
    IomObject link =
        (IomObject) rows.get(1).getData()[InterlisEnvelopeRowLayout.OBJECT_INDEX];
    assertThat(link.getattrvalue("Share")).isEqualTo("0.5");
  }

  @Test
  void full_generic_roundtrip_preserves_objects_and_baskets() throws Exception {
    Path outputFile = tempDir.resolve("generic-roundtrip.xtf");

    InterlisTransferOutputMeta output = new InterlisTransferOutputMeta();
    output.setDefault();
    output.setFileName(outputFile.toString());
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(TestData.path("/models").toString());
    output.setOverwrite(true);
    output.setEventMode(true);

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta(
            "INTERLIS_TRANSFER_INPUT",
            "INTERLIS Transfer Input",
            transferInput(TransferInputMode.EVENTS));
    TransformMeta sink = new TransformMeta("INTERLIS Transfer Output", output);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));

    IPipelineEngine<PipelineMeta> engine = runPipeline(pipelineMeta);
    assertThat(engine.getErrors()).isZero();

    List<InterlisObjectEnvelope> events = readObjects(outputFile);
    List<InterlisObjectEnvelope> objects =
        events.stream().filter(e -> e.eventType() == InterlisEventType.OBJECT).toList();
    assertThat(objects).hasSize(14);
    assertThat(objects).allSatisfy(e -> assertThat(e.basketId()).isEqualTo("b1"));
    InterlisObjectEnvelope membership =
        objects.stream()
            .filter(e -> e.className().equals("HopIli_Associations_V1.Data.Membership"))
            .findFirst()
            .orElseThrow();
    assertThat(membership.object().getattrvalue("Function")).isEqualTo("CEO");
  }

  @Test
  void event_mode_roundtrip_preserves_delete_operations() throws Exception {
    Path outputFile = tempDir.resolve("generic-delete-roundtrip.xtf");

    InterlisTransferInputMeta input = new InterlisTransferInputMeta();
    input.setFileName(TestData.path("/data/HopIli_Associations_V1_delete.xtf").toString());
    input.setModelNames(ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(TestData.path("/models").toString());
    input.setMode(TransferInputMode.EVENTS.name());

    InterlisTransferOutputMeta output = new InterlisTransferOutputMeta();
    output.setDefault();
    output.setFileName(outputFile.toString());
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(TestData.path("/models").toString());
    output.setOverwrite(true);
    output.setEventMode(true);

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta("INTERLIS_TRANSFER_INPUT", "INTERLIS Transfer Input", input);
    TransformMeta sink = new TransformMeta("INTERLIS Transfer Output", output);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));

    IPipelineEngine<PipelineMeta> engine = runPipeline(pipelineMeta);
    assertThat(engine.getErrors()).isZero();

    List<InterlisObjectEnvelope> events = readObjects(outputFile);
    InterlisObjectEnvelope object =
        events.stream()
            .filter(e -> e.eventType() == InterlisEventType.OBJECT)
            .findFirst()
            .orElseThrow();
    assertThat(object.operation()).isEqualTo(ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.DELETE);
  }

  @Test
  void object_mode_derives_baskets_and_rejects_explicit_events() throws Exception {
    Path outputFile = tempDir.resolve("generic-object-mode.xtf");

    InterlisTransferOutputMeta output = new InterlisTransferOutputMeta();
    output.setDefault();
    output.setFileName(outputFile.toString());
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(TestData.path("/models").toString());
    output.setOverwrite(true);
    output.setEventMode(false);

    // EVENTS stream into object mode: the START_TRANSFER event must be rejected.
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta(
            "INTERLIS_TRANSFER_INPUT",
            "INTERLIS Transfer Input",
            transferInput(TransferInputMode.EVENTS));
    TransformMeta sink = new TransformMeta("INTERLIS Transfer Output", output);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));

    IPipelineEngine<PipelineMeta> engine = runPipeline(pipelineMeta);
    assertThat(engine.getErrors()).isGreaterThan(0);
  }
}
