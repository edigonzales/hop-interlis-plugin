package ch.so.agi.hop.interlis.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowSchemaBuilder;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.mapping.RowToIomMapper;
import ch.so.agi.hop.interlis.core.mapping.RowWriteOptions;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XtfTransferWriterTest {

  @TempDir Path tempDir;

  private static CompiledInterlisModel model;
  private static InterlisSchemaDescriptor schema;
  private static InterlisRowMappingPlan plan;

  @BeforeAll
  static void compile() throws Exception {
    InterlisModelService service = new InterlisModelServiceImpl();
    model =
        service.compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Spike_V1.ili")),
                List.of(),
                List.of()),
            ModelCompileOptions.defaults());
    schema = new InterlisSchemaExtractor().extract(model.transferDescription());
    plan =
        new InterlisRowSchemaBuilder()
            .build(
                schema,
                schema.findClass("HopIli_Spike_V1.Data.Building").orElseThrow(),
                ProjectionOptions.defaults());
  }

  private IomObject building(String tid) throws Exception {
    // _ili_tid, _ili_bid, Code, Location, Address_Street, Address_Number, Municipality_ref, Note
    Object[] values = {tid, "b1", 42L, null, "Main Street", "10", "m1", "inherited note"};
    return new RowToIomMapper().map(values, plan, RowWriteOptions.defaults());
  }

  @Test
  void written_transfer_can_be_read_back_with_same_values() throws Exception {
    Path file = tempDir.resolve("roundtrip.xtf");

    try (InterlisTransferWriter writer =
        XtfTransferWriter.open(file, model.transferDescription(), List.of("HopIli_Spike_V1"))) {
      writer.startTransfer("hop-interlis-tests");
      writer.startBasket("HopIli_Spike_V1.Data", "b1");
      writer.writeObject(building("g1"));
      writer.endBasket();
      writer.startBasket("HopIli_Spike_V1.Data", "b2");
      writer.writeObject(building("g2"));
      writer.endBasket();
      writer.endTransfer();
    }

    List<InterlisObjectEnvelope> events = readAll(file);
    assertThat(events)
        .extracting(InterlisObjectEnvelope::eventType)
        .containsExactly(
            InterlisEventType.START_TRANSFER,
            InterlisEventType.START_BASKET,
            InterlisEventType.OBJECT,
            InterlisEventType.END_BASKET,
            InterlisEventType.START_BASKET,
            InterlisEventType.OBJECT,
            InterlisEventType.END_BASKET,
            InterlisEventType.END_TRANSFER);

    InterlisObjectEnvelope first = events.stream()
        .filter(e -> e.eventType() == InterlisEventType.OBJECT)
        .findFirst()
        .orElseThrow();
    assertThat(first.className()).isEqualTo("HopIli_Spike_V1.Data.Building");
    assertThat(first.objectId()).isEqualTo("g1");
    assertThat(first.basketId()).isEqualTo("b1");
    assertThat(first.object().getattrvalue("Code")).isEqualTo("42");
    assertThat(first.object().getattrvalue("Note")).isEqualTo("inherited note");

    IomObject address = first.object().getattrobj("Address", 0);
    assertThat(address.getattrvalue("Street")).isEqualTo("Main Street");
    assertThat(address.getattrvalue("Number")).isEqualTo("10");
    assertThat(first.object().getattrobj("Municipality", 0).getobjectrefoid()).isEqualTo("m1");

    InterlisObjectEnvelope second = events.stream()
        .filter(e -> e.eventType() == InterlisEventType.OBJECT)
        .skip(1)
        .findFirst()
        .orElseThrow();
    assertThat(second.objectId()).isEqualTo("g2");
    assertThat(second.basketId()).isEqualTo("b2");
  }

  @Test
  void written_header_declares_the_model() throws Exception {
    Path file = tempDir.resolve("header.xtf");

    try (InterlisTransferWriter writer =
        XtfTransferWriter.open(file, model.transferDescription(), List.of("HopIli_Spike_V1"))) {
      writer.startTransfer("hop-interlis-tests");
      writer.startBasket("HopIli_Spike_V1.Data", "b1");
      writer.endBasket();
      writer.endTransfer();
    }

    try (XtfTransferReader reader = XtfTransferReader.open(file)) {
      reader.next(); // START_TRANSFER
      assertThat(reader.detectedModelNames()).containsExactly("HopIli_Spike_V1");
    }
  }

  @Test
  void event_order_is_enforced() throws Exception {
    Path file = tempDir.resolve("order.xtf");
    InterlisTransferWriter writer =
        XtfTransferWriter.open(file, model.transferDescription(), List.of("HopIli_Spike_V1"));

    assertThatThrownBy(() -> writer.writeObject(new Iom_jObject("HopIli_Spike_V1.Data.Building", "g1")))
        .isInstanceOf(InterlisWriteException.class)
        .hasMessageContaining("started");

    writer.startTransfer("sender");
    writer.startBasket("HopIli_Spike_V1.Data", "b1");
    writer.endBasket();
    writer.endTransfer();

    // Writing after the transfer was ended fails.
    assertThatThrownBy(() -> writer.writeObject(new Iom_jObject("HopIli_Spike_V1.Data.Building", "g1")))
        .isInstanceOf(InterlisWriteException.class)
        .hasMessageContaining("started");

    writer.close();
  }

  @Test
  void geometry_arcs_survive_the_write_read_cycle() throws Exception {
    CompiledInterlisModel geometryModel =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(TestResources.path("/models/HopIli_Geometry_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());
    InterlisSchemaDescriptor geometrySchema =
        new InterlisSchemaExtractor().extract(geometryModel.transferDescription());
    InterlisRowMappingPlan geometryPlan =
        new InterlisRowSchemaBuilder()
            .build(
                geometrySchema,
                geometrySchema.findClass("HopIli_Geometry_V1.Data.TestObject").orElseThrow(),
                ProjectionOptions.defaults());

    org.locationtech.jts.geom.GeometryFactory factory =
        new org.locationtech.jts.geom.GeometryFactory();
    org.locationtech.jts.geom.LineString straight =
        factory.createLineString(
            new org.locationtech.jts.geom.Coordinate[] {
              new org.locationtech.jts.geom.Coordinate(0, 0),
              new org.locationtech.jts.geom.Coordinate(5, 0)
            });
    com.atolcd.hop.gis.geometry.curve.CircularString arc =
        new com.atolcd.hop.gis.geometry.curve.CircularString(
            new org.locationtech.jts.geom.Coordinate[] {
              new org.locationtech.jts.geom.Coordinate(5, 0),
              new org.locationtech.jts.geom.Coordinate(7.5, 2.5),
              new org.locationtech.jts.geom.Coordinate(10, 0)
            },
            factory);
    com.atolcd.hop.gis.geometry.curve.CompoundCurve axis =
        new com.atolcd.hop.gis.geometry.curve.CompoundCurve(List.of(straight, arc), factory);

    // _ili_tid, _ili_bid, Name, Center, Points, Axis, Axes, Boundary, Area, Surfaces
    Object[] values = {"o1", "b1", "A", null, null, axis, null, null, null, null};
    IomObject sourceObject =
        new RowToIomMapper().map(values, geometryPlan, RowWriteOptions.defaults());

    Path file = tempDir.resolve("arc.xtf");
    try (InterlisTransferWriter writer =
        XtfTransferWriter.open(
            file, geometryModel.transferDescription(), List.of("HopIli_Geometry_V1"))) {
      writer.startTransfer("hop-interlis-tests");
      writer.startBasket("HopIli_Geometry_V1.Data", "b1");
      writer.writeObject(sourceObject);
      writer.endBasket();
      writer.endTransfer();
    }

    InterlisObjectEnvelope readObject = readAll(file, geometryModel.transferDescription()).stream()
        .filter(e -> e.eventType() == InterlisEventType.OBJECT)
        .findFirst()
        .orElseThrow();
    IomObject polyline = readObject.object().getattrobj("Axis", 0);
    IomObject sequence = polyline.getattrobj("sequence", 0);
    IomObject arcSegment = sequence.getattrobj("segment", 2);
    assertThat(arcSegment.getobjecttag()).isEqualTo("ARC");
    assertThat(arcSegment.getattrvalue("A1")).isEqualTo("7.5");
    assertThat(arcSegment.getattrvalue("A2")).isEqualTo("2.5");
  }

  private static List<InterlisObjectEnvelope> readAll(Path file) throws Exception {
    return readAll(file, null);
  }

  private static List<InterlisObjectEnvelope> readAll(
      Path file, ch.interlis.ili2c.metamodel.TransferDescription td) throws Exception {
    try (XtfTransferReader reader = XtfTransferReader.open(file, td)) {
      java.util.ArrayList<InterlisObjectEnvelope> events = new java.util.ArrayList<>();
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        events.add(event);
      }
      return events;
    }
  }
}
