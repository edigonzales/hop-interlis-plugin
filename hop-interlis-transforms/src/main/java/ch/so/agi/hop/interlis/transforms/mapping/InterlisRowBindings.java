package ch.so.agi.hop.interlis.transforms.mapping;

import java.util.List;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;

/** A plan-ordered collection of immutable field bindings. No per-row schema lookups. */
public final class InterlisRowBindings {
  private final List<InterlisFieldBinding> fields;

  public InterlisRowBindings(List<InterlisFieldBinding> fields) {
    this.fields = List.copyOf(fields);
    for (int i = 0; i < fields.size(); i++)
      if (fields.get(i).targetIndex() != i)
        throw new IllegalArgumentException("Bindings must be in target order");
  }

  public IRowMeta normalRowMeta() {
    var meta = new org.apache.hop.core.row.RowMeta();
    for (var binding : fields) {
      var value = binding.sourceMeta();
      value.setStorageType(org.apache.hop.core.row.IValueMeta.STORAGE_TYPE_NORMAL);
      value.setStorageMetadata(null);
      value.setIndex(null);
      meta.addValueMeta(value);
    }
    return meta;
  }

  public Object[] values(Object[] row) throws HopTransformException {
    Object[] values = new Object[fields.size()];
    for (var field : fields) values[field.targetIndex()] = field.read(row);
    return values;
  }
}
