package ch.so.agi.hop.interlis.transforms.input;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisInputDialogControllerTest {

  private final InterlisInputDialogController controller = new InterlisInputDialogController();

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisInputMeta validMeta() {
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
  void valid_configuration_yields_projection_and_classes() {
    InterlisProbeResult result = controller.probe(validMeta(), new Variables());

    assertThat(result.successful()).isTrue();
    assertThat(result.classes())
        .extracting(c -> c.scopedName())
        .contains("HopIli_Geometry_V1.Data.TestObject");
  }

  @Test
  void unresolved_variable_yields_friendly_message() {
    InterlisInputMeta meta = validMeta();
    meta.setFileName("${UNRESOLVED}/input.xtf");

    InterlisProbeResult result = controller.probe(meta, new Variables());

    assertThat(result.successful()).isFalse();
    assertThat(result.message()).containsIgnoringCase("incomplete");
  }

  @Test
  void missing_file_yields_friendly_message() {
    InterlisInputMeta meta = validMeta();
    meta.setFileName("/does/not/exist.xtf");

    InterlisProbeResult result = controller.probe(meta, new Variables());

    assertThat(result.successful()).isFalse();
    assertThat(result.message()).isNotBlank();
  }

  @Test
  void unknown_class_yields_friendly_message_with_class_name() {
    InterlisInputMeta meta = validMeta();
    meta.setClassName("HopIli_Geometry_V1.Data.DoesNotExist");

    InterlisProbeResult result = controller.probe(meta, new Variables());

    assertThat(result.successful()).isFalse();
    assertThat(result.message()).contains("DoesNotExist");
  }

  @Test
  void schema_preview_lists_fields_with_types_and_sources() {
    InterlisProbeResult result = controller.probe(validMeta(), new Variables());

    String preview = controller.formatSchemaPreview(result.projection().plan());

    assertThat(preview).contains("Projected Hop schema");
    assertThat(preview).contains("_ili_tid");
    assertThat(preview).contains("_ili_bid");
    assertThat(preview).contains("Name");
    assertThat(preview).contains("Center");
    assertThat(preview).contains("Geometry");
  }

  @Test
  void schema_preview_shows_warnings_for_multi_valued_structures() {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName(TestData.path("/data/HopIli_Geometry_V1_valid.xtf").toString());
    meta.setModelNames("HopIli_Mapping_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Mapping_V1.Data.MultiStruct");

    InterlisProbeResult result = controller.probe(meta, new Variables());
    String preview = controller.formatSchemaPreview(result.projection().plan());

    assertThat(preview).contains("Qualities");
    assertThat(preview).contains("Structure Explode");
  }
}
