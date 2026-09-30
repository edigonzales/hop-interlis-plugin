package ch.so.agi.hop.interlis.transforms.buffer;

import ch.so.agi.hop.interlis.core.buffer.*;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.transform.BaseTransform;

/** Reads either branch without leaving a shared producer blocked on the other branch. */
public final class FairInputReader implements AutoCloseable {
  private final BaseTransform<?, ?> owner;
  private final IRowSet[] inputs;
  private final SpillStore<Object[]>[] pending;
  private final boolean[] exhausted;
  private final SpillOptions options;
  private int first;

  @SuppressWarnings("unchecked")
  public FairInputReader(BaseTransform<?, ?> owner, SpillOptions options, IRowSet... inputs) {
    this.owner = owner;
    this.inputs = inputs.clone();
    this.options = options;
    pending = (SpillStore<Object[]>[]) new SpillStore<?>[inputs.length];
    exhausted = new boolean[inputs.length];
  }

  public Object[] next(int wanted) throws HopException {
    while (!owner.isStopped()) {
      while (owner.isPaused() && !owner.isStopped()) pause();
      if (pending[wanted] != null && pending[wanted].size() > 0) return pending[wanted].poll();
      if (exhausted[wanted]) return null;
      boolean progress = false;
      for (int offset = 0; offset < inputs.length; offset++) {
        int i = (first + offset) % inputs.length;
        if (exhausted[i]) continue;
        var input = inputs[i];
        Object[] row = input.getRowImmediate();
        if (row == null && input.isDone()) {
          row = input.getRowImmediate();
          if (row == null) exhausted[i] = true;
        }
        if (row != null) {
          progress = true;
          owner.incrementLinesRead();
          if (owner.getFirstRowReadDate() == null) owner.setFirstRowReadDate(new java.util.Date());
          for (var listener : owner.getRowListeners())
            listener.rowReadEvent(input.getRowMeta(), row);
          if (pending[i] == null)
            pending[i] =
                new SpillStore<>(
                    new HopRowCodec(input.getRowMeta()), options.divided(inputs.length));
          pending[i].append(row);
        }
      }
      first = (first + 1) % inputs.length;
      if (!progress) pause();
    }
    return null;
  }

  private void pause() throws HopException {
    try {
      Thread.sleep(2);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HopException(e);
    }
  }

  @Override
  public void close() {
    RuntimeException failure = null;
    for (var queue : pending)
      if (queue != null)
        try {
          queue.close();
        } catch (RuntimeException e) {
          if (failure == null) failure = e;
          else failure.addSuppressed(e);
        }
    if (failure != null) throw failure;
  }
}
