package ch.so.agi.hop.interlis.transforms.rolejoin;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisRoleJoinDialogControllerTest {

  private final InterlisRoleJoinDialogController controller =
      new InterlisRoleJoinDialogController();

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void structured_preview_contains_role_configuration_and_target_fields() throws Exception {
    InterlisRoleJoinMeta meta = new InterlisRoleJoinMeta();
    meta.setDefault();
    meta.setModelNames("HopIli_Associations_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setMainClassName("HopIli_Associations_V1.Data.Person");
    meta.setRoleName("Address");

    InterlisRoleJoinProbeResult result = controller.probe(meta, new Variables());
    var preview = controller.createSchemaPreview(meta, result);

    assertThat(preview.rows())
        .extracting(row -> row.fieldName())
        .contains("Main class", "Role", "Main reference field", "Lookup TID field", "Address_Street");
    assertThat(preview.rows())
        .anyMatch(row -> row.fieldName().equals("Address_Street") && row.hopType().equals("String"));
    assertThat(preview.errorMessage()).isNull();
  }
}
