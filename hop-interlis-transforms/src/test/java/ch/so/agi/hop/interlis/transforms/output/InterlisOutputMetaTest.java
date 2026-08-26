package ch.so.agi.hop.interlis.transforms.output;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

class InterlisOutputMetaTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisOutputMeta configuredMeta() {
    InterlisOutputMeta meta = new InterlisOutputMeta();
    meta.setFileName("/tmp/out.xtf");
    meta.setModelNames("HopIli_Spike_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Spike_V1.Data.Building");
    meta.setObjectIdField("_ili_tid");
    meta.setBasketIdField("_ili_bid");
    meta.setBasketId("b1");
    meta.setOverwrite(true);
    return meta;
  }

  @Test
  void get_fields_does_not_change_the_schema() throws Exception {
    InterlisOutputMeta meta = configuredMeta();
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("name"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(1);
    assertThat(rowMeta.getValueMeta(0).getName()).isEqualTo("name");
  }

  @Test
  void try_project_resolves_the_class() throws Exception {
    Optional<ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult> projection =
        configuredMeta().tryProject(new Variables());

    assertThat(projection).isPresent();
    assertThat(projection.get().plan().classDescriptor().scopedName())
        .isEqualTo("HopIli_Spike_V1.Data.Building");
    assertThat(projection.get().model().transferDescription()).isNotNull();
  }

  @Test
  void try_project_rejects_percent_data_models() {
    InterlisOutputMeta meta = configuredMeta();
    meta.setModelNames("%DATA");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> meta.tryProject(new Variables()))
        .isInstanceOf(ch.so.agi.hop.interlis.core.model.InterlisModelException.class)
        .hasMessageContaining("explicit model");
  }

  @Test
  void try_project_returns_empty_for_unresolved_variables() throws Exception {
    InterlisOutputMeta meta = configuredMeta();
    meta.setFileName("${UNRESOLVED}/out.xtf");

    assertThat(meta.tryProject(new Variables())).isEmpty();
  }

  @Test
  void check_reports_errors_for_missing_configuration() {
    List<ICheckResult> remarks = new ArrayList<>();
    new InterlisOutputMeta().check(remarks, null, new TransformMeta("x", new InterlisOutputMeta()),
        null, new String[0], new String[0], null, new Variables(), null);

    assertThat(remarks)
        .anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR && r.getText().contains("required"));
  }

  @Test
  void check_reports_ok_for_valid_configuration() {
    List<ICheckResult> remarks = new ArrayList<>();
    configuredMeta().check(remarks, null, new TransformMeta("x", configuredMeta()), null,
        new String[0], new String[0], null, new Variables(), null);

    assertThat(remarks).anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK);
  }

  @Test
  void check_reports_error_for_unknown_class() {
    InterlisOutputMeta meta = configuredMeta();
    meta.setClassName("HopIli_Spike_V1.Data.DoesNotExist");
    List<ICheckResult> remarks = new ArrayList<>();

    meta.check(remarks, null, new TransformMeta("x", meta), null, new String[0], new String[0],
        null, new Variables(), null);

    assertThat(remarks)
        .anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR && r.getText().contains("DoesNotExist"));
  }

  @Test
  void meta_roundtrips_through_hop_xml_serialization() throws Exception {
    InterlisOutputMeta original = configuredMeta();
    TransformMeta transformMeta = new TransformMeta("INTERLIS_OUTPUT", "INTERLIS Output", original);
    String xml = transformMeta.getXml();

    Node transformNode =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new org.xml.sax.InputSource(new StringReader(xml)))
            .getDocumentElement();
    TransformMeta loaded =
        new TransformMeta(transformNode, new MemoryMetadataProvider());
    InterlisOutputMeta roundtripped = (InterlisOutputMeta) loaded.getTransform();

    assertThat(roundtripped.getFileName()).isEqualTo(original.getFileName());
    assertThat(roundtripped.getModelNames()).isEqualTo(original.getModelNames());
    assertThat(roundtripped.getModelDirectories()).isEqualTo(original.getModelDirectories());
    assertThat(roundtripped.getClassName()).isEqualTo(original.getClassName());
    assertThat(roundtripped.getObjectIdField()).isEqualTo(original.getObjectIdField());
    assertThat(roundtripped.getBasketIdField()).isEqualTo(original.getBasketIdField());
    assertThat(roundtripped.getBasketId()).isEqualTo(original.getBasketId());
    assertThat(roundtripped.isOverwrite()).isEqualTo(original.isOverwrite());
  }
}
