package ch.so.agi.hop.interlis.transforms.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.row.value.ValueMetaString;
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
import org.locationtech.jts.geom.Point;

class InterlisTestPipelineTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void transform_is_found_in_the_hop_plugin_registry() {
    assertThat(
            PluginRegistry.getInstance()
                .findPluginWithId(TransformPluginType.class, "INTERLIS_TEST"))
        .isNotNull();
  }

  @Test
  void transform_runs_in_a_pipeline_and_emits_typed_geometry_rows() throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta("INTERLIS_TEST", "INTERLIS Test", new InterlisTestMeta());
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
    Pipeline pipeline = (Pipeline) engine;
    assertThat(pipeline.getResultRows()).hasSize(3);

    RowMetaAndData first = pipeline.getResultRows().get(0);
    assertThat(first.getRowMeta().getValueMeta(0).getName()).isEqualTo("_ili_tid");
    assertThat(first.getRowMeta().getValueMeta(1).getName()).isEqualTo("_ili_bid");
    assertThat(first.getRowMeta().getValueMeta(2)).isInstanceOf(ValueMetaString.class);
    assertThat(first.getRowMeta().getValueMeta(3).getName()).isEqualTo("geom");

    assertThat(first.getData()[0]).isEqualTo("o1");
    assertThat(first.getData()[1]).isEqualTo("b1");
    assertThat(first.getData()[2]).isEqualTo("interlis-a");

    Geometry geometry = (Geometry) first.getData()[3];
    assertThat(geometry).isInstanceOf(Point.class);
    assertThat(geometry.getCoordinate().getX()).isEqualTo(2600000.0);

    // Third row proves that a null geometry value passes through unchanged.
    assertThat(pipeline.getResultRows().get(2).getData()[3]).isNull();
  }
}
