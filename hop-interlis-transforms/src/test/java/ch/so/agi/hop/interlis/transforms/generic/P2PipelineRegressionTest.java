package ch.so.agi.hop.interlis.transforms.generic;

import static ch.so.agi.hop.interlis.transforms.generic.P1PipelineRegressionTest.run;
import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.*;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import ch.so.agi.hop.interlis.transforms.objecttorow.*;
import ch.so.agi.hop.interlis.transforms.rowtoobject.*;
import ch.so.agi.hop.interlis.transforms.testutil.*;
import ch.so.agi.hop.interlis.transforms.transferoutput.*;
import ch.so.agi.hop.interlis.transforms.value.*;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class P2PipelineRegressionTest {
  @TempDir Path dir;
  static final String MODEL = "HopIli_P1_V1", ITEM = MODEL + ".Data.Item";
  static String models;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
    models = TestData.path("/models").toString();
  }

  @Test
  void reordered_envelope_and_custom_object_field_preserve_append_positions() throws Exception {
    var source = new RegressionRowsMeta();
    source.schema = new RowMeta();
    source.schema.addValueMeta(new ValueMetaString("context"));
    source.schema.addValueMeta(new ValueMetaInterlisObject("payload"));
    source.schema.addValueMeta(new ValueMetaString("_ili_bid"));
    var object = new Iom_jObject(ITEM, "i1");
    object.setattrvalue("Name", "typed");
    source.rows.add(new Object[] {"first", object, "b1"});
    var meta = new InterlisObjectToRowMeta();
    meta.setDefault();
    meta.setModelNames(MODEL);
    meta.setModelDirectories(models);
    meta.setClassName(ITEM);
    meta.setObjectFieldName("payload");
    meta.setAppendEnvelopeFields(true);
    var pipeline = run(2, false, source, meta);
    assertThat(pipeline.getErrors()).isZero();
    assertThat(pipeline.getResultRows()).hasSize(1);
    var result = pipeline.getResultRows().getFirst();
    assertThat(result.getData()[0]).isEqualTo("first");
    assertThat(result.getData()[1]).isSameAs(object);
    assertThat(result.getData()[2]).isEqualTo("b1");
    assertThat(result.getData()[result.getRowMeta().indexOfValue("Name")]).isEqualTo("typed");
    assertThat(result.getData()).hasSize(result.getRowMeta().size());
  }

  @Test
  void invalid_event_and_wrong_field_types_fail_with_context() throws Exception {
    var schema = InterlisEnvelopeSchemaFactory.createRowMeta();
    var bindings = InterlisEnvelopeBindings.bind(schema, "_ili_object", true);
    var row = new Object[schema.size()];
    row[0] = "TYPO";
    row[3] = "b1";
    row[5] = "i1";
    assertThatThrownBy(() -> bindings.fromRow(row))
        .hasMessageContaining("_ili_event_type")
        .hasMessageContaining("i1");
    schema.removeValueMeta(7);
    schema.addValueMeta(new ValueMetaString("_ili_object"));
    assertThatThrownBy(() -> InterlisEnvelopeBindings.bind(schema, "_ili_object", true))
        .hasMessageContaining("_ili_object");
  }

  @Test
  void row_to_object_preserves_carrier_operation_and_unprojected_payload_and_delete_is_empty()
      throws Exception {
    var p =
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(null, List.of(MODEL), List.of(models)),
                ITEM,
                ProjectionOptions.defaults());
    var source = new RegressionRowsMeta();
    source.schema = new HopRowSchemaFactory().createRowMeta(p.plan());
    source.schema.addValueMeta(new ValueMetaInterlisObject("carrier"));
    source.schema.addValueMeta(new ValueMetaString("action"));
    var carrier = new Iom_jObject(ITEM, "i1");
    carrier.setattrvalue("Name", "old");
    carrier.setobjectoperation(InterlisObjectOperation.UPDATE.toIom());
    var child = new Iom_jObject(MODEL + ".Data.Detail", null);
    child.setattrvalue("Code", "keep");
    carrier.addattrobj("Children", child);
    var row = new Object[source.schema.size()];
    row[0] = "i1";
    row[1] = "b1";
    row[source.schema.indexOfValue("Name")] = "new";
    row[row.length - 2] = carrier;
    source.rows.add(row);
    var delete = row.clone();
    delete[0] = "i2";
    delete[delete.length - 1] = "DELETE";
    source.rows.add(delete);
    var meta = new InterlisRowToObjectMeta();
    meta.setDefault();
    meta.setModelNames(MODEL);
    meta.setModelDirectories(models);
    meta.setClassName(ITEM);
    meta.setSourceObjectField("carrier");
    meta.setOperationField("action");
    var pipeline = run(1, false, source, meta);
    assertThat(pipeline.getErrors()).isZero();
    assertThat(pipeline.getResultRows()).hasSize(2);
    var result = InterlisEnvelopeRowLayout.fromRow(pipeline.getResultRows().getFirst().getData());
    assertThat(result.operation()).isEqualTo(InterlisObjectOperation.UPDATE);
    assertThat(result.object().getattrvalue("Name")).isEqualTo("new");
    assertThat(result.object().getattrvaluecount("Children")).isEqualTo(1);
    assertThat(carrier.getattrvalue("Name")).isEqualTo("old");
    assertThat(
            InterlisEnvelopeRowLayout.fromRow(pipeline.getResultRows().getLast().getData())
                .object()
                .getattrcount())
        .isZero();
  }

  @Test
  void incomplete_event_stream_fails_the_pipeline() throws Exception {
    var source = new RegressionRowsMeta();
    source.schema = InterlisEnvelopeSchemaFactory.createRowMeta();
    source.rows.add(
        InterlisEnvelopeRowLayout.toRow(
            new InterlisObjectEnvelope(
                InterlisEventType.START_TRANSFER,
                null,
                null,
                null,
                null,
                null,
                InterlisObjectOperation.NONE,
                null)));
    var meta = new InterlisTransferOutputMeta();
    meta.setDefault();
    meta.setModelNames(MODEL);
    meta.setModelDirectories(models);
    meta.setFileName(dir.resolve("incomplete.xtf").toString());
    meta.setEventMode(true);
    assertThat(run(1, false, source, meta).getErrors()).isPositive();
  }

  @Test
  void event_objects_must_match_the_open_basket() throws Exception {
    for (boolean mismatch : new boolean[] {false, true}) {
      var source = new RegressionRowsMeta();
      source.schema = InterlisEnvelopeSchemaFactory.createRowMeta();
      for (var type :
          List.of(
              InterlisEventType.START_TRANSFER,
              InterlisEventType.START_BASKET,
              InterlisEventType.OBJECT,
              InterlisEventType.END_BASKET,
              InterlisEventType.END_TRANSFER)) {
        boolean object = type == InterlisEventType.OBJECT;
        source.rows.add(
            InterlisEnvelopeRowLayout.toRow(
                new InterlisObjectEnvelope(
                    type,
                    MODEL,
                    MODEL + ".Data",
                    mismatch && object ? "wrong-basket" : "b1",
                    object ? ITEM : null,
                    object ? "i1" : null,
                    InterlisObjectOperation.NONE,
                    object ? new Iom_jObject(ITEM, "i1") : null)));
      }
      var meta = new InterlisTransferOutputMeta();
      meta.setDefault();
      meta.setModelNames(MODEL);
      meta.setModelDirectories(models);
      meta.setFileName(dir.resolve("basket-" + mismatch + ".xtf").toString());
      meta.setEventMode(true);
      if (mismatch) assertThat(run(1, false, source, meta).getErrors()).isPositive();
      else assertThat(run(1, false, source, meta).getErrors()).isZero();
    }
  }

  @Test
  void empty_parent_stream_drains_or_rejects_children_according_to_policy() throws Exception {
    for (boolean fail : new boolean[] {false, true}) {
      var parents = new RegressionRowsMeta();
      parents.schema = new RowMeta();
      parents.schema.addValueMeta(new ValueMetaString("_ili_tid"));
      parents.schema.addValueMeta(new ValueMetaInterlisObject("_ili_source_object"));
      var children = new RegressionRowsMeta();
      children.schema = new RowMeta();
      children.schema.addValueMeta(new ValueMetaString("_ili_parent_tid"));
      children.schema.addValueMeta(new ValueMetaInteger("_ili_index"));
      children.schema.addValueMeta(new ValueMetaString("Code"));
      children.schema.addValueMeta(new ValueMetaString("Note"));
      children.schema.addValueMeta(
          org.apache.hop.core.row.value.ValueMetaFactory.createValueMeta(
              "Location",
              org.apache.hop.core.row.value.ValueMetaFactory.getIdForValueMeta("Geometry")));
      for (int i = 0; i < 100; i++)
        children.rows.add(new Object[] {"absent", (long) i, "child", null, null});
      var collect = new ch.so.agi.hop.interlis.transforms.collect.InterlisStructureCollectMeta();
      collect.setDefault();
      collect.setModelNames(MODEL);
      collect.setModelDirectories(models);
      collect.setClassName(ITEM);
      collect.setStructureAttributePath("Children");
      collect.setParentInputTransform("parents");
      collect.setChildInputTransform("children");
      collect.setFailOnChildWithoutParent(fail);
      var pm = new org.apache.hop.pipeline.PipelineMeta();
      var parent = new org.apache.hop.pipeline.transform.TransformMeta("parents", parents);
      var child = new org.apache.hop.pipeline.transform.TransformMeta("children", children);
      var transform = new org.apache.hop.pipeline.transform.TransformMeta("collect", collect);
      pm.addTransform(parent);
      pm.addTransform(child);
      pm.addTransform(transform);
      pm.addPipelineHop(new org.apache.hop.pipeline.PipelineHopMeta(parent, transform));
      pm.addPipelineHop(new org.apache.hop.pipeline.PipelineHopMeta(child, transform));
      var config = new org.apache.hop.pipeline.config.PipelineRunConfiguration();
      var local = new org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration();
      local.setEnginePluginId("Local");
      local.setRowSetSize("2");
      config.setEngineRunConfiguration(local);
      var engine =
          org.apache.hop.pipeline.engine.PipelineEngineFactory.createPipelineEngine(config, pm);
      engine.prepareExecution();
      engine.startThreads();
      engine.waitUntilFinished();
      if (fail) assertThat(engine.getErrors()).isPositive();
      else assertThat(engine.getErrors()).isZero();
    }
  }
}
