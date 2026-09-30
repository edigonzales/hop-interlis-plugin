package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.transforms.collect.*;
import ch.so.agi.hop.interlis.transforms.explode.*;
import ch.so.agi.hop.interlis.transforms.rolejoin.*;
import ch.so.agi.hop.interlis.transforms.testutil.*;
import ch.so.agi.hop.interlis.transforms.value.*;
import java.nio.file.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.config.*;
import org.apache.hop.pipeline.engine.*;
import org.apache.hop.pipeline.engines.local.*;
import org.apache.hop.pipeline.transform.*;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.rowstoresult.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class SharedProducerTest {
  @TempDir Path temp;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void role_join_consumes_both_branches_of_one_producer_with_queue_one_and_two() throws Exception {
    for (int queue : new int[] {1, 2}) {
      var source = new RegressionRowsMeta();
      source.schema = new RowMeta();
      source.schema.addValueMeta(new ValueMetaString("_ili_tid"));
      source.schema.addValueMeta(new ValueMetaString("Target_ref"));
      source.schema.addValueMeta(new ValueMetaString("Name"));
      for (int i = 0; i < 100; i++)
        source.rows.add(
            new Object[] {String.format("i%04d", i), String.format("i%04d", i), "item" + i});
      var join = new InterlisRoleJoinMeta();
      join.setDefault();
      join.setModelNames("HopIli_P1_V1");
      join.setModelDirectories(TestData.path("/models").toString());
      join.setMainClassName("HopIli_P1_V1.Data.Item");
      join.setRoleName("Target");
      join.setMainInputTransform("main");
      join.setLookupInputTransform("other");
      join.setMaxLookupRows(0);
      join.setBufferMemoryMiB(1);
      join.setSpillDirectory(temp.toString());
      var engine = run(queue, source, new DummyMeta(), new DummyMeta(), join);
      assertThat(engine.getErrors()).isZero();
      assertThat(engine.getResultRows()).hasSize(100);
      for (int i = 0; i < 100; i++)
        assertThat(engine.getResultRows().get(i).getData()[3]).isEqualTo("item" + i);
    }
  }

  @Test
  void collect_consumes_explode_and_parent_branches_and_keeps_child_carriers() throws Exception {
    for (int queue : new int[] {1, 2}) {
      var source = new RegressionRowsMeta();
      source.schema = new RowMeta();
      source.schema.addValueMeta(new ValueMetaString("_ili_tid"));
      source.schema.addValueMeta(new ValueMetaString("_ili_bid"));
      source.schema.addValueMeta(new ValueMetaInterlisObject("_ili_source_object"));
      for (int i = 0; i < 100; i++) {
        var id = String.format("i%04d", i % 50);
        var item = new Iom_jObject("HopIli_Collections_V1.Data.Item", id);
        for (int c = 0; c < 3; c++) {
          var child = new Iom_jObject("HopIli_Collections_V1.Data.SpecialChild", null);
          child.setattrvalue("Name", "child" + c);
          child.setattrvalue("Extra", "subtype");
          child.setattrvalue("Hidden", "keep");
          item.addattrobj("Children", child);
        }
        source.rows.add(new Object[] {id, i < 50 ? "b1" : "b2", item});
      }
      var explode = new InterlisStructureExplodeMeta();
      explode.setDefault();
      explode.setModelNames("HopIli_Collections_V1");
      explode.setModelDirectories(TestData.path("/models").toString());
      explode.setClassName("HopIli_Collections_V1.Data.Item");
      explode.setStructureAttributePath("Children");
      explode.setSelectedChildFields(java.util.List.of("Name"));
      var collect = new InterlisStructureCollectMeta();
      collect.setDefault();
      collect.setModelNames("HopIli_Collections_V1");
      collect.setModelDirectories(TestData.path("/models").toString());
      collect.setClassName("HopIli_Collections_V1.Data.Item");
      collect.setStructureAttributePath("Children");
      collect.setSelectedChildFields(java.util.List.of("Name"));
      collect.setParentInputTransform("main");
      collect.setChildInputTransform("other");
      collect.setBufferMemoryMiB(1);
      collect.setSpillDirectory(temp.toString());
      var engine = run(queue, source, new DummyMeta(), explode, collect);
      assertThat(engine.getErrors()).isZero();
      assertThat(engine.getResultRows()).hasSize(100);
      for (var row : engine.getResultRows()) {
        var item = (ch.interlis.iom.IomObject) row.getData()[2];
        assertThat(item.getattrvaluecount("Children")).isEqualTo(3);
        assertThat(item.getattrobj("Children", 0).getattrvalue("Extra")).isEqualTo("subtype");
        assertThat(item.getattrobj("Children", 0).getattrvalue("Hidden")).isEqualTo("keep");
      }
    }
    try (var files = Files.list(temp)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  Pipeline run(
      int queue,
      RegressionRowsMeta source,
      ITransformMeta main,
      ITransformMeta other,
      ITransformMeta consumer)
      throws Exception {
    var pm = new PipelineMeta();
    var a = new TransformMeta("source", source);
    a.setDistributes(false);
    var b = new TransformMeta("main", main);
    var c = new TransformMeta("other", other);
    var d = new TransformMeta("consumer", consumer);
    var e = new TransformMeta("result", new RowsToResultMeta());
    for (var transform : java.util.List.of(a, b, c, d, e)) pm.addTransform(transform);
    pm.addPipelineHop(new PipelineHopMeta(a, b));
    pm.addPipelineHop(new PipelineHopMeta(a, c));
    pm.addPipelineHop(new PipelineHopMeta(b, d));
    pm.addPipelineHop(new PipelineHopMeta(c, d));
    pm.addPipelineHop(new PipelineHopMeta(d, e));
    var config = new PipelineRunConfiguration();
    var local = new LocalPipelineRunConfiguration();
    local.setEnginePluginId("Local");
    local.setRowSetSize(Integer.toString(queue));
    config.setEngineRunConfiguration(local);
    var engine = (Pipeline) PipelineEngineFactory.createPipelineEngine(config, pm);
    engine.prepareExecution();
    engine
        .getTransform("result", 0)
        .addRowListener(
            new RowAdapter() {
              @Override
              public void rowReadEvent(IRowMeta meta, Object[] row) {
                try {
                  Thread.sleep(2);
                } catch (InterruptedException error) {
                  Thread.currentThread().interrupt();
                }
              }
            });
    engine.startThreads();
    engine.waitUntilFinished();
    return engine;
  }
}
