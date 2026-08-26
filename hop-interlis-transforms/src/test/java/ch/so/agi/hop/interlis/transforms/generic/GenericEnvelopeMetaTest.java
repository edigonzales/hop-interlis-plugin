package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.objecttorow.InterlisObjectToRowMeta;
import ch.so.agi.hop.interlis.transforms.rowtoobject.InterlisRowToObjectMeta;
import ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta;
import ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

class GenericEnvelopeMetaTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private static void assertContract(Class<?> metaClass, String id) {
    Transform annotation = metaClass.getAnnotation(Transform.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.id()).isEqualTo(id);
    assertThat(annotation.classLoaderGroup()).isEqualTo("sogeo-geometry");
    assertThat(annotation.categoryDescription()).isEqualTo("Geospatial");
    assertThat(metaClass.getResource("/" + annotation.image())).isNotNull();
  }

  @Test
  void plugin_contracts() {
    assertContract(InterlisTransferInputMeta.class, "INTERLIS_TRANSFER_INPUT");
    assertContract(InterlisObjectToRowMeta.class, "INTERLIS_OBJECT_TO_ROW");
    assertContract(InterlisRowToObjectMeta.class, "INTERLIS_ROW_TO_OBJECT");
    assertContract(InterlisTransferOutputMeta.class, "INTERLIS_TRANSFER_OUTPUT");
  }

  @Test
  void transfer_input_get_fields_produces_the_constant_envelope_schema() throws Exception {
    InterlisTransferInputMeta meta = new InterlisTransferInputMeta();
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames()).containsExactlyElementsOf(InterlisEnvelopeRowLayout.FIELD_NAMES);
    assertThat(rowMeta.getValueMeta(InterlisEnvelopeRowLayout.OBJECT_INDEX).getType())
        .isEqualTo(
            ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject
                .TYPE_INTERLIS_OBJECT);
  }

  @Test
  void row_to_object_get_fields_replaces_with_the_envelope_schema() throws Exception {
    InterlisRowToObjectMeta meta = new InterlisRowToObjectMeta();
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("_ili_tid"));
    rowMeta.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("Name"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames()).containsExactlyElementsOf(InterlisEnvelopeRowLayout.FIELD_NAMES);
  }

  @Test
  void object_to_row_get_fields_produces_typed_schema() throws Exception {
    InterlisObjectToRowMeta meta = new InterlisObjectToRowMeta();
    meta.setDefault();
    meta.setModelNames("HopIli_Associations_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Associations_V1.Data.Person");
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "Name", "Address_ref", "Address_Share");
  }

  @Test
  void check_reports_configuration_problems() throws Exception {
    for (var meta :
        List.of(
            new InterlisTransferInputMeta(),
            new InterlisObjectToRowMeta(),
            new InterlisRowToObjectMeta(),
            new InterlisTransferOutputMeta())) {
      List<ICheckResult> remarks = new ArrayList<>();
      meta.check(
          remarks, null, new TransformMeta("step", meta), null, null, null, null, new Variables(),
          new MemoryMetadataProvider());
      assertThat(remarks).isNotEmpty();
      assertThat(remarks.get(0).getType()).isEqualTo(ICheckResult.TYPE_RESULT_ERROR);
    }
  }

  @Test
  void hop_xml_roundtrips_preserve_configuration() throws Exception {
    InterlisTransferInputMeta input = new InterlisTransferInputMeta();
    input.setFileName("/data/x.xtf");
    input.setModelNames("%DATA");
    input.setMode("EVENTS");
    InterlisTransferInputMeta inputRestored =
        roundtrip(input, new InterlisTransferInputMeta());
    assertThat(inputRestored.getFileName()).isEqualTo("/data/x.xtf");
    assertThat(inputRestored.resolvedMode().name()).isEqualTo("EVENTS");

    InterlisObjectToRowMeta objectToRow = new InterlisObjectToRowMeta();
    objectToRow.setDefault();
    objectToRow.setModelNames("M");
    objectToRow.setClassName("M.T.C");
    objectToRow.setAppendEnvelopeFields(true);
    InterlisObjectToRowMeta objectToRowRestored =
        roundtrip(objectToRow, new InterlisObjectToRowMeta());
    assertThat(objectToRowRestored.isAppendEnvelopeFields()).isTrue();
    assertThat(objectToRowRestored.getClassName()).isEqualTo("M.T.C");

    InterlisRowToObjectMeta rowToObject = new InterlisRowToObjectMeta();
    rowToObject.setDefault();
    rowToObject.setModelNames("M");
    rowToObject.setClassName("M.T.C");
    rowToObject.setBasketIdField("_ili_bid");
    InterlisRowToObjectMeta rowToObjectRestored =
        roundtrip(rowToObject, new InterlisRowToObjectMeta());
    assertThat(rowToObjectRestored.getClassName()).isEqualTo("M.T.C");
    assertThat(rowToObjectRestored.getBasketIdField()).isEqualTo("_ili_bid");

    InterlisTransferOutputMeta output = new InterlisTransferOutputMeta();
    output.setDefault();
    output.setFileName("/out/x.xtf");
    output.setModelNames("M");
    output.setOverwrite(true);
    output.setEventMode(true);
    InterlisTransferOutputMeta outputRestored =
        roundtrip(output, new InterlisTransferOutputMeta());
    assertThat(outputRestored.isEventMode()).isTrue();
    assertThat(outputRestored.isOverwrite()).isTrue();
  }

  private static <T extends org.apache.hop.pipeline.transform.ITransformMeta> T roundtrip(
      T meta, T restored) throws Exception {
    String xml = new TransformMeta("step", meta).getXml();
    Node node =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new org.xml.sax.InputSource(new StringReader(xml)))
            .getDocumentElement();
    restored.loadXml(node, new MemoryMetadataProvider());
    return restored;
  }
}
