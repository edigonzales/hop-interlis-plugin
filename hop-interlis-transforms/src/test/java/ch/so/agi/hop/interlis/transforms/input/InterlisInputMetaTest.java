package ch.so.agi.hop.interlis.transforms.input;

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

class InterlisInputMetaTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisInputMeta configuredMeta() {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName(TestData.path("/data/HopIli_Geometry_V1_valid.xtf").toString());
    meta.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Geometry_V1.Data.TestObject");
    meta.setIncludeTid(true);
    meta.setIncludeBid(true);
    return meta;
  }

  @Test
  void get_fields_produces_typed_metadata() throws Exception {
    InterlisInputMeta meta = configuredMeta();
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(10);
    assertThat(rowMeta.getValueMeta(0).getName()).isEqualTo("_ili_tid");
    assertThat(rowMeta.getValueMeta(3).getName()).isEqualTo("Center");
    assertThat(rowMeta.getValueMeta(3).getType())
        .isEqualTo(com.atolcd.hop.core.row.value.ValueMetaGeometry.TYPE_GEOMETRY);
  }

  @Test
  void try_load_model_does_not_require_class_name() throws Exception {
    InterlisInputMeta meta = configuredMeta();
    meta.setClassName("");

    var context = meta.tryLoadModel(new Variables());

    assertThat(context).isPresent();
    assertThat(context.orElseThrow().schema().classes())
        .extracting(c -> c.scopedName())
        .contains("HopIli_Geometry_V1.Data.TestObject");
  }

  @Test
  void get_fields_with_unresolved_variables_stays_silent() throws Exception {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName("${UNRESOLVED_VARIABLE}/data.xtf");
    meta.setModelNames("HopIli_Geometry_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Geometry_V1.Data.TestObject");
    RowMeta rowMeta = new RowMeta();

    // Must not throw: design-time probing with unresolved variables stays silent.
    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isZero();
  }

  @Test
  void get_fields_with_missing_file_stays_silent() throws Exception {
    InterlisInputMeta meta = configuredMeta();
    meta.setFileName("/does/not/exist.xtf");
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isZero();
  }

  @Test
  void get_fields_appends_source_object_carrier_when_configured() throws Exception {
    InterlisInputMeta meta = configuredMeta();
    meta.setKeepSourceObject(true);
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(11);
    assertThat(rowMeta.getValueMeta(10).getName()).isEqualTo("_ili_source_object");
    assertThat(rowMeta.getValueMeta(10).getType())
        .isEqualTo(
            ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject
                .TYPE_INTERLIS_OBJECT);
  }

  @Test
  void check_reports_errors_for_missing_configuration() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    new InterlisInputMeta().check(remarks, null, new TransformMeta("x", new InterlisInputMeta()),
        null, new String[0], new String[0], null, new Variables(), null);

    assertThat(remarks)
        .anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR && r.getText().contains("required"));
  }

  @Test
  void check_reports_ok_for_valid_configuration() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    configuredMeta()
        .check(remarks, null, new TransformMeta("x", configuredMeta()), null, new String[0],
            new String[0], null, new Variables(), null);

    assertThat(remarks).anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK);
  }

  @Test
  void check_reports_error_for_unknown_class() throws Exception {
    InterlisInputMeta meta = configuredMeta();
    meta.setClassName("HopIli_Geometry_V1.Data.DoesNotExist");
    List<ICheckResult> remarks = new ArrayList<>();

    meta.check(remarks, null, new TransformMeta("x", meta), null, new String[0], new String[0],
        null, new Variables(), null);

    assertThat(remarks)
        .anyMatch(
            r ->
                r.getType() == ICheckResult.TYPE_RESULT_ERROR
                    && r.getText().contains("DoesNotExist"));
  }

  @Test
  void meta_roundtrips_through_hop_xml_serialization() throws Exception {
    InterlisInputMeta original = configuredMeta();
    TransformMeta transformMeta = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", original);
    String xml = transformMeta.getXml();

    Node transformNode =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new org.xml.sax.InputSource(new StringReader(xml)))
            .getDocumentElement();
    TransformMeta loaded =
        new TransformMeta(transformNode, new MemoryMetadataProvider());
    InterlisInputMeta roundtripped = (InterlisInputMeta) loaded.getTransform();

    assertThat(roundtripped.getFileName()).isEqualTo(original.getFileName());
    assertThat(roundtripped.getModelNames()).isEqualTo(original.getModelNames());
    assertThat(roundtripped.getModelDirectories()).isEqualTo(original.getModelDirectories());
    assertThat(roundtripped.getClassName()).isEqualTo(original.getClassName());
    assertThat(roundtripped.isIncludeTid()).isTrue();
    assertThat(roundtripped.isIncludeBid()).isTrue();
    assertThat(roundtripped.isIncludeClassName()).isFalse();
    assertThat(roundtripped.getDefaultSrid()).isEqualTo(original.getDefaultSrid());
  }
}
