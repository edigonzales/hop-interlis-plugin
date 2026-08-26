package ch.so.agi.hop.interlis.transforms.input;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
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

class InterlisInputAssociationPipelineTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private List<RowMetaAndData> runInput(String className) throws Exception {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName(TestData.path("/data/HopIli_Associations_V1_valid.xtf").toString());
    meta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName(className);
    meta.setIncludeTid(true);
    meta.setIncludeBid(true);

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
    return ((Pipeline) engine).getResultRows();
  }

  @Test
  void reads_association_rows_with_roles_attributes_and_order_positions() throws Exception {
    List<RowMetaAndData> rows =
        runInput("HopIli_Associations_V1.Data.PersonTask");

    // _ili_bid, Person_ref, Task_ref, Task_order_pos
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactly("_ili_bid", "Person_ref", "Task_ref", "Task_order_pos");
    assertThat(rows.get(0).getData()[1]).isEqualTo("p1");
    assertThat(rows.get(0).getData()[2]).isEqualTo("t1");
    assertThat(rows.get(0).getData()[3]).isEqualTo(0L);
    assertThat(rows.get(1).getData()[3]).isEqualTo(1L);
  }

  @Test
  void reads_membership_rows_with_attributes() throws Exception {
    List<RowMetaAndData> rows =
        runInput("HopIli_Associations_V1.Data.Membership");

    // _ili_bid, Person_ref, Organisation_ref, Function, Entry
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getData()[1]).isEqualTo("p1");
    assertThat(rows.get(0).getData()[2]).isEqualTo("o1");
    assertThat(rows.get(0).getData()[3]).isEqualTo("CEO");
    assertThat(rows.get(1).getData()[3]).isEqualTo("Dev");
  }

  @Test
  void class_rows_carry_flattened_association_attributes() throws Exception {
    List<RowMetaAndData> rows = runInput("HopIli_Associations_V1.Data.Person");

    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "Name", "Address_ref", "Address_Share");
    assertThat(rows).hasSize(3);
    // p1's link is in the same basket: ref and share are resolved.
    assertThat(rows.get(0).getData()[3]).isEqualTo("a1");
    assertThat(rows.get(0).getData()[4]).isEqualTo(new java.math.BigDecimal("0.5"));
    assertThat(rows.get(1).getData()[3]).isNull();
    assertThat(rows.get(2).getData()[3]).isNull();
  }

  @Test
  void full_association_roundtrip_writes_links_and_flattened_attributes() throws Exception {
    Path outputFile = tempDir.resolve("associations-roundtrip.xtf");

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(TestData.path("/data/HopIli_Associations_V1_valid.xtf").toString());
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(TestData.path("/models").toString());
    input.setClassName("HopIli_Associations_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);

    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setFileName(outputFile.toString());
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(TestData.path("/models").toString());
    output.setClassName("HopIli_Associations_V1.Data.Person");
    output.setObjectIdField("_ili_tid");
    output.setBasketIdField("_ili_bid");
    output.setBasketId("b1");
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

    // Re-read: the class objects and the regenerated AddressOwnership link must be present.
    CompiledInterlisModel model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(TestData.path("/models/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());
    List<InterlisObjectEnvelope> objects = new ArrayList<>();
    try (XtfTransferReader reader = XtfTransferReader.open(outputFile, model.transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        if (event.eventType() == InterlisEventType.OBJECT) {
          objects.add(event);
        }
      }
    }
    assertThat(objects)
        .filteredOn(e -> e.className().equals("HopIli_Associations_V1.Data.Person"))
        .hasSize(3);
    List<InterlisObjectEnvelope> links =
        objects.stream()
            .filter(e -> e.className().equals("HopIli_Associations_V1.Data.AddressOwnership"))
            .toList();
    assertThat(links).hasSize(1);
    IomObject link = links.get(0).object();
    assertThat(link.getattrobj("Person", 0).getobjectrefoid()).isEqualTo("p1");
    assertThat(link.getattrobj("Address", 0).getobjectrefoid()).isEqualTo("a1");
    assertThat(link.getattrvalue("Share")).isEqualTo("0.5");
  }
}
