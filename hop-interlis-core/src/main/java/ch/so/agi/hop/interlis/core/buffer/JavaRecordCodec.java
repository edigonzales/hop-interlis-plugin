package ch.so.agi.hop.interlis.core.buffer;

import java.io.*;

/** For locally generated IOM/metadata records only; a fresh stream avoids object retention. */
public final class JavaRecordCodec<T> implements RecordCodec<T> {
  public byte[] encode(T value) throws IOException {
    var bytes = new ByteArrayOutputStream();
    try (var out = new ObjectOutputStream(bytes)) {
      out.writeObject(value);
    }
    return bytes.toByteArray();
  }

  @SuppressWarnings("unchecked")
  public T decode(byte[] bytes) throws Exception {
    try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return (T) in.readObject();
    }
  }
}
