package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.core.update.*;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import ch.so.agi.hop.interlis.transforms.output.*;
import ch.so.agi.hop.interlis.transforms.testutil.*;
import ch.so.agi.hop.interlis.transforms.update.*;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.config.*;
import org.apache.hop.pipeline.engine.*;
import org.apache.hop.pipeline.engines.local.*;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
class Prio3PipelineTest {
  static final String MODEL = "HopIli_Collections_V1", CLS = MODEL + ".Data.";
  @TempDir Path temp;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  Path original() {
    return TestData.path("/data/" + MODEL + "_valid.xtf");
  }

  InterlisModelContext model() throws Exception {
    return new InterlisProjectionService()
        .loadModel(
            new InterlisModelRequest(
                original(), List.of(MODEL), List.of(TestData.path("/models").toString())));
  }

  List<InterlisObjectEnvelope> read(Path file) throws Exception {
    var events = new ArrayList<InterlisObjectEnvelope>();
    try (var reader = XtfTransferReader.open(file, model().model().transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) events.add(event);
    }
    return events;
  }

  RegressionRowsMeta rows(String... fields) {
    var source = new RegressionRowsMeta();
    source.schema = new RowMeta();
    for (String field : fields) source.schema.addValueMeta(new ValueMetaString(field));
    return source;
  }

  InterlisMappedInput mapping(String transform, String cls, String source, String target) {
    var config = new InterlisMappedInput();
    config.setTransformName(transform);
    config.setClassName(CLS + cls);
    if (source != null) config.setFields(List.of(new InterlisFieldAssignment(source, target)));
    return config;
  }

  InterlisUpdateMeta update(InterlisMappedInput... inputs) {
    var update = new InterlisUpdateMeta();
    update.setOriginalFile(original().toString());
    update.setFileName(temp.resolve("updated.xtf").toString());
    update.setModelDirectories(TestData.path("/models").toString());
    update.setInputs(List.of(inputs));
    update.setBufferMemoryMiB(1);
    return update;
  }

  Pipeline run(ITransformMeta sink, Map<String, RegressionRowsMeta> sources) throws Exception {
    var pm = new PipelineMeta();
    var target = new TransformMeta("sink", sink);
    pm.addTransform(target);
    for (var entry : sources.entrySet()) {
      var source = new TransformMeta(entry.getKey(), entry.getValue());
      pm.addTransform(source);
      pm.addPipelineHop(new PipelineHopMeta(source, target));
    }
    sink.searchInfoAndTargetTransforms(pm.getTransforms());
    var config = new PipelineRunConfiguration();
    var local = new LocalPipelineRunConfiguration();
    local.setEnginePluginId("Local");
    local.setRowSetSize("1");
    config.setEngineRunConfiguration(local);
    var engine = (Pipeline) PipelineEngineFactory.createPipelineEngine(config, pm);
    engine.setSafeModeEnabled(true);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();
    return engine;
  }

  InterlisObjectEnvelope object(List<InterlisObjectEnvelope> events, String tid) {
    return events.stream().filter(e -> tid.equals(e.objectId())).findFirst().orElseThrow();
  }

  @Test
  void heterogeneous_inputs_are_grouped_by_basket_in_safe_mode() throws Exception {
    var targets = rows("_ili_tid", "_ili_bid", "label");
    targets.rows.add(new Object[] {"t1", "b1", "first"});
    targets.rows.add(new Object[] {"t2", "b2", "second"});
    var items = rows("_ili_tid", "_ili_bid");
    items.schema.addValueMeta(new ValueMetaInterlisObject("carrier"));
    items.rows.add(new Object[] {"i1", "b1", object(read(original()), "i1").object()});
    var item = mapping("items", "Item", null, null);
    item.setSourceObjectField("carrier");
    var writer = new InterlisOutputMeta();
    writer.setDefault();
    writer.setModelNames(MODEL);
    writer.setModelDirectories(TestData.path("/models").toString());
    writer.setFileName(temp.resolve("multi.xtf").toString());
    writer.setBasketMode(InterlisOutputMeta.BasketMode.FROM_FIELD);
    writer.setInputs(List.of(mapping("targets", "Target", "label", "Name"), item));
    assertThat(run(writer, Map.of("targets", targets, "items", items)).getErrors()).isZero();
    var output = read(Path.of(writer.getFileName()));
    assertThat(output.stream().filter(e -> e.eventType() == InterlisEventType.START_BASKET))
        .hasSize(2);
    assertThat(output.stream().filter(e -> e.eventType() == InterlisEventType.OBJECT)).hasSize(3);
    assertThat(UpdateReference.fingerprint(object(output, "i1").object()))
        .isEqualTo(UpdateReference.fingerprint(object(read(original()), "i1").object()));
  }

