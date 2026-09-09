package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.core.model.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class P1MappingRegressionTest {
  static InterlisProjectionResult projection;
  static InterlisRowMappingPlan plan;
  final RowToIomMapper writer = new RowToIomMapper();
  final GeometryFactory factory = new GeometryFactory();
  @TempDir Path temp;

  @BeforeAll
  static void compile() throws Exception {
    projection =
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(
                    null,
                    List.of("HopIli_P1_V1"),
                    List.of(TestResources.path("/models").toString())),
                "HopIli_P1_V1.Data.Item",
                new ProjectionOptions(
                    true, true, false, false, false, true, "_", null, Set.of(), true, true));
    plan = projection.plan();
  }

  Object[] values() {
    var values = new Object[plan.fieldCount()];
    set(values, "_ili_tid", "i1");
    set(values, "Name", "item");
    return values;
  }

  static void set(Object[] values, String name, Object value) {
    values[
            plan.fields().stream()
                .filter(f -> name.equals(f.hopFieldName()))
                .findFirst()
                .orElseThrow()
                .outputIndex()] =
        value;
  }

  Point point(double z) {
    return factory.createPoint(new Coordinate(1, 2, z));
  }

  @Test
  void overlay_replaces_and_clears_without_mutating_carrier() throws Exception {
    var values = values();
    set(values, "Location", point(3));
    set(values, "Details_Code", "old");
    set(values, "Details_Note", "old");
    set(values, "Details_Location", point(3));
    set(values, "Target_ref", "old-target");
    set(values, "Target_ref_bid", "external");
    IomObject carrier = writer.map(values, plan, RowWriteOptions.defaults());
    var child = new Iom_jObject("HopIli_P1_V1.Data.Detail", null);
    child.setattrvalue("Code", "child");
    carrier.addattrobj("Children", child);
    set(values, "Location", point(7));
    set(values, "Details_Note", null);
    set(values, "Details_Location", point(8));
    set(values, "Target_ref", "new-target");
    set(values, "Target_ref_bid", null);
    var result = writer.map(carrier, values, plan, RowWriteOptions.defaults());
    result = writer.map(result, values, plan, RowWriteOptions.defaults());
    assertThat(result.getattrvaluecount("Location")).isEqualTo(1);
    assertThat(result.getattrobj("Location", 0).getattrvalue("C3")).isEqualTo("7.0");
    assertThat(result.getattrvaluecount("Target")).isEqualTo(1);
    assertThat(result.getattrobj("Target", 0).getobjectrefoid()).isEqualTo("new-target");
    assertThat(result.getattrobj("Target", 0).getobjectrefbid()).isNull();
    assertThat(result.getattrobj("Details", 0).getattrvalue("Note")).isNull();
    assertThat(result.getattrobj("Details", 0).getattrvaluecount("Location")).isEqualTo(1);
    assertThat(result.getattrvaluecount("Children")).isEqualTo(1);
    assertThat(carrier.getattrobj("Target", 0).getobjectrefoid()).isEqualTo("old-target");
    assertThat(carrier.getattrobj("Details", 0).getattrvalue("Note")).isEqualTo("old");
    set(values, "Location", null);
    set(values, "Target_ref", null);
    set(values, "Details_Code", null);
    set(values, "Details_Location", null);
    result = writer.map(result, values, plan, RowWriteOptions.defaults());
    assertThat(result.getattrvaluecount("Location")).isZero();
    assertThat(result.getattrvaluecount("Target")).isZero();
    assertThat(result.getattrvaluecount("Details")).isZero();
    assertThat(result.getattrvaluecount("Children")).isEqualTo(1);
  }

  @Test
  void clearing_projection_keeps_unprojected_contents_of_optional_structure() throws Exception {
    var values = values();
    set(values, "Details_Code", "code");
    var carrier = writer.map(values, plan, RowWriteOptions.defaults());
    var tag = new Iom_jObject("HopIli_P1_V1.Data.Tag", null);
    tag.setattrvalue("Text", "keep");
    carrier.getattrobj("Details", 0).addattrobj("Tags", tag);
    set(values, "Details_Code", null);
    // Without strict validation the incomplete but nonempty structure must survive the overlay.
    var result =
        writer.map(
            carrier, values, plan, new RowWriteOptions(false, "b1", InterlisObjectOperation.NONE));
    assertThat(result.getattrobj("Details", 0).getattrobj("Tags", 0).getattrvalue("Text"))
        .isEqualTo("keep");
    assertThat(carrier.getattrobj("Details", 0).getattrvalue("Code")).isEqualTo("code");
    assertThatThrownBy(() -> writer.map(carrier, values, plan, RowWriteOptions.defaults()))
        .hasMessageContaining("Code");
  }

  @Test
  void mandatory_leaf_is_checked_after_optional_structure_creation() {
    var values = values();
    set(values, "Details_Note", "creates structure");
    assertThatThrownBy(() -> writer.map(values, plan, RowWriteOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Code");
  }

  @Test
  void delete_discards_carrier_and_all_row_payload() throws Exception {
    var values = values();
    set(values, "Details_Code", "value");
    set(values, "Target_ref", "t1");
    var carrier = writer.map(values, plan, RowWriteOptions.defaults());
    var result =
        writer.mapAll(
            carrier, values, plan, new RowWriteOptions(true, "b1", InterlisObjectOperation.DELETE));
    assertThat(result.object().getattrcount()).isZero();
    assertThat(result.object().getobjectoid()).isEqualTo("i1");
    assertThat(result.object().getobjectoperation())
        .isEqualTo(InterlisObjectOperation.DELETE.toIom());
    assertThat(result.additionalObjects()).isEmpty();
  }

  @Test
  void model_driven_xyz_survives_xtf_roundtrip_for_all_straight_shapes() throws Exception {
    var values = values();
    var line =
        factory.createLineString(
            new Coordinate[] {new Coordinate(1, 2, 3), new Coordinate(4, 5, 6)});
    var polygon =
        factory.createPolygon(
            new Coordinate[] {
              new Coordinate(1, 1, 3),
              new Coordinate(4, 1, 3),
              new Coordinate(4, 4, 3),
              new Coordinate(1, 1, 3)
            });
    set(values, "InheritedAxis", line);
    set(values, "Location", point(3));
    set(values, "Axis", line);
    set(values, "Face", polygon);
    set(values, "Axes", factory.createMultiLineString(new LineString[] {line}));
    set(values, "Faces", factory.createMultiPolygon(new Polygon[] {polygon}));
    var file = temp.resolve("3d.xtf");
    try (var xtf =
        XtfTransferWriter.open(
            file, projection.model().transferDescription(), projection.modelNames())) {
      xtf.startTransfer("test");
      xtf.startBasket("HopIli_P1_V1.Data", "b1");
      xtf.writeObject(writer.map(values, plan, RowWriteOptions.defaults()));
      xtf.endBasket();
      xtf.endTransfer();
    }
    InterlisObjectEnvelope envelope;
    try (var reader = XtfTransferReader.open(file, projection.model().transferDescription())) {
      do {
        envelope = reader.next();
      } while (envelope.eventType() != InterlisEventType.OBJECT);
    }
    var actual = new DefaultInterlisObjectToRowMapper().map(envelope, plan);
    for (var field : plan.fields())
      if (values[field.outputIndex()] instanceof Geometry expected) {
        assertThat(field.attributeDescriptor().coordDimension()).isEqualTo(3);
        var coordinates = ((Geometry) actual[field.outputIndex()]).getCoordinates();
        assertThat(coordinates).hasSize(expected.getCoordinates().length);
        for (int i = 0; i < coordinates.length; i++)
          assertThat(coordinates[i].equals3D(expected.getCoordinates()[i]))
              .as(field.hopFieldName() + " coordinate " + i)
              .isTrue();
      }
    // The inverse mapping of reader-produced multi-geometries must also retain XYZ.
    var second = writer.map(actual, plan, RowWriteOptions.defaults());
    assertThat(second.getattrvaluecount("Axes")).isEqualTo(1);
  }
}
