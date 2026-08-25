package ch.so.agi.hop.interlis.core.model;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.LineForm;
import ch.interlis.ili2c.metamodel.LineType;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
    List<InterlisStructureDescriptor> structures = new ArrayList<>();

    for (Iterator<ch.interlis.ili2c.metamodel.Model> models = td.iterator();
        models.hasNext(); ) {
      ch.interlis.ili2c.metamodel.Model model = models.next();
      // The predefined INTERLIS model (units, domains, TIMESYSTEMS) is not transfer data.
      if (model instanceof ch.interlis.ili2c.metamodel.PredefinedModel) {
        continue;
      }
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
            structures.add(extractStructure(table));
          } else if (child instanceof AbstractClassDef viewable
              && !(viewable instanceof AssociationDef)) {
            classes.add(extractClass(viewable, topic));
          }
        }
      }
    }
    return new InterlisSchemaDescriptor(classes, structures);
  }

  public InterlisClassDescriptor extractClass(AbstractClassDef viewable, Topic topic) {
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

    return new InterlisClassDescriptor(
        viewable.getName(),
        viewable.getScopedName(null),
        topic.getScopedName(null),
        viewable.isAbstract(),
        declared,
        effective);
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

    if (type instanceof CompositionType composition) {
      Table component = composition.getComponentType();
      String structureName =
          component == null ? null : component.getScopedName(null);
      return new InterlisAttributeDescriptor(
          attribute.getName(),
          scopedName,
          cardinality,
          mandatory,
          InterlisAttributeKind.STRUCTURE,
          "STRUCTURE " + (structureName == null ? "?" : structureName),
          inherited,
          null,
          null,
          false,
          structureName);
    }

    if (type instanceof MultiSurfaceType) {
      return geometryAttribute(
          attribute, scopedName, cardinality, mandatory, inherited, InterlisGeometryKind.MULTISURFACE, null, allowsArcs(type));
    }
    if (type instanceof SurfaceOrAreaType) {
      InterlisGeometryKind kind =
          type instanceof ch.interlis.ili2c.metamodel.AreaType
              ? InterlisGeometryKind.AREA
              : InterlisGeometryKind.SURFACE;
      return geometryAttribute(
          attribute, scopedName, cardinality, mandatory, inherited, kind, null, allowsArcs(type));
    }
    if (type instanceof MultiPolylineType) {
      return geometryAttribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          inherited,
          InterlisGeometryKind.MULTIPOLYLINE,
          null,
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
          null,
          allowsArcs(type));
    }
    if (type instanceof MultiCoordType) {
      return geometryAttribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          inherited,
          InterlisGeometryKind.MULTICOORD,
          null,
          false);
    }
    if (type instanceof CoordType coordType) {
      return geometryAttribute(
          attribute,
          scopedName,
          cardinality,
          mandatory,
          inherited,
          InterlisGeometryKind.COORD,
          coordType.getDimensions() == null ? null : coordType.getDimensions().length,
          false);
    }

    if (type instanceof EnumerationType) {
      // INTERLIS BOOLEAN is modelled as a predefined enumeration; detect it on the
      // unresolved domain so the TypeAlias chain is still visible.
      if (domain != null && domain.isBoolean()) {
        return new InterlisAttributeDescriptor(
            attribute.getName(),
            scopedName,
            cardinality,
            mandatory,
            InterlisAttributeKind.PRIMITIVE,
            "BOOLEAN",
            inherited,
            null,
            null,
            false,
            null);
      }
      EnumerationType enumerationType = (EnumerationType) type;
      return new InterlisAttributeDescriptor(
          attribute.getName(),
          scopedName,
          cardinality,
          mandatory,
          InterlisAttributeKind.ENUM,
          "ENUMERATION",
          inherited,
          null,
          null,
          false,
          null);
    }

    String typeName = formatPrimitiveType(type, domain);
    return new InterlisAttributeDescriptor(
        attribute.getName(),
        scopedName,
        cardinality,
        mandatory,
        InterlisAttributeKind.PRIMITIVE,
        typeName,
        inherited,
        null,
        null,
        false,
        null);
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
    return new InterlisAttributeDescriptor(
        attribute.getName(),
        scopedName,
        cardinality,
        mandatory,
        InterlisAttributeKind.GEOMETRY,
        kind.name() + (dimension != null ? " " + dimension + "D" : ""),
        inherited,
        kind,
        dimension,
        allowsArcs,
        null);
  }

  private InterlisRoleDescriptor extractRole(RoleDef role, boolean inherited) {
    Viewable destination = role.getDestination();
    return new InterlisRoleDescriptor(
        role.getName(),
        role.getScopedName(null),
        cardinalityOf(role.getCardinality()),
        inherited,
        destination == null ? null : destination.getScopedName(null),
        role.isOrdered());
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

  private String formatPrimitiveType(Type type, Type domain) {
    if (type == null) {
      return "?";
    }
    if (type instanceof TextType textType) {
      return "TEXT*" + textType.getMaxLength();
    }
    if (type instanceof NumericType numericType) {
      return numericType.getMinimum() + " .. " + numericType.getMaximum();
    }
    if (type instanceof ch.interlis.ili2c.metamodel.FormattedType formattedType) {
      String format = formattedType.getFormat();
      return format == null || format.isBlank() ? "FORMATTED" : "FORMAT " + format.trim();
    }
    return type.getClass().getSimpleName();
  }
}
