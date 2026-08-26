package ch.so.agi.hop.interlis.transforms.enumerations;

import static org.assertj.core.api.Assertions.assertThat;

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

class InterlisEnumerationsPipelineTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void lists_enumeration_values_with_hierarchy() throws Exception {
    InterlisEnumerationsMeta meta = new InterlisEnumerationsMeta();
    meta.setModelNames("HopIli_Enums_V1");
    meta.setModelDirectories(TestData.path("/models").toString());

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta("INTERLIS_ENUMERATIONS", "INTERLIS Enumerations", meta);
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

    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactly(
            InterlisEnumerationsMeta.ENUM_DEFINITION,
            InterlisEnumerationsMeta.ENUM_VALUE,
            InterlisEnumerationsMeta.ENUM_PATH,
            InterlisEnumerationsMeta.ENUM_PARENT,
            InterlisEnumerationsMeta.ENUM_DEPTH,
            InterlisEnumerationsMeta.ENUM_IS_LEAF);

    assertThat(rows)
        .anySatisfy(
            row -> {
              assertThat(row.getData()[1]).isEqualTo("beta");
              assertThat(row.getData()[3]).isNull();
              assertThat(row.getData()[4]).isEqualTo(0L);
              assertThat(row.getData()[5]).isEqualTo(Boolean.FALSE);
            });
    assertThat(rows)
        .anySatisfy(
            row -> {
              assertThat(row.getData()[1]).isEqualTo("beta_1");
              assertThat(row.getData()[3]).isEqualTo("beta");
              assertThat(row.getData()[4]).isEqualTo(1L);
              assertThat(row.getData()[5]).isEqualTo(Boolean.TRUE);
            });
    assertThat(rows)
        .anySatisfy(
            row -> {
              assertThat(row.getData()[1]).isEqualTo("one");
              assertThat(row.getData()[0]).isEqualTo("HopIli_Enums_V1.Data.Kind_");
            });
  }
}
