package ch.so.agi.hop.interlis.transforms.value;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaPlugin;

/**
 * Hop value type carrying a raw INTERLIS {@link IomObject}.
 *
 * <p>Used for the technical {@code _ili_source_object} carrier field: INTERLIS Input keeps the
 * source object when multi-valued structures are to be exploded downstream, INTERLIS Structure
 * Collect updates it and INTERLIS Output overlays the row values onto it. The field is a hidden
 * implementation detail of the structure pipeline, not a user-facing data type.
 *
 * <p>{@link #getString(Object)} renders the IOM object as XML for debug/preview only; there is no
 * lossless string conversion back to an object, on purpose.
 */
@ValueMetaPlugin(
    id = "" + ValueMetaInterlisObject.TYPE_INTERLIS_OBJECT,
    name = "InterlisObject",
    description = "A raw INTERLIS IOM object",
    classLoaderGroup = "sogeo-geometry")
public final class ValueMetaInterlisObject extends ValueMetaBase {

  /** Type code: "INTERLIS" on a phone keypad. */
  public static final int TYPE_INTERLIS_OBJECT = 46837547;

  public ValueMetaInterlisObject() {
    this(null);
  }

  public ValueMetaInterlisObject(String name) {
    super(name, TYPE_INTERLIS_OBJECT);
  }

  @Override
  public ValueMetaInterlisObject clone() {
    return (ValueMetaInterlisObject) super.clone();
  }

  @Override
  public Object cloneValueData(Object object) throws HopValueException {
    if (object == null) {
      return null;
    }
    if (!(object instanceof IomObject iomObject)) {
      throw new HopValueException(
          "Expected an IomObject value but got " + object.getClass().getName());
    }
    return new Iom_jObject(iomObject);
  }

  @Override
  public String getString(Object object) throws HopValueException {
    if (object == null) {
      return null;
    }
    if (!(object instanceof IomObject iomObject)) {
      throw new HopValueException(
          "Expected an IomObject value but got " + object.getClass().getName());
    }
    return iomObject.toString();
  }

  @Override
  public Object getNativeDataType(Object object) throws HopValueException {
    return object instanceof IomObject ? object : null;
  }

  @Override
  public String getTypeDesc() {
    return "InterlisObject";
  }

  @Override
  public void writeData(DataOutputStream outputStream, Object object) throws HopFileException {
    try {
      if (object == null) {
        outputStream.writeInt(-1);
        return;
      }
      if (!(object instanceof IomObject iomObject)) {
        throw new HopFileException(
            "Expected an IomObject value but got " + object.getClass().getName());
      }
      java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
      try (ObjectOutputStream oos = new ObjectOutputStream(buffer)) {
        oos.writeObject(iomObject);
      }
      byte[] bytes = buffer.toByteArray();
      outputStream.writeInt(bytes.length);
      outputStream.write(bytes);
    } catch (IOException e) {
      throw new HopFileException("Failed to serialize INTERLIS object: " + e.getMessage(), e);
    }
  }

  @Override
  public Object readData(DataInputStream inputStream) throws HopFileException {
    try {
      int length = inputStream.readInt();
      if (length < 0) {
        return null;
      }
      byte[] bytes = new byte[length];
      inputStream.readFully(bytes);
      try (ObjectInputStream ois =
          new ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
        return ois.readObject();
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new HopFileException("Failed to deserialize INTERLIS object: " + e.getMessage(), e);
    }
  }
}
