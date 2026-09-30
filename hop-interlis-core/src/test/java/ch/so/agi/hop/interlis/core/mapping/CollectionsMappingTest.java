package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.core.model.*;
import ch.so.agi.hop.interlis.core.structures.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CollectionsMappingTest {
  static final String MODEL = "HopIli_Collections_V1",
      TOPIC = MODEL + ".Data",
      ITEM = TOPIC + ".Item";
  static InterlisProjectionResult projection;
  @TempDir Path temp;

  @BeforeAll
  static void compile() throws Exception {
    projection =
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(
                    null, List.of(MODEL), List.of(TestResources.path("/models").toString())),
                ITEM,
                ProjectionOptions.defaults());
  }

  static Iom_jObject ref(String tid, String bid) {
    var ref = new Iom_jObject("REF", null);
    ref.setobjectrefoid(tid);
    ref.setobjectrefbid(bid);
    return ref;
  }

  static Iom_jObject item() {
    var item = new Iom_jObject(ITEM, "i1");
    item.addattrvalue("Texts", "first");
    item.addattrvalue("Texts", "second");
    item.addattrvalue("Numbers", "7");
    item.addattrvalue("Numbers", "7");
    item.addattrvalue("Flags", "true");
    item.addattrvalue("Flags", "false");
    item.addattrvalue("Choices", "red");
    item.addattrvalue("Choices", "blue");
    item.addattrvalue("Required", "required");
    item.addattrobj("InternalRef", ref("t1", null));
    item.addattrobj("ExternalRef", ref("t2", "b2"));
    var holder = new Iom_jObject(TOPIC + ".Holder", null);
    holder.addattrobj("TargetRef", ref("t2", "b2"));
    item.addattrobj("Holder", holder);
    var child = new Iom_jObject(TOPIC + ".SpecialChild", null);
    child.setattrvalue("Name", "old");
    child.setattrvalue("Hidden", "keep");
    child.setattrvalue("Extra", "subtype");
    var detail = new Iom_jObject(TOPIC + ".Detail", null);
    detail.setattrvalue("Text", "nested");
    child.addattrobj("Details", detail);
    child.addattrobj("TargetRef", ref("t2", "b2"));
    item.addattrobj("Children", child);
    return item;
  }

  static InterlisObjectEnvelope envelope(IomObject object) {
    return new InterlisObjectEnvelope(
        InterlisEventType.OBJECT,
        MODEL,
        TOPIC,
        "b1",
        ITEM,
        object.getobjectoid(),
        InterlisObjectOperation.NONE,
        object,
        null,
        null);
  }

  static InterlisStructurePlan collection(String path, Set<String> selected) throws Exception {
    var options = new ProjectionOptions(true, true, false, false, false, true, "_", null, selected);
    return new InterlisStructureLocator()
        .locate(
            projection.schema(), projection.schema().findClass(ITEM).orElseThrow(), path, options);
  }

  @Test
  void committed_fixture_is_fully_valid_and_keeps_external_reference_bids() throws Exception {
    var file = TestResources.path("/data/HopIli_Collections_V1_valid.xtf");
    var result =
        new InterlisValidationService()
            .validate(
                file, projection.model().transferDescription(), null, () -> false, finding -> {});
    assertThat(result.errors()).isZero();
    assertThat(result.incomplete()).isNull();
    try (var reader = XtfTransferReader.open(file, projection.model().transferDescription())) {
      InterlisObjectEnvelope e;
      while ((e = reader.next()) != null)
        if ("i1".equals(e.objectId())) {
          assertThat(e.object().getattrobj("ExternalRef", 0).getobjectrefbid()).isEqualTo("b2");
          assertThat(
                  e.object().getattrobj("Children", 0).getattrobj("TargetRef", 0).getobjectrefbid())
              .isEqualTo("b2");
        }
    }
  }

  @Test
  void descriptors_keep_alias_collection_and_real_reference_type() {
    var attrs = projection.schema().findClass(ITEM).orElseThrow().effectiveProperties();
    var texts =
        (InterlisAttributeDescriptor)
            attrs.stream().filter(a -> a.name().equals("Texts")).findFirst().orElseThrow();
    assertThat(texts.kind()).isEqualTo(InterlisValueKind.TEXT);
    assertThat(texts.cardinality().isSingleValued()).isFalse();
    assertThat(texts.ordered()).isTrue();
    var reference =
        (InterlisAttributeDescriptor)
            attrs.stream().filter(a -> a.name().equals("ExternalRef")).findFirst().orElseThrow();
    assertThat(reference.kind()).isEqualTo(InterlisValueKind.REFERENCE);
    assertThat(reference.referenceTarget()).isEqualTo(TOPIC + ".Target");
    assertThat(reference.externalReference()).isTrue();
    assertThat(projection.plan().fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .doesNotContain("Texts", "Numbers", "Flags", "Choices", "Required")
        .contains(
            "InternalRef_ref",
            "InternalRef_ref_bid",
            "Holder_TargetRef_ref",
            "Holder_TargetRef_ref_bid");
    assertThat(projection.plan().warnings())
        .anyMatch(s -> s.contains("Texts") && s.contains("Explode/Collect"));
  }

  @Test
  void unsupported_selected_types_fail_and_unselected_contents_can_be_preserved() throws Exception {
    var unsupported =
        new InterlisAttributeDescriptor(
            "Opaque",
            ITEM + ".Opaque",
            new InterlisCardinality(0, 1),
            false,
            InterlisValueKind.UNSUPPORTED,
            "UnsupportedVendorType",
            false,
            null,
            null,
            false,
            null,
            false,
            -1,
            -1);
    var root =
        new InterlisClassDescriptor(
            "Item", ITEM, TOPIC, false, List.of(unsupported), List.of(unsupported));
    var schema = new InterlisSchemaDescriptor(List.of(root), List.of());
    assertThatThrownBy(
            () -> new InterlisRowSchemaBuilder().build(schema, root, ProjectionOptions.defaults()))
        .hasMessageContaining(ITEM + ".Opaque")
        .hasMessageContaining("UnsupportedVendorType");
    var selected =
        new ProjectionOptions(true, true, false, false, false, true, "_", null, Set.of("Other"));
    assertThat(new InterlisRowSchemaBuilder().build(schema, root, selected).fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .doesNotContain("Opaque");
  }

  @Test
  void references_read_write_clear_and_overlay_preserves_collections() throws Exception {
    var source = item();
    var plan = projection.plan();
    var row = new DefaultInterlisObjectToRowMapper().map(envelope(source), plan);
    int tid = index(plan, "ExternalRef_ref"), bid = index(plan, "ExternalRef_ref_bid");
    assertThat(row[tid]).isEqualTo("t2");
    assertThat(row[bid]).isEqualTo("b2");
    row[tid] = "t1";
    row[bid] = null;
    var result = new RowToIomMapper().map(source, row, plan, RowWriteOptions.defaults());
    assertThat(result.getattrobj("ExternalRef", 0).getobjectrefoid()).isEqualTo("t1");
    assertThat(result.getattrobj("ExternalRef", 0).getobjectrefbid()).isNull();
    assertThat(result.getattrvaluecount("Texts")).isEqualTo(2);
    assertThat(source.getattrobj("ExternalRef", 0).getobjectrefoid()).isEqualTo("t2");
    row[tid] = null;
    assertThat(
            new RowToIomMapper()
                .map(result, row, plan, RowWriteOptions.defaults())
                .getattrvaluecount("ExternalRef"))
        .isZero();
    row[bid] = "b2";
    assertThatThrownBy(
            () -> new RowToIomMapper().map(source, row, plan, RowWriteOptions.defaults()))
        .hasMessageContaining("ExternalRef")
        .hasMessageContaining("without");
    row[bid] = null;
    row[index(plan, "Holder_TargetRef_ref")] = null;
    row[index(plan, "Holder_TargetRef_ref_bid")] = "b2";
    assertThatThrownBy(
            () -> new RowToIomMapper().map(source, row, plan, RowWriteOptions.defaults()))
        .hasMessageContaining("TargetRef");
  }

  static int index(InterlisRowMappingPlan plan, String name) {
    return plan.fields().stream()
        .filter(f -> f.hopFieldName().equals(name))
        .findFirst()
        .orElseThrow()
        .outputIndex();
  }

  @Test
  void primitive_collections_roundtrip_duplicates_order_null_and_cardinality() throws Exception {
    var source = item();
    var exploder = new InterlisStructureExploder();
    var collector = new InterlisStructureCollector();
    for (var path : List.of("Texts", "Numbers", "Flags", "Choices", "Required")) {
      var plan = collection(path, Set.of());
      var children = exploder.explode(source, plan);
      assertThat(plan.childFields())
          .extracting(InterlisFieldPlan::hopFieldName)
          .containsExactly("_ili_value");
      var rows =
          children.stream()
              .map(c -> new InterlisStructureCollector.StructureChild(c.index(), c.values()))
              .toList();
      source =
          new Iom_jObject(
              collector.collect(
                  source,
                  rows,
                  plan,
                  InterlisStructureCollector.StructureCollectOptions.defaults()));
    }
    assertThat(source.getattrprim("Texts", 1)).isEqualTo("second");
    assertThat(source.getattrvaluecount("Numbers")).isEqualTo(2);
    assertThat(source.getattrprim("Numbers", 1)).isEqualTo("7");
    assertThat(source.getattrprim("Flags", 1)).isEqualTo("false");
    var texts = collection("Texts", Set.of());
    var reversed =
        collector.collect(
            source,
            List.of(
                new InterlisStructureCollector.StructureChild(1, new Object[] {"first"}),
                new InterlisStructureCollector.StructureChild(0, new Object[] {"second"})),
            texts,
            InterlisStructureCollector.StructureCollectOptions.defaults());
    assertThat(reversed.getattrprim("Texts", 0)).isEqualTo("second");
    assertThat(
            collector
                .collect(
                    source,
                    List.of(),
                    texts,
                    InterlisStructureCollector.StructureCollectOptions.defaults())
                .getattrvaluecount("Texts"))
        .isZero();
    var required = collection("Required", Set.of());
    var original = source;
    assertThatThrownBy(
            () ->
                collector.collect(
                    original,
                    List.of(),
                    required,
                    InterlisStructureCollector.StructureCollectOptions.defaults()))
        .hasMessageContaining("Required");
    assertThatThrownBy(
            () ->
                collector.collect(
                    original,
                    List.of(new InterlisStructureCollector.StructureChild(0, new Object[] {null})),
                    texts,
                    InterlisStructureCollector.StructureCollectOptions.defaults()))
        .hasMessageContaining("Null");
    assertThatThrownBy(
            () ->
                collector.collect(
                    original,
                    List.of(new InterlisStructureCollector.StructureChild(-1, new Object[] {"x"})),
                    texts,
                    InterlisStructureCollector.StructureCollectOptions.defaults()))
        .hasMessageContaining("index");
    var many =
        List.of(
            new InterlisStructureCollector.StructureChild(0, new Object[] {"a"}),
            new InterlisStructureCollector.StructureChild(1, new Object[] {"b"}),
            new InterlisStructureCollector.StructureChild(2, new Object[] {"c"}));
    assertThatThrownBy(
            () ->
                collector.collect(
                    original,
                    many,
                    required,
                    InterlisStructureCollector.StructureCollectOptions.defaults()))
        .hasMessageContaining("Cardinality");
    validateRoundtrip(source);
  }

  @Test
  void preserve_keeps_subtype_hidden_fields_and_nested_lists_and_filter_removes_child()
      throws Exception {
    var source = item();
    var plan = collection("Children", Set.of("Name"));
    var child = new InterlisStructureExploder().explode(source, plan).getFirst();
    var options =
        new InterlisStructureCollector.StructureCollectOptions(
            true, true, RowWriteOptions.defaults(), true);
    var result =
        new InterlisStructureCollector()
            .collect(
                source,
                List.of(
                    new InterlisStructureCollector.StructureChild(
                        0, new Object[] {"edited"}, child.source())),
                plan,
                options);
    var preserved = result.getattrobj("Children", 0);
    assertThat(preserved.getobjecttag()).isEqualTo(TOPIC + ".SpecialChild");
    assertThat(preserved.getattrvalue("Extra")).isEqualTo("subtype");
    assertThat(preserved.getattrvalue("Hidden")).isEqualTo("keep");
    assertThat(preserved.getattrobj("Details", 0).getattrvalue("Text")).isEqualTo("nested");
    assertThat(preserved.getattrvalue("Name")).isEqualTo("edited");
    assertThat(source.getattrobj("Children", 0).getattrvalue("Name")).isEqualTo("old");
    assertThat(
            new InterlisStructureCollector()
                .collect(source, List.of(), plan, options)
                .getattrvaluecount("Children"))
        .isZero();
    assertThatThrownBy(
            () ->
                new InterlisStructureCollector()
                    .collect(
                        source,
                        List.of(
                            new InterlisStructureCollector.StructureChild(0, new Object[] {"x"})),
                        plan,
                        options))
        .hasMessageContaining("child source");
    var bad = new Iom_jObject(TOPIC + ".Holder", null);
    assertThatThrownBy(
            () ->
                new InterlisStructureCollector()
                    .collect(
                        source,
                        List.of(
                            new InterlisStructureCollector.StructureChild(
                                0, new Object[] {"x"}, bad)),
                        plan,
                        options))
        .hasMessageContaining("incompatible");
    validateRoundtrip(result);
  }

  @Test
  void composition_restrictions_are_checked_before_preserving_or_rebuilding_children()
      throws Exception {
    var plan = collection("RestrictedChildren", Set.of("Name"));
    assertThat(plan.allowedChildTypes()).containsExactly(TOPIC + ".SpecialChild");
    var options =
        new InterlisStructureCollector.StructureCollectOptions(
            true, true, RowWriteOptions.defaults(), true);
    var base = new Iom_jObject(TOPIC + ".Child", null);
    assertThatThrownBy(
            () ->
                new InterlisStructureCollector()
                    .collect(
                        item(),
                        List.of(
                            new InterlisStructureCollector.StructureChild(
                                0, new Object[] {"x"}, base)),
                        plan,
                        options))
        .hasMessageContaining("incompatible");
    assertThatThrownBy(
            () ->
                new InterlisStructureCollector()
                    .collect(
                        item(),
                        List.of(
                            new InterlisStructureCollector.StructureChild(0, new Object[] {"x"})),
                        plan,
                        InterlisStructureCollector.StructureCollectOptions.defaults()))
        .hasMessageContaining("restricted");
  }

  void validateRoundtrip(IomObject source) throws Exception {
    var file = temp.resolve("valid.xtf");
    try (var writer =
        XtfTransferWriter.open(file, projection.model().transferDescription(), List.of(MODEL))) {
      writer.startTransfer("test");
      writer.startBasket(TOPIC, "b1");
      var t1 = new Iom_jObject(TOPIC + ".Target", "t1");
      t1.setattrvalue("Name", "first");
      writer.writeObject(t1);
      writer.writeObject(source);
      writer.endBasket();
      writer.startBasket(TOPIC, "b2");
      var t2 = new Iom_jObject(TOPIC + ".Target", "t2");
      t2.setattrvalue("Name", "second");
      writer.writeObject(t2);
      writer.endBasket();
      writer.endTransfer();
    }
    var result =
        new InterlisValidationService()
            .validate(
                file, projection.model().transferDescription(), null, () -> false, finding -> {});
    assertThat(result.errors()).isZero();
    assertThat(result.incomplete()).isNull();
    try (var reader = XtfTransferReader.open(file, projection.model().transferDescription())) {
      InterlisObjectEnvelope e;
      IomObject read = null;
      while ((e = reader.next()) != null) if ("i1".equals(e.objectId())) read = e.object();
      assertThat(read).isNotNull();
      assertThat(read.getattrvaluecount("Texts")).isEqualTo(source.getattrvaluecount("Texts"));
      assertThat(read.getattrobj("ExternalRef", 0).getobjectrefbid()).isEqualTo("b2");
    }
  }
}
