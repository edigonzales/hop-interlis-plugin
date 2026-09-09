package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.transforms.*;
import ch.so.agi.hop.interlis.transforms.explode.*;
import ch.so.agi.hop.interlis.transforms.rolejoin.*;
import ch.so.agi.hop.interlis.transforms.testutil.*;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;

@Timeout(30)
class BindingPipelineRegressionTest {
  static String models;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
    models = TestData.path("/models").toString();
  }

  static ValueMetaString lazy(String name) {
    var result = new ValueMetaString(name);
    result.setStorageType(IValueMeta.STORAGE_TYPE_BINARY_STRING);
    result.setStorageMetadata(new ValueMetaString(name));
    return result;
  }

  @Test
  void explode_preserves_layout_and_pass_through_storage_and_rejects_bad_keys() throws Exception {
    for (boolean invalid : new boolean[] {false, true}) {
      var source = new RegressionRowsMeta();
      source.schema = new RowMeta();
      source.schema.addValueMeta(lazy("context"));
      source.schema.addValueMeta(new ValueMetaInterlisObject("carrier"));
      source.schema.addValueMeta(invalid ? new ValueMetaInteger("key") : lazy("key"));
      var object = new Iom_jObject("HopIli_P1_V1.Data.Item", "p1");
      var child = object.addattrobj("Children", "HopIli_P1_V1.Data.Detail");
      child.setattrvalue("Code", "kept");
      byte[] context = "context".getBytes();
      source.rows.add(new Object[] {context, object, invalid ? 1L : "p1".getBytes()});
      var meta = new InterlisStructureExplodeMeta();
      meta.setDefault();
      meta.setModelNames("HopIli_P1_V1");
      meta.setModelDirectories(models);
      meta.setClassName("HopIli_P1_V1.Data.Item");
      meta.setStructureAttributePath("Children");
      meta.setSourceObjectField("carrier");
      meta.setParentTidField("key");
      meta.setEmitParentBid(false);
      meta.setIncludeParentFields(List.of("context"));
      var expected = source.schema.clone();
      meta.getFields(expected, "explode", null, null, new Variables(), null);
      var result = P1PipelineRegressionTest.run(1, false, source, meta);
      if (invalid) assertThat(result.getErrors()).isPositive();
      else {
        assertThat(result.getErrors()).isZero();
        var row = result.getResultRows().getFirst();
        assertThat(row.getRowMeta().getFieldNames()).containsExactly(expected.getFieldNames());
        for (int i = 0; i < expected.size(); i++)
          assertThat(row.getRowMeta().getValueMeta(i).getType())
              .isEqualTo(expected.getValueMeta(i).getType());
        assertThat(row.getData()[0]).isEqualTo("p1");
        assertThat(row.getData()[expected.indexOfValue("Code")]).isEqualTo("kept");
        assertThat(row.getData()[expected.indexOfValue("context")]).isSameAs(context);
        assertThat(row.getRowMeta().getValueMeta(expected.indexOfValue("context")).getStorageType())
            .isEqualTo(IValueMeta.STORAGE_TYPE_BINARY_STRING);
        assertThat(object.getattrvaluecount("Children")).isEqualTo(1);
      }
    }
  }

  @Test
  void join_checks_both_streams_and_matches_design_layout_with_lazy_values() throws Exception {
    for (String fault : List.of("none", "numeric-key", "missing-field", "collision")) {
      var main = new RegressionRowsMeta();
      main.schema = new RowMeta();
      main.schema.addValueMeta(
          new ValueMetaString(fault.equals("collision") ? "Address_Street" : "context"));
      main.schema.addValueMeta(new ValueMetaString("reference"));
      main.rows.add(new Object[] {"keep", "a1"});
      var lookup = new RegressionRowsMeta();
      lookup.schema = new RowMeta();
      lookup.schema.addValueMeta(lazy(fault.equals("missing-field") ? "wrong" : "Street"));
      lookup.schema.addValueMeta(
          fault.equals("numeric-key") ? new ValueMetaInteger("key") : lazy("key"));
      lookup.rows.add(
          new Object[] {
            "Main Street".getBytes(), fault.equals("numeric-key") ? 1L : "a1".getBytes()
          });
      var meta = new InterlisRoleJoinMeta();
      meta.setDefault();
      meta.setModelNames("HopIli_Associations_V1");
      meta.setModelDirectories(models);
      meta.setMainClassName("HopIli_Associations_V1.Data.Person");
      meta.setRoleName("Address");
      meta.setMainInputTransform("main");
      meta.setLookupInputTransform("lookup");
      meta.setMainReferenceField("reference");
      meta.setLookupTidField("key");
      meta.setLookupFields(List.of("Street"));
      var pm = new PipelineMeta();
      var mainTransform = new TransformMeta("main", main);
      var lookupTransform = new TransformMeta("lookup", lookup);
      var join = new TransformMeta("join", meta);
      pm.addTransform(mainTransform);
      pm.addTransform(lookupTransform);
      pm.addTransform(join);
      pm.addPipelineHop(new PipelineHopMeta(mainTransform, join));
      pm.addPipelineHop(new PipelineHopMeta(lookupTransform, join));
      var sink =
          new TransformMeta(
              "result", new org.apache.hop.pipeline.transforms.rowstoresult.RowsToResultMeta());
      pm.addTransform(sink);
      pm.addPipelineHop(new PipelineHopMeta(join, sink));
      var checks = new ArrayList<ICheckResult>();
      meta.check(
          checks,
          pm,
          join,
          main.schema,
          new String[] {"main", "lookup"},
          new String[] {"result"},
          lookup.schema,
          new Variables(),
          null);
      var config = new org.apache.hop.pipeline.config.PipelineRunConfiguration();
      var local = new org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration();
      local.setEnginePluginId("Local");
      local.setRowSetSize("2");
      config.setEngineRunConfiguration(local);
      var engine =
          (Pipeline)
              org.apache.hop.pipeline.engine.PipelineEngineFactory.createPipelineEngine(config, pm);
      engine.prepareExecution();
      engine.startThreads();
      engine.waitUntilFinished();
      if (!fault.equals("none")) {
        assertThat(checks).anyMatch(c -> c.getType() == ICheckResult.TYPE_RESULT_ERROR);
        assertThat(engine.getErrors()).isPositive();
      } else {
        assertThat(checks).noneMatch(c -> c.getType() == ICheckResult.TYPE_RESULT_ERROR);
        assertThat(engine.getErrors()).isZero();
        var expected = main.schema.clone();
        meta.getFields(expected, "join", null, null, new Variables(), null);
        var row = engine.getResultRows().getFirst();
        assertThat(row.getRowMeta().getFieldNames()).containsExactly(expected.getFieldNames());
        assertThat(row.getData()).containsExactly("keep", "a1", "Main Street");
        assertThat(row.getRowMeta().getValueMeta(2).getStorageType())
            .isEqualTo(IValueMeta.STORAGE_TYPE_NORMAL);
      }
    }
  }
}