  @Test
  void automatic_baskets_separate_topics_and_explicit_bid_collisions_fail() throws Exception {
    Files.writeString(
        temp.resolve("TwoTopics.ili"),
        """
        INTERLIS 2.4;
        MODEL TwoTopics (en) AT "https://example.org" VERSION "2026-10-02" =
          TOPIC A = CLASS Item = Name : MANDATORY TEXT*40; END Item; END A;
          TOPIC B = CLASS Item = Name : MANDATORY TEXT*40; END Item; END B;
        END TwoTopics.
        """);
    var a = rows("_ili_tid", "_ili_bid", "Name");
    a.rows.add(new Object[] {"a1", "same", "a"});
    var b = rows("_ili_tid", "_ili_bid", "Name");
    b.rows.add(new Object[] {"b1", "same", "b"});
    var empty = rows("_ili_tid", "_ili_bid", "Name");
    var configs = new ArrayList<InterlisMappedInput>();
    for (String name : List.of("a", "b", "empty")) {
      var config = mapping(name, "Item", "Name", "Name");
      config.setClassName("TwoTopics." + (name.equals("b") ? "B" : "A") + ".Item");
      configs.add(config);
    }
    var writer = new InterlisOutputMeta();
    writer.setDefault();
    writer.setModelNames("TwoTopics");
    writer.setModelDirectories(temp.toString());
    writer.setFileName(temp.resolve("topics.xtf").toString());
    writer.setInputs(configs);
    assertThat(run(writer, Map.of("a", a, "b", b, "empty", empty)).getErrors()).isZero();
    var context =
        new InterlisProjectionService()
            .loadModel(
                new InterlisModelRequest(null, List.of("TwoTopics"), List.of(temp.toString())));
    var baskets = new LinkedHashMap<String, String>();
    try (var reader =
        XtfTransferReader.open(
            Path.of(writer.getFileName()), context.model().transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null)
        if (event.eventType() == InterlisEventType.START_BASKET)
          baskets.put(event.topicName(), event.basketId());
    }
    assertThat(baskets.keySet()).containsExactlyInAnyOrder("TwoTopics.A", "TwoTopics.B");
    assertThat(new HashSet<>(baskets.values())).hasSize(2);
    baskets.values().forEach(UUID::fromString);
    byte[] valid = Files.readAllBytes(Path.of(writer.getFileName()));
    writer.setBasketMode(InterlisOutputMeta.BasketMode.FROM_FIELD);
    writer.setOverwrite(true);
    assertThat(run(writer, Map.of("a", a, "b", b, "empty", empty)).getErrors()).isPositive();
    assertThat(Files.readAllBytes(Path.of(writer.getFileName()))).isEqualTo(valid);
  }

