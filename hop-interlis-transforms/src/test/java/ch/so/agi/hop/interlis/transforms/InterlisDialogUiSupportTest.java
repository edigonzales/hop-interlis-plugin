package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InterlisDialogUiSupportTest {

  @Test
  void status_height_includes_padding_and_native_border_for_single_line_message() {
    assertThat(InterlisDialogUiSupport.calculateStatusHeight(14, 3, 1)).isEqualTo(22);
  }

  @Test
  void status_height_scales_with_wrapped_message_and_border_width() {
    assertThat(InterlisDialogUiSupport.calculateStatusHeight(28, 3, 1)).isEqualTo(36);
    assertThat(InterlisDialogUiSupport.calculateStatusHeight(14, 3, 2)).isEqualTo(24);
  }

  @Test
  void class_selection_request_is_an_informational_status() {
    assertThat(
            InterlisDialogUiSupport.statusSeverity(
                false, true, "Model loaded. Select an INTERLIS class."))
        .isEqualTo(InterlisDialogUiSupport.StatusSeverity.INFO);
  }

  @Test
  void incomplete_configuration_is_an_informational_status() {
    var result =
        new InterlisProbeResult(false, "Any translated message", null, List.of())
            .withStatus(InterlisProbeStatus.INFO);
    assertThat(result.status()).isEqualTo(InterlisProbeStatus.INFO);
  }

  @Test
  void model_resolution_failure_is_an_error_status() {
    assertThat(
            InterlisDialogUiSupport.statusSeverity(
                false, false, "Model NoSuchModel was not found in the repositories."))
        .isEqualTo(InterlisDialogUiSupport.StatusSeverity.ERROR);
  }

  @Test
  void successful_probe_is_a_success_status() {
    assertThat(InterlisDialogUiSupport.statusSeverity(true, true, "Model loaded"))
        .isEqualTo(InterlisDialogUiSupport.StatusSeverity.SUCCESS);
  }

  @Test
  void preview_warning_refines_success_to_warning_without_changing_the_message() {
    InterlisSchemaPreview preview =
        new InterlisSchemaPreview(List.of(), List.of("field skipped"), null);

    assertThat(
            InterlisDialogUiSupport.statusSeverity(
                InterlisDialogUiSupport.StatusSeverity.SUCCESS, preview))
        .isEqualTo(InterlisDialogUiSupport.StatusSeverity.WARNING);
  }

  @Test
  void preview_error_refines_success_to_error() {
    InterlisSchemaPreview preview =
        new InterlisSchemaPreview(List.of(), List.of(), "preview failed");

    assertThat(
            InterlisDialogUiSupport.statusSeverity(
                InterlisDialogUiSupport.StatusSeverity.SUCCESS, preview))
        .isEqualTo(InterlisDialogUiSupport.StatusSeverity.ERROR);
  }
}
