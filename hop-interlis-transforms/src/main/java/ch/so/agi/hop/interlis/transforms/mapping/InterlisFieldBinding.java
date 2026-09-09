package ch.so.agi.hop.interlis.transforms.mapping;

import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.IVariables;

/** Immutable, typed source-to-target binding. Metadata is privately cloned at initialization. */
public final class InterlisFieldBinding {
  private final String context;
  private final int sourceIndex;
  private final int targetIndex;
  private final IValueMeta sourceMeta;
  private final Object constant;

  private InterlisFieldBinding(
      String context, int sourceIndex, int targetIndex, IValueMeta sourceMeta, Object constant) {
    this.context = context;
    this.sourceIndex = sourceIndex;
    this.targetIndex = targetIndex;
    this.sourceMeta = sourceMeta == null ? null : sourceMeta.clone();
    this.constant = constant;
  }

  public static String resolve(IVariables variables, String name) {
    return name == null ? "" : (variables == null ? name : variables.resolve(name)).trim();
  }

  /** A null expected type is reserved for unchanged pass-through columns. */
  public static InterlisFieldBinding bind(
      IRowMeta input,
      String stream,
      String source,
      String target,
      int targetIndex,
      IValueMeta expected,
      boolean required)
      throws HopTransformException {
    String context = stream + ": field <" + source + "> -> <" + target + ">";
    int index = find(input, source, context);
    if (index < 0 && required) throw new HopTransformException(context + " not found in the input");
    IValueMeta actual = index < 0 ? null : input.getValueMeta(index);
    if (actual != null && expected != null && actual.getType() != expected.getType())
      throw new HopTransformException(
          context + ": expected " + expected.getTypeDesc() + ", actual " + actual.getTypeDesc());
    return new InterlisFieldBinding(context, index, targetIndex, actual, null);
  }

  public static InterlisFieldBinding constant(
      String stream, String target, int targetIndex, Object value) {
    return new InterlisFieldBinding(
        stream + ": constant <" + target + ">", -1, targetIndex, null, value);
  }

  public static int find(IRowMeta input, String name, String context) throws HopTransformException {
    int index = -1;
    if (name == null || name.isBlank()) return index;
    for (int i = 0; i < input.size(); i++) {
      if (name.equalsIgnoreCase(input.getValueMeta(i).getName())) {
        if (index >= 0)
          throw new HopTransformException(
              "Ambiguous " + context + ": duplicate field <" + name + ">");
        index = i;
      }
    }
    return index;
  }

  public static void addUnique(IRowMeta output, IValueMeta field, String context)
      throws HopTransformException {
    if (field.getName() == null || field.getName().isBlank())
      throw new HopTransformException(context + ": output field name must not be blank");
    if (find(output, field.getName(), context) >= 0)
      throw new HopTransformException(
          context + ": output field <" + field.getName() + "> collides with an existing field");
    output.addValueMeta(field.clone());
  }

  public int sourceIndex() {
    return sourceIndex;
  }

  public int targetIndex() {
    return targetIndex;
  }

  public boolean present() {
    return sourceIndex >= 0;
  }

  public IValueMeta sourceMeta() {
    return sourceMeta == null ? null : sourceMeta.clone();
  }

  public Object read(Object[] row) throws HopTransformException {
    if (!present()) return constant;
    try {
      return row[sourceIndex] == null
          ? null
          : sourceMeta.convertToNormalStorageType(row[sourceIndex]);
    } catch (Exception e) {
      throw new HopTransformException(
          context + ": cannot read " + sourceMeta.getTypeDesc() + " value", e);
    }
  }
}
