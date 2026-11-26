package ch.so.agi.hop.interlis.transforms.rolejoin;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.transform.TransformMeta;
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
  void saved_role_survives_refreshing_the_role_choices_and_saving() throws Exception {
    var meta = new InterlisRoleJoinMeta();
    var saved = XmlHandler.loadXmlString("<transform><roleName>Address</roleName></transform>");
    meta.loadXml(saved.getDocumentElement(), new MemoryMetadataProvider());
    meta.setModelNames("HopIli_Associations_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setMainClassName("HopIli_Associations_V1.Data.Person");

    var result = controller.probe(meta, new Variables());
    var choices = result.roles();
    String selection = controller.selectedRoleLabel(choices, meta.getRoleName());
    assertThat(selection).startsWith("Address -> ");
    assertThat(controller.selectedRoleLabel(choices, selection)).isEqualTo(selection);
    assertThat(controller.selectedRoleLabel(choices, "${ROLE}")).isEqualTo("${ROLE}");

    meta.setRoleName(InterlisRoleJoinDialogController.roleName(selection));
    meta.setFailOnDuplicateKey(false);
    assertThat(new TransformMeta("INTERLIS Role Join", meta).getXml())
        .contains("<roleName>Address</roleName>", "<failOnDuplicateKey>N</failOnDuplicateKey>");
  }

  @Test
  void structured_preview_contains_role_configuration_and_target_fields() throws Exception {
    InterlisRoleJoinMeta meta = new InterlisRoleJoinMeta();
    meta.setDefault();
    meta.setModelNames("HopIli_Associations_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setMainClassName("HopIli_Associations_V1.Data.Person");
    meta.setRoleName("Address");

    var result = controller.probe(meta, new Variables());
    var preview = controller.createSchemaPreview(meta, result.selectedRole(), new Variables());

    assertThat(preview.rows())
        .extracting(row -> row.fieldName())
        .contains(
            "Main class",
            "Role",
            "Main reference field",
            "Main BID field",
            "Lookup BID field",
            "Lookup TID field",
            "Address_Street");
    assertThat(preview.rows())
        .anyMatch(row -> row.fieldName().equals("Address_Street") && row.hopType().equals("String"));
    assertThat(preview.errorMessage()).isNull();
  }
}
