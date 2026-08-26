package ch.so.agi.hop.interlis.transforms.input;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import com.atolcd.hop.gis.geometry.curve.CircularString;
import com.atolcd.hop.gis.geometry.curve.CompoundCurve;
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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

class InterlisInputPipelineTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private List<RowMetaAndData> runInput(
      String xtfFile, String modelName, String className, boolean modelsFromData)
      throws Exception {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName(TestData.path(xtfFile).toString());
    meta.setModelNames(modelsFromData ? InterlisInputMeta.MODELS_FROM_DATA : modelName);
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
  void reads_xtf_into_typed_rows_with_multiple_geometries() throws Exception {
    List<RowMetaAndData> rows =
        runInput(
            "/data/HopIli_Geometry_V1_valid.xtf",
            "HopIli_Geometry_V1",
            "HopIli_Geometry_V1.Data.TestObject",
            false);

    assertThat(rows).hasSize(2);

    RowMetaAndData first = rows.get(0);
    assertThat(first.getRowMeta().getFieldNames())
        .containsExactly(
            "_ili_tid", "_ili_bid", "Name", "Center", "Points", "Axis", "Axes", "Boundary",
            "Area", "Surfaces");
    assertThat(first.getData()[0]).isEqualTo("o1");
    assertThat(first.getData()[1]).isEqualTo("b1");
    assertThat(first.getData()[2]).isEqualTo("A");
    assertThat(first.getData()[3]).isInstanceOf(Point.class);
    assertThat(first.getData()[4]).isNull(); // Points absent
    assertThat(first.getData()[5]).isInstanceOf(LineString.class);
    assertThat(first.getData()[7]).isInstanceOf(Polygon.class);
  }

  @Test
  void arc_survives_as_circular_string() throws Exception {
    List<RowMetaAndData> rows =
        runInput(
            "/data/HopIli_Geometry_V1_valid.xtf",
            "HopIli_Geometry_V1",
            "HopIli_Geometry_V1.Data.TestObject",
            false);

    Geometry axis = (Geometry) rows.get(1).getData()[5];
    assertThat(axis).isInstanceOf(CompoundCurve.class);
    CompoundCurve curve = (CompoundCurve) axis;
    assertThat(curve.getComponents()).anyMatch(CircularString.class::isInstance);
    CircularString arc =
        curve.getComponents().stream()
            .filter(CircularString.class::isInstance)
            .map(CircularString.class::cast)
            .findFirst()
            .orElseThrow();
    // Exact control points: start (2600000,1200000), mid (2600050,1200050), end (2600100,1200000)
    assertThat(arc.getControlPoints()).hasSize(3);
    assertThat(arc.getControlPoints()[1].getX()).isEqualTo(2600050.0);
    assertThat(arc.getControlPoints()[1].getY()).isEqualTo(1200050.0);
  }

  @Test
  void detects_models_from_the_transfer_file() throws Exception {
    List<RowMetaAndData> rows =
        runInput(
            "/data/HopIli_Geometry_V1_valid.xtf",
            null,
            "HopIli_Geometry_V1.Data.TestObject",
            true);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getData()[2]).isEqualTo("A");
  }

  @Test
  void reads_structures_roles_and_inherited_attributes() throws Exception {
    List<RowMetaAndData> rows =
        runInput(
            "/data/HopIli_Spike_V1_valid.xtf",
            "HopIli_Spike_V1",
            "HopIli_Spike_V1.Data.Building",
            false);

    assertThat(rows).hasSize(1);
    RowMetaAndData building = rows.get(0);
    // _ili_tid, _ili_bid, Code, Location, Address_Street, Address_Number, Municipality_ref, Note
    assertThat(building.getData()[0]).isEqualTo("g1");
    assertThat(building.getData()[2]).isEqualTo(42L);
    assertThat(building.getData()[4]).isEqualTo("Main Street");
    assertThat(building.getData()[5]).isEqualTo("10");
    assertThat(building.getData()[6]).isEqualTo("m1");
    assertThat(building.getData()[7]).isEqualTo("inherited note");
  }

  @Test
  void objects_of_other_classes_are_skipped() throws Exception {
    List<RowMetaAndData> rows =
        runInput(
            "/data/HopIli_Spike_V1_valid.xtf",
            "HopIli_Spike_V1",
            "HopIli_Spike_V1.Data.Municipality",
            false);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getData()[0]).isEqualTo("m1");
    assertThat(rows.get(0).getData()[2]).isEqualTo("Springfield");
  }
}
