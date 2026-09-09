package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.validate.InterlisValidateMeta;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Concurrency smoke test for the thread-safety invariants of the INTERLIS libraries (see the
 * threading section of the specification and AGENTS.md):
 *
 * <ul>
 *   <li>the ili2c compiler is serialized under the global model lock; concurrent compiles of the
 *       same request must hand out one shared, immutable {@code TransferDescription};
 *   <li>that shared model is read concurrently by several schema analyses (two INTERLIS Input
 *       transforms of the same model from different files);
 *   <li>iox readers and validators are per-instance and never shared (two parallel INTERLIS
 *       Validate transforms).
 * </ul>
 *
 * The pipeline runs four transforms in parallel and is repeated; a deadlock or corrupted shared
 * state would surface as a hanging pipeline (bounded by the join timeout) or a transform error.
 */
class ConcurrentTransformsStressTest {

  private static final long PIPELINE_TIMEOUT_MS = 120_000;
  private static final int REPETITIONS = 5;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void parallel_inputs_and_validations_on_the_shared_model_are_stable() throws Exception {
    for (int run = 0; run < REPETITIONS; run++) {
      IPipelineEngine<PipelineMeta> engine = newEngine();
      runEngineWithTimeout(engine);
      assertThat(engine.getErrors())
          .as("pipeline run %d of %d must complete without transform errors", run + 1,
              REPETITIONS)
          .isZero();
    }
  }

  private static IPipelineEngine<PipelineMeta> newEngine() throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("concurrent-transforms-stress");

    add(pipelineMeta, input("INTERLIS Input A", "/data/HopIli_Associations_V1_mapping.xtf"));
    add(pipelineMeta, input("INTERLIS Input B", "/data/HopIli_Associations_V1_basketmeta.xtf"));
    add(pipelineMeta, validate("INTERLIS Validate A", "/data/HopIli_Associations_V1_mapping.xtf"));
    add(pipelineMeta, validate("INTERLIS Validate B", "/data/HopIli_Enums_V1_invalid.xtf"));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    return PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
  }

  private static void add(PipelineMeta pipelineMeta, TransformMeta transform) {
    pipelineMeta.addTransform(transform);
    TransformMeta sink = new TransformMeta(transform.getName() + " sink", new DummyMeta());
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(transform, sink));
  }

  private static TransformMeta input(String name, String fixture) {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName(TestData.path(fixture).toString());
    meta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Associations_V1.Data.Person");
    meta.setIncludeTid(true);
    meta.setIncludeBid(true);
    TransformMeta transform = new TransformMeta(name, meta);
    transform.setLocation(100, 100);
    return transform;
  }

  private static TransformMeta validate(String name, String fixture) {
    InterlisValidateMeta meta = new InterlisValidateMeta();
    meta.setFileName(TestData.path(fixture).toString());
    meta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    TransformMeta transform = new TransformMeta(name, meta);
    transform.setLocation(100, 100);
    return transform;
  }

  /**
   * Runs the engine in a daemon thread bounded by {@link #PIPELINE_TIMEOUT_MS} so a deadlock
   * fails the test instead of hanging the build forever.
   */
  private static void runEngineWithTimeout(IPipelineEngine<PipelineMeta> engine)
      throws Exception {
    Thread runner =
        new Thread(
            () -> {
              try {
                engine.prepareExecution();
                engine.startThreads();
                engine.waitUntilFinished();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    runner.setDaemon(true);
    runner.start();
    runner.join(PIPELINE_TIMEOUT_MS);
    assertThat(runner.isAlive())
        .as("pipeline must finish within %d ms; a hanging run indicates a deadlock",
            PIPELINE_TIMEOUT_MS)
        .isFalse();
  }
}
