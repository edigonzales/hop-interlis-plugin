package ch.so.agi.hop.interlis.core.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.geometry.InterlisGeometryMapper;
import ch.so.agi.hop.interlis.core.mapping.DefaultInterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowSchemaBuilder;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.mapping.RowToIomMapper;
import ch.so.agi.hop.interlis.core.mapping.RowWriteOptions;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisGeometryEncoding;
import ch.so.agi.hop.interlis.core.model.InterlisGeometryKind;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import com.atolcd.hop.gis.geometry.curve.MultiCurve;
import com.atolcd.hop.gis.geometry.curve.MultiSurface;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;

/** End-to-end row/IOM/XTF coverage for CHLV95 geometry representations. */
class Chlv95GeometryMappingTest {

  private static final InterlisGeometryMapper GEOMETRY_MAPPER = new InterlisGeometryMapper();
  private static CompiledInterlisModel v1Model;
  private static InterlisRowMappingPlan v1Plan;
  private static CompiledInterlisModel v2Model;
  private static InterlisRowMappingPlan v2Plan;

  @BeforeAll
  static void compileModels() throws Exception {
    InterlisModelService service = new InterlisModelServiceImpl();
    v1Model = compile(service, "HopIli_CHLV95_V1.ili", ModelCompileOptions.defaults());
    v1Plan = plan(v1Model, "HopIli_CHLV95_V1.Data.TestObject");
    v2Model = compile(service, "HopIli_CHLV95_V2.ili", new ModelCompileOptions("2.4"));
    v2Plan = plan(v2Model, "HopIli_CHLV95_V2.Data.TestObject");
  }

  @Test
  void v1_multi_geometries_roundtrip_through_row_mapping_and_xtf(@TempDir Path tempDir)
      throws Exception {
    IomObject v1Surface = legacySurface();
    IomObject v1Line = legacyLine("GeometryCHLV95_V1.LineStructure", 0.0);
    IomObject v1DirectedLine = legacyLine("GeometryCHLV95_V1.DirectedLineStructure", 20.0);
    Geometry multiSurface =
        GEOMETRY_MAPPER.toHopGeometry(
            v1Surface,
            InterlisGeometryKind.MULTISURFACE,
            2,
            InterlisGeometryEncoding.CHLV95_V1_MULTISURFACE);
    Geometry multiLine =
        GEOMETRY_MAPPER.toHopGeometry(
            legacyMultiLine("GeometryCHLV95_V1.MultiLine", "GeometryCHLV95_V1.LineStructure"),
            InterlisGeometryKind.MULTIPOLYLINE,
            2,
            InterlisGeometryEncoding.CHLV95_V1_MULTILINE);
    Geometry multiDirectedLine =
        GEOMETRY_MAPPER.toHopGeometry(
            legacyMultiLine(
                "GeometryCHLV95_V1.MultiDirectedLine",
                "GeometryCHLV95_V1.DirectedLineStructure"),
            InterlisGeometryKind.MULTIPOLYLINE,
            2,
            InterlisGeometryEncoding.CHLV95_V1_MULTIDIRECTED_LINE);

    Object[] values = values(v1Plan, "o1");
    values[field(v1Plan, "MPoly").outputIndex()] = multiSurface;
    values[field(v1Plan, "MLine").outputIndex()] = multiLine;
    values[field(v1Plan, "MDirectedLine").outputIndex()] = multiDirectedLine;
    IomObject source = new RowToIomMapper().map(values, v1Plan, RowWriteOptions.defaults());
    assertThat(source.getattrobj("MPoly", 0).getobjecttag())
        .isEqualTo("GeometryCHLV95_V1.MultiSurface");
    assertThat(source.getattrobj("MLine", 0).getobjecttag())
        .isEqualTo("GeometryCHLV95_V1.MultiLine");
    assertThat(source.getattrobj("MDirectedLine", 0).getobjecttag())
        .isEqualTo("GeometryCHLV95_V1.MultiDirectedLine");

    Path file = tempDir.resolve("chlv95-v1.xtf");
    try (InterlisTransferWriter writer =
        XtfTransferWriter.open(file, v1Model.transferDescription(), List.of("HopIli_CHLV95_V1"))) {
      writer.startTransfer("hop-interlis-tests");
      writer.startBasket("HopIli_CHLV95_V1.Data", "b1");
      writer.writeObject(source);
      writer.endBasket();
      writer.endTransfer();
    }

    InterlisObjectEnvelope object =
        readObject(file, v1Model.transferDescription(), "HopIli_CHLV95_V1.Data.TestObject");
    assertThat(object.object().getattrobj("MPoly", 0).getobjecttag())
        .isEqualTo("GeometryCHLV95_V1.MultiSurface");
    assertThat(object.object().getattrobj("MLine", 0).getobjecttag())
        .isEqualTo("GeometryCHLV95_V1.MultiLine");
    assertThat(object.object().getattrobj("MDirectedLine", 0).getobjecttag())
        .isEqualTo("GeometryCHLV95_V1.MultiDirectedLine");

    Object[] row =
        new DefaultInterlisObjectToRowMapper()
            .map(envelope(object.object()), v1Plan);
    assertThat(row[field(v1Plan, "MPoly").outputIndex()]).isInstanceOf(MultiSurface.class);
    assertThat(row[field(v1Plan, "MLine").outputIndex()]).isInstanceOf(MultiCurve.class);
    assertThat(row[field(v1Plan, "MDirectedLine").outputIndex()]).isInstanceOf(MultiCurve.class);
  }

