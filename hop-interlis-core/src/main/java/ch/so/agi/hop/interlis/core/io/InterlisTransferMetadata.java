package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom_j.xtf.XtfStartTransferEvent;
import ch.interlis.iox_j.StartTransferEvent;
import java.util.*;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

/** Versioned, immutable header semantics; JSON uses Hop's existing json-simple runtime. */
public record InterlisTransferMetadata(
    String sender,
    String comment,
    String xtfVersion,
    List<ModelEntry> models,
    List<OidSpace> oidSpaces,
    List<String> unsupportedHeaders) {
  public record ModelEntry(String name, String uri, String version) {}

  public record OidSpace(String name, String domain) {}

  public InterlisTransferMetadata {
    models = models == null ? List.of() : List.copyOf(models);
    oidSpaces = oidSpaces == null ? List.of() : List.copyOf(oidSpaces);
    unsupportedHeaders = unsupportedHeaders == null ? List.of() : List.copyOf(unsupportedHeaders);
  }

  public static InterlisTransferMetadata fromEvent(StartTransferEvent event) {
    var models = new ArrayList<ModelEntry>();
    var spaces = new ArrayList<OidSpace>();
    var unsupported = new ArrayList<String>();
    if (event instanceof XtfStartTransferEvent xtf) {
      if (xtf.getHeaderObjects() != null)
        for (var object : xtf.getHeaderObjects().values()) {
          if ("iom04.metamodel.ModelEntry".equals(object.getobjecttag())) {
            models.add(
                new ModelEntry(
                    object.getattrvalue("model"),
                    object.getattrvalue("uri"),
                    object.getattrvalue("version")));
          } else unsupported.add(object.getobjecttag());
        }
      for (var space : xtf.getOidSpaces())
        spaces.add(new OidSpace(space.getName(), space.getOiddomain()));
      if (!spaces.isEmpty())
        unsupported.add("Original OID-space names: iox-ili 1.24.4 replaces names during reading");
    }
    return new InterlisTransferMetadata(
        event.getSender(), event.getComment(), event.getVersion(), models, spaces, unsupported);
  }

  public String toJson() {
    var values = new LinkedHashMap<String, Object>();
    values.put("schemaVersion", 1);
    values.put("sender", sender);
    values.put("comment", comment);
    values.put("xtfVersion", xtfVersion);
    values.put(
        "models", models.stream().map(m -> Arrays.asList(m.name(), m.uri(), m.version())).toList());
    values.put("oidSpaces", oidSpaces.stream().map(o -> List.of(o.name(), o.domain())).toList());
    values.put("unsupportedHeaders", unsupportedHeaders);
    return JSONValue.toJSONString(values);
  }

  public static InterlisTransferMetadata fromJson(String json) {
    if (json == null || json.isBlank()) return null;
    try {
      var values = (Map<?, ?>) new JSONParser().parse(json);
      if (!Long.valueOf(1).equals(values.get("schemaVersion")))
        throw new IllegalArgumentException("Unsupported metadata schemaVersion");
      var models = new ArrayList<ModelEntry>();
      for (var raw : (List<?>) values.get("models")) {
        var m = (List<?>) raw;
        if (m.size() != 3)
          throw new IllegalArgumentException("Model entry must contain name, URI and version");
        models.add(new ModelEntry((String) m.get(0), (String) m.get(1), (String) m.get(2)));
      }
      var spaces = new ArrayList<OidSpace>();
      for (var raw : (List<?>) values.get("oidSpaces")) {
        var o = (List<?>) raw;
        if (o.size() != 2)
          throw new IllegalArgumentException("OID space must contain name and domain");
        spaces.add(new OidSpace((String) o.get(0), (String) o.get(1)));
      }
      var unsupported =
          ((List<?>) values.get("unsupportedHeaders")).stream().map(String.class::cast).toList();
      return new InterlisTransferMetadata(
          (String) values.get("sender"),
          (String) values.get("comment"),
          (String) values.get("xtfVersion"),
          models,
          spaces,
          unsupported);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid _ili_transfer_metadata: " + e.getMessage(), e);
    }
  }
}
