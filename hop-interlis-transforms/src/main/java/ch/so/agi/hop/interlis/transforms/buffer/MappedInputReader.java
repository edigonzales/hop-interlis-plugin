package ch.so.agi.hop.interlis.transforms.buffer;

import ch.so.agi.hop.interlis.transforms.mapping.InterlisMappedInput;
import java.util.*;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransform;

/** Fairly drains every physical input copy, retaining each input's own metadata. */
public final class MappedInputReader {
  public record Row(int inputIndex, IRowSet rowSet, IRowMeta meta, Object[] values) {}

  private final BaseTransform<?, ?> owner;
  private final List<IRowSet> rowSets;
  private final int[] mappings;
  private final boolean[] done;
  private int next;

  public MappedInputReader(BaseTransform<?, ?> owner, List<InterlisMappedInput> inputs)
      throws HopException {
    this.owner = owner;
    rowSets = List.copyOf(owner.getInputRowSets());
    mappings = new int[rowSets.size()];
    done = new boolean[rowSets.size()];
    var names = new HashMap<String, Integer>();
    for (int i = 0; i < inputs.size(); i++) {
      String name = inputs.get(i).getTransformName();
      if (name.isBlank() || names.putIfAbsent(name, i) != null)
        throw new HopException("Missing or duplicate INTERLIS input transform: " + name);
    }
    var found = new HashSet<String>();
    for (int i = 0; i < rowSets.size(); i++) {
      String name = rowSets.get(i).getOriginTransformName();
      Integer mapping = names.get(name);
      if (mapping == null) throw new HopException("Unconfigured INTERLIS input: " + name);
      mappings[i] = mapping;
      found.add(name);
    }
    for (String name : names.keySet())
      if (!found.contains(name)) throw new HopException("INTERLIS input is not connected: " + name);
    if (inputs.isEmpty()) throw new HopException("Configure at least one INTERLIS input mapping");
  }

  public Row next() throws HopException {
    while (!owner.isStopped()) {
      if (owner.isPaused()) {
        pause();
        continue;
      }
      boolean waiting = false;
      for (int n = 0; n < rowSets.size(); n++) {
        int i = (next + n) % rowSets.size();
        if (done[i]) continue;
        var rs = rowSets.get(i);
        Object[] row = rs.getRowImmediate();
        if (row == null && rs.isDone()) {
          row = rs.getRowImmediate();
          if (row == null) done[i] = true;
        }
        if (row != null) {
          next = (i + 1) % rowSets.size();
          owner.incrementLinesRead();
          if (owner.getFirstRowReadDate() == null) owner.setFirstRowReadDate(new Date());
          for (var listener : owner.getRowListeners()) listener.rowReadEvent(rs.getRowMeta(), row);
          return new Row(mappings[i], rs, rs.getRowMeta(), row);
        }
        waiting |= !done[i];
      }
      if (!waiting) return null;
      pause();
    }
    throw new HopException("INTERLIS input processing was stopped");
  }

  private static void pause() throws HopException {
    try {
      Thread.sleep(2);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HopException(e);
    }
  }
}