  @Test
  void v2_native_multi_geometries_roundtrip_through_row_mapping_and_xtf(@TempDir Path tempDir)
      throws Exception {
    Iom_jObject nativeSurface = new Iom_jObject("MULTISURFACE", null);
    nativeSurface.addattrobj("surface", surface(0.0));
    Iom_jObject nativePoints3D = new Iom_jObject("MULTICOORD", null);
    nativePoints3D.addattrobj("coord", coord3d(0.0, 0.0, 10.0));
    nativePoints3D.addattrobj("coord", coord3d(10.0, 0.0, 20.0));
    Iom_jObject nativeLine = new Iom_jObject("MULTIPOLYLINE", null);
    nativeLine.addattrobj("polyline", polyline(0.0));

    Geometry multiPoint3D =
        GEOMETRY_MAPPER.toHopGeometry(nativePoints3D, InterlisGeometryKind.MULTICOORD, 3);
    Geometry multiSurface =
        GEOMETRY_MAPPER.toHopGeometry(nativeSurface, InterlisGeometryKind.MULTISURFACE, 2);
    Geometry multiLine =
        GEOMETRY_MAPPER.toHopGeometry(nativeLine, InterlisGeometryKind.MULTIPOLYLINE, 2);
    Object[] values = values(v2Plan, "o1");
    values[field(v2Plan, "Points3D").outputIndex()] = multiPoint3D;
    values[field(v2Plan, "MPoly").outputIndex()] = multiSurface;
    values[field(v2Plan, "MLine").outputIndex()] = multiLine;
    IomObject source = new RowToIomMapper().map(values, v2Plan, RowWriteOptions.defaults());

    Path file = tempDir.resolve("chlv95-v2.xtf");
    try (InterlisTransferWriter writer =
        XtfTransferWriter.open(file, v2Model.transferDescription(), List.of("HopIli_CHLV95_V2"))) {
      writer.startTransfer("hop-interlis-tests");
      writer.startBasket("HopIli_CHLV95_V2.Data", "b1");
      writer.writeObject(source);
      writer.endBasket();
      writer.endTransfer();
    }

    InterlisObjectEnvelope object =
        readObject(file, v2Model.transferDescription(), "HopIli_CHLV95_V2.Data.TestObject");
    assertThat(object.object().getattrobj("Points3D", 0).getobjecttag()).isEqualTo("MULTICOORD");
    assertThat(object.object().getattrobj("MPoly", 0).getobjecttag()).isEqualTo("MULTISURFACE");
    assertThat(object.object().getattrobj("MLine", 0).getobjecttag()).isEqualTo("MULTIPOLYLINE");
    Object[] row = new DefaultInterlisObjectToRowMapper().map(envelope(object.object()), v2Plan);
    assertThat(((Geometry) row[field(v2Plan, "Points3D").outputIndex()]).getCoordinate().getZ())
        .isEqualTo(10.0);
    assertThat(row[field(v2Plan, "MPoly").outputIndex()]).isInstanceOf(MultiSurface.class);
    assertThat(row[field(v2Plan, "MLine").outputIndex()]).isInstanceOf(MultiCurve.class);
  }

  private static CompiledInterlisModel compile(
      InterlisModelService service, String fileName, ModelCompileOptions options) throws Exception {
    return service.compile(
        new ModelSource(List.of(TestResources.path("/models/" + fileName)), List.of(), List.of()),
        options);
  }

