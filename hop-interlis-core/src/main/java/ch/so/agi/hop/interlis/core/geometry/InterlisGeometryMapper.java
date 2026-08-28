package ch.so.agi.hop.interlis.core.geometry;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox_j.wkb.Iox2wkb;
import ch.interlis.iox_j.wkb.Wkb2iox;
import ch.so.agi.hop.interlis.core.model.InterlisGeometryEncoding;
import ch.so.agi.hop.interlis.core.model.InterlisGeometryKind;
import com.atolcd.hop.gis.geometry.curve.CircularString;
import com.atolcd.hop.gis.geometry.curve.CompoundCurve;
import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import com.atolcd.hop.gis.geometry.curve.CurvePolygon;
import com.atolcd.hop.gis.geometry.curve.MultiCurve;
import com.atolcd.hop.gis.geometry.curve.MultiSurface;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

/**
 * Converts between INTERLIS IOM geometries and the shared Hop geometry value type.
 *
 * <p>The bridge goes through SQL/MM WKB so that circular arcs are preserved:
 *
 * <pre>
 * IomObject -&gt; Iox2wkb (asCompoundCurve/asCurvePolygon) -&gt; SQL/MM WKB -&gt; Hop Geometry
 * Hop Geometry -&gt; SQL/MM WKB -&gt; Wkb2iox -&gt; IomObject
 * </pre>
 *
 * <p>Arcs are never stroked: the stroke tolerance passed to iox-ili is {@code 0.0}, which keeps
 * ARC segments as circular strings. Straight-only compound geometries are normalized to their
 * plain JTS counterparts ({@link LineString}, {@link Polygon}); this loses no information because
 * no curve is involved.
 */
public final class InterlisGeometryMapper {

  private static final String TAG_COORD = "COORD";
  private static final String TAG_MULTICOORD = "MULTICOORD";
  private static final String TAG_POLYLINE = "POLYLINE";
  private static final String TAG_MULTIPOLYLINE = "MULTIPOLYLINE";
  private static final String TAG_SURFACE = "SURFACE";
  private static final String TAG_MULTISURFACE = "MULTISURFACE";

  /**
   * Converts an INTERLIS geometry structure to a Hop geometry value.
   *
   * @param geometry the IOM geometry object, may be {@code null}
   * @param kind expected INTERLIS geometry kind
   * @param dimension coordinate dimension (2 or 3)
   * @return the Hop geometry or {@code null} if {@code geometry} was {@code null}
   */
  public Geometry toHopGeometry(IomObject geometry, InterlisGeometryKind kind, int dimension)
      throws InterlisGeometryException {
    return toHopGeometry(geometry, kind, dimension, InterlisGeometryEncoding.NATIVE);
  }

  /** Converts an IOM geometry using its model-specific representation. */
  public Geometry toHopGeometry(
      IomObject geometry,
      InterlisGeometryKind kind,
      int dimension,
      InterlisGeometryEncoding encoding)
      throws InterlisGeometryException {
    if (geometry == null) {
      return null;
    }
    if (dimension != 2 && dimension != 3) {
      throw new InterlisGeometryException(
          "Unsupported coordinate dimension " + dimension + " for geometry kind " + kind);
    }

    try {
      if (encoding != InterlisGeometryEncoding.NATIVE) {
        return toHopLegacyMultiGeometry(geometry, kind, dimension, encoding);
      }
      Iox2wkb converter = new Iox2wkb(dimension, ByteOrder.BIG_ENDIAN, true);
      byte[] wkb =
          switch (kind) {
            case COORD -> converter.coord2wkb(geometry);
            case MULTICOORD -> converter.multicoord2wkb(geometry);
            case POLYLINE -> converter.polyline2wkb(geometry, false, true, 0.0);
            case MULTIPOLYLINE -> converter.multiline2wkb(geometry, true, 0.0);
            case SURFACE, AREA -> converter.surface2wkb(geometry, true, 0.0);
            case MULTISURFACE -> converter.multisurface2wkb(geometry, true, 0.0);
          };
      Geometry hopGeometry = CurveGeometrySupport.readWkb(wkb);
      return normalizeStraightOnly(hopGeometry);
    } catch (Exception e) {
      throw new InterlisGeometryException(
          "Failed to convert INTERLIS " + kind + " geometry to Hop geometry: " + e.getMessage(),
          e);
    }
  }

