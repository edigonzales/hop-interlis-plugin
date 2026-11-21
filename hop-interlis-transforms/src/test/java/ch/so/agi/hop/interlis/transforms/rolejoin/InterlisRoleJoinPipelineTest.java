package ch.so.agi.hop.interlis.transforms.rolejoin;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.testutil.SyntheticLookupRowsMeta;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
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
    input.setFileName(TestData.path("/data/HopIli_Associations_V1_mapping.xtf").toString());
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

  private InterlisInputMeta addressInput() {
    InterlisInputMeta input = personInput();
    input.setClassName("HopIli_Associations_V1.Data.Address");
    return input;
  }

  private Pipeline run(InterlisRoleJoinMeta join, TransformMeta source, TransformMeta lookup)
      throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta joinTransform = new TransformMeta("INTERLIS Role Join", join);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    for (TransformMeta transform : List.of(source, lookup, joinTransform, sink)) {
      pipelineMeta.addTransform(transform);
    }
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, joinTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(lookup, joinTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(joinTransform, sink));

    PipelineRunConfiguration config = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration local = new LocalPipelineRunConfiguration();
    local.setEnginePluginId("Local");
    local.setRowSetSize("2");
    config.setEngineRunConfiguration(local);
    Pipeline engine = (Pipeline) PipelineEngineFactory.createPipelineEngine(config, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();
    return engine;
  }

  private SyntheticLookupRowsMeta mainRows(String... bids) {
    var main = new SyntheticLookupRowsMeta();
    main.setFields("Address_ref_bid", "Address_ref", "_ili_bid");
    for (String bid : bids) {
      main.addRow(bid, "a1", "b1");
    }
    return main;
  }

  private Pipeline run(
      InterlisRoleJoinMeta join, SyntheticLookupRowsMeta main, SyntheticLookupRowsMeta lookup)
      throws Exception {
    return run(
        join, new TransformMeta("INTERLIS Input", main), new TransformMeta("lookup", lookup));
  }

  private List<Object> streets(Pipeline engine) {
    assertThat(engine.getErrors()).isZero();
    return engine.getResultRows().stream()
        .map(row -> row.getData()[row.getRowMeta().indexOfValue("Address_Street")])
        .toList();
  }

  private String failureLog(Pipeline engine) {
    assertThat(engine.getErrors()).isPositive();
    return HopLogStore.getAppender().getBuffer(engine.getLogChannelId(), false).toString();
  }

  @Test
  void joins_target_fields_from_a_real_lookup_stream() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    join.setFailOnMissingMandatoryReference(false);

    Pipeline engine =
        run(
            join,
            new TransformMeta("INTERLIS Input", personInput()),
            new TransformMeta("lookup", addressInput()));
    assertThat(engine.getErrors()).isZero();
    List<RowMetaAndData> rows = engine.getResultRows();

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).getRowMeta().getFieldNames()).endsWith("Address_Street");
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

    Pipeline engine =
        run(
            join,
            new TransformMeta("INTERLIS Input", personInput()),
            new TransformMeta("lookup", lookupRows));
    assertThat(engine.getErrors()).isZero();
    List<RowMetaAndData> rows = engine.getResultRows();

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).getData()[rows.get(0).getData().length - 1]).isEqualTo("Main Street");
  }

  @Test
  void missing_mandatory_reference_fails_by_default() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    SyntheticLookupRowsMeta lookupRows = new SyntheticLookupRowsMeta();
    lookupRows.addRow("a1", "Main Street");

    Pipeline engine =
        run(
            join,
            new TransformMeta("INTERLIS Input", personInput()),
            new TransformMeta("lookup", lookupRows));

    // p2/p3 have a mandatory Address role but no link: the join fails.
    assertThat(failureLog(engine)).contains("mandatory target not found");
  }

  @Test
  void projects_external_role_bids_from_xtf_and_joins_across_baskets() throws Exception {
    var main = personInput();
    var lookup = addressInput();
    main.setClassName("HopIli_ExternalRole_V1.Data.Person");
    lookup.setClassName("HopIli_ExternalRole_V1.Data.Address");
    String transfer = TestData.path("/data/HopIli_ExternalRole_V1.xtf").toString();
    main.setFileName(transfer);
    lookup.setFileName(transfer);
    var join = roleJoin("lookup");
    join.setModelNames("HopIli_ExternalRole_V1");
    join.setMainClassName("HopIli_ExternalRole_V1.Data.Person");

    Pipeline engine =
        run(join, new TransformMeta("INTERLIS Input", main), new TransformMeta("lookup", lookup));
    assertThat(streets(engine)).containsExactly("Local street", "External street");
    assertThat(engine.getResultRows())
        .extracting(row -> row.getData()[row.getRowMeta().indexOfValue("Address_ref_bid")])
        .containsExactly(null, "b2");
  }

  @Test
  void resolves_local_and_explicit_references_only_in_the_target_basket() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    join.setFailOnMissingMandatoryReference(false);
    var lookup = new SyntheticLookupRowsMeta();
    lookup.setFields("Street", "_ili_tid", "_ili_bid");
    lookup.addRow("Local", "a1", "b1");
    lookup.addRow("External", "a1", "b2");

    assertThat(streets(run(join, mainRows(null, "b2", "b3"), lookup)))
        .containsExactly("Local", "External", null);
    assertThat(failureLog(run(roleJoin("lookup"), mainRows("b3"), lookup)))
        .contains(
            "Role Address",
            "lookup <lookup>",
            "TID <a1>",
            "BID <b3>",
            "mandatory target not found");
  }

  @Test
  void explicit_reference_rejects_lookup_rows_without_basket_information() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    join.setFailOnMissingMandatoryReference(false);
    var lookup = new SyntheticLookupRowsMeta();
    lookup.addRow("a1", "Without BID field");

    assertThat(failureLog(run(join, mainRows("b2"), lookup)))
        .contains(
            "Role Address",
            "lookup <lookup>",
            "TID <a1>",
            "BID <b2>",
            "requires a String _ili_bid");

    var lookupWithoutValue = new SyntheticLookupRowsMeta();
    lookupWithoutValue.setFields("_ili_tid", "Street", "_ili_bid");
    lookupWithoutValue.addRow("a1", "Without BID value", null);
    assertThat(failureLog(run(join, mainRows("b2"), lookupWithoutValue)))
        .contains("TID <a1>", "BID <b2>", "missing its _ili_bid value");
  }

  @Test
  void duplicate_policy_applies_to_the_effective_lookup_key() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    var lookup = new SyntheticLookupRowsMeta();
    lookup.setFields("_ili_tid", "_ili_bid", "Street");
    lookup.addRow("a1", "b2", "First");
    lookup.addRow("a1", "b2", "Second");

    assertThat(failureLog(run(join, mainRows("b2"), lookup)))
        .contains("TID <a1>", "BID <b2>", "duplicate lookup key");

    join.setFailOnDuplicateTid(false);
    assertThat(streets(run(join, mainRows("b2"), lookup))).containsExactly("First");

    var legacyLookup = new SyntheticLookupRowsMeta();
    legacyLookup.addRow("a1", "First");
    legacyLookup.addRow("a1", "Second");
    assertThat(failureLog(run(roleJoin("lookup"), mainRows((String) null), legacyLookup)))
        .contains("TID <a1>", "duplicate lookup key");
  }

  @Test
  void joins_by_tid_when_the_main_stream_has_no_basket_fields() throws Exception {
    InterlisRoleJoinMeta join = roleJoin("lookup");
    join.setFailOnMissingMandatoryReference(false);
    var input = personInput();
    input.setIncludeBid(false);
    assertThat(
            streets(
                run(
                    join,
                    new TransformMeta("INTERLIS Input", input),
                    new TransformMeta("lookup", addressInput()))))
        .containsExactly("Main Street", null, null);
  }
}
