package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.InterlisObjectOperation;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

class InterlisObjectToRowMapperTest {

  private static InterlisSchemaDescriptor spike;
  private static InterlisSchemaDescriptor geometry;

  private final InterlisRowSchemaBuilder builder = new InterlisRowSchemaBuilder();
  private final InterlisObjectToRowMapper mapper = new DefaultInterlisObjectToRowMapper();

  @BeforeAll
  static void compile() throws Exception {
    InterlisModelService service = new InterlisModelServiceImpl();
    InterlisSchemaExtractor extractor = new InterlisSchemaExtractor();
    spike =
        extractor.extract(
            service
                .compile(
                    new ModelSource(
                        List.of(TestResources.path("/models/HopIli_Spike_V1.ili")),
                        List.of(),
                        List.of()),
                    ModelCompileOptions.defaults())
                .transferDescription());
    geometry =
        extractor.extract(
            service
                .compile(
                    new ModelSource(
                        List.of(TestResources.path("/models/HopIli_Geometry_V1.ili")),
                        List.of(),
                        List.of()),
                    ModelCompileOptions.defaults())
                .transferDescription());
  }

  private InterlisObjectEnvelope envelope(Iom_jObject object, String basketId) {
    return new InterlisObjectEnvelope(
        InterlisEventType.OBJECT,
        "HopIli_Spike_V1",
        "HopIli_Spike_V1.Data",
        basketId,
        object.getobjecttag(),
        object.getobjectoid(),
        InterlisObjectOperation.NONE,
        object);
  }

  private static Iom_jObject building(String tid) {
    Iom_jObject building = new Iom_jObject("HopIli_Spike_V1.Data.Building", tid);
    building.setattrvalue("Note", "inherited note");
    building.setattrvalue("Code", "42");
    Iom_jObject coord = new Iom_jObject("COORD", null);
    coord.setattrvalue("C1", "2600000.0");
    coord.setattrvalue("C2", "1200000.0");
    building.addattrobj("Location", coord);
    Iom_jObject address = new Iom_jObject("HopIli_Spike_V1.Data.Address", null);
    address.setattrvalue("Street", "Main Street");
    address.setattrvalue("Number", "10");
    building.addattrobj("Address", address);
    Iom_jObject ref = new Iom_jObject("REF", null);
    ref.setobjectrefoid("m1");
    building.addattrobj("Municipality", ref);
    return building;
  }

  @Test
  void maps_tid_and_bid() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(spike, classOf("Building"), ProjectionOptions.defaults());

    Object[] row = mapper.map(envelope(building("g1"), "b1"), plan);

