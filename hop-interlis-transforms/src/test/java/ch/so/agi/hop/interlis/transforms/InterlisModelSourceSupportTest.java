package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.collect.InterlisStructureCollectMeta;
import ch.so.agi.hop.interlis.transforms.enumerations.InterlisEnumerationsMeta;
import ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.objecttorow.InterlisObjectToRowMeta;
import ch.so.agi.hop.interlis.transforms.output.InterlisOutputMeta;
import ch.so.agi.hop.interlis.transforms.rolejoin.InterlisRoleJoinMeta;
import ch.so.agi.hop.interlis.transforms.rowtoobject.InterlisRowToObjectMeta;
import ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta;
import ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta;
import ch.so.agi.hop.interlis.transforms.validate.InterlisValidateMeta;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterlisModelSourceSupportTest {

  @Test
  void parses_semicolon_separated_model_names() {
    assertThat(InterlisModelSourceSupport.parseModelNames("ModelA; ModelB;ModelC"))
        .containsExactly("ModelA", "ModelB", "ModelC");
  }

  @Test
  void keeps_comma_separated_model_names_for_backwards_compatibility() {
    assertThat(InterlisModelSourceSupport.parseModelNames("ModelA, ModelB"))
        .containsExactly("ModelA", "ModelB");
  }

  @Test
  void treats_data_placeholder_as_model_inference() {
    assertThat(InterlisModelSourceSupport.parseModelNames(" %DATA ")).isEmpty();
    assertThat(InterlisModelSourceSupport.parseModelNames(" ")).isEmpty();
  }

  @Test
  void parses_model_directories_only_with_semicolons() {
    assertThat(
            InterlisModelSourceSupport.parseModelDirectories(
                "/tmp/models; https://models.example.test/; /opt/ili"))
        .containsExactly("/tmp/models", "https://models.example.test/", "/opt/ili");
  }

  @Test
  void exposes_the_shared_default_model_directories() {
    assertThat(InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES)
        .isEqualTo("%XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch");
    assertThat(
            InterlisModelSourceSupport.parseModelDirectories(
                InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES))
        .containsExactly(
            "%XTF_DIR", "https://models.interlis.ch", "https://models.geo.admin.ch");
  }

  @Test
  void all_model_directory_meta_defaults_use_the_shared_value() {
    InterlisInputMeta input = new InterlisInputMeta();
    input.setDefault();
    InterlisTransferInputMeta transferInput = new InterlisTransferInputMeta();
    transferInput.setDefault();
    InterlisValidateMeta validate = new InterlisValidateMeta();
    validate.setDefault();
    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setDefault();
    InterlisTransferOutputMeta transferOutput = new InterlisTransferOutputMeta();
    transferOutput.setDefault();
    InterlisObjectToRowMeta objectToRow = new InterlisObjectToRowMeta();
    objectToRow.setDefault();
    InterlisRowToObjectMeta rowToObject = new InterlisRowToObjectMeta();
    rowToObject.setDefault();
    InterlisStructureExplodeMeta explode = new InterlisStructureExplodeMeta();
    explode.setDefault();
    InterlisStructureCollectMeta collect = new InterlisStructureCollectMeta();
    collect.setDefault();
    InterlisEnumerationsMeta enumerations = new InterlisEnumerationsMeta();
    enumerations.setDefault();
    InterlisRoleJoinMeta roleJoin = new InterlisRoleJoinMeta();
    roleJoin.setDefault();

    List<String> defaults =
        List.of(
            input.getModelDirectories(),
            transferInput.getModelDirectories(),
            validate.getModelDirectories(),
            output.getModelDirectories(),
            transferOutput.getModelDirectories(),
            objectToRow.getModelDirectories(),
            rowToObject.getModelDirectories(),
            explode.getModelDirectories(),
            collect.getModelDirectories(),
            enumerations.getModelDirectories(),
            roleJoin.getModelDirectories());

    assertThat(defaults)
        .hasSize(11)
        .containsOnly(InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES);
  }
}
