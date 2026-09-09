package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

@Timeout(10)
class InterlisProbeCoordinatorTest {
  @Test
  void bundles_requests_and_delivers_only_latest_result() throws Exception {
    var ui = new LinkedBlockingQueue<Runnable>();
    var calls = new AtomicInteger();
    var values = new ArrayList<Integer>();
    try (var coordinator = new InterlisProbeCoordinator(ui::add)) {
      for (int i = 0; i < 20; i++) {
        int value = i;
        coordinator.submit(
            false,
            () -> {
              calls.incrementAndGet();
              return value;
            },
            values::add,
            e -> {
              throw new AssertionError(e);
            });
      }
      ui.poll(3, TimeUnit.SECONDS).run();
      assertThat(calls).hasValue(1);
      assertThat(values).containsExactly(19);
    }
  }

  @Test
  void running_and_already_queued_results_cannot_overwrite_newer_input_or_closed_dialog()
      throws Exception {
    var ui = new LinkedBlockingQueue<Runnable>();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var values = new ArrayList<Integer>();
    try (var coordinator = new InterlisProbeCoordinator(ui::add)) {
      coordinator.submit(
          true,
          () -> {
            started.countDown();
            release.await();
            return 1;
          },
          values::add,
          e -> {});
      assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
      coordinator.submit(false, () -> 2, values::add, e -> {});
      release.countDown();
      Runnable result = ui.poll(3, TimeUnit.SECONDS);
      assertThat(result).isNotNull();
      coordinator.submit(false, () -> 3, values::add, e -> {});
      result.run();
      assertThat(values).isEmpty();
      Runnable last = ui.poll(3, TimeUnit.SECONDS);
      coordinator.close();
      last.run();
      assertThat(values).isEmpty();
    }
  }
}