  /**
   * Converts a Hop geometry value back to an INTERLIS IOM geometry structure.
   *
   * @param geometry the Hop geometry, may be {@code null}
   * @param kind expected INTERLIS geometry kind
   * @param dimension coordinate dimension (2 or 3)
   * @return the IOM geometry or {@code null} if {@code geometry} was {@code null}
   */
  public IomObject toIomGeometry(Geometry geometry, InterlisGeometryKind kind, int dimension)
      throws InterlisGeometryException {
    return toIomGeometry(geometry, kind, dimension, InterlisGeometryEncoding.NATIVE);
  }

  /** Converts a Hop geometry to the model-specific IOM representation. */
  public IomObject toIomGeometry(
      Geometry geometry,
      InterlisGeometryKind kind,
      int dimension,
      InterlisGeometryEncoding encoding)
      throws InterlisGeometryException {
    if (geometry == null) {
      return null;
    }

    try {
      if (encoding != InterlisGeometryEncoding.NATIVE) {
        return toIomLegacyMultiGeometry(geometry, kind, dimension, encoding);
      }
      byte[] wkb;
      if (CurveGeometrySupport.isCurveGeometry(geometry)) {
        if (hasZ(geometry)) {
          throw new InterlisGeometryException(
              "3D curve geometries cannot be written to SQL/MM WKB yet "
                  + "(not supported by hop-geometry-type-plugin)");
        }
        wkb = CurveGeometrySupport.writeWkb(geometry);
      } else {
        boolean includeSrid = geometry.getSRID() != 0;
        int outputDimension = dimension == 3 ? 3 : 2;
        wkb =
            new org.locationtech.jts.io.WKBWriter(
                    outputDimension, org.locationtech.jts.io.ByteOrderValues.BIG_ENDIAN, includeSrid)
                .write(geometry);
      }
      IomObject iomGeometry = new Wkb2iox().read(wkb);
      validateKind(iomGeometry, kind);
      return iomGeometry;
    } catch (InterlisGeometryException e) {
      throw e;
    } catch (Exception e) {
      throw new InterlisGeometryException(
          "Failed to convert Hop geometry to INTERLIS " + kind + ": " + e.getMessage(), e);
    }
  }

  private Geometry toHopLegacyMultiGeometry(
      IomObject wrapper,
      InterlisGeometryKind kind,
      int dimension,
      InterlisGeometryEncoding encoding)
      throws InterlisGeometryException {
    if (kind == InterlisGeometryKind.MULTISURFACE
        && encoding == InterlisGeometryEncoding.CHLV95_V1_MULTISURFACE) {
      List<Polygon> polygons = new ArrayList<>();
      for (int i = 0; i < wrapper.getattrvaluecount("Surfaces"); i++) {
        IomObject surfaceStructure = wrapper.getattrobj("Surfaces", i);
        if (surfaceStructure == null) {
          throw new InterlisGeometryException("CHLV95_V1 MultiSurface contains a null surface");
        }
        IomObject surface = surfaceStructure.getattrobj("Surface", 0);
        Geometry converted = toHopGeometry(surface, InterlisGeometryKind.SURFACE, dimension);
        if (!(converted instanceof Polygon polygon)) {
          throw new InterlisGeometryException(
              "CHLV95_V1 MultiSurface member did not convert to a polygon: "
                  + (converted == null ? "null" : converted.getGeometryType()));
        }
        polygons.add(polygon);
      }
      if (polygons.isEmpty()) {
        throw new InterlisGeometryException("CHLV95_V1 MultiSurface contains no surfaces");
      }
      GeometryFactory factory = polygons.get(0).getFactory();
      MultiSurface result = new MultiSurface(polygons, factory);
      result.setSRID(polygons.get(0).getSRID());
      return normalizeStraightOnly(result);
    }

    if ((kind == InterlisGeometryKind.MULTIPOLYLINE
            && (encoding == InterlisGeometryEncoding.CHLV95_V1_MULTILINE
                || encoding == InterlisGeometryEncoding.CHLV95_V1_MULTIDIRECTED_LINE))) {
      List<LineString> lines = new ArrayList<>();
      for (int i = 0; i < wrapper.getattrvaluecount("Lines"); i++) {
        IomObject lineStructure = wrapper.getattrobj("Lines", i);
        if (lineStructure == null) {
          throw new InterlisGeometryException("CHLV95_V1 MultiLine contains a null line");
        }
        IomObject line = lineStructure.getattrobj("Line", 0);
        Geometry converted = toHopGeometry(line, InterlisGeometryKind.POLYLINE, dimension);
        if (!(converted instanceof LineString lineString)) {
          throw new InterlisGeometryException(
              "CHLV95_V1 MultiLine member did not convert to a line: "
                  + (converted == null ? "null" : converted.getGeometryType()));
        }
        lines.add(lineString);
      }
      if (lines.isEmpty()) {
        throw new InterlisGeometryException("CHLV95_V1 MultiLine contains no lines");
      }
      GeometryFactory factory = lines.get(0).getFactory();
      MultiCurve result = new MultiCurve(lines, factory);
      result.setSRID(lines.get(0).getSRID());
      return result;
    }

    throw new InterlisGeometryException(
        "Unsupported legacy geometry encoding " + encoding + " for geometry kind " + kind);
  }

