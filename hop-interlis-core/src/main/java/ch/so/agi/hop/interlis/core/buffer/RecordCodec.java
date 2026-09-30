package ch.so.agi.hop.interlis.core.buffer;

/** Private spill representation; never a transfer or a public interchange format. */
public interface RecordCodec<T> {
  byte[] encode(T value) throws Exception;

  T decode(byte[] bytes) throws Exception;
}
