package ch.so.agi.hop.interlis.transforms.explode;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
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
import org.locationtech.jts.geom.Point;

class InterlisStructureExplodePipelineTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private List<RowMetaAndData> runExplode(String structurePath, String[] parentFields)
      throws Exception {
    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(TestData.path("/data/HopIli_Structures_V1_valid.xtf").toString());
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(TestData.path("/models").toString());
    input.setClassName("HopIli_Structures_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    input.setKeepSourceObject(true);

    InterlisStructureExplodeMeta explode = new InterlisStructureExplodeMeta();
    explode.setDefault();
    explode.setModelNames("HopIli_Structures_V1");
    explode.setModelDirectories(TestData.path("/models").toString());
    explode.setClassName("HopIli_Structures_V1.Data.Person");
    explode.setStructureAttributePath(structurePath);
    if (parentFields != null) {
      explode.setIncludeParentFields(List.of(parentFields));
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    TransformMeta middle = new TransformMeta("INTERLIS Structure Explode", explode);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(middle);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, middle));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(middle, sink));

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
  void explodes_list_with_index_parent_keys_and_child_geometry() throws Exception {
    List<RowMetaAndData> rows = runExplode("Addresses", null);

    // p1 has 3 addresses, p2 has 1, p3 none.
    assertThat(rows).hasSize(4);
    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactly(
            "_ili_parent_tid", "_ili_parent_bid", "_ili_index", "Street", "Number", "Location",
            "PostCode_Code", "PostCode_Town");

    RowMetaAndData first = rows.get(0);
    assertThat(first.getData()[0]).isEqualTo("p1");
    assertThat(first.getData()[1]).isEqualTo("b1");
    assertThat(first.getData()[2]).isEqualTo(0L);
    assertThat(first.getData()[3]).isEqualTo("Main Street");
    assertThat(first.getData()[5]).isInstanceOf(Point.class);
    assertThat(first.getData()[6]).isEqualTo("4600");
    assertThat(first.getData()[7]).isEqualTo("Olten");

    // LIST order is preserved: Main Street, Station Street, Village Road.
    assertThat(rows.get(1).getData()[3]).isEqualTo("Station Street");
    assertThat(rows.get(1).getData()[2]).isEqualTo(1L);
    assertThat(rows.get(2).getData()[3]).isEqualTo("Village Road");
    assertThat(rows.get(2).getData()[2]).isEqualTo(2L);
    // The third address has no PostCode: flattened fields are null.
    assertThat(rows.get(2).getData()[6]).isNull();
    assertThat(rows.get(2).getData()[7]).isNull();

    assertThat(rows.get(3).getData()[0]).isEqualTo("p2");
    assertThat(rows.get(3).getData()[3]).isEqualTo("Village Road");
  }

  @Test
  void explodes_bag_without_order_guarantees() throws Exception {
    List<RowMetaAndData> rows = runExplode("Contacts", null);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getData()[0]).isEqualTo("p1");
    assertThat(rows.get(0).getData()[3]).isEqualTo("phone");
    assertThat(rows.get(1).getData()[3]).isEqualTo("email");
  }

  @Test
  void copies_selected_parent_fields() throws Exception {
    List<RowMetaAndData> rows = runExplode("Contacts", new String[] {"Name"});

    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .endsWith("Name");
    assertThat(rows.get(0).getData()[rows.get(0).getRowMeta().size() - 1]).isEqualTo("Meier");
  }

  @Test
  void explodes_structure_below_single_structures() throws Exception {
    List<RowMetaAndData> rows = runExplode("Home.Place.Phones", null);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getData()[0]).isEqualTo("p1");
    assertThat(rows.get(0).getData()[3]).isEqualTo("phone");
    assertThat(rows.get(0).getData()[4]).isEqualTo("+41 79 123 45 67");
  }

  @Test
  void missing_carrier_fails_with_actionable_error() throws Exception {
    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(TestData.path("/data/HopIli_Structures_V1_valid.xtf").toString());
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(TestData.path("/models").toString());
    input.setClassName("HopIli_Structures_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    input.setKeepSourceObject(false); // no carrier emitted

    InterlisStructureExplodeMeta explode = new InterlisStructureExplodeMeta();
    explode.setDefault();
    explode.setModelNames("HopIli_Structures_V1");
    explode.setModelDirectories(TestData.path("/models").toString());
    explode.setClassName("HopIli_Structures_V1.Data.Person");
    explode.setStructureAttributePath("Addresses");

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    TransformMeta middle = new TransformMeta("INTERLIS Structure Explode", explode);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(middle);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, middle));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(middle, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();

    assertThat(engine.getErrors()).isGreaterThan(0);
  }
}
