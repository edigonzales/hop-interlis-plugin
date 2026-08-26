package ch.so.agi.hop.interlis.transforms.explode;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

class InterlisStructureExplodeMetaTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisStructureExplodeMeta configuredMeta() {
    InterlisStructureExplodeMeta meta = new InterlisStructureExplodeMeta();
    meta.setDefault();
    meta.setModelNames("HopIli_Structures_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Structures_V1.Data.Person");
    meta.setStructureAttributePath("Addresses");
    return meta;
  }

  @Test
  void get_fields_produces_child_row_schema() throws Exception {
    InterlisStructureExplodeMeta meta = configuredMeta();
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("_ili_tid"));
    rowMeta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("_ili_bid"));
    rowMeta.addValueMeta(
        new ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject(
            InterlisStructureExplodeMeta.DEFAULT_SOURCE_OBJECT_FIELD));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames())
        .containsExactly(
            "_ili_parent_tid", "_ili_parent_bid", "_ili_index", "Street", "Number", "Location",
            "PostCode_Code", "PostCode_Town");
    assertThat(rowMeta.getValueMeta(2).getType())
        .isEqualTo(org.apache.hop.core.row.value.ValueMetaInteger.TYPE_INTEGER);
    assertThat(rowMeta.getValueMeta(5).getType())
        .isEqualTo(com.atolcd.hop.core.row.value.ValueMetaGeometry.TYPE_GEOMETRY);
  }

  @Test
  void get_fields_copies_selected_parent_fields() throws Exception {
    InterlisStructureExplodeMeta meta = configuredMeta();
    meta.setIncludeParentFields(List.of("_ili_tid", "Name"));
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("_ili_tid"));
    rowMeta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("Name"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames())
        .containsSubsequence(
            "_ili_parent_tid", "_ili_parent_bid", "_ili_index", "Street", "Number", "Location",
            "PostCode_Code", "PostCode_Town")
        .endsWith("_ili_tid", "Name");
  }

  @Test
  void bag_keeps_technical_index() throws Exception {
    InterlisStructureExplodeMeta meta = configuredMeta();
    meta.setStructureAttributePath("Contacts");
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames())
        .containsExactly("_ili_parent_tid", "_ili_parent_bid", "_ili_index", "Kind", "Value");
  }

  @Test
  void get_fields_with_unresolved_configuration_stays_silent() throws Exception {
    InterlisStructureExplodeMeta meta = new InterlisStructureExplodeMeta();
    meta.setModelNames("");
    meta.setClassName("");
    meta.setStructureAttributePath("");
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isZero();
  }

  @Test
  void get_fields_with_unknown_structure_path_stays_silent() throws Exception {
    InterlisStructureExplodeMeta meta = configuredMeta();
    meta.setStructureAttributePath("NoSuchStructure");
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isZero();
  }

  @Test
  void try_structure_plan_reports_invalid_path() {
    InterlisStructureExplodeMeta meta = configuredMeta();
    meta.setStructureAttributePath("Home");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> meta.tryStructurePlan(new Variables()))
        .isInstanceOf(ch.so.agi.hop.interlis.core.mapping.InterlisMappingException.class)
        .hasMessageContaining("not a multi-valued");
  }

  @Test
  void check_reports_configuration_problems() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    InterlisStructureExplodeMeta meta = new InterlisStructureExplodeMeta();

    meta.check(
        remarks, null, new TransformMeta("INTERLIS Structure Explode", meta), null, null, null,
        null, new Variables(), new MemoryMetadataProvider());

    assertThat(remarks).isNotEmpty();
    assertThat(remarks.get(0).getType()).isEqualTo(ICheckResult.TYPE_RESULT_ERROR);
  }

  @Test
  void check_reports_ok_for_valid_configuration() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    InterlisStructureExplodeMeta meta = configuredMeta();

    meta.check(
        remarks, null, new TransformMeta("INTERLIS Structure Explode", meta), null, null, null,
        null, new Variables(), new MemoryMetadataProvider());

    assertThat(remarks)
        .anySatisfy(r -> assertThat(r.getType()).isEqualTo(ICheckResult.TYPE_RESULT_OK));
  }

  @Test
  void hop_xml_roundtrip_preserves_configuration() throws Exception {
    InterlisStructureExplodeMeta meta = configuredMeta();
    meta.setIncludeParentFields(List.of("_ili_tid"));
    meta.setSelectedChildFields(List.of("Street", "Number"));

    String xml = new TransformMeta("INTERLIS Structure Explode", meta).getXml();
    Node node =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new org.xml.sax.InputSource(new StringReader(xml)))
            .getDocumentElement();
    InterlisStructureExplodeMeta restored = new InterlisStructureExplodeMeta();
    restored.loadXml(node, new MemoryMetadataProvider());

    assertThat(restored.getModelNames()).isEqualTo("HopIli_Structures_V1");
    assertThat(restored.getClassName()).isEqualTo("HopIli_Structures_V1.Data.Person");
    assertThat(restored.getStructureAttributePath()).isEqualTo("Addresses");
    assertThat(restored.getSourceObjectField()).isEqualTo("_ili_source_object");
    assertThat(restored.getParentTidField()).isEqualTo("_ili_tid");
    assertThat(restored.isEmitParentBid()).isTrue();
    assertThat(restored.getIncludeParentFields()).containsExactly("_ili_tid");
    assertThat(restored.getSelectedChildFields()).containsExactly("Street", "Number");
    assertThat(restored.getParentKeyFieldName()).isEqualTo("_ili_parent_tid");
    assertThat(restored.getIndexFieldName()).isEqualTo("_ili_index");
  }
}
