package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import com.atolcd.hop.gis.geometry.curve.CircularString;
import com.atolcd.hop.gis.geometry.curve.CompoundCurve;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

class RowToIomMapperTest {

  private static InterlisSchemaDescriptor spike;
  private static InterlisSchemaDescriptor primitives;

  private final InterlisRowSchemaBuilder builder = new InterlisRowSchemaBuilder();
  private final RowToIomMapper mapper = new RowToIomMapper();

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
    primitives =
        extractor.extract(
            service
                .compile(
                    new ModelSource(
                        List.of(TestResources.path("/models/HopIli_Primitives_V1.ili")),
                        List.of(),
                        List.of()),
                    new ModelCompileOptions("2.4"))
                .transferDescription());
  }

  private InterlisRowMappingPlan buildingPlan() throws Exception {
    return builder.build(
        spike,
        spike.findClass("HopIli_Spike_V1.Data.Building").orElseThrow(),
        ProjectionOptions.defaults());
  }

  private InterlisRowMappingPlan primitivesPlan() throws Exception {
    return builder.build(
        primitives,
        primitives.findClass("HopIli_Primitives_V1.Data.Primitive").orElseThrow(),
        ProjectionOptions.defaults());
  }

  private GeometryFactory factory() {
    return new GeometryFactory();
  }

  @Test
  void creates_iom_object_with_class_tag_and_tid() throws Exception {
    InterlisRowMappingPlan plan = buildingPlan();
    // _ili_tid, _ili_bid, Code, Location, Address_Street, Address_Number, Municipality_ref, Note
    Object[] values = {"g1", "b1", 42L, null, "Main Street", "10", "m1", "note"};

    IomObject object = mapper.map(values, plan, RowWriteOptions.defaults());

    assertThat(object.getobjecttag()).isEqualTo("HopIli_Spike_V1.Data.Building");
    assertThat(object.getobjectoid()).isEqualTo("g1");
  }

  @Test
  void writes_primitives_in_interlis_lexical_form() throws Exception {
    InterlisRowMappingPlan plan = primitivesPlan();
    // _ili_tid,_ili_bid,Text,Mtext,Label,Link,Flag,Count,Value,At,Ts,Kind,Opt
    Object[] values = {
      "p1", "b1",
      "hello", "multi\nline", "Zürich", "https://example.com",
      Boolean.TRUE, 9999999999L, new BigDecimal("12345678901234567890.123456789"),
      java.util.Date.from(
          java.time.LocalDate.of(2026, 8, 28)
              .atStartOfDay(java.time.ZoneOffset.UTC)
              .toInstant()),
      null, "one", "opt"
    };

    IomObject object = mapper.map(values, plan, RowWriteOptions.defaults());

    assertThat(object.getattrvalue("Text")).isEqualTo("hello");
    assertThat(object.getattrvalue("Mtext")).isEqualTo("multi\nline");
    assertThat(object.getattrvalue("Label")).isEqualTo("Zürich");
    assertThat(object.getattrvalue("Link")).isEqualTo("https://example.com");
    assertThat(object.getattrvalue("Flag")).isEqualTo("true");
    assertThat(object.getattrvalue("Count")).isEqualTo("9999999999");
    assertThat(object.getattrvalue("Value")).isEqualTo("12345678901234567890.123456789");
    assertThat(object.getattrvalue("At")).isEqualTo("2026-08-28");
    assertThat(object.getattrvalue("Kind")).isEqualTo("one");
    assertThat(object.getattrvalue("Opt")).isEqualTo("opt");
    // null timestamp stays undefined
    assertThat(object.getattrvalue("Ts")).isNull();
  }

  @Test
  void writes_geometry_values() throws Exception {
    InterlisRowMappingPlan plan = buildingPlan();
    Point location = factory().createPoint(new Coordinate(2600000, 1200000));
    Object[] values = {"g1", "b1", 42L, location, null, null, null, "note"};

    IomObject object = mapper.map(values, plan, RowWriteOptions.defaults());

    assertThat(object.getattrvaluecount("Location")).isEqualTo(1);
    IomObject coord = object.getattrobj("Location", 0);
    assertThat(coord.getobjecttag()).isEqualTo("COORD");
    assertThat(coord.getattrvalue("C1")).isEqualTo("2600000.0");
    assertThat(coord.getattrvalue("C2")).isEqualTo("1200000.0");
  }

  @Test
  void writes_arc_geometries_without_linearization() throws Exception {
    InterlisSchemaDescriptor geometry =
        new InterlisSchemaExtractor()
            .extract(
                new InterlisModelServiceImpl()
                    .compile(
                        new ModelSource(
                            List.of(TestResources.path("/models/HopIli_Geometry_V1.ili")),
                            List.of(),
                            List.of()),
                        ModelCompileOptions.defaults())
                    .transferDescription());
    InterlisRowMappingPlan plan =
        builder.build(
            geometry,
            geometry.findClass("HopIli_Geometry_V1.Data.TestObject").orElseThrow(),
            ProjectionOptions.defaults());

    LineString straight =
        factory().createLineString(
            new Coordinate[] {new Coordinate(0, 0), new Coordinate(5, 0)});
    CircularString arc =
        new CircularString(
            new Coordinate[] {
              new Coordinate(5, 0), new Coordinate(7.5, 2.5), new Coordinate(10, 0)
            },
            factory());
    CompoundCurve axis = new CompoundCurve(List.of(straight, arc), factory());

    // _ili_tid, _ili_bid, Name, Center, Points, Axis, Axes, Boundary, Area, Surfaces
    Object[] values = {"o1", "b1", "A", null, null, axis, null, null, null, null};

    IomObject object = mapper.map(values, plan, RowWriteOptions.defaults());

    IomObject polyline = object.getattrobj("Axis", 0);
    assertThat(polyline.getobjecttag()).isEqualTo("POLYLINE");
    IomObject sequence = polyline.getattrobj("sequence", 0);
    assertThat(sequence.getattrvaluecount("segment")).isEqualTo(3);
    IomObject arcSegment = sequence.getattrobj("segment", 2);
    assertThat(arcSegment.getobjecttag()).isEqualTo("ARC");
    assertThat(arcSegment.getattrvalue("A1")).isEqualTo("7.5");
    assertThat(arcSegment.getattrvalue("A2")).isEqualTo("2.5");
    assertThat(arcSegment.getattrvalue("C1")).isEqualTo("10.0");
    assertThat(arcSegment.getattrvalue("C2")).isEqualTo("0.0");
  }

  @Test
  void reconstructs_flattened_structure_once() throws Exception {
    InterlisRowMappingPlan plan = buildingPlan();
    Object[] values = {"g1", "b1", 42L, null, "Main Street", "10", null, "note"};

    IomObject object = mapper.map(values, plan, RowWriteOptions.defaults());

    assertThat(object.getattrvaluecount("Address")).isEqualTo(1);
    IomObject address = object.getattrobj("Address", 0);
    assertThat(address.getobjecttag()).isEqualTo("HopIli_Spike_V1.Data.Address");
    assertThat(address.getattrvalue("Street")).isEqualTo("Main Street");
    assertThat(address.getattrvalue("Number")).isEqualTo("10");
  }

  @Test
  void entirely_null_structure_is_not_created() throws Exception {
    InterlisRowMappingPlan plan = buildingPlan();
    Object[] values = {"g1", "b1", 42L, null, null, null, null, "note"};

    IomObject object = mapper.map(values, plan, RowWriteOptions.defaults());

    assertThat(object.getattrvaluecount("Address")).isZero();
  }

  @Test
  void writes_role_reference() throws Exception {
    InterlisRowMappingPlan plan = buildingPlan();
    Object[] values = {"g1", "b1", 42L, null, null, null, "m1", "note"};

    IomObject object = mapper.map(values, plan, RowWriteOptions.defaults());

    assertThat(object.getattrvaluecount("Municipality")).isEqualTo(1);
    assertThat(object.getattrobj("Municipality", 0).getobjectrefoid()).isEqualTo("m1");
  }

  @Test
  void null_optional_role_is_skipped() throws Exception {
    InterlisRowMappingPlan plan = buildingPlan();
    Object[] values = {"g1", "b1", 42L, null, null, null, null, "note"};

    IomObject object = mapper.map(values, plan, RowWriteOptions.defaults());

    assertThat(object.getattrvaluecount("Municipality")).isZero();
  }

  @Test
  void rejects_null_for_mandatory_attribute_in_strict_mode() throws Exception {
    InterlisRowMappingPlan plan = primitivesPlan();
    // _ili_tid,_ili_bid,Text(mandatory),... — everything else null
    Object[] values = new Object[plan.fieldCount()];
    values[0] = "p1";
    values[1] = "b1";

    assertThatThrownBy(() -> mapper.map(values, plan, RowWriteOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Text");
  }

  @Test
  void lenient_mode_leaves_mandatory_attribute_undefined() throws Exception {
    InterlisRowMappingPlan plan = primitivesPlan();
    Object[] values = new Object[plan.fieldCount()];
    values[0] = "p1";
    values[1] = "b1";
    values[8] = new BigDecimal("1.5"); // Value is optional, fine

    IomObject object = mapper.map(values, plan, new RowWriteOptions(false, "b1"));

    assertThat(object.getattrvalue("Text")).isNull();
    assertThat(object.getattrvalue("Value")).isEqualTo("1.5");
  }

  @Test
  void rejects_wrong_hop_type_with_field_name() throws Exception {
    InterlisRowMappingPlan plan = buildingPlan();
    // Code expects Long
    Object[] values = {"g1", "b1", "42", null, null, null, null, "note"};

    assertThatThrownBy(() -> mapper.map(values, plan, RowWriteOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Code");
  }

  @Test
  void missing_tid_is_rejected() throws Exception {
    InterlisRowMappingPlan plan = buildingPlan();
    Object[] values = {null, "b1", 42L, null, null, null, null, "note"};

    assertThatThrownBy(() -> mapper.map(values, plan, RowWriteOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("_ili_tid");
  }
}