    assertThat(row).hasSize(plan.fieldCount());
    assertThat(row[0]).isEqualTo("g1");
    assertThat(row[1]).isEqualTo("b1");
  }

  @Test
  void maps_primitives_inherited_attributes_and_role_reference() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(spike, classOf("Building"), ProjectionOptions.defaults());

    Object[] row = mapper.map(envelope(building("g1"), "b1"), plan);

    // _ili_tid, _ili_bid, Code, Location, Address_Street, Address_Number, Municipality_ref, Note
    assertThat(row[2]).isEqualTo(42L);
    assertThat(row[4]).isEqualTo("Main Street");
    assertThat(row[5]).isEqualTo("10");
    assertThat(row[6]).isEqualTo("m1");
    assertThat(row[7]).isEqualTo("inherited note");
  }

  @Test
  void maps_geometry_attribute_to_hop_geometry() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(spike, classOf("Building"), ProjectionOptions.defaults());

    Object[] row = mapper.map(envelope(building("g1"), "b1"), plan);

    Geometry location = (Geometry) row[3];
    assertThat(location).isInstanceOf(Point.class);
    assertThat(location.getCoordinate().getX()).isEqualTo(2600000.0);
    assertThat(location.getCoordinate().getY()).isEqualTo(1200000.0);
  }

  @Test
  void optional_missing_structure_produces_null_children() throws Exception {
    Iom_jObject building = new Iom_jObject("HopIli_Spike_V1.Data.Building", "g1");
    building.setattrvalue("Code", "42");
    Iom_jObject ref = new Iom_jObject("REF", null);
    ref.setobjectrefoid("m1");
    building.addattrobj("Municipality", ref);

    InterlisRowMappingPlan plan =
        builder.build(spike, classOf("Building"), ProjectionOptions.defaults());

    Object[] row = mapper.map(envelope(building, "b1"), plan);

    assertThat(row[4]).isNull();
    assertThat(row[5]).isNull();
    assertThat(row[6]).isEqualTo("m1");
  }

  @Test
  void optional_missing_role_produces_null_reference() throws Exception {
    Iom_jObject building = new Iom_jObject("HopIli_Spike_V1.Data.Building", "g1");
    building.setattrvalue("Code", "42");

    InterlisRowMappingPlan plan =
        builder.build(spike, classOf("Building"), ProjectionOptions.defaults());

    Object[] row = mapper.map(envelope(building, "b1"), plan);

    assertThat(row[6]).isNull();
  }

  @Test
  void maps_multiple_geometry_attributes_independently() throws Exception {
    Iom_jObject testObject = new Iom_jObject("HopIli_Geometry_V1.Data.TestObject", "o1");
    testObject.setattrvalue("Name", "A");
    Iom_jObject center = new Iom_jObject("COORD", null);
    center.setattrvalue("C1", "2600000.0");
    center.setattrvalue("C2", "1200000.0");
    testObject.addattrobj("Center", center);
    Iom_jObject axis =
        polyline(
            segment("COORD", "2600000.0", "1200000.0"),
            segment("COORD", "2600100.0", "1200000.0"));
    testObject.addattrobj("Axis", axis);

    InterlisRowMappingPlan plan =
        builder.build(geometry, classOf(geometry, "HopIli_Geometry_V1.Data.TestObject"),
            ProjectionOptions.defaults());
    InterlisObjectEnvelope envelope =
        new InterlisObjectEnvelope(
            InterlisEventType.OBJECT, "HopIli_Geometry_V1", "HopIli_Geometry_V1.Data", "b1",
            testObject.getobjecttag(), "o1", InterlisObjectOperation.NONE, testObject);

    Object[] row = mapper.map(envelope, plan);

    // _ili_tid, _ili_bid, Name, Center, Points, Axis, Axes, Boundary, Area, Surfaces
    assertThat(row[2]).isEqualTo("A");
    assertThat(row[3]).isInstanceOf(Point.class);
    assertThat(row[5]).isInstanceOf(LineString.class);
    assertThat(row[4]).isNull(); // Points not present in the fixture object
    assertThat(row[6]).isNull();
  }

  @Test
  void invalid_primitive_value_reports_class_tid_and_attribute() throws Exception {
    Iom_jObject building = building("g1");
    building.setattrvalue("Code", "not-a-number");

    InterlisRowMappingPlan plan =
        builder.build(spike, classOf("Building"), ProjectionOptions.defaults());

    assertThatThrownBy(() -> mapper.map(envelope(building, "b1"), plan))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Code")
        .hasMessageContaining("g1")
        .hasMessageContaining("b1");
  }

  @Test
  void multiple_structure_values_are_rejected() throws Exception {
    Iom_jObject building = building("g1");
    Iom_jObject second = new Iom_jObject("HopIli_Spike_V1.Data.Address", null);
    second.setattrvalue("Street", "Second");
    building.addattrobj("Address", second);

    InterlisRowMappingPlan plan =
        builder.build(spike, classOf("Building"), ProjectionOptions.defaults());

    assertThatThrownBy(() -> mapper.map(envelope(building, "b1"), plan))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Address");
  }

  @Test
  void maps_decimal_without_precision_loss() throws Exception {
    InterlisSchemaDescriptor primitives =
        new InterlisSchemaExtractor()
            .extract(
                new InterlisModelServiceImpl()
                    .compile(
                        new ModelSource(
                            List.of(TestResources.path("/models/HopIli_Primitives_V1.ili")),
                            List.of(),
                            List.of()),
                        new ModelCompileOptions("2.4"))
                    .transferDescription());
    Iom_jObject primitive = new Iom_jObject("HopIli_Primitives_V1.Data.Primitive", "p1");
    primitive.setattrvalue("Text", "hello");
    primitive.setattrvalue("Flag", "true");
    primitive.setattrvalue("Count", "9999999999");
    primitive.setattrvalue("Value", "12345678901234567890.123456789");
    primitive.setattrvalue("Kind", "one");
    primitive.setattrvalue("At", "2026-08-25");

    InterlisRowMappingPlan plan =
        builder.build(
            primitives, classOf(primitives, "HopIli_Primitives_V1.Data.Primitive"),
            ProjectionOptions.defaults());
    InterlisObjectEnvelope envelope =
        new InterlisObjectEnvelope(
            InterlisEventType.OBJECT, "HopIli_Primitives_V1", "HopIli_Primitives_V1.Data", "b1",
            primitive.getobjecttag(), "p1", InterlisObjectOperation.NONE, primitive);

    Object[] row = mapper.map(envelope, plan);

    // _ili_tid, _ili_bid, Text, Mtext, Label, Link, Flag, Count, Value, At, Ts, Kind, Opt
    assertThat(row[2]).isEqualTo("hello");
    assertThat(row[6]).isEqualTo(Boolean.TRUE);
    assertThat(row[7]).isEqualTo(9999999999L);
    assertThat(row[8]).isEqualTo(new BigDecimal("12345678901234567890.123456789"));
    assertThat(row[11]).isEqualTo("one");
    assertThat(row[9]).isNotNull(); // parsed date
    assertThat(row[10]).isNull(); // Ts not set
  }

  private static Iom_jObject segment(String tag, String c1, String c2) {
    Iom_jObject segment = new Iom_jObject(tag, null);
    segment.setattrvalue("C1", c1);
    segment.setattrvalue("C2", c2);
    return segment;
  }

  private static Iom_jObject polyline(Iom_jObject... segments) {
    Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
    Iom_jObject sequence = new Iom_jObject("SEGMENTS", null);
    for (Iom_jObject segment : segments) {
      sequence.addattrobj("segment", segment);
    }
    polyline.addattrobj("sequence", sequence);
    return polyline;
  }

  private InterlisClassDescriptor classOf(String name) {
    return spike.findClass("HopIli_Spike_V1.Data." + name).orElseThrow();
  }

  private InterlisClassDescriptor classOf(InterlisSchemaDescriptor schema, String scopedName) {
    return schema.findClass(scopedName).orElseThrow();
  }
}