  @Test
  void geometry_and_scalar_structure_update_combine_with_collection_inside_that_structure()
      throws Exception {
    String modelName = "HopIli_P1_V1", cls = modelName + ".Data.Item";
    var context =
        new InterlisProjectionService()
            .loadModel(
                new InterlisModelRequest(
                    null, List.of(modelName), List.of(TestData.path("/models").toString())));
    var item = new Iom_jObject(cls, "i1");
    item.setattrvalue("Name", "before");
    var detail = item.addattrobj("Details", modelName + ".Data.Detail");
    detail.setattrvalue("Code", "keep");
    detail.setattrvalue("Note", "clear me");
    detail.addattrobj("Tags", modelName + ".Data.Tag").setattrvalue("Text", "first");
    detail.addattrobj("Tags", modelName + ".Data.Tag").setattrvalue("Text", "second");
    Path original = temp.resolve("original.xtf");
    try (var writer =
        XtfTransferWriter.open(
            original, context.model().transferDescription(), List.of(modelName))) {
      writer.startTransfer("test");
      writer.startBasket(modelName + ".Data", "b1");
      writer.writeObject(item);
      writer.endBasket();
      writer.endTransfer();
    }
    // Fingerprint exactly the original as the reader sees it, including IOX metadata.
    try (var reader = XtfTransferReader.open(original, context.model().transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null)
        if (event.object() != null) item = new Iom_jObject(event.object());
    }
    var objects = rows("_ili_tid", "_ili_bid", "Name", "Note");
    objects.schema.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("point"));
    var geometry =
        new org.locationtech.jts.geom.GeometryFactory()
            .createPoint(new org.locationtech.jts.geom.Coordinate(11, 22, 33));
    objects.rows.add(new Object[] {"i1", "b1", "after", null, geometry});
    var objectMapping = mapping("objects", "Item", "Name", "Name");
    objectMapping.setClassName(cls);
    objectMapping.setFields(
        List.of(
            new InterlisFieldAssignment("Name", "Name"),
            new InterlisFieldAssignment("Note", "Details.Note"),
            new InterlisFieldAssignment("point", "Location")));
    var children = rows("_ili_update_ref", "Text");
    children.rows.add(
        new Object[] {
          new UpdateReference(cls, "b1", "i1", "Details.Tags", 1, UpdateReference.fingerprint(item))
              .encode(),
          "updated"
        });
    var childMapping = mapping("children", "Item", "Text", "Text");
    childMapping.setClassName(cls);
    childMapping.setStructurePath("Details.Tags");
    var update = update(objectMapping, childMapping);
    update.setOriginalFile(original.toString());
    update.setModelNames(modelName);
    assertThat(run(update, Map.of("objects", objects, "children", children)).getErrors()).isZero();
    try (var reader =
        XtfTransferReader.open(
            Path.of(update.getFileName()), context.model().transferDescription())) {
      InterlisObjectEnvelope event;
      int count = 0;
      while ((event = reader.next()) != null)
        if (event.object() != null) {
          count++;
          var result = event.object();
          assertThat(result.getattrvalue("Name")).isEqualTo("after");
          var coordinate = result.getattrobj("Location", 0);
          assertThat(Double.parseDouble(coordinate.getattrvalue("C1"))).isEqualTo(11);
          assertThat(Double.parseDouble(coordinate.getattrvalue("C2"))).isEqualTo(22);
          assertThat(Double.parseDouble(coordinate.getattrvalue("C3"))).isEqualTo(33);
          var details = result.getattrobj("Details", 0);
          assertThat(details.getattrvalue("Code")).isEqualTo("keep");
          assertThat(details.getattrvalue("Note")).isNull();
          assertThat(details.getattrvaluecount("Tags")).isEqualTo(2);
          assertThat(details.getattrobj("Tags", 0).getattrvalue("Text")).isEqualTo("first");
          assertThat(details.getattrobj("Tags", 1).getattrvalue("Text")).isEqualTo("updated");
        }
      assertThat(count).isEqualTo(1);
    }
  }

  @Test
  void selected_attribute_changes_while_transfer_and_other_objects_are_preserved()
      throws Exception {
    var source = rows("_ili_tid", "_ili_bid", "replacement");
    source.rows.add(new Object[] {"t1", "b1", "renamed"});
    var update = update(mapping("changes", "Target", "replacement", "Name"));
    assertThat(run(update, Map.of("changes", source)).getErrors()).isZero();
    var before = read(original());
    var after = read(Path.of(update.getFileName()));
    assertThat(after).hasSameSizeAs(before);
    assertThat(object(after, "t1").object().getattrvalue("Name")).isEqualTo("renamed");
    for (String unchanged : List.of("i1", "t2"))
      assertThat(UpdateReference.fingerprint(object(after, unchanged).object()))
          .isEqualTo(UpdateReference.fingerprint(object(before, unchanged).object()));
    assertThat(after.getFirst().transferMetadata()).isEqualTo(before.getFirst().transferMetadata());
  }

  @Test
  void no_patch_rows_copy_the_complete_transfer() throws Exception {
    var update = update(mapping("changes", "Target", "Name", "Name"));
    assertThat(run(update, Map.of("changes", rows("_ili_tid", "_ili_bid", "Name"))).getErrors())
        .isZero();
    var before = read(original());
    var after = read(Path.of(update.getFileName()));
    for (var event : before)
      if (event.object() != null)
        assertThat(UpdateReference.fingerprint(object(after, event.objectId()).object()))
            .isEqualTo(UpdateReference.fingerprint(event.object()));
  }

  @Test
  void structure_and_primitive_occurrences_preserve_subtypes_nested_content_and_order()
      throws Exception {
    var item = object(read(original()), "i1").object();
    String hash = UpdateReference.fingerprint(item);
    var children = rows("_ili_update_ref", "replacement");
    children.rows.add(
        new Object[] {
          new UpdateReference(CLS + "Item", "b1", "i1", "Children", 0, hash).encode(), "new"
        });
    var childMapping = mapping("children", "Item", "replacement", "Name");
    childMapping.setStructurePath("Children");
    var texts = rows("_ili_update_ref", "replacement");
    texts.rows.add(
        new Object[] {
          new UpdateReference(CLS + "Item", "b1", "i1", "Texts", 1, hash).encode(), "changed"
        });
    var textMapping = mapping("texts", "Item", "replacement", "_ili_value");
    textMapping.setStructurePath("Texts");
    var update = update(childMapping, textMapping);
    assertThat(run(update, Map.of("children", children, "texts", texts)).getErrors()).isZero();
    var actual = object(read(Path.of(update.getFileName())), "i1").object();
    var expected = new Iom_jObject(item);
    expected.getattrobj("Children", 0).setattrvalue("Name", "new");
    expected.setattrvalue("Texts", 1, "changed");
    assertThat(UpdateReference.fingerprint(actual))
        .isEqualTo(UpdateReference.fingerprint(expected));
  }

  @Test
  void invalid_updates_never_replace_existing_target() throws Exception {
    for (String failure : List.of("missing", "duplicate", "mandatory", "stale", "reference")) {
      var source = rows("_ili_tid", "_ili_bid", "Name", "_ili_update_ref");
      source.rows.add(
          new Object[] {
            failure.equals("missing") ? "unknown" : "t1",
            "b1",
            failure.equals("mandatory") ? null : "new",
            ""
          });
      if (failure.equals("duplicate")) source.rows.add(source.rows.getFirst().clone());
      var config = mapping("changes", "Target", "Name", "Name");
      if (failure.equals("reference")) {
        config.setClassName(CLS + "Item");
        config.setFields(List.of(new InterlisFieldAssignment("Name", "InternalRef_ref")));
      }
      if (failure.equals("stale")) {
        config.setClassName(CLS + "Item");
        config.setStructurePath("Children");
        source.rows.set(
            0,
            new Object[] {
              "i1",
              "b1",
              "new",
              new UpdateReference(CLS + "Item", "b1", "i1", "Children", 0, "0".repeat(64)).encode()
            });
      }
      var update = update(config);
      update.setOverwrite(true);
      Path target = Path.of(update.getFileName());
      Files.writeString(target, "previous");
      assertThat(run(update, Map.of("changes", source)).getErrors()).as(failure).isPositive();
      assertThat(Files.readString(target)).isEqualTo("previous");
    }
    try (var files = Files.list(temp)) {
      assertThat(files.toList()).noneMatch(p -> p.toString().contains("pending"));
    }
  }

  @Test
  void same_original_path_symlink_and_hardlink_are_rejected() throws Exception {
    Path originalCopy = temp.resolve("original.xtf");
    Files.copy(original(), originalCopy);
    for (String alias : List.of("direct", "symlink", "hardlink")) {
      Path target = alias.equals("direct") ? originalCopy : temp.resolve(alias + ".xtf");
      if (alias.equals("symlink")) Files.createSymbolicLink(target, originalCopy);
      if (alias.equals("hardlink")) Files.createLink(target, originalCopy);
      var update = update(mapping("changes", "Target", "Name", "Name"));
      update.setOriginalFile(originalCopy.toString());
      update.setFileName(target.toString());
      update.setOverwrite(true);
      assertThat(run(update, Map.of("changes", rows("_ili_tid", "_ili_bid", "Name"))).getErrors())
          .as(alias)
          .isPositive();
      assertThat(Files.readAllBytes(originalCopy)).isEqualTo(Files.readAllBytes(original()));
    }
  }

  @Test
  void structure_keys_survive_reordering_filtering_and_bag_duplicate_values() throws Exception {
    var originalItem = object(read(original()), "i1").object();
    String hash = UpdateReference.fingerprint(originalItem);
    var texts = rows("_ili_update_ref", "replacement");
    // Reverse the patch order: original occurrence positions still decide the destination.
    for (int index : List.of(1, 0))
      texts.rows.add(
          new Object[] {
            new UpdateReference(CLS + "Item", "b1", "i1", "Texts", index, hash).encode(),
            "text" + index
          });
    var textMapping = mapping("texts", "Item", "replacement", "_ili_value");
    textMapping.setStructurePath("Texts");
    var numbers = rows("_ili_update_ref");
    numbers.schema.addValueMeta(new ValueMetaInteger("replacement"));
    numbers.rows.add(
        new Object[] {
          new UpdateReference(CLS + "Item", "b1", "i1", "Numbers", 1, hash).encode(), 8L
        });
    var numberMapping = mapping("numbers", "Item", "replacement", "_ili_value");
    numberMapping.setStructurePath("Numbers");
    var update = update(textMapping, numberMapping);
    assertThat(run(update, Map.of("texts", texts, "numbers", numbers)).getErrors()).isZero();
    var expected = new Iom_jObject(originalItem);
    expected.setattrvalue("Texts", 0, "text0");
    expected.setattrvalue("Texts", 1, "text1");
    expected.setattrvalue("Numbers", 1, "8");
    assertThat(
            UpdateReference.fingerprint(object(read(Path.of(update.getFileName())), "i1").object()))
        .isEqualTo(UpdateReference.fingerprint(expected));
  }

  @Test
  void invalid_structure_keys_and_null_primitive_elements_fail() throws Exception {
    String hash = UpdateReference.fingerprint(object(read(original()), "i1").object());
    for (String failure : List.of("missing", "malformed", "path", "index", "duplicate", "null")) {
      var source = rows("_ili_update_ref", "replacement");
      String token =
          new UpdateReference(
                  CLS + "Item",
                  "b1",
                  "i1",
                  failure.equals("path") ? "Numbers" : "Texts",
                  failure.equals("index") ? 100 : 0,
                  hash)
              .encode();
      if (failure.equals("missing")) token = null;
      if (failure.equals("malformed")) token = "invalid-token";
      source.rows.add(new Object[] {token, failure.equals("null") ? null : "new"});
      if (failure.equals("duplicate")) source.rows.add(source.rows.getFirst().clone());
      var config = mapping("changes", "Item", "replacement", "_ili_value");
      config.setStructurePath("Texts");
      var update = update(config);
      update.setOverwrite(true);
      Files.writeString(Path.of(update.getFileName()), "previous");
      assertThat(run(update, Map.of("changes", source)).getErrors()).as(failure).isPositive();
      assertThat(Files.readString(Path.of(update.getFileName()))).isEqualTo("previous");
    }
  }

  @Test
  void all_physical_input_copies_are_consumed_with_tiny_queues() throws Exception {
    var source = rows("_ili_tid", "_ili_bid", "Name");
    for (int i = 0; i < 50; i++)
      source.rows.add(new Object[] {"t" + i, "b" + (i % 3), "target" + i});
    var writer = new InterlisOutputMeta();
    writer.setDefault();
    writer.setModelNames(MODEL);
    writer.setModelDirectories(TestData.path("/models").toString());
    writer.setFileName(temp.resolve("copies.xtf").toString());
    writer.setBasketMode(InterlisOutputMeta.BasketMode.FROM_FIELD);
    writer.setInputs(List.of(mapping("copies", "Target", "Name", "Name")));
    var pm = new PipelineMeta();
    var src = new TransformMeta("source", source);
    var copies =
        new TransformMeta("copies", new org.apache.hop.pipeline.transforms.dummy.DummyMeta());
    copies.setCopies(3);
    var sink = new TransformMeta("sink", writer);
    for (var transform : List.of(src, copies, sink)) pm.addTransform(transform);
    pm.addPipelineHop(new PipelineHopMeta(src, copies));
    pm.addPipelineHop(new PipelineHopMeta(copies, sink));
    writer.searchInfoAndTargetTransforms(pm.getTransforms());
    var engine = prepare(pm);
    engine.startThreads();
    engine.waitUntilFinished();
    assertThat(engine.getErrors()).isZero();
    assertThat(read(Path.of(writer.getFileName())).stream().filter(e -> e.object() != null))
        .hasSize(50);
  }

  Pipeline prepare(PipelineMeta pm) throws Exception {
    var config = new PipelineRunConfiguration();
    var local = new LocalPipelineRunConfiguration();
    local.setEnginePluginId("Local");
    local.setRowSetSize("1");
    config.setEngineRunConfiguration(local);
    var engine = (Pipeline) PipelineEngineFactory.createPipelineEngine(config, pm);
    engine.setSafeModeEnabled(true);
    engine.prepareExecution();
    return engine;
  }

  @Test
  void both_sinks_defer_publication_until_late_branches_finish() throws Exception {
    for (boolean editing : List.of(false, true))
      for (String outcome : List.of("success", "error", "stop", "original-change")) {
        var source = rows("_ili_tid", "_ili_bid", "Name");
        source.rows.add(new Object[] {"t1", "b1", "changed"});
        var target = temp.resolve(editing + "-" + outcome + ".xtf");
        Files.writeString(target, "previous");
        Path workingOriginal = temp.resolve("original-" + editing + "-" + outcome + ".xtf");
        Files.copy(original(), workingOriginal);
        ITransformMeta sinkMeta;
        if (editing) {
          var u = update(mapping("source", "Target", "Name", "Name"));
          u.setFileName(target.toString());
          u.setOriginalFile(workingOriginal.toString());
          u.setOverwrite(true);
          sinkMeta = u;
        } else {
          var w = new InterlisOutputMeta();
          w.setDefault();
          w.setModelNames(MODEL);
          w.setModelDirectories(TestData.path("/models").toString());
          w.setFileName(target.toString());
          w.setOverwrite(true);
          w.setInputs(List.of(mapping("source", "Target", "Name", "Name")));
          sinkMeta = w;
        }
        var pm = new PipelineMeta();
        var src = new TransformMeta("source", source);
        var sink = new TransformMeta("sink", sinkMeta);
        var late = new TransformMeta("late", source);
        var lateSink =
            new TransformMeta(
                "late sink",
                new org.apache.hop.pipeline.transforms.rowstoresult.RowsToResultMeta());
        for (var t : List.of(src, sink, late, lateSink)) pm.addTransform(t);
        pm.addPipelineHop(new PipelineHopMeta(src, sink));
        pm.addPipelineHop(new PipelineHopMeta(late, lateSink));
        sinkMeta.searchInfoAndTargetTransforms(pm.getTransforms());
        var engine = prepare(pm);
        var finished = new java.util.concurrent.CountDownLatch(1);
        engine
            .getTransform("sink", 0)
            .addTransformFinishedListener((pipeline, meta, transform) -> finished.countDown());
        engine
            .getTransform("late sink", 0)
            .addRowListener(
                new RowAdapter() {
                  @Override
                  public void rowReadEvent(IRowMeta meta, Object[] row)
                      throws org.apache.hop.core.exception.HopTransformException {
                    try {
                      if (!finished.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        throw new AssertionError("Sink did not finish");
                      assertThat(Files.readString(target)).isEqualTo("previous");
                      if (outcome.equals("error"))
                        throw new org.apache.hop.core.exception.HopTransformException(
                            "late failure");
                      if (outcome.equals("stop")) engine.stopAll();
                      if (outcome.equals("original-change"))
                        Files.writeString(workingOriginal, "changed original");
                    } catch (InterruptedException | java.io.IOException e) {
                      throw new org.apache.hop.core.exception.HopTransformException(e);
                    }
                  }
                });
        engine.startThreads();
        engine.waitUntilFinished();
        if (outcome.equals("success") || (!editing && outcome.equals("original-change"))) {
          assertThat(engine.getErrors()).isZero();
          assertThat(Files.readString(target)).contains("changed");
        } else assertThat(Files.readString(target)).isEqualTo("previous");
      }
    try (var files = Files.list(temp)) {
      assertThat(files.toList()).noneMatch(p -> p.toString().contains("pending"));
    }
  }
}
