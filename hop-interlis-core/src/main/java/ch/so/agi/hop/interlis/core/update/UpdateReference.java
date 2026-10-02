package ch.so.agi.hop.interlis.core.update;

import ch.interlis.iom.IomObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Versioned provenance, not a persistent identity. Decoding never uses Java deserialization. */
public record UpdateReference(
    String className, String bid, String tid, String path, int index, String fingerprint)
    implements Serializable {
  public static final String FIELD = "_ili_update_ref";

  public UpdateReference {
    if (className == null
        || className.isBlank()
        || bid == null
        || bid.isBlank()
        || tid == null
        || tid.isBlank()
        || path == null
        || path.isBlank()
        || index < 0
        || fingerprint == null
        || !fingerprint.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid INTERLIS structure update reference");
  }

  public String encode() {
    try {
      var bytes = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(bytes)) {
        out.writeInt(1);
        for (String value : List.of(className, bid, tid, path, fingerprint)) out.writeUTF(value);
        out.writeInt(index);
      }
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot encode update reference", e);
    }
  }

  public static UpdateReference decode(String value) {
    try {
      if (value == null || value.length() > 65536)
        throw new IOException("Missing or oversized reference");
      try (var in =
          new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(value)))) {
        if (in.readInt() != 1) throw new IOException("Unsupported reference version");
        String cls = in.readUTF(),
            bid = in.readUTF(),
            tid = in.readUTF(),
            path = in.readUTF(),
            hash = in.readUTF();
        var ref = new UpdateReference(cls, bid, tid, path, in.readInt(), hash);
        if (in.read() != -1) throw new IOException("Trailing data");
        return ref;
      }
    } catch (IOException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid INTERLIS structure update reference: " + e.getMessage(), e);
    }
  }

  /** Attribute names are sorted; repeated values retain their original occurrence order. */
  public static String fingerprint(IomObject object) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      try (var out =
          new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
        hashObject(out, object);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void text(DataOutputStream out, String value) throws IOException {
    if (value == null) {
      out.writeInt(-1);
      return;
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  private static void hashObject(DataOutputStream out, IomObject object) throws IOException {
    text(out, object.getobjecttag());
    text(out, object.getobjectoid());
    text(out, object.getobjectrefoid());
    text(out, object.getobjectrefbid());
    out.writeLong(object.getobjectreforderpos());
    out.writeInt(object.getobjectoperation());
    out.writeInt(object.getobjectconsistency());
    var attrs = new ArrayList<String>();
    for (int i = 0; i < object.getattrcount(); i++) attrs.add(object.getattrname(i));
    Collections.sort(attrs);
    out.writeInt(attrs.size());
    for (String attr : attrs) {
      text(out, attr);
      int count = object.getattrvaluecount(attr);
      out.writeInt(count);
      for (int i = 0; i < count; i++) {
        var child = object.getattrobj(attr, i);
        out.writeBoolean(child != null);
        if (child != null) hashObject(out, child);
        else text(out, object.getattrprim(attr, i));
      }
    }
  }

  public static String key(String... parts) {
    var result = new StringBuilder();
    for (String part : parts) {
      if (part == null) throw new IllegalArgumentException("Null INTERLIS identity");
      result.append(part.length()).append(':').append(part);
    }
    return result.toString();
  }
}
