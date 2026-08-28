package ch.so.agi.hop.interlis.transforms.input;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.InterlisProbeResult;
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

  private InterlisInputMeta validMetaWithoutClass() {
    InterlisInputMeta meta = validMeta();
    meta.setClassName("");
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
  void model_only_probe_loads_model_and_lists_classes_without_selection() {
    InterlisProbeResult result = controller.probe(validMetaWithoutClass(), new Variables());

    assertThat(result.configured()).isTrue();
    assertThat(result.successful()).isFalse();
    assertThat(result.projection()).isNull();
    assertThat(result.classes())
        .extracting(c -> c.scopedName())
        .contains("HopIli_Geometry_V1.Data.TestObject");
    assertThat(result.message()).contains("Select an INTERLIS class");
  }

  @Test
  void selecting_class_after_model_only_probe_builds_projection_and_preview() {
    InterlisInputMeta meta = validMetaWithoutClass();

    InterlisProbeResult modelOnly = controller.probe(meta, new Variables());
    assertThat(modelOnly.classes()).isNotEmpty();

    meta.setClassName("HopIli_Geometry_V1.Data.TestObject");
    InterlisProbeResult projected = controller.probe(meta, new Variables());

    assertThat(projected.successful()).isTrue();
    assertThat(controller.formatSchemaPreview(projected.projection().plan()))
        .contains("Projected Hop schema", "Center", "Geometry");
  }

  @Test
  void unresolved_class_variable_still_leaves_model_classes_visible() {
    InterlisInputMeta meta = validMeta();
    meta.setClassName("${CLASS}");

    InterlisProbeResult result = controller.probe(meta, new Variables());

    assertThat(result.configured()).isTrue();
    assertThat(result.successful()).isFalse();
    assertThat(result.classes())
        .extracting(c -> c.scopedName())
        .contains("HopIli_Geometry_V1.Data.TestObject");
  }

  @Test
  void unknown_model_yields_empty_classes_and_actionable_message() {
    InterlisInputMeta meta = validMetaWithoutClass();
    meta.setModelNames("HopIli_NoSuchModel_V1");

    InterlisProbeResult result = controller.probe(meta, new Variables());

    assertThat(result.configured()).isFalse();
    assertThat(result.classes()).isEmpty();
    assertThat(result.message()).contains("HopIli_NoSuchModel_V1");
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
    assertThat(result.classes()).isNotEmpty();
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
