package ch.so.agi.hop.interlis.transforms.buffer;

import ch.so.agi.hop.interlis.core.buffer.*;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.mapping.InterlisBasketProjectionBuffer.Entry;
import java.io.*;
import org.apache.hop.core.row.IRowMeta;

public final class BasketEntryCodec implements RecordCodec<Entry<Object[]>> {
  private final JavaRecordCodec<InterlisObjectEnvelope> envelope = new JavaRecordCodec<>();
  private final HopRowCodec row;

  public BasketEntryCodec(IRowMeta meta) {
    row = new HopRowCodec(meta);
  }

  public byte[] encode(Entry<Object[]> entry) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var out = new DataOutputStream(bytes)) {
      var object = envelope.encode(entry.envelope());
      out.writeInt(object.length);
      out.write(object);
      out.writeBoolean(entry.context() != null);
      if (entry.context() != null) {
        var context = row.encode(entry.context());
        out.writeInt(context.length);
        out.write(context);
      }
    }
    return bytes.toByteArray();
  }

  public Entry<Object[]> decode(byte[] bytes) throws Exception {
    try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      var object = envelope.decode(in.readNBytes(in.readInt()));
      return new Entry<>(object, in.readBoolean() ? row.decode(in.readNBytes(in.readInt())) : null);
    }
  }
}
