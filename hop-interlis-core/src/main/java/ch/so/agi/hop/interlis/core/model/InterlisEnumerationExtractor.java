package ch.so.agi.hop.interlis.core.model;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts the enumeration values of a compiled model into flat rows.
 *
 * <p>Enumeration types are discovered through the attribute domains of classes and structures
 * plus the DOMAIN aliases declared in topics; the same type is listed once (first definition in
 * model order wins). Sub-enumerations are flattened recursively: each value gets its scoped
 * path, its parent, its depth and whether it is a leaf.
 */
public final class InterlisEnumerationExtractor {

  /** All enumeration values of the model in model order. */
  public List<InterlisEnumerationRow> extract(CompiledInterlisModel model) {
    Map<EnumerationType, String> definitions = new LinkedHashMap<>();
    List<InterlisEnumerationRow> rows = new ArrayList<>();

    for (Iterator<ch.interlis.ili2c.metamodel.Model> models =
            model.transferDescription().iterator();
        models.hasNext(); ) {
      ch.interlis.ili2c.metamodel.Model currentModel = models.next();
      if (currentModel instanceof ch.interlis.ili2c.metamodel.PredefinedModel) {
        continue;
      }
      for (Iterator<Element> elements = currentModel.iterator(); elements.hasNext(); ) {
        Element element = elements.next();
        if (!(element instanceof Topic topic)) {
          continue;
        }
        for (Iterator<Element> children = topic.iterator(); children.hasNext(); ) {
          Element child = children.next();
          if (child instanceof ch.interlis.ili2c.metamodel.Domain domain) {
            // A DOMAIN declaration may alias an enumeration type.
            Type real = domain.getType() == null ? null : Type.findReal(domain.getType());
            if (real instanceof EnumerationType enumerationType) {
              definitions.putIfAbsent(enumerationType, child.getScopedName(null));
            }
          } else if (child instanceof Table table) {
            collectFromAttributes(table, definitions);
          }
        }
      }
    }

    for (Map.Entry<EnumerationType, String> entry : definitions.entrySet()) {
      flatten(rows, entry.getValue(), entry.getKey().getConsolidatedEnumeration());
    }
    return rows;
  }

  private void collectFromAttributes(
      Table table, Map<EnumerationType, String> definitions) {
    for (Iterator<ViewableTransferElement> attributes = table.getAttributesAndRoles2();
        attributes.hasNext(); ) {
      ViewableTransferElement element = attributes.next();
      if (!(element.obj instanceof AttributeDef attribute)) {
        continue;
      }
      Type domain = attribute.getDomain();
      Type real = domain == null ? null : Type.findReal(domain);
      if (real instanceof EnumerationType enumerationType) {
        definitions.putIfAbsent(
            enumerationType,
            attribute.getScopedName(null) != null
                ? attribute.getScopedName(null)
                : table.getScopedName(null) + "." + attribute.getName());
      }
    }
  }

  private void flatten(
      List<InterlisEnumerationRow> rows, String definition, Enumeration enumeration) {
    if (enumeration == null) {
      return;
    }
    for (Iterator<Enumeration.Element> elements = enumeration.getElements();
        elements.hasNext(); ) {
      Enumeration.Element element = elements.next();
      flattenElement(rows, definition, definition + "." + element.getName(), element, null, 0);
    }
  }

  private void flattenElement(
      List<InterlisEnumerationRow> rows,
      String definition,
      String path,
      Enumeration.Element element,
      String parentValue,
      int depth) {
    Enumeration subEnumeration = element.getSubEnumeration();
    boolean isLeaf = subEnumeration == null || subEnumeration.size() == 0;
    rows.add(
        new InterlisEnumerationRow(
            definition, element.getName(), path, parentValue, depth, isLeaf));
    if (!isLeaf) {
      for (Iterator<Enumeration.Element> elements = subEnumeration.getElements();
          elements.hasNext(); ) {
        Enumeration.Element child = elements.next();
        flattenElement(
            rows, definition, path + "." + child.getName(), child, element.getName(), depth + 1);
      }
    }
  }
}
