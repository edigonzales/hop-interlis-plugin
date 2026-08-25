package ch.so.agi.hop.interlis.core.geometry;

import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.wkb.Iox2wkb;
import ch.interlis.iox_j.wkb.Wkb2iox;
import ch.so.agi.hop.interlis.core.model.InterlisGeometryKind;
import com.atolcd.hop.gis.geometry.curve.CompoundCurve;
import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import com.atolcd.hop.gis.geometry.curve.CurvePolygon;
import java.nio.ByteOrder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
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
    if (geometry == null) {
      return null;
    }
    if (dimension != 2 && dimension != 3) {
      throw new InterlisGeometryException(
          "Unsupported coordinate dimension " + dimension + " for geometry kind " + kind);
    }

    try {
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
    if (geometry == null) {
      return null;
    }

    try {
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
   * Replaces curve-only representations with their plain counterparts when no curve is present:
   * a {@link CompoundCurve} consisting solely of straight segments becomes a {@link LineString}
   * and a {@link CurvePolygon} with straight-only rings becomes a {@link Polygon}.
   */
  private Geometry normalizeStraightOnly(Geometry geometry) {
    if (geometry instanceof CompoundCurve compoundCurve) {
      boolean hasCurve =
          compoundCurve.getComponents().stream()
              .anyMatch(CurveGeometrySupport::isCurveGeometry);
      if (!hasCurve) {
        Geometry plain = geometry.getFactory().createLineString(compoundCurve.getCoordinates());
        plain.setSRID(geometry.getSRID());
        return plain;
      }
    }
    if (geometry instanceof CurvePolygon curvePolygon) {
      boolean hasCurve =
          curvePolygon.getCurveRings().stream()
              .anyMatch(CurveGeometrySupport::isCurveGeometry);
      if (!hasCurve) {
        Geometry plain = plainPolygon(curvePolygon);
        plain.setSRID(geometry.getSRID());
        return plain;
      }
    }
    return geometry;
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
