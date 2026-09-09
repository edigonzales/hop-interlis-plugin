package ch.so.agi.hop.interlis.transforms;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** One running probe and one replaceable request; no SWT dependency, testable with a UI queue. */
public final class InterlisProbeCoordinator implements AutoCloseable {
  private final ScheduledThreadPoolExecutor worker;
  private final Consumer<Runnable> dispatch;
  private ScheduledFuture<?> pending;
  private long generation;
  private boolean closed;

  public InterlisProbeCoordinator(Consumer<Runnable> dispatch) {
    this.dispatch = dispatch;
    worker =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              Thread thread = new Thread(runnable, "interlis-model-preview");
              thread.setDaemon(true);
              return thread;
            });
    worker.setRemoveOnCancelPolicy(true);
  }

  public synchronized <T> void submit(
      boolean immediate, Callable<T> probe, Consumer<T> success, Consumer<Exception> failure) {
    if (closed) return;
    long request = ++generation;
    if (pending != null) pending.cancel(false);
    pending =
        worker.schedule(
            () -> {
              try {
                if (immediate)
                  new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl().clearCache();
                T result = probe.call();
                deliver(request, () -> success.accept(result));
              } catch (Exception e) {
                deliver(request, () -> failure.accept(e));
              }
            },
            immediate ? 0 : 300,
            TimeUnit.MILLISECONDS);
  }

  private void deliver(long request, Runnable result) {
    synchronized (this) {
      if (closed || generation != request) return;
    }
    dispatch.accept(
        () -> {
          synchronized (this) {
            if (closed || generation != request) return;
          }
          result.run();
        });
  }

  @Override
  public synchronized void close() {
    closed = true;
    generation++;
    if (pending != null) pending.cancel(false);
    worker.shutdownNow();
  }

  public static org.apache.hop.core.variables.IVariables snapshot(
      org.apache.hop.core.variables.IVariables source) {
    var copy = new org.apache.hop.core.variables.Variables();
    for (String name : source.getVariableNames()) copy.setVariable(name, source.getVariable(name));
    return copy;
  }
}
