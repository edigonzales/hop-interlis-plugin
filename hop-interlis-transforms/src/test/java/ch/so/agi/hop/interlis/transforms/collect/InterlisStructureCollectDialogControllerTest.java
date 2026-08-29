package ch.so.agi.hop.interlis.transforms.collect;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.InterlisStructureProbeResult;
import ch.so.agi.hop.interlis.transforms.TestData;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisStructureCollectDialogControllerTest {

  private final InterlisStructureCollectDialogController controller =
      new InterlisStructureCollectDialogController();

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
  void probe_reports_success_and_structure_paths() {
    InterlisStructureProbeResult result = controller.probe(configuredMeta(), new Variables());

    assertThat(result.ok()).isTrue();
    assertThat(result.projection()).isNotNull();
    assertThat(result.structurePaths()).containsExactly("Home.Place.Phones", "Addresses", "Contacts");
  }

  @Test
  void probe_without_models_is_a_friendly_failure() {
    InterlisStructureCollectMeta meta = new InterlisStructureCollectMeta();
    meta.setDefault();

    InterlisStructureProbeResult result = controller.probe(meta, new Variables());

    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("no INTERLIS models");
  }

  @Test
  void collect_preview_describes_streams_and_structure() {
    InterlisStructureCollectMeta meta = configuredMeta();
    InterlisStructureProbeResult result = controller.probe(meta, new Variables());

    var structured =
        controller.createSchemaPreview(
            result.projection().plan(),
            "INTERLIS Input",
            "INTERLIS Structure Explode",
            "_ili_tid",
            "_ili_parent_tid");

    assertThat(structured.rows())
        .extracting(row -> row.fieldName())
        .contains("Parent input", "Child input", "Structure", "Street");
    assertThat(structured.errorMessage()).isNull();

    String preview =
        controller.formatCollectPreview(
            result.projection().plan(), "INTERLIS Input", "INTERLIS Structure Explode",
            "_ili_tid", "_ili_parent_tid");

    assertThat(preview)
        .contains("Parent stream  INTERLIS Input")
        .contains("Child stream   INTERLIS Structure Explode")
        .contains("LIST OF HopIli_Structures_V1.Data.Address")
        .contains("sorted");
  }
}
