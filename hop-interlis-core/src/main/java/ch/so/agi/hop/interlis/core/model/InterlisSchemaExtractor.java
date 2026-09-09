package ch.so.agi.hop.interlis.core.model;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.LineForm;
import ch.interlis.ili2c.metamodel.LineType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts immutable {@link InterlisClassDescriptor}/{@link InterlisStructureDescriptor} views
 * from a compiled {@link TransferDescription}.
 *
 * <p>Only transfer-relevant elements are considered. Property order is the metamodel's stable
 * order; no hash-based ordering is used anywhere.
 */
public final class InterlisSchemaExtractor {

  public InterlisSchemaDescriptor extract(TransferDescription td) {
    List<InterlisClassDescriptor> classes = new ArrayList<>();
    Map<String, InterlisStructureDescriptor> structures = new LinkedHashMap<>();
    List<InterlisAssociationDescriptor> associations = new ArrayList<>();

    for (Iterator<Model> models = td.iterator();
        models.hasNext(); ) {
      Model model = models.next();
      // The predefined INTERLIS model (units, domains, TIMESYSTEMS) is not transfer data.
      if (model instanceof ch.interlis.ili2c.metamodel.PredefinedModel) {
        continue;
      }
      InterlisModelKind modelKind = InterlisModelKind.from(model);
      for (Iterator<Element> elements = model.iterator(); elements.hasNext(); ) {
        Element element = elements.next();
        if (!(element instanceof Topic topic)) {
          continue;
        }
        for (Iterator<Element> children = topic.iterator(); children.hasNext(); ) {
          Element child = children.next();
          // In ili2c concrete classes and structures are both Table instances;
          // identifiable tables are classes, non-identifiable ones are STRUCTUREs.
          if (child instanceof Table table && !table.isIdentifiable()) {
            structures.putIfAbsent(table.getScopedName(null), extractStructure(table));
          } else if (child instanceof AssociationDef association) {
            associations.add(extractAssociation(association, topic, modelKind));
          } else if (child instanceof AbstractClassDef viewable
              && !(viewable instanceof AssociationDef)) {
            classes.add(extractClass(viewable, topic, modelKind));
          }
        }
      }
    }
    registerReferencedStructures(td, classes, associations, structures);
    return new InterlisSchemaDescriptor(
        classes, new ArrayList<>(structures.values()), associations);
  }

  public InterlisClassDescriptor extractClass(AbstractClassDef viewable, Topic topic) {
    return extractClass(viewable, topic, InterlisModelKind.DATA);
  }

  private InterlisClassDescriptor extractClass(
      AbstractClassDef viewable, Topic topic, InterlisModelKind modelKind) {
    List<InterlisPropertyDescriptor> declared = extractDeclaredProperties(viewable);
    List<InterlisPropertyDescriptor> effective = new ArrayList<>(declared);

    for (Iterator<ViewableTransferElement> it = viewable.getAttributesAndRoles2();
        it.hasNext(); ) {
      ViewableTransferElement element = it.next();
      String name = propertyName(element);
      if (declared.stream().noneMatch(p -> p.name().equals(name))) {
        if (element.obj instanceof AttributeDef attribute) {
          effective.add(extractAttribute(attribute, true));
        } else if (element.obj instanceof RoleDef role) {
          effective.add(extractRole(role, true));
        }
      }
    }
    // ili2c exposes the "other side" of associations as opposide roles; collect them too.
    for (Iterator<RoleDef> it = viewable.getOpposideRoles(); it.hasNext(); ) {
      RoleDef role = it.next();
      if (effective.stream().noneMatch(p -> p.name().equals(role.getName()))) {
        effective.add(extractRole(role, true));
      }
    }

    return new InterlisClassDescriptor(
        viewable.getName(),
        viewable.getScopedName(null),
        topic.getScopedName(null),
        viewable.isAbstract(),
        declared,
        effective,
        modelKind);
  }

  public InterlisStructureDescriptor extractStructure(Table table) {
    List<InterlisAttributeDescriptor> attributes = new ArrayList<>();
    for (Iterator<ViewableTransferElement> it = table.getAttributesAndRoles2(); it.hasNext(); ) {
      ViewableTransferElement element = it.next();
      if (element.obj instanceof AttributeDef attribute) {
        attributes.add(extractAttribute(attribute, false));
      }
    }
    return new InterlisStructureDescriptor(
        table.getName(), table.getScopedName(null), attributes);
  }