  private static InterlisRowMappingPlan plan(CompiledInterlisModel model, String className)
      throws Exception {
    InterlisSchemaDescriptor schema = new InterlisSchemaExtractor().extract(model.transferDescription());
    return new InterlisRowSchemaBuilder()
        .build(schema, schema.findClass(className).orElseThrow(), ProjectionOptions.defaults());
  }

  private static Object[] values(InterlisRowMappingPlan plan, String tid) {
    Object[] values = new Object[plan.fieldCount()];
    values[field(plan, "_ili_tid").outputIndex()] = tid;
    values[field(plan, "_ili_bid").outputIndex()] = "b1";
    return values;
  }

  private static ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan field(
      InterlisRowMappingPlan plan, String name) {
    return plan.fields().stream()
        .filter(field -> field.hopFieldName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing field " + name));
  }

  private static InterlisObjectEnvelope readObject(
      Path file, ch.interlis.ili2c.metamodel.TransferDescription td, String className)
      throws Exception {
    try (XtfTransferReader reader = XtfTransferReader.open(file, td)) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        if (event.eventType() == InterlisEventType.OBJECT && className.equals(event.className())) {
          return event;
        }
      }
    }
    throw new AssertionError("No object " + className + " in " + file);
  }

  private static InterlisObjectEnvelope envelope(IomObject object) {
    return new InterlisObjectEnvelope(
        InterlisEventType.OBJECT,
        object.getobjecttag().substring(0, object.getobjecttag().indexOf('.')),
        "HopIli_" + (object.getobjecttag().contains("V1") ? "CHLV95_V1" : "CHLV95_V2") + ".Data",
        "b1",
        object.getobjecttag(),
        object.getobjectoid(),
        InterlisObjectOperation.NONE,
        object);
  }

  private static IomObject legacySurface() {
    Iom_jObject wrapper = new Iom_jObject("GeometryCHLV95_V1.MultiSurface", null);
    wrapper.addattrobj("Surfaces", surfaceStructure(0.0));
    wrapper.addattrobj("Surfaces", surfaceStructure(20.0));
    return wrapper;
  }

  private static IomObject surfaceStructure(double offset) {
    Iom_jObject structure = new Iom_jObject("GeometryCHLV95_V1.SurfaceStructure", null);
    structure.addattrobj("Surface", surfaceGeometry(offset));
    return structure;
  }

  private static IomObject legacyMultiLine(String wrapperTag, String structureTag) {
    Iom_jObject wrapper = new Iom_jObject(wrapperTag, null);
    wrapper.addattrobj("Lines", legacyLine(structureTag, 0.0));
    wrapper.addattrobj("Lines", legacyLine(structureTag, 20.0));
    return wrapper;
  }

  private static IomObject legacyLine(String structureTag, double offset) {
    Iom_jObject structure = new Iom_jObject(structureTag, null);
    structure.addattrobj("Line", polyline(offset));
    return structure;
  }

  private static IomObject surface(double offset) {
    Iom_jObject surface = new Iom_jObject("SURFACE", null);
    Iom_jObject boundary = new Iom_jObject("BOUNDARY", null);
    boundary.addattrobj("polyline", polyline(offset));
    surface.addattrobj("boundary", boundary);
    return surface;
  }

  private static IomObject surfaceGeometry(double offset) {
    Iom_jObject geometry = new Iom_jObject("MULTISURFACE", null);
    geometry.addattrobj("surface", surface(offset));
    return geometry;
  }

  private static IomObject polyline(double offset) {
    Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
    polyline.addattrobj(
        "sequence",
        segments(
            coord(offset, 0.0), coord(offset + 10.0, 0.0), coord(offset + 10.0, 10.0),
            coord(offset, 0.0)));
    return polyline;
  }

  private static IomObject segments(IomObject... segmentObjects) {
    Iom_jObject segments = new Iom_jObject("SEGMENTS", null);
    for (IomObject segment : segmentObjects) {
      segments.addattrobj("segment", segment);
    }
    return segments;
  }

  private static IomObject coord(double c1, double c2) {
    Iom_jObject coord = new Iom_jObject("COORD", null);
    coord.setattrvalue("C1", Double.toString(c1));
    coord.setattrvalue("C2", Double.toString(c2));
    return coord;
  }

  private static IomObject coord3d(double c1, double c2, double c3) {
    Iom_jObject coord = (Iom_jObject) coord(c1, c2);
    coord.setattrvalue("C3", Double.toString(c3));
    return coord;
  }
}
