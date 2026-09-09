package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import org.apache.hop.core.HopEnvironment;
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

/**
 * Threading policy: file-based transforms must fail fast when configured with parallel copies
 * (duplicate rows / corrupted output), while per-row transforms support them.
 */
class ParallelCopiesPolicyTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private static IPipelineEngine<PipelineMeta> run(InterlisInputMeta meta, int copies)
      throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", meta);
    source.setCopies(copies);
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

  private static InterlisInputMeta primitivesInput() {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName(TestData.path("/data/HopIli_Associations_V1_mapping.xtf").toString());
    meta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Associations_V1.Data.Person");
    return meta;
  }

  @Test
  void file_reader_rejects_parallel_copies_with_a_clear_error() throws Exception {
    IPipelineEngine<PipelineMeta> engine = run(primitivesInput(), 2);

    assertThat(engine.getErrors()).isGreaterThan(0);
    assertThat(((Pipeline) engine).getResultRows()).isEmpty();
  }

  @Test
  void guard_messages_are_actionable() {
    TransformMeta transform = new TransformMeta("INTERLIS Input", primitivesInput());
    transform.setCopies(2);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                ch.so.agi.hop.interlis.transforms.InterlisParallelCopies.requireSingleCopy(
                    transform, new org.apache.hop.core.variables.Variables(), "file processing"))
        .isInstanceOf(org.apache.hop.core.exception.HopException.class)
        .hasMessageContaining("does not support parallel copies")
        .hasMessageContaining("INTERLIS Input");
  }

  @Test
  void file_reader_runs_fine_with_a_single_copy() throws Exception {
    IPipelineEngine<PipelineMeta> engine = run(primitivesInput(), 1);

    assertThat(engine.getErrors()).isZero();
    assertThat(((Pipeline) engine).getResultRows()).isNotEmpty();
  }

  @Test
  void stateless_row_transforms_support_parallel_copies() throws Exception {
    // A class without link-resolved attributes is safe with two copies.
    ch.so.agi.hop.interlis.transforms.objecttorow.InterlisObjectToRowMeta meta =
        new ch.so.agi.hop.interlis.transforms.objecttorow.InterlisObjectToRowMeta();
    meta.setClassName("HopIli_Associations_V1.Data.Project");
    meta.setModelNames("HopIli_Associations_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setObjectFieldName("_ili_object");

    PipelineMeta pipelineMeta = new PipelineMeta();
    ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta sourceMeta =
        new ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta();
    sourceMeta.setFileName(TestData.path("/data/HopIli_Associations_V1_mapping.xtf").toString());
    sourceMeta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    sourceMeta.setModelDirectories(TestData.path("/models").toString());

    TransformMeta source = new TransformMeta("INTERLIS Transfer Input", sourceMeta);
    TransformMeta objectToRow = new TransformMeta("INTERLIS Object to Row", meta);
    objectToRow.setCopies(2);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(objectToRow);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, objectToRow));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(objectToRow, sink));

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
    assertThat(((Pipeline) engine).getResultRows()).isNotEmpty();
  }
}
