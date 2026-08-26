package ch.so.agi.hop.interlis.transforms.output;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisOutputDialogControllerTest {

  private final InterlisOutputDialogController controller = new InterlisOutputDialogController();

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisOutputMeta validMeta() {
    InterlisOutputMeta meta = new InterlisOutputMeta();
    meta.setFileName("/tmp/out.xtf");
    meta.setModelNames("HopIli_Spike_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Spike_V1.Data.Building");
    meta.setObjectIdField("_ili_tid");
    meta.setBasketIdField("_ili_bid");
    meta.setBasketId("b1");
    return meta;
  }

  @Test
  void valid_configuration_yields_projection_and_classes() {
    var result = controller.probe(validMeta(), new Variables());

    assertThat(result.successful()).isTrue();
    assertThat(result.classes())
        .extracting(c -> c.scopedName())
        .contains("HopIli_Spike_V1.Data.Building");
  }

  @Test
  void unresolved_variable_yields_friendly_message() {
    InterlisOutputMeta meta = validMeta();
    meta.setFileName("${UNRESOLVED}/out.xtf");

    var result = controller.probe(meta, new Variables());

    assertThat(result.successful()).isFalse();
    assertThat(result.message()).containsIgnoringCase("incomplete");
  }

  @Test
  void unknown_class_yields_friendly_message_with_class_name() {
    InterlisOutputMeta meta = validMeta();
    meta.setClassName("HopIli_Spike_V1.Data.DoesNotExist");

    var result = controller.probe(meta, new Variables());

    assertThat(result.successful()).isFalse();
    assertThat(result.message()).contains("DoesNotExist");
  }

  @Test
  void mapping_lists_technical_fields_attributes_and_roles() {
    var result = controller.probe(validMeta(), new Variables());
    List<InterlisFieldMapping> mappings = controller.mapping(result.projection().plan());

    assertThat(mappings)
        .extracting(InterlisFieldMapping::property)
        .containsExactly(
            "@TID", "@BID", "Code", "Location", "Address.Street", "Address.Number",
            "Municipality -> HopIli_Spike_V1.Data.Municipality", "Note");
    assertThat(mappings.get(0).hopField()).isEqualTo("_ili_tid");
    assertThat(mappings.get(1).hopField()).isEqualTo("_ili_bid");
    assertThat(mappings.get(3).type()).contains("COORD");
    assertThat(mappings)
        .allMatch(m -> m.status().equals("auto-map by name"));
  }
}
