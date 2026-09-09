package ch.so.agi.hop.interlis.transforms.validate;

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

class InterlisValidatePipelineTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisValidateMeta validateMeta(String fixture) {
    InterlisValidateMeta meta = new InterlisValidateMeta();
    meta.setFileName(TestData.path(fixture).toString());
    meta.setModelNames(ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    return meta;
  }

  private IPipelineEngine<PipelineMeta> run(InterlisValidateMeta meta) throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS_VALIDATE", "INTERLIS Validate", meta);
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
    return engine;
  }

  @Test
  void valid_transfer_produces_no_rows() throws Exception {
    IPipelineEngine<PipelineMeta> engine =
        run(validateMeta("/data/HopIli_Enums_V1_valid.xtf"));

    assertThat(engine.getErrors()).isZero();
    assertThat(((Pipeline) engine).getResultRows()).isEmpty();
  }

  @Test
  void invalid_transfer_produces_error_rows_with_context() throws Exception {
    IPipelineEngine<PipelineMeta> engine =
        run(validateMeta("/data/HopIli_Enums_V1_invalid.xtf"));

    assertThat(engine.getErrors()).isZero();
    List<RowMetaAndData> rows = ((Pipeline) engine).getResultRows();
    assertThat(rows).isNotEmpty();

    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactlyElementsOf(
            ch.so.agi.hop.interlis.core.io.InterlisValidationRowLayout.FIELD_NAMES);

    // The mandatory Kind attribute of s1 is missing: a multiplicity/type error row with the
    // class and TID context must be present.
    assertThat(rows)
        .anySatisfy(
            row -> {
              assertThat(row.getData()[0]).isEqualTo("ERROR");
              assertThat(String.valueOf(row.getData()[9])).isEqualTo("s1");
              assertThat(String.valueOf(row.getData()[8]))
                  .isEqualTo("HopIli_Enums_V1.Data.Sample");
            });
    // The invalid enum value (Kind=four) is flagged as well.
    assertThat(rows)
        .anySatisfy(
            row -> assertThat(String.valueOf(row.getData()[1])).contains("four"));
    // Line numbers of the source file are carried.
    assertThat(rows)
        .anySatisfy(row -> assertThat(row.getData()[3]).isInstanceOf(Long.class));
  }

  @Test
  void fail_on_errors_fails_the_pipeline_after_emitting_rows() throws Exception {
    InterlisValidateMeta meta = validateMeta("/data/HopIli_Enums_V1_invalid.xtf");
    meta.setFailOnErrors(true);

    IPipelineEngine<PipelineMeta> engine = run(meta);

    assertThat(engine.getErrors()).isGreaterThan(0);
  }

  @Test
  void stop_on_first_error_limits_rows() throws Exception {
    InterlisValidateMeta meta = validateMeta("/data/HopIli_Enums_V1_invalid.xtf");
    meta.setStopOnFirstError(true);

    IPipelineEngine<PipelineMeta> engine = run(meta);

    assertThat(engine.getErrors()).isZero();
    List<RowMetaAndData> rows = ((Pipeline) engine).getResultRows();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getData()[0]).isEqualTo("ERROR");
    assertThat(rows.get(1).getData()[1].toString()).contains("Validation incomplete", "1 error(s)");
  }

  @Test
  void warnings_can_be_excluded() throws Exception {
    InterlisValidateMeta meta = validateMeta("/data/HopIli_Enums_V1_invalid.xtf");
    meta.setIncludeWarnings(false);

    IPipelineEngine<PipelineMeta> engine = run(meta);

    assertThat(engine.getErrors()).isZero();
    List<RowMetaAndData> rows = ((Pipeline) engine).getResultRows();
    assertThat(rows).allSatisfy(row -> assertThat(row.getData()[0]).isEqualTo("ERROR"));
  }

  @Test
  void config_file_can_disable_multiplicity_checks() throws Exception {
    java.nio.file.Path config =
        java.nio.file.Files.createTempFile("validation", ".toml");
    java.nio.file.Files.writeString(
        config,
        "[PARAMETER]\nMULTIPLICITY = \"OFF\"\n");

    InterlisValidateMeta meta = validateMeta("/data/HopIli_Enums_V1_invalid.xtf");
    meta.setConfigFile(config.toString());

    IPipelineEngine<PipelineMeta> engine = run(meta);

    assertThat(engine.getErrors()).isZero();
    List<RowMetaAndData> rows = ((Pipeline) engine).getResultRows();
    // The multiplicity error for the missing Kind is suppressed; the invalid enum value remains.
    assertThat(rows)
        .noneSatisfy(
            row -> {
              Object message = row.getData()[1];
              assertThat(String.valueOf(message)).contains("Kind").doesNotContain("four");
            });
  }
}