  private IomObject toIomLegacyMultiGeometry(
      Geometry geometry,
      InterlisGeometryKind kind,
      int dimension,
      InterlisGeometryEncoding encoding)
      throws InterlisGeometryException {
    if (kind == InterlisGeometryKind.MULTISURFACE
        && encoding == InterlisGeometryEncoding.CHLV95_V1_MULTISURFACE) {
      Iom_jObject wrapper = new Iom_jObject("GeometryCHLV95_V1.MultiSurface", null);
      int count = geometry instanceof MultiPolygon ? geometry.getNumGeometries() : 1;
      for (int i = 0; i < count; i++) {
        Geometry component = geometry instanceof MultiPolygon ? geometry.getGeometryN(i) : geometry;
        if (!(component instanceof Polygon)) {
          throw new InterlisGeometryException(
              "CHLV95_V1 MultiSurface requires polygon components, got "
                  + component.getGeometryType());
        }
        Iom_jObject surfaceStructure =
            new Iom_jObject("GeometryCHLV95_V1.SurfaceStructure", null);
        surfaceStructure.addattrobj(
            "Surface", toIomGeometry(component, InterlisGeometryKind.SURFACE, dimension));
        wrapper.addattrobj("Surfaces", surfaceStructure);
      }
      return wrapper;
    }

    if ((kind == InterlisGeometryKind.MULTIPOLYLINE
            && (encoding == InterlisGeometryEncoding.CHLV95_V1_MULTILINE
                || encoding == InterlisGeometryEncoding.CHLV95_V1_MULTIDIRECTED_LINE))) {
      Iom_jObject wrapper =
          new Iom_jObject(
              encoding == InterlisGeometryEncoding.CHLV95_V1_MULTIDIRECTED_LINE
                  ? "GeometryCHLV95_V1.MultiDirectedLine"
                  : "GeometryCHLV95_V1.MultiLine",
              null);
      int count = geometry instanceof MultiLineString ? geometry.getNumGeometries() : 1;
      String structureTag =
          encoding == InterlisGeometryEncoding.CHLV95_V1_MULTIDIRECTED_LINE
              ? "GeometryCHLV95_V1.DirectedLineStructure"
              : "GeometryCHLV95_V1.LineStructure";
      for (int i = 0; i < count; i++) {
        Geometry component = geometry instanceof MultiLineString ? geometry.getGeometryN(i) : geometry;
        if (!(component instanceof LineString)) {
          throw new InterlisGeometryException(
              "CHLV95_V1 MultiLine requires line components, got "
                  + component.getGeometryType());
        }
        Iom_jObject lineStructure = new Iom_jObject(structureTag, null);
        lineStructure.addattrobj(
            "Line", toIomGeometry(component, InterlisGeometryKind.POLYLINE, dimension));
        wrapper.addattrobj("Lines", lineStructure);
      }
      return wrapper;
    }

    throw new InterlisGeometryException(
        "Unsupported legacy geometry encoding " + encoding + " for geometry kind " + kind);
  }