  public InterlisAssociationDescriptor extractAssociation(
      AssociationDef association, Topic topic) {
    return extractAssociation(association, topic, InterlisModelKind.DATA);
  }

  private InterlisAssociationDescriptor extractAssociation(
      AssociationDef association, Topic topic, InterlisModelKind modelKind) {
    List<InterlisRoleDescriptor> roles = new ArrayList<>();
    List<InterlisAttributeDescriptor> attributes = new ArrayList<>();
    for (Iterator<ViewableTransferElement> it = association.getAttributesAndRoles2();
        it.hasNext(); ) {
      ViewableTransferElement element = it.next();
      if (element.obj instanceof RoleDef role) {
        roles.add(extractRole(role, false));
      } else if (element.obj instanceof AttributeDef attribute) {
        attributes.add(extractAttribute(attribute, false));
      }
    }
    return new InterlisAssociationDescriptor(
        association.getName(),
        association.getScopedName(null),
        topic.getScopedName(null),
        association.getOid() != null,
        roles,
        attributes,
        modelKind);
  }

  /**
   * Registers structures referenced by attributes, including structures declared in a model-level
   * DOMAIN section. ili2c exposes these structures through TransferDescription#getElement even
   * though they are not children of a Topic.
   */
  private void registerReferencedStructures(
      TransferDescription td,
      List<InterlisClassDescriptor> classes,
      List<InterlisAssociationDescriptor> associations,
      Map<String, InterlisStructureDescriptor> structures) {
    Deque<String> pending = new ArrayDeque<>();
    for (InterlisClassDescriptor descriptor : classes) {
      addStructureReferences(descriptor.effectiveProperties(), pending);
    }
    for (InterlisAssociationDescriptor descriptor : associations) {
      addStructureReferences(descriptor.attributes(), pending);
    }

    while (!pending.isEmpty()) {
      String scopedName = pending.removeFirst();
      if (scopedName == null || structures.containsKey(scopedName)) {
        continue;
      }
      Element element = td.getElement(scopedName);
      if (!(element instanceof Table table) || table.isIdentifiable()) {
        continue;
      }
      InterlisStructureDescriptor descriptor = extractStructure(table);
      structures.put(scopedName, descriptor);
      addStructureReferences(descriptor.attributes(), pending);
    }
  }

  private void addStructureReferences(
      List<? extends InterlisPropertyDescriptor> properties, Deque<String> pending) {
    for (InterlisPropertyDescriptor property : properties) {
      if (property instanceof InterlisAttributeDescriptor attribute
          && attribute.kind() == InterlisValueKind.STRUCTURE) {
        pending.addLast(attribute.structureScopedName());
      }
    }
  }

  private List<InterlisPropertyDescriptor> extractDeclaredProperties(Viewable viewable) {
    List<InterlisPropertyDescriptor> properties = new ArrayList<>();
    for (Iterator<ViewableTransferElement> it = viewable.getDefinedAttributesAndRoles2();
        it.hasNext(); ) {
      ViewableTransferElement element = it.next();
      if (element.obj instanceof AttributeDef attribute) {
        properties.add(extractAttribute(attribute, false));
      } else if (element.obj instanceof RoleDef role) {
        properties.add(extractRole(role, false));
      }
    }
    return properties;
  }

  private String propertyName(ViewableTransferElement element) {
    return element.obj instanceof AttributeDef attribute
        ? attribute.getName()
        : ((RoleDef) element.obj).getName();
  }

  private InterlisAttributeDescriptor extractAttribute(AttributeDef attribute, boolean inherited) {
    Type domain = attribute.getDomain();
    Type type = domain == null ? null : Type.findReal(domain);

    String scopedName = attribute.getScopedName(null);
    InterlisCardinality cardinality = cardinalityOf(type);
    boolean mandatory = domain != null && domain.isMandatoryConsideringAliases();
    String aliasDomainName = aliasDomainName(domain);

    LegacyGeometryMapping legacyGeometry = legacyGeometry(attribute, type, cardinality);
    if (legacyGeometry != null) {
      return geometryAttribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          inherited,
          legacyGeometry.kind(),
          legacyGeometry.dimension(),
          legacyGeometry.allowsArcs(),
          legacyGeometry.encoding());
    }

