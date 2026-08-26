package ch.so.agi.hop.interlis.transforms.rolejoin;

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

class InterlisRoleJoinMetaTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisRoleJoinMeta configuredMeta() {
    InterlisRoleJoinMeta meta = new InterlisRoleJoinMeta();
    meta.setDefault();
    meta.setMainInputTransform("INTERLIS Input");
    meta.setLookupInputTransform("lookup");
    meta.setModelNames("HopIli_Associations_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setMainClassName("HopIli_Associations_V1.Data.Person");
    meta.setRoleName("Address");
    return meta;
  }

  @Test
  void probes_role_and_target_class() throws Exception {
    InterlisRoleJoinProbeResult result = configuredMeta().probeRole(new Variables());

    assertThat(result.role().name()).isEqualTo("Address");
    assertThat(result.role().targetClassScopedName())
        .isEqualTo("HopIli_Associations_V1.Data.Address");
    assertThat(result.role().cardinality().min()).isEqualTo(1);
    assertThat(result.target().scopedName()).isEqualTo("HopIli_Associations_V1.Data.Address");
  }

  @Test
  void probe_rejects_unknown_role() {
    InterlisRoleJoinMeta meta = configuredMeta();
    meta.setRoleName("NoSuchRole");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> meta.probeRole(new Variables()))
        .isInstanceOf(ch.so.agi.hop.interlis.core.mapping.InterlisMappingException.class)
        .hasMessageContaining("NoSuchRole");
  }

  @Test
  void get_fields_appends_prefixed_target_fields() throws Exception {
    InterlisRoleJoinMeta meta = configuredMeta();
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames()).containsExactly("Address_Street");
  }

  @Test
  void get_fields_with_unresolved_configuration_stays_silent() throws Exception {
    InterlisRoleJoinMeta meta = new InterlisRoleJoinMeta();
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isZero();
  }

  @Test
  void check_reports_configuration_problems() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    InterlisRoleJoinMeta meta = new InterlisRoleJoinMeta();

    meta.check(
        remarks, null, new TransformMeta("INTERLIS Role Join", meta), null, null, null, null,
        new Variables(), new MemoryMetadataProvider());

    assertThat(remarks).isNotEmpty();
    assertThat(remarks.get(0).getType()).isEqualTo(ICheckResult.TYPE_RESULT_ERROR);
  }

  @Test
  void check_reports_ok_for_valid_configuration() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    InterlisRoleJoinMeta meta = configuredMeta();

    meta.check(
        remarks, null, new TransformMeta("INTERLIS Role Join", meta), null, null, null, null,
        new Variables(), new MemoryMetadataProvider());

    assertThat(remarks)
        .anySatisfy(r -> assertThat(r.getType()).isEqualTo(ICheckResult.TYPE_RESULT_OK));
  }

  @Test
  void hop_xml_roundtrip_preserves_configuration() throws Exception {
    InterlisRoleJoinMeta meta = configuredMeta();
    meta.setLookupFields(List.of("Street"));
    meta.setPrefix("Addr_");

    String xml = new TransformMeta("INTERLIS Role Join", meta).getXml();
    Node node =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new org.xml.sax.InputSource(new StringReader(xml)))
            .getDocumentElement();
    InterlisRoleJoinMeta restored = new InterlisRoleJoinMeta();
    restored.loadXml(node, new MemoryMetadataProvider());

    assertThat(restored.getMainInputTransform()).isEqualTo("INTERLIS Input");
    assertThat(restored.getLookupInputTransform()).isEqualTo("lookup");
    assertThat(restored.getModelNames()).isEqualTo("HopIli_Associations_V1");
    assertThat(restored.getMainClassName()).isEqualTo("HopIli_Associations_V1.Data.Person");
    assertThat(restored.getRoleName()).isEqualTo("Address");
    assertThat(restored.getLookupTidField()).isEqualTo("_ili_tid");
    assertThat(restored.getLookupFields()).containsExactly("Street");
    assertThat(restored.getPrefix()).isEqualTo("Addr_");
    assertThat(restored.isFailOnMissingMandatoryReference()).isTrue();
    assertThat(restored.isFailOnDuplicateTid()).isTrue();
  }

  @Test
  void plugin_contract() {
    org.apache.hop.core.annotations.Transform annotation =
        InterlisRoleJoinMeta.class.getAnnotation(org.apache.hop.core.annotations.Transform.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.id()).isEqualTo("INTERLIS_ROLE_JOIN");
    assertThat(annotation.classLoaderGroup()).isEqualTo("sogeo-geometry");
    assertThat(annotation.categoryDescription()).isEqualTo("Geospatial");
    assertThat(InterlisRoleJoinMeta.class.getResource("/" + annotation.image())).isNotNull();
  }
}
