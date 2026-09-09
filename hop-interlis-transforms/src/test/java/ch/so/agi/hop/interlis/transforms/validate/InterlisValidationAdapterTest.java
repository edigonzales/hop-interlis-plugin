package ch.so.agi.hop.interlis.transforms.validate;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class InterlisValidationAdapterTest {
  @Test
  void cancellation_is_checked_during_second_pass_messages() {
    var cancelled = new AtomicBoolean();
    var adapter = new InterlisValidate.CollectingLogFactory(0, cancelled::get);
    adapter.begin();
    adapter.addEvent(adapter.logInfoMsg("first pass completed"));
    cancelled.set(true);
    assertThatThrownBy(() -> adapter.addEvent(adapter.logInfoMsg("second pass")))
        .hasMessageContaining("user cancellation");
  }

  @Test
  void constructor_diagnostics_are_counted_but_abort_waits_until_resources_can_be_closed() {
    var adapter = new InterlisValidate.CollectingLogFactory(1, () -> false);
    assertThatCode(() -> adapter.addEvent(adapter.logErrorMsg("initial error")))
        .doesNotThrowAnyException();
    assertThatThrownBy(adapter::begin).hasMessageContaining("error limit 1");
  }
}
