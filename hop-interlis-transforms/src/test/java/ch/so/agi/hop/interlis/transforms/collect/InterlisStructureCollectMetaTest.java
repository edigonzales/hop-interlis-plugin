package ch.so.agi.hop.interlis.transforms.collect;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

class InterlisStructureCollectMetaTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisStructureCollectMeta configuredMeta() {
    InterlisStructureCollectMeta meta = new InterlisStructureCollectMeta();
    meta.setDefault();
    meta.setParentInputTransform("INTERLIS Input");
    meta.setChildInputTransform("INTERLIS Structure Explode");
    meta.setModelNames("HopIli_Structures_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Structures_V1.Data.Person");
    meta.setStructureAttributePath("Addresses");
    return meta;
  }

  @Test
  void get_fields_passes_parent_stream_through() throws Exception {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("_ili_tid"));
    rowMeta.addValueMeta(new ValueMetaString("Name"));

    InterlisStructureCollectMeta meta = configuredMeta();
    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames()).containsExactly("_ili_tid", "Name");
  }

  @Test
  void check_reports_configuration_problems() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    InterlisStructureCollectMeta meta = new InterlisStructureCollectMeta();

    meta.check(
        remarks, null, new TransformMeta("INTERLIS Structure Collect", meta), null, null, null,
        null, new Variables(), new MemoryMetadataProvider());

    assertThat(remarks).isNotEmpty();
    assertThat(remarks.get(0).getType()).isEqualTo(ICheckResult.TYPE_RESULT_ERROR);
  }

  @Test
  void check_reports_ok_for_valid_configuration() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    InterlisStructureCollectMeta meta = configuredMeta();

    meta.check(
        remarks, null, new TransformMeta("INTERLIS Structure Collect", meta), null, null, null,
        null, new Variables(), new MemoryMetadataProvider());

    assertThat(remarks)
        .anySatisfy(r -> assertThat(r.getType()).isEqualTo(ICheckResult.TYPE_RESULT_OK));
  }

  @Test
  void try_structure_plan_reports_invalid_path() {
    InterlisStructureCollectMeta meta = configuredMeta();
    meta.setStructureAttributePath("Name");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> meta.tryStructurePlan(new Variables()))
        .isInstanceOf(ch.so.agi.hop.interlis.core.mapping.InterlisMappingException.class)
        .hasMessageContaining("not a structure");
  }

  @Test
  void hop_xml_roundtrip_preserves_configuration() throws Exception {
    InterlisStructureCollectMeta meta = configuredMeta();
    meta.setStrictOrdering(false);

    String xml = new TransformMeta("INTERLIS Structure Collect", meta).getXml();
    Node node =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new org.xml.sax.InputSource(new StringReader(xml)))
            .getDocumentElement();
    InterlisStructureCollectMeta restored = new InterlisStructureCollectMeta();
    restored.loadXml(node, new MemoryMetadataProvider());

    assertThat(restored.getParentInputTransform()).isEqualTo("INTERLIS Input");
    assertThat(restored.getChildInputTransform()).isEqualTo("INTERLIS Structure Explode");
    assertThat(restored.getParentKeyField()).isEqualTo("_ili_tid");
    assertThat(restored.getChildParentKeyField()).isEqualTo("_ili_parent_tid");
    assertThat(restored.getChildIndexField()).isEqualTo("_ili_index");
    assertThat(restored.getModelNames()).isEqualTo("HopIli_Structures_V1");
    assertThat(restored.getClassName()).isEqualTo("HopIli_Structures_V1.Data.Person");
    assertThat(restored.getStructureAttributePath()).isEqualTo("Addresses");
    assertThat(restored.getSourceObjectField()).isEqualTo("_ili_source_object");
    assertThat(restored.isStrictOrdering()).isFalse();
    assertThat(restored.isFailOnDuplicateIndex()).isTrue();
    assertThat(restored.isFailOnChildWithoutParent()).isTrue();
  }
}
