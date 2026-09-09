package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.io.InterlisValidationRowLayout;
import ch.so.agi.hop.interlis.transforms.enumerations.InterlisEnumerationsMeta;
import ch.so.agi.hop.interlis.transforms.validate.InterlisValidateMeta;
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
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

class Phase6MetaTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void plugin_contracts() {
    Transform validate = InterlisValidateMeta.class.getAnnotation(Transform.class);
    assertThat(validate).isNotNull();
    assertThat(validate.id()).isEqualTo("INTERLIS_VALIDATE");
    assertThat(validate.classLoaderGroup()).isEqualTo("sogeo-geometry");

    Transform enumerations = InterlisEnumerationsMeta.class.getAnnotation(Transform.class);
    assertThat(enumerations).isNotNull();
    assertThat(enumerations.id()).isEqualTo("INTERLIS_ENUMERATIONS");
    assertThat(enumerations.classLoaderGroup()).isEqualTo("sogeo-geometry");
  }

  @Test
  void validate_get_fields_produces_the_validation_error_schema() throws Exception {
    InterlisValidateMeta meta = new InterlisValidateMeta();
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames())
        .containsExactlyElementsOf(InterlisValidationRowLayout.FIELD_NAMES);
  }

  @Test
  void enumerations_get_fields_produces_the_enum_schema() throws Exception {
    InterlisEnumerationsMeta meta = new InterlisEnumerationsMeta();
    RowMeta rowMeta = new RowMeta();

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.getFieldNames())
        .containsExactly(
            "enum_definition", "enum_value", "enum_path", "parent_value", "depth", "is_leaf");
  }

  @Test
  void check_reports_configuration_problems() throws Exception {
    for (var meta : List.of(new InterlisValidateMeta(), new InterlisEnumerationsMeta())) {
      List<ICheckResult> remarks = new ArrayList<>();
      meta.check(
          remarks,
          null,
          new TransformMeta("step", meta),
          null,
          null,
          null,
          null,
          new Variables(),
          new MemoryMetadataProvider());
      assertThat(remarks).isNotEmpty();
      assertThat(remarks.get(0).getType()).isEqualTo(ICheckResult.TYPE_RESULT_ERROR);
    }
  }

  @Test
  void hop_xml_roundtrips_preserve_configuration() throws Exception {
    InterlisValidateMeta validate = new InterlisValidateMeta();
    validate.setFileName("/data/x.xtf");
    validate.setModelNames("%DATA");
    validate.setConfigFile("/cfg.toml");
    validate.setValidateMultiplicity(false);
    validate.setMaxErrors(42);
    validate.setStopOnFirstError(true);
    validate.setIncludeWarnings(false);
    validate.setFailOnErrors(true);
    InterlisValidateMeta validateRestored = roundtrip(validate, new InterlisValidateMeta());
    assertThat(validateRestored.getFileName()).isEqualTo("/data/x.xtf");
    assertThat(validateRestored.getConfigFile()).isEqualTo("/cfg.toml");
    assertThat(validateRestored.isValidateMultiplicity()).isFalse();
    assertThat(validateRestored.getMaxErrors()).isEqualTo(42);
    assertThat(validateRestored.isStopOnFirstError()).isTrue();
    assertThat(validateRestored.isIncludeWarnings()).isFalse();
    assertThat(validateRestored.isFailOnErrors()).isTrue();

    InterlisEnumerationsMeta enumerations = new InterlisEnumerationsMeta();
    enumerations.setModelNames("M");
    enumerations.setModelDirectories("/models");
    InterlisEnumerationsMeta enumerationsRestored =
        roundtrip(enumerations, new InterlisEnumerationsMeta());
    assertThat(enumerationsRestored.getModelNames()).isEqualTo("M");
    assertThat(enumerationsRestored.getModelDirectories()).isEqualTo("/models");
  }

  @Test
  void envelope_schema_now_carries_basket_metadata() throws Exception {
    RowMeta rowMeta =
        ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory.createRowMeta();

    assertThat(rowMeta.getFieldNames())
        .containsExactlyElementsOf(InterlisEnvelopeRowLayout.FIELD_NAMES);
    assertThat(InterlisEnvelopeRowLayout.FIELD_NAMES)
        .endsWith(
            "_ili_basket_consistency",
            "_ili_basket_kind",
            "_ili_basket_start_state",
            "_ili_basket_end_state",
            "_ili_transfer_metadata");
  }

  private static <T extends ITransformMeta> T roundtrip(T meta, T restored) throws Exception {
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
