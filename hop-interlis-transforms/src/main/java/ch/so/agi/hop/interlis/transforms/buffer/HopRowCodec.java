package ch.so.agi.hop.interlis.transforms.buffer;

import ch.so.agi.hop.interlis.core.buffer.RecordCodec;
import java.io.*;
import org.apache.hop.core.row.IRowMeta;

/** Uses each registered value type's binary codec, including curves and IOM carriers. */
public final class HopRowCodec implements RecordCodec<Object[]> {
  private final IRowMeta meta;

  public HopRowCodec(IRowMeta meta) {
    this.meta = meta.clone();
  }

  public byte[] encode(Object[] row) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var out = new DataOutputStream(bytes)) {
      meta.writeData(out, row);
    }
    return bytes.toByteArray();
  }

  public Object[] decode(byte[] bytes) throws Exception {
    try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      return meta.readData(in);
    }
  }
}
