package ch.so.agi.hop.interlis.core.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.model.InterlisGeometryKind;
import com.atolcd.hop.gis.geometry.curve.CircularString;
import com.atolcd.hop.gis.geometry.curve.CompoundCurve;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

class InterlisGeometryMapperTest {

  private final InterlisGeometryMapper mapper = new InterlisGeometryMapper();

  @Test
  void coord_roundtrips_to_point() throws Exception {
    IomObject coord = coord(2600000.0, 1200000.0);

    Geometry hopGeometry = mapper.toHopGeometry(coord, InterlisGeometryKind.COORD, 2);

    assertThat(hopGeometry).isInstanceOf(Point.class);
    assertThat(hopGeometry.getCoordinate().getX()).isEqualTo(2600000.0);
    assertThat(hopGeometry.getCoordinate().getY()).isEqualTo(1200000.0);

    IomObject roundtripped = mapper.toIomGeometry(hopGeometry, InterlisGeometryKind.COORD, 2);
    assertThat(roundtripped.getobjecttag()).isEqualTo("COORD");
    assertThat(roundtripped.getattrvalue("C1")).isEqualTo("2600000.0");
    assertThat(roundtripped.getattrvalue("C2")).isEqualTo("1200000.0");
  }

  @Test
  void coord_3d_roundtrips() throws Exception {
    Iom_jObject coord = new Iom_jObject("COORD", null);
    coord.setattrvalue("C1", "2600000.0");
    coord.setattrvalue("C2", "1200000.0");
    coord.setattrvalue("C3", "450.5");

    Geometry hopGeometry = mapper.toHopGeometry(coord, InterlisGeometryKind.COORD, 3);

    assertThat(hopGeometry.getCoordinate().getZ()).isEqualTo(450.5);

    IomObject roundtripped = mapper.toIomGeometry(hopGeometry, InterlisGeometryKind.COORD, 3);
    assertThat(roundtripped.getattrvalue("C3")).isEqualTo("450.5");
  }

  @Test
  void straight_polyline_roundtrips_to_linestring() throws Exception {
    IomObject polyline =
        polyline(coord(0.0, 0.0), coord(10.0, 0.0), coord(10.0, 10.0));

    Geometry hopGeometry = mapper.toHopGeometry(polyline, InterlisGeometryKind.POLYLINE, 2);

    assertThat(hopGeometry).isInstanceOf(LineString.class);
    assertThat(hopGeometry).isNotInstanceOf(CompoundCurve.class);
    assertThat(hopGeometry.getCoordinates())
        .extracting(c -> c.getX() + "/" + c.getY())
        .containsExactly("0.0/0.0", "10.0/0.0", "10.0/10.0");

    IomObject roundtripped =
        mapper.toIomGeometry(hopGeometry, InterlisGeometryKind.POLYLINE, 2);
    assertThat(roundtripped.getobjecttag()).isEqualTo("POLYLINE");
    assertThat(segmentCoordinates(roundtripped)).hasSize(3);
  }

  @Test
  void polyline_with_arc_remains_curve_in_hop() throws Exception {
    IomObject polyline =
        polyline(coord(0.0, 0.0), coord(5.0, 0.0), arc(10.0, 0.0, 7.5, 2.5));

    Geometry hopGeometry = mapper.toHopGeometry(polyline, InterlisGeometryKind.POLYLINE, 2);

    assertThat(hopGeometry).isInstanceOf(CompoundCurve.class);
    CompoundCurve curve = (CompoundCurve) hopGeometry;
    assertThat(curve.getComponents()).hasSize(2);
    assertThat(curve.getComponents().get(0)).isInstanceOf(LineString.class);
    assertThat(curve.getComponents().get(0)).isNotInstanceOf(CircularString.class);
    // The arc is the second component and must be a CircularString, not stroked segments.
    assertThat(curve.getComponents().get(1)).isInstanceOf(CircularString.class);
    CircularString arcComponent = (CircularString) curve.getComponents().get(1);
    // The control points of the arc (start/mid/end) are preserved exactly.
    assertThat(arcComponent.getControlPoints()).hasSize(3);
    assertThat(arcComponent.getControlPoints()[1].getX()).isEqualTo(7.5);
    assertThat(arcComponent.getControlPoints()[1].getY()).isEqualTo(2.5);
  }

