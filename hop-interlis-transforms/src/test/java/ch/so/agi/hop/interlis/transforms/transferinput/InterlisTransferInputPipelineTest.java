package ch.so.agi.hop.interlis.transforms.transferinput;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.transforms.TestData;
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

class InterlisTransferInputPipelineTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private List<RowMetaAndData> run(String fileName, TransferInputMode mode) throws Exception {
    InterlisTransferInputMeta meta = new InterlisTransferInputMeta();
    meta.setFileName(TestData.path(fileName).toString());
    meta.setModelNames(ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setMode(mode.name());

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta("INTERLIS_TRANSFER_INPUT", "INTERLIS Transfer Input", meta);
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
    return ((Pipeline) engine).getResultRows();
  }

  @Test
  void emits_constant_envelope_schema_in_objects_mode() throws Exception {
    List<RowMetaAndData> rows =
        run("/data/HopIli_Associations_V1_mapping.xtf", TransferInputMode.OBJECTS);

    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactlyElementsOf(InterlisEnvelopeRowLayout.FIELD_NAMES);
    // Only OBJECT rows: the fixture contains 14 objects.
    assertThat(rows).hasSize(14);
    assertThat(rows).allSatisfy(
        row -> assertThat(row.getData()[InterlisEnvelopeRowLayout.EVENT_TYPE_INDEX])
            .isEqualTo("OBJECT"));
    RowMetaAndData first = rows.get(0);
    assertThat(first.getData()[InterlisEnvelopeRowLayout.CLASS_INDEX])
        .isEqualTo("HopIli_Associations_V1.Data.Person");
    assertThat(first.getData()[InterlisEnvelopeRowLayout.TID_INDEX]).isEqualTo("p1");
    assertThat(first.getData()[InterlisEnvelopeRowLayout.BID_INDEX]).isEqualTo("b1");
    assertThat(first.getData()[InterlisEnvelopeRowLayout.OBJECT_INDEX])
        .isInstanceOf(ch.interlis.iom.IomObject.class);
  }

  @Test
  void emits_the_exact_event_sequence_in_events_mode() throws Exception {
    List<RowMetaAndData> rows =
        run("/data/HopIli_Associations_V1_mapping.xtf", TransferInputMode.EVENTS);

    assertThat(rows)
        .extracting(row -> row.getData()[InterlisEnvelopeRowLayout.EVENT_TYPE_INDEX])
        .startsWith("START_TRANSFER", "START_BASKET", "OBJECT")
        .endsWith("END_BASKET", "END_TRANSFER");
    // 1 START_TRANSFER + 1 START_BASKET + 14 OBJECT + 1 END_BASKET + 1 END_TRANSFER
    assertThat(rows).hasSize(18);
  }

  @Test
  void carries_delete_operations_in_object_rows() throws Exception {
    List<RowMetaAndData> rows =
        run("/data/HopIli_Associations_V1_delete.xtf", TransferInputMode.OBJECTS);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getData()[InterlisEnvelopeRowLayout.OPERATION_INDEX])
        .isEqualTo("DELETE");
  }

  @Test
  void mixed_classes_travel_in_one_stream() throws Exception {
    List<RowMetaAndData> rows =
        run("/data/HopIli_Associations_V1_mapping.xtf", TransferInputMode.OBJECTS);

    assertThat(rows)
        .extracting(row -> row.getData()[InterlisEnvelopeRowLayout.CLASS_INDEX])
        .contains(
            "HopIli_Associations_V1.Data.Person",
            "HopIli_Associations_V1.Data.Organisation",
            "HopIli_Associations_V1.Data.AddressOwnership",
            "HopIli_Associations_V1.Data.Membership",
            "HopIli_Associations_V1.Data.PersonTask");
  }
}