    if (type instanceof CompositionType composition) {
      Table component = composition.getComponentType();
      String structureName = component == null ? null : component.getScopedName(null);
      return attribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          InterlisValueKind.STRUCTURE,
          "STRUCTURE " + (structureName == null ? "?" : structureName),
          inherited,
          null,
          null,
          false,
          structureName,
          composition.isOrdered(),
          -1,
          -1);
    }

    if (type instanceof MultiSurfaceType) {
      return geometryAttribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          inherited,
          InterlisGeometryKind.MULTISURFACE,
          lineDimension(type),
          allowsArcs(type));
    }
    if (type instanceof SurfaceOrAreaType) {
      InterlisGeometryKind geometryKind =
          type instanceof ch.interlis.ili2c.metamodel.AreaType
              ? InterlisGeometryKind.AREA
              : InterlisGeometryKind.SURFACE;
      return geometryAttribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          inherited,
          geometryKind,
          lineDimension(type),
          allowsArcs(type));
    }
    if (type instanceof MultiPolylineType) {
      return geometryAttribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          inherited,
          InterlisGeometryKind.MULTIPOLYLINE,
          lineDimension(type),
          allowsArcs(type));
    }
    if (type instanceof PolylineType) {
      return geometryAttribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          inherited,
          InterlisGeometryKind.POLYLINE,
          lineDimension(type),
          allowsArcs(type));
    }
    if (type instanceof MultiCoordType) {
      return geometryAttribute(
          attribute, scopedName, cardinality, mandatory, inherited,
          InterlisGeometryKind.MULTICOORD,
          ((MultiCoordType) type).getDimensions() == null
              ? null
              : ((MultiCoordType) type).getDimensions().length,
          false);
    }
    if (type instanceof CoordType coordType) {
      return geometryAttribute(
          attribute, scopedName, cardinality, mandatory, inherited,
          InterlisGeometryKind.COORD,
          coordType.getDimensions() == null ? null : coordType.getDimensions().length,
          false);
    }

    if (type instanceof EnumerationType) {
      // INTERLIS BOOLEAN is modelled as a predefined enumeration; detect it on the
      // unresolved domain so the TypeAlias chain is still visible.
      if (domain != null && domain.isBoolean()) {
        return attribute(
            attribute, scopedName, cardinality, mandatory, InterlisValueKind.BOOLEAN,
            "BOOLEAN", inherited, null, null, false, null, false, -1, -1);
      }
      return attribute(
          attribute, scopedName, cardinality, mandatory, InterlisValueKind.ENUM,
          "ENUMERATION", inherited, null, null, false, null, false, -1, -1);
    }

    if (type instanceof TextType textType) {
      InterlisValueKind valueKind = textualKind(aliasDomainName);
      return attribute(
          attribute, scopedName, cardinality, mandatory, valueKind,
          valueKind + "*" + textType.getMaxLength(),
          inherited, null, null, false, null, false, textType.getMaxLength(), -1);
    }

    if (type instanceof NumericType numericType) {
      int decimalPlaces =
          Math.max(
              numericType.getMinimum() == null ? 0 : numericType.getMinimum().getAccuracy(),
              numericType.getMaximum() == null ? 0 : numericType.getMaximum().getAccuracy());
      InterlisValueKind valueKind = decimalPlaces > 0 ? InterlisValueKind.DECIMAL : InterlisValueKind.INTEGER;
      return attribute(
          attribute, scopedName, cardinality, mandatory, valueKind,
          numericType.getMinimum() + " .. " + numericType.getMaximum(),
          inherited, null, null, false, null, false, -1,
          valueKind == InterlisValueKind.DECIMAL ? decimalPlaces : -1);
    }

    if (type instanceof FormattedType formattedType) {
      InterlisValueKind valueKind = temporalKind(aliasDomainName);
      return attribute(
          attribute, scopedName, cardinality, mandatory, valueKind,
          "FORMAT " + String.valueOf(formattedType.getFormat()).trim(),
          inherited, null, null, false, null, false, -1, -1);
    }

    return attribute(
        attribute, scopedName, cardinality, mandatory, InterlisValueKind.TEXT,
        type == null ? "?" : type.getClass().getSimpleName(),
        inherited, null, null, false, null, false, -1, -1);
  }

  private InterlisValueKind textualKind(String aliasDomainName) {
    return switch (aliasDomainName) {
      case "INTERLIS.MTEXT" -> InterlisValueKind.MTEXT;
      case "INTERLIS.NAME" -> InterlisValueKind.NAME;
      case "INTERLIS.URI" -> InterlisValueKind.URI;
      default -> InterlisValueKind.TEXT;
    };
  }

  private InterlisValueKind temporalKind(String aliasDomainName) {
    return switch (aliasDomainName) {
      case "INTERLIS.XMLDate" -> InterlisValueKind.DATE;
      case "INTERLIS.XMLDateTime" -> InterlisValueKind.DATETIME;
      case "INTERLIS.XMLTime" -> InterlisValueKind.TIME;
      default -> InterlisValueKind.TEXT;
    };
  }

  private String aliasDomainName(Type domain) {
    if (domain instanceof TypeAlias alias) {
      String name = alias.getAliasing() == null ? null : alias.getAliasing().getScopedName(null);
      return name == null ? "" : name;
    }
    return "";
  }

  private InterlisAttributeDescriptor geometryAttribute(
      AttributeDef attribute,
      String scopedName,
      InterlisCardinality cardinality,
      boolean mandatory,
      boolean inherited,
      InterlisGeometryKind kind,
      Integer dimension,
      boolean allowsArcs) {
    return geometryAttribute(
        attribute,
        scopedName,
        cardinality,
        mandatory,
        inherited,
        kind,
        dimension,
        allowsArcs,
        InterlisGeometryEncoding.NATIVE);
  }

  private InterlisAttributeDescriptor geometryAttribute(
      AttributeDef attribute,
      String scopedName,
      InterlisCardinality cardinality,
      boolean mandatory,
      boolean inherited,
      InterlisGeometryKind kind,
      Integer dimension,
      boolean allowsArcs,
      InterlisGeometryEncoding encoding) {
    return attribute(
        attribute,
        scopedName,
        cardinality,
        mandatory,
        InterlisValueKind.GEOMETRY,
        kind.name() + (dimension != null ? " " + dimension + "D" : ""),
        inherited,
        kind,
        dimension,
        allowsArcs,
        null,
        false,
        -1,
        -1,
        encoding);
  }

  private InterlisAttributeDescriptor attribute(
      AttributeDef attribute,
      String scopedName,
      InterlisCardinality cardinality,
      boolean mandatory,
      InterlisValueKind kind,
      String typeName,
      boolean inherited,
      InterlisGeometryKind geometryKind,
      Integer dimension,
      boolean allowsArcs,
      String structureScopedName,
      boolean ordered,
      int textMaxLength,
      int decimalPlaces) {
    return attribute(
        attribute,
        scopedName,
        cardinality,
        mandatory,
        kind,
        typeName,
        inherited,
        geometryKind,
        dimension,
        allowsArcs,
        structureScopedName,
        ordered,
        textMaxLength,
        decimalPlaces,
        InterlisGeometryEncoding.NATIVE);
  }

  private InterlisAttributeDescriptor attribute(
      AttributeDef attribute,
      String scopedName,
      InterlisCardinality cardinality,
      boolean mandatory,
      InterlisValueKind kind,
      String typeName,
      boolean inherited,
      InterlisGeometryKind geometryKind,
      Integer dimension,
      boolean allowsArcs,
      String structureScopedName,
      boolean ordered,
      int textMaxLength,
      int decimalPlaces,
      InterlisGeometryEncoding encoding) {
    return new InterlisAttributeDescriptor(
        attribute.getName(),
        scopedName,
        cardinality,
        mandatory,
        kind,
        typeName,
        inherited,
        geometryKind,
        dimension,
        allowsArcs,
        structureScopedName,
        ordered,
        textMaxLength,
        decimalPlaces,
        encoding);
  }

  /**
   * Detects the CHBASE V1 geometry wrappers that ili2db treats as smart geometry mappings. The
   * shape is validated instead of matching names alone so user-defined structures are unaffected.
   */
  private LegacyGeometryMapping legacyGeometry(
      AttributeDef attribute, Type type, InterlisCardinality cardinality) {
    if (!(type instanceof CompositionType composition) || !cardinality.isSingleValued()) {
      return null;
    }
    Table component = composition.getComponentType();
    if (component == null) {
      return null;
    }
    Table root = (Table) component.getRootExtending();
    if (root == null) {
      root = component;
    }
    if (root.getContainer() == null
        || !"GeometryCHLV95_V1".equals(root.getContainer().getScopedName(null))) {
      return null;
    }

    String rootName = root.getName();
    if (!("MultiSurface".equals(rootName)
        || "MultiLine".equals(rootName)
        || "MultiDirectedLine".equals(rootName))) {
      return null;
    }
    AttributeDef members = singleAttribute(component);
    if (members == null
        || (!"Surfaces".equals(members.getName()) && !"Lines".equals(members.getName()))) {
      return null;
    }
    Type membersType = members.getDomain() == null ? null : Type.findReal(members.getDomain());
    if (!(membersType instanceof CompositionType membersComposition)) {
      return null;
    }
    AttributeDef leaf = singleAttribute(membersComposition.getComponentType());
    if (leaf == null) {
      return null;
    }
    Type leafType = leaf.getDomain() == null ? null : Type.findReal(leaf.getDomain());
    if ("MultiSurface".equals(rootName) && leafType instanceof SurfaceType) {
      return new LegacyGeometryMapping(
          InterlisGeometryKind.MULTISURFACE,
          lineDimension(leafType),
          allowsArcs(leafType),
          InterlisGeometryEncoding.CHLV95_V1_MULTISURFACE);
    }
    if (("MultiLine".equals(rootName) || "MultiDirectedLine".equals(rootName))
        && leafType instanceof PolylineType polyline
        && polyline.isDirected() == "MultiDirectedLine".equals(rootName)) {
      return new LegacyGeometryMapping(
          InterlisGeometryKind.MULTIPOLYLINE,
          lineDimension(leafType),
          allowsArcs(leafType),
          "MultiDirectedLine".equals(rootName)
              ? InterlisGeometryEncoding.CHLV95_V1_MULTIDIRECTED_LINE
              : InterlisGeometryEncoding.CHLV95_V1_MULTILINE);
    }
    return null;
  }

  private AttributeDef singleAttribute(Table table) {
    if (table == null) {
      return null;
    }
    AttributeDef result = null;
    int count = 0;
    for (Iterator<ViewableTransferElement> it = table.getAttributesAndRoles2(); it.hasNext(); ) {
      ViewableTransferElement element = it.next();
      count++;
      if (element.obj instanceof AttributeDef attribute) {
        result = attribute;
      }
    }
    return count == 1 ? result : null;
  }

  private record LegacyGeometryMapping(
      InterlisGeometryKind kind,
      Integer dimension,
      boolean allowsArcs,
      InterlisGeometryEncoding encoding) {}

  private InterlisRoleDescriptor extractRole(RoleDef role, boolean inherited) {
    Viewable destination = role.getDestination();
    return new InterlisRoleDescriptor(
        role.getName(),
        role.getScopedName(null),
        cardinalityOf(role.getCardinality()),
        inherited,
        destination == null ? null : destination.getScopedName(null),
        role.isOrdered(),
        associationOf(role));
  }

  /** Derives the association a role belongs to from its scoped name. */
  private String associationOf(RoleDef role) {
    String scopedName = role.getScopedName(null);
    if (scopedName == null) {
      return null;
    }
    int separator = scopedName.lastIndexOf('.');
    return separator < 0 ? null : scopedName.substring(0, separator);
  }

  private InterlisCardinality cardinalityOf(Type type) {
    if (type == null) {
      return new InterlisCardinality(1, 1);
    }
    return cardinalityOf(type.getCardinality());
  }

  private InterlisCardinality cardinalityOf(Cardinality cardinality) {
    if (cardinality == null) {
      return new InterlisCardinality(1, 1);
    }
    long max = cardinality.getMaximum();
    long min = cardinality.getMinimum();
    if (min > Integer.MAX_VALUE) {
      min = Integer.MAX_VALUE;
    }
    return new InterlisCardinality(
        (int) min,
        max == Cardinality.UNBOUND ? InterlisCardinality.UNBOUNDED : max);
  }

  private Integer lineDimension(Type type) {
    if (!(type instanceof LineType line) || line.getControlPointDomain() == null) return null;
    Type coord = Type.findReal(line.getControlPointDomain().getType());
    if (coord instanceof CoordType coordinate && coordinate.getDimensions() != null) {
      return coordinate.getDimensions().length;
    }
    return null;
  }

  private boolean allowsArcs(Type type) {
    if (!(type instanceof LineType lineType)) {
      return false;
    }
    LineForm[] forms = lineType.getLineForms();
    if (forms == null) {
      return false;
    }
    for (LineForm form : forms) {
      if ("ARCS".equalsIgnoreCase(form.getName())) {
        return true;
      }
    }
    return false;
  }
}