  private boolean hasZ(Geometry geometry) {
    for (org.locationtech.jts.geom.Coordinate coordinate : geometry.getCoordinates()) {
      if (!Double.isNaN(coordinate.getZ())) {
        return true;
      }
    }
    return false;
  }

  private void validateKind(IomObject iomGeometry, InterlisGeometryKind kind)
      throws InterlisGeometryException {
    String tag = iomGeometry == null ? null : iomGeometry.getobjecttag();
    boolean ok =
        switch (kind) {
          case COORD -> TAG_COORD.equals(tag);
          case MULTICOORD -> TAG_MULTICOORD.equals(tag);
          case POLYLINE -> TAG_POLYLINE.equals(tag);
          case MULTIPOLYLINE -> TAG_MULTIPOLYLINE.equals(tag);
          case SURFACE, AREA -> TAG_SURFACE.equals(tag) || TAG_MULTISURFACE.equals(tag);
          case MULTISURFACE -> TAG_MULTISURFACE.equals(tag);
        };
    if (!ok) {
      throw new InterlisGeometryException(
          "Hop geometry of kind " + kind + " maps to unexpected INTERLIS structure tag " + tag);
    }
  }

  /**
   * Replaces curve-only representations with their plain counterparts when no true curve is
   * present: a {@link CompoundCurve} consisting solely of straight segments becomes a
   * {@link LineString} and a {@link CurvePolygon} with straight-only rings becomes a
   * {@link Polygon}. Straight {@code CompoundCurve} rings inside a {@code CurvePolygon} do not
   * count as curves.
   */
  private Geometry normalizeStraightOnly(Geometry geometry) {
    if (geometry instanceof CompoundCurve compoundCurve) {
      boolean hasCurve = containsTrueCurve(compoundCurve);
      if (!hasCurve) {
        Geometry plain = geometry.getFactory().createLineString(compoundCurve.getCoordinates());
        plain.setSRID(geometry.getSRID());
        return plain;
      }
    }
    if (geometry instanceof CurvePolygon curvePolygon) {
      boolean hasCurve =
          curvePolygon.getCurveRings().stream().anyMatch(this::containsTrueCurve);
      if (!hasCurve) {
        Geometry plain = plainPolygon(curvePolygon);
        plain.setSRID(geometry.getSRID());
        return plain;
      }
    }
    return geometry;
  }

  /** {@code true} if the geometry contains at least one circular string segment. */
  private boolean containsTrueCurve(Geometry geometry) {
    if (geometry instanceof CircularString) {
      return true;
    }
    if (geometry instanceof CompoundCurve compoundCurve) {
      return compoundCurve.getComponents().stream().anyMatch(this::containsTrueCurve);
    }
    if (geometry instanceof CurvePolygon curvePolygon) {
      return curvePolygon.getCurveRings().stream().anyMatch(this::containsTrueCurve);
    }
    if (geometry instanceof com.atolcd.hop.gis.geometry.curve.MultiCurve multiCurve) {
      return multiCurve.getCurves().stream().anyMatch(this::containsTrueCurve);
    }
    if (geometry instanceof com.atolcd.hop.gis.geometry.curve.MultiSurface multiSurface) {
      return multiSurface.getSurfaces().stream().anyMatch(this::containsTrueCurve);
    }
    return false;
  }

  private Geometry plainPolygon(CurvePolygon curvePolygon) {
    org.locationtech.jts.geom.GeometryFactory factory = curvePolygon.getFactory();
    org.locationtech.jts.geom.LinearRing shell =
        factory.createLinearRing(curvePolygon.getCurveRings().get(0).getCoordinates());
    org.locationtech.jts.geom.LinearRing[] holes =
        curvePolygon.getCurveRings().stream()
            .skip(1)
            .map(ring -> factory.createLinearRing(ring.getCoordinates()))
            .toArray(org.locationtech.jts.geom.LinearRing[]::new);
    return factory.createPolygon(shell, holes);
  }
}
