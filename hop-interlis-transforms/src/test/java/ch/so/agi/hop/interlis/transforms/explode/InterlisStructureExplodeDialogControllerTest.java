package ch.so.agi.hop.interlis.transforms.explode;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.InterlisStructureProbeResult;
import ch.so.agi.hop.interlis.transforms.TestData;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisStructureExplodeDialogControllerTest {

  private final InterlisStructureExplodeDialogController controller =
      new InterlisStructureExplodeDialogController();

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
  void probe_reports_success_and_structure_paths() {
    InterlisStructureProbeResult result = controller.probe(configuredMeta(), new Variables());

    assertThat(result.ok()).isTrue();
    assertThat(result.projection()).isNotNull();
    assertThat(result.structurePaths()).containsExactly("Home.Place.Phones", "Addresses", "Contacts");
    assertThat(result.message()).contains("5 child fields");
  }

  @Test
  void probe_without_models_is_a_friendly_failure() {
    InterlisStructureExplodeMeta meta = new InterlisStructureExplodeMeta();
    meta.setDefault();

    InterlisStructureProbeResult result = controller.probe(meta, new Variables());

    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("no INTERLIS models");
  }

  @Test
  void probe_with_invalid_structure_path_is_a_friendly_failure() {
    InterlisStructureExplodeMeta meta = configuredMeta();
    meta.setStructureAttributePath("Name");

    InterlisStructureProbeResult result = controller.probe(meta, new Variables());

    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("not a structure");
  }

  @Test
  void schema_preview_contains_child_row_shape() {
    InterlisStructureExplodeMeta meta = configuredMeta();
    InterlisStructureProbeResult result = controller.probe(meta, new Variables());

    var structured = controller.createSchemaPreview(result.projection().plan());

    assertThat(structured.rows())
        .extracting(row -> row.fieldName())
        .contains("_ili_parent_tid", "_ili_index", "Street", "PostCode_Town");
    assertThat(structured.errorMessage()).isNull();

    String preview = controller.formatSchemaPreview(result.projection().plan());

    assertThat(preview)
        .contains("_ili_parent_tid")
        .contains("_ili_index")
        .contains("Street")
        .contains("PostCode_Town")
        .contains("LIST");
  }
}
