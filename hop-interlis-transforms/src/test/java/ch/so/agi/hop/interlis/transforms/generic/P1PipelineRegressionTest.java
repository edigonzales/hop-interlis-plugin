package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.*;
import ch.so.agi.hop.interlis.transforms.objecttorow.*;
import ch.so.agi.hop.interlis.transforms.output.*;
import ch.so.agi.hop.interlis.transforms.testutil.*;
import ch.so.agi.hop.interlis.transforms.transferinput.*;
import ch.so.agi.hop.interlis.transforms.validate.*;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.pipeline.transform.*;
import org.apache.hop.pipeline.transforms.rowstoresult.RowsToResultMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class P1PipelineRegressionTest {
  @TempDir Path temp;
  static final String MODEL = "HopIli_P1_V1", ITEM = MODEL + ".Data.Item";
  static String dirs;

  @BeforeAll
  static void initialize() throws Exception {
    HopEnvironment.init();
    dirs = TestData.path("/models").toString();
  }

  static Pipeline run(int copies, boolean slow, ITransformMeta... metas) throws Exception {
    var pm = new PipelineMeta();
    TransformMeta previous = null;
    int index = 0;
    for (var meta : metas) {
      var t = new TransformMeta("step" + index++, meta);
      if (meta instanceof InterlisObjectToRowMeta
          || meta instanceof ch.so.agi.hop.interlis.transforms.collect.InterlisStructureCollectMeta
          || meta instanceof ch.so.agi.hop.interlis.transforms.rolejoin.InterlisRoleJoinMeta)
        t.setCopies(copies);
      pm.addTransform(t);
      if (previous != null) pm.addPipelineHop(new PipelineHopMeta(previous, t));
      previous = t;
    }
    var sink = new TransformMeta("result", new RowsToResultMeta());
    pm.addTransform(sink);
    pm.addPipelineHop(new PipelineHopMeta(previous, sink));
    var config = new PipelineRunConfiguration();
    var local = new LocalPipelineRunConfiguration();
    local.setEnginePluginId("Local");
    local.setRowSetSize("2");
    config.setEngineRunConfiguration(local);
    var engine = (Pipeline) PipelineEngineFactory.createPipelineEngine(config, pm);
    engine.prepareExecution();
    if (slow)
      engine
          .getTransform("result", 0)
          .addRowListener(
              new RowAdapter() {
                @Override
                public void rowReadEvent(IRowMeta m, Object[] row) {
                  try {
                    Thread.sleep(10);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }
              });
    engine.startThreads();
    engine.waitUntilFinished();
    return engine;
  }

  InterlisTransferInputMeta input(Path file) {
    var m = new InterlisTransferInputMeta();
    m.setDefault();
    m.setFileName(file.toString());
    m.setModelNames("%DATA");
    m.setModelDirectories(dirs);
    return m;
  }

  InterlisObjectToRowMeta objectMeta(String model, String clazz) {
    var m = new InterlisObjectToRowMeta();
    m.setDefault();
    m.setModelNames(model);
    m.setModelDirectories(dirs);
    m.setClassName(clazz);
    return m;
  }

  InterlisValidateMeta validation(Path file) {
    var m = new InterlisValidateMeta();
    m.setDefault();
    m.setFileName(file.toString());
    m.setModelNames("%DATA");
    m.setModelDirectories(dirs);
    return m;
  }

  Path transfer(boolean missing, boolean duplicate, int count) throws Exception {
    var projection =
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(null, List.of(MODEL), List.of(dirs)),
                ITEM,
                ProjectionOptions.defaults());
    var file = temp.resolve(UUID.randomUUID() + ".xtf");
    try (var w =
        XtfTransferWriter.open(file, projection.model().transferDescription(), List.of(MODEL))) {
      w.startTransfer("regression");
      w.startBasket(MODEL + ".Data", "b1");
      for (int i = 0; i < count; i++) {
        var item = new Iom_jObject(ITEM, "i" + i);
        item.setattrvalue("Name", duplicate ? "same" : "item" + i);
        var ref = new Iom_jObject("REF", null);
        ref.setobjectrefoid(missing ? "absent" : "t1");
        item.addattrobj("Target", ref);
        w.writeObject(item);
      }
      var target = new Iom_jObject(MODEL + ".Data.Target", "t1");
      target.setattrvalue("Name", "target");
      w.writeObject(target);
      w.endBasket();
      w.endTransfer();
    }
    return file;
  }

  @Test
  void
      complete_validation_accepts_forward_reference_and_detects_missing_target_and_unique_constraint()
          throws Exception {
    var good = run(1, false, validation(transfer(false, false, 2)));
    assertThat(good.getErrors()).isZero();
    assertThat(good.getResultRows()).isEmpty();
    var missing = run(1, false, validation(transfer(true, false, 2)));
    assertThat(missing.getResultRows())
        .anySatisfy(r -> assertThat(r.getData()[1].toString()).contains("absent"));
    var duplicates = run(1, false, validation(transfer(false, true, 2)));
    assertThat(duplicates.getResultRows())
        .anySatisfy(r -> assertThat(r.getData()[0]).isEqualTo("ERROR"));
  }

  @Test
  void fail_after_completion_delivers_all_errors_to_slow_consumer() throws Exception {
    var m = validation(transfer(true, false, 10));
    m.setFailOnErrors(true);
    var pipeline = run(1, true, m);
    assertThat(pipeline.getErrors()).isPositive();
    assertThat(pipeline.getResultRows().stream().filter(r -> "ERROR".equals(r.getData()[0])))
        .hasSize(10);
  }

  @Test
  void error_limit_counts_only_errors_and_marks_incomplete_even_with_warnings_hidden()
      throws Exception {
    var m = validation(transfer(true, false, 4));
    m.setMaxErrors(1);
    m.setIncludeWarnings(false);
    var pipeline = run(1, false, m);
    assertThat(pipeline.getErrors()).isZero();
    assertThat(pipeline.getResultRows().stream().filter(r -> "ERROR".equals(r.getData()[0])))
        .hasSize(1);
    assertThat(pipeline.getResultRows())
        .anySatisfy(r -> assertThat(r.getData()[1].toString()).contains("incomplete"));
  }

  @Test
  void complete_validation_runs_second_pass_once_and_honors_explicit_target_configuration()
      throws Exception {
    var file = transfer(true, false, 2);
    var meta = validation(file);
    meta.setIncludeInfo(true);
    meta.setMaxErrors(0);
    var result = run(1, false, meta);
    assertThat(
            result.getResultRows().stream()
                .filter(r -> r.getData()[1].toString().contains("second validation pass")))
        .hasSize(1);
    var config = temp.resolve("target-off.toml");
    Files.writeString(config, "[HopIli_P1_V1.Data.ItemTarget.Target]\ntarget=\"off\"\n");
    meta.setConfigFile(config.toString());
    assertThat(run(1, false, meta).getResultRows()).noneMatch(r -> "ERROR".equals(r.getData()[0]));
  }

  @Test
  void named_valid_association_fixture_passes_complete_validation() throws Exception {
    var result = run(1, false, validation(TestData.path("/data/HopIli_Associations_V1_valid.xtf")));
    assertThat(result.getResultRows().stream().map(r -> r.getData()[1].toString()).toList())
        .isEmpty();
  }

  @Test
  void negative_error_limit_is_rejected() throws Exception {
    var m = validation(transfer(false, false, 1));
    m.setMaxErrors(-1);
    assertThat(run(1, false, m).getErrors()).isPositive();
  }

  @Test
  void appended_buffered_rows_match_design_time_schema_and_values() throws Exception {
    var file = TestData.path("/data/HopIli_Associations_V1_mapping.xtf");
    var m = objectMeta("HopIli_Associations_V1", "HopIli_Associations_V1.Data.Person");
    m.setAppendEnvelopeFields(true);
    var schema = InterlisEnvelopeSchemaFactory.createRowMeta();
    m.getFields(schema, "test", null, null, new Variables(), null);
    var pipeline = run(1, false, input(file), m);
    assertThat(pipeline.getErrors()).isZero();
    assertThat(pipeline.getResultRows())
        .hasSize(3)
        .allSatisfy(
            r -> {
              assertThat(r.getRowMeta().getFieldNames()).containsExactly(schema.getFieldNames());
              assertThat(r.getData()).hasSize(schema.size());
              assertThat(r.getData()[0]).isEqualTo("OBJECT");
            });
    var first = pipeline.getResultRows().getFirst();
    assertThat(first.getData()[schema.indexOfValue("Name")]).isEqualTo("Meier");
    assertThat(first.getData()[schema.indexOfValue("Address_ref")]).isEqualTo("a1");
  }

  @Test
  void unsafe_parallel_mapping_is_rejected_but_straight_projection_preserves_all_rows()
      throws Exception {
    String text = Files.readString(TestData.path("/data/HopIli_Associations_V1_mapping.xtf"));
    var file = temp.resolve("reordered.xtf");
    Files.writeString(
        file,
        text.replace(
            "<HopIli_Associations_V1:AddressOwnership>",
            "<HopIli_Associations_V1:Person"
                + " ili:tid=\"p4\"><HopIli_Associations_V1:Name>Extra</HopIli_Associations_V1:Name></HopIli_Associations_V1:Person><HopIli_Associations_V1:AddressOwnership>"));
    var m = objectMeta("HopIli_Associations_V1", "HopIli_Associations_V1.Data.Person");
    var bad = run(2, false, input(file), m);
    assertThat(bad.getErrors()).isPositive();
    assertThat(bad.getResultRows()).isEmpty();
    var goodFile = transfer(false, false, 11);
    var safe = objectMeta(MODEL, ITEM);
    var good = run(2, false, input(goodFile), safe);
    assertThat(good.getErrors()).isZero();
    assertThat(good.getResultRows())
        .hasSize(11)
        .allSatisfy(
            r -> {
              assertThat(r.getData()[r.getRowMeta().indexOfValue("Name")])
                  .isEqualTo("item" + r.getData()[0].toString().substring(1));
              assertThat(r.getData()[r.getRowMeta().indexOfValue("Target_ref")]).isEqualTo("t1");
            });
    assertThat(good.getResultRows().stream().map(r -> r.getData()[0]))
        .containsExactlyInAnyOrderElementsOf(
            java.util.stream.IntStream.range(0, 11).mapToObj(i -> "i" + i).toList());
  }

  @Test
  void multi_stream_collect_and_join_reject_multiple_copies_before_output() throws Exception {
    for (var meta :
        List.of(
            new ch.so.agi.hop.interlis.transforms.collect.InterlisStructureCollectMeta(),
            new ch.so.agi.hop.interlis.transforms.rolejoin.InterlisRoleJoinMeta())) {
      var source = new RegressionRowsMeta();
      source.schema = new RowMeta();
      source.schema.addValueMeta(new ValueMetaString("dummy"));
      source.rows.add(new Object[] {"value"});
      var pipeline = run(2, false, source, meta);
      assertThat(pipeline.getErrors()).isPositive();
      assertThat(pipeline.getResultRows()).isEmpty();
    }
  }

  @Test
  void output_uses_the_same_resolved_basket_binding_as_metadata() throws Exception {
    var p =
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(null, List.of(MODEL), List.of(dirs)),
                ITEM,
                ProjectionOptions.defaults());
    var source = new RegressionRowsMeta();
    source.schema = new HopRowSchemaFactory().createRowMeta(p.plan());
    source.schema.getValueMeta(source.schema.indexOfValue("_ili_bid")).setName("custom_bid");
    var row = new Object[source.schema.size()];
    row[source.schema.indexOfValue("_ili_tid")] = "wanted";
    row[source.schema.indexOfValue("custom_bid")] = "field-basket";
    row[source.schema.indexOfValue("Name")] = "item";
    source.rows.add(row);
    var out = new InterlisOutputMeta();
    out.setDefault();
    out.setFileName(temp.resolve("custom-bid.xtf").toString());
    out.setModelNames(MODEL);
    out.setModelDirectories(dirs);
    out.setClassName(ITEM);
    out.setBasketIdField(" custom_bid ");
    out.setBasketId("fallback");
    assertThat(run(1, false, source, out).getErrors()).isZero();
    assertThat(Files.readString(Path.of(out.getFileName())))
        .contains("ili:bid=\"field-basket\"")
        .doesNotContain("ili:bid=\"fallback\"");
  }

  @Test
  void output_uses_custom_tid_and_constant_basket_without_reserved_fields() throws Exception {
    var p =
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(null, List.of(MODEL), List.of(dirs)),
                ITEM,
                ProjectionOptions.defaults());
    var source = new RegressionRowsMeta();
    source.schema = new HopRowSchemaFactory().createRowMeta(p.plan());
    source.schema.getValueMeta(source.schema.indexOfValue("_ili_tid")).setName("custom_id");
    source.schema.removeValueMeta(source.schema.indexOfValue("_ili_bid"));
    source.schema.addValueMeta(new ValueMetaString("_ili_tid"));
    var row = new Object[source.schema.size()];
    row[source.schema.indexOfValue("custom_id")] = "wanted";
    row[source.schema.indexOfValue("_ili_tid")] = "wrong";
    row[source.schema.indexOfValue("Name")] = "item";
    source.rows.add(row);
    var out = new InterlisOutputMeta();
    out.setDefault();
    out.setFileName(temp.resolve("out.xtf").toString());
    out.setModelNames(MODEL);
    out.setModelDirectories(dirs);
    out.setClassName(ITEM);
    out.setObjectIdField("custom_id");
    out.setBasketIdField("");
    out.setBasketId("constant");
    var pipeline = run(1, false, source, out);
    assertThat(pipeline.getErrors()).isZero();
    try (var reader =
        XtfTransferReader.open(Path.of(out.getFileName()), p.model().transferDescription())) {
      InterlisObjectEnvelope e;
      do {
        e = reader.next();
      } while (e.eventType() != InterlisEventType.OBJECT);
      assertThat(e.objectId()).isEqualTo("wanted");
      assertThat(e.basketId()).isEqualTo("constant");
    }
  }
}
