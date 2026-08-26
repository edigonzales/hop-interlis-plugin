package ch.so.agi.hop.interlis.transforms.rolejoin;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.testutil.SyntheticLookupRowsMeta;
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

class InterlisRoleJoinPipelineTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisInputMeta personInput() {
    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(TestData.path("/data/HopIli_Associations_V1_valid.xtf").toString());
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(TestData.path("/models").toString());
    input.setClassName("HopIli_Associations_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    return input;
  }

  private InterlisRoleJoinMeta roleJoin(String lookupTransformName) {
    InterlisRoleJoinMeta join = new InterlisRoleJoinMeta();
    join.setDefault();
    join.setMainInputTransform("INTERLIS Input");
    join.setLookupInputTransform(lookupTransformName);
    join.setModelNames("HopIli_Associations_V1");
    join.setModelDirectories(TestData.path("/models").toString());
    join.setMainClassName("HopIli_Associations_V1.Data.Person");
    join.setRoleName("Address");
    return join;
  }

  private List<RowMetaAndData> run(
      InterlisRoleJoinMeta join, boolean withLookupInput, SyntheticLookupRowsMeta lookupRows)
      throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS Input", personInput());
    source.setLocation(100, 100);
    TransformMeta joinTransform = new TransformMeta("INTERLIS Role Join", join);
    joinTransform.setLocation(300, 100);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    sink.setLocation(500, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(joinTransform);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, joinTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(joinTransform, sink));

    if (withLookupInput) {
      InterlisInputMeta addressInput = new InterlisInputMeta();
      addressInput.setFileName(TestData.path("/data/HopIli_Associations_V1_valid.xtf").toString());
      addressInput.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
      addressInput.setModelDirectories(TestData.path("/models").toString());
      addressInput.setClassName("HopIli_Associations_V1.Data.Address");
      addressInput.setIncludeTid(true);
      addressInput.setIncludeBid(true);
      TransformMeta lookup = new TransformMeta("lookup", addressInput);
      lookup.setLocation(100, 300);
      pipelineMeta.addTransform(lookup);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(lookup, joinTransform));
    } else if (lookupRows != null) {
      TransformMeta lookup = new TransformMeta("lookup", lookupRows);
      lookup.setLocation(100, 300);
      pipelineMeta.addTransform(lookup);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(lookup, joinTransform));
    }

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();
    return ((Pipeline) engine).getResultRows();
  }

  @Test
  void joins_target_fields_from_a_real_lookup_stream() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    join.setFailOnMissingMandatoryReference(false);

    List<RowMetaAndData> rows = run(join, true, null);

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .endsWith("Address_Street");
    RowMetaAndData p1 = rows.get(0);
    assertThat(p1.getData()[0]).isEqualTo("p1");
    assertThat(p1.getData()[3]).isEqualTo("a1");
    assertThat(p1.getData()[p1.getData().length - 1]).isEqualTo("Main Street");
    // p2 and p3 have no Address link: null join fields.
    assertThat(rows.get(1).getData()[rows.get(1).getData().length - 1]).isNull();
    assertThat(rows.get(2).getData()[rows.get(2).getData().length - 1]).isNull();
  }

  @Test
  void joins_target_fields_from_a_synthetic_lookup_stream() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    join.setFailOnMissingMandatoryReference(false);
    SyntheticLookupRowsMeta lookupRows = new SyntheticLookupRowsMeta();
    lookupRows.addRow("a1", "Main Street");

    List<RowMetaAndData> rows = run(join, false, lookupRows);

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).getData()[rows.get(0).getData().length - 1]).isEqualTo("Main Street");
  }

  @Test
  void missing_mandatory_reference_fails_by_default() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    SyntheticLookupRowsMeta lookupRows = new SyntheticLookupRowsMeta();
    lookupRows.addRow("a1", "Main Street");

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS Input", personInput());
    TransformMeta joinTransform = new TransformMeta("INTERLIS Role Join", join);
    TransformMeta lookup = new TransformMeta("lookup", lookupRows);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(joinTransform);
    pipelineMeta.addTransform(lookup);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, joinTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(lookup, joinTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(joinTransform, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();

    // p2/p3 have a mandatory Address role but no link: the join fails.
    assertThat(engine.getErrors()).isGreaterThan(0);
  }

  @Test
  void duplicate_lookup_tid_fails() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    join.setFailOnMissingMandatoryReference(false);
    SyntheticLookupRowsMeta lookupRows = new SyntheticLookupRowsMeta();
    lookupRows.addRow("a1", "First");
    lookupRows.addRow("a1", "Second");

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS Input", personInput());
    TransformMeta joinTransform = new TransformMeta("INTERLIS Role Join", join);
    TransformMeta lookup = new TransformMeta("lookup", lookupRows);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(joinTransform);
    pipelineMeta.addTransform(lookup);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, joinTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(lookup, joinTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(joinTransform, sink));

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