  @Test
  void arc_roundtrips_back_to_iom_arc() throws Exception {
    IomObject polyline =
        polyline(coord(0.0, 0.0), coord(5.0, 0.0), arc(10.0, 0.0, 7.5, 2.5));

    Geometry hopGeometry = mapper.toHopGeometry(polyline, InterlisGeometryKind.POLYLINE, 2);
    IomObject roundtripped =
        mapper.toIomGeometry(hopGeometry, InterlisGeometryKind.POLYLINE, 2);

    // The roundtripped INTERLIS polyline must again contain an ARC segment.
    assertThat(roundtripped.getobjecttag()).isEqualTo("POLYLINE");
    IomObject sequence = roundtripped.getattrobj("sequence", 0);
    assertThat(sequence.getattrvaluecount("segment")).isEqualTo(3);
    IomObject arcSegment = sequence.getattrobj("segment", 2);
    assertThat(arcSegment.getobjecttag()).isEqualTo("ARC");
    assertThat(arcSegment.getattrvalue("A1")).isEqualTo("7.5");
    assertThat(arcSegment.getattrvalue("A2")).isEqualTo("2.5");
    assertThat(arcSegment.getattrvalue("C1")).isEqualTo("10.0");
    assertThat(arcSegment.getattrvalue("C2")).isEqualTo("0.0");
  }

  @Test
  void straight_surface_roundtrips_to_polygon() throws Exception {
    IomObject surface = surface(polyline(
        coord(0.0, 0.0), coord(10.0, 0.0), coord(10.0, 10.0), coord(0.0, 0.0)));

    Geometry hopGeometry = mapper.toHopGeometry(surface, InterlisGeometryKind.SURFACE, 2);

    assertThat(hopGeometry).isInstanceOf(Polygon.class);
    // Straight-only surfaces must not remain SQL/MM curve polygons.
    assertThat(hopGeometry).isNotInstanceOf(com.atolcd.hop.gis.geometry.curve.CurvePolygon.class);
    Polygon polygon = (Polygon) hopGeometry;
    assertThat(polygon.getNumInteriorRing()).isZero();
    assertThat(polygon.getExteriorRing().getNumPoints()).isEqualTo(4);

    IomObject roundtripped =
        mapper.toIomGeometry(hopGeometry, InterlisGeometryKind.SURFACE, 2);
    assertThat(roundtripped.getobjecttag()).isIn("SURFACE", "MULTISURFACE");
  }

  @Test
  void null_geometry_stays_null() throws Exception {
    assertThat(mapper.toHopGeometry(null, InterlisGeometryKind.POLYLINE, 2)).isNull();
    assertThat(mapper.toIomGeometry(null, InterlisGeometryKind.POLYLINE, 2)).isNull();
  }

  @Test
  void geometry_kind_mismatch_is_reported() throws Exception {
    IomObject coord = coord(1.0, 2.0);

    Geometry point = mapper.toHopGeometry(coord, InterlisGeometryKind.COORD, 2);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> mapper.toIomGeometry(point, InterlisGeometryKind.POLYLINE, 2))
        .isInstanceOf(InterlisGeometryException.class)
        .hasMessageContaining("POLYLINE");
  }

  // -- helpers -------------------------------------------------------------

  private static IomObject coord(double c1, double c2) {
    Iom_jObject coord = new Iom_jObject("COORD", null);
    coord.setattrvalue("C1", Double.toString(c1));
    coord.setattrvalue("C2", Double.toString(c2));
    return coord;
  }

  /** ARC segment with C1/C2 (end point) and A1/A2 (mid point), as used by iox-ili. */
  private static IomObject arc(double endC1, double endC2, double a1, double a2) {
    Iom_jObject arc = new Iom_jObject("ARC", null);
    arc.setattrvalue("C1", Double.toString(endC1));
    arc.setattrvalue("C2", Double.toString(endC2));
    arc.setattrvalue("A1", Double.toString(a1));
    arc.setattrvalue("A2", Double.toString(a2));
    return arc;
  }

  /** POLYLINE with a "sequence" of "segment" children, as produced by the XTF reader. */
  private static IomObject polyline(IomObject... segments) {
    Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
    Iom_jObject sequence = new Iom_jObject("SEGMENTS", null);
    for (IomObject segment : segments) {
      sequence.addattrobj("segment", segment);
    }
    polyline.addattrobj("sequence", sequence);
    return polyline;
  }

  /** MULTISURFACE with a single SURFACE child, as produced by the XTF reader. */
  private static IomObject surface(IomObject boundaryPolyline) {
    Iom_jObject multiSurface = new Iom_jObject("MULTISURFACE", null);
    Iom_jObject surface = new Iom_jObject("SURFACE", null);
    Iom_jObject boundary = new Iom_jObject("BOUNDARY", null);
    boundary.addattrobj("polyline", boundaryPolyline);
    surface.addattrobj("boundary", boundary);
    multiSurface.addattrobj("surface", surface);
    return multiSurface;
  }

  private static java.util.List<String> segmentCoordinates(IomObject polyline) {
    IomObject sequence = polyline.getattrobj("sequence", 0);
    java.util.List<String> coordinates = new java.util.ArrayList<>();
    for (int i = 0; i < sequence.getattrvaluecount("segment"); i++) {
      IomObject segment = sequence.getattrobj("segment", i);
      coordinates.add(segment.getattrvalue("C1") + "/" + segment.getattrvalue("C2"));
    }
    return coordinates;
  }
}
