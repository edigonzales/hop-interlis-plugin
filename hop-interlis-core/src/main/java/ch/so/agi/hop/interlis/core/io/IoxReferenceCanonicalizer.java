package ch.so.agi.hop.interlis.core.io;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import java.util.*;

/**
 * Repairs the specific duplicate REF created by the pinned 2.4 reader for a standalone association
 * role with ili:bid. Attribute collections are never changed. Model analysis is performed once,
 * before reading any objects.
 */
final class IoxReferenceCanonicalizer {
  private final Map<String, List<String>> roles = new HashMap<>();

  IoxReferenceCanonicalizer(TransferDescription model) {
    if (model != null && "2.4".equals(model.getLastModel().getIliVersion()))
      for (var association : new InterlisSchemaExtractor().extract(model).associations())
        roles.put(
            association.scopedName(),
            association.roles().stream().map(role -> role.name()).toList());
  }

  IomObject repair(IomObject object) {
    if (object == null) return null;
    var names = roles.get(object.getobjecttag());
    if (names == null) return object;
    Iom_jObject copy = null;
    for (var name : names) {
      if (object.getattrvaluecount(name) != 2) continue;
      var first = object.getattrobj(name, 0);
      var extra = object.getattrobj(name, 1);
      if (first == null
          || extra == null
          || first.getobjectrefoid() == null
          || first.getobjectrefbid() == null
          || extra.getobjectrefoid() != null
          || !Objects.equals(first.getobjectrefbid(), extra.getobjectrefbid())
          || first.getattrcount() != 0
          || extra.getattrcount() != 0
          || !"REF".equals(first.getobjecttag())
          || !"REF".equals(extra.getobjecttag())) continue;
      if (copy == null) copy = new Iom_jObject(object);
      IomObject reference = copy.getattrobj(name, 0);
      copy.setattrundefined(name);
      copy.addattrobj(name, reference);
    }
    return copy == null ? object : copy;
  }

  IoxEvent repair(IoxEvent event) {
    if (!(event instanceof ch.interlis.iox.ObjectEvent objectEvent)) return event;
    var object = repair(objectEvent.getIomObject());
    return object == objectEvent.getIomObject() ? event : new ObjectEvent(object);
  }
}
