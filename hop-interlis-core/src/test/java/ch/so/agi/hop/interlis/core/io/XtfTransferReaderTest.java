package ch.so.agi.hop.interlis.core.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.TestResources;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class XtfTransferReaderTest {

  @Test
  void reads_xtf_transfer_events_in_order() throws Exception {
    List<InterlisObjectEnvelope> events = readAll("HopIli_Geometry_V1_valid.xtf");

    assertThat(events)
        .extracting(InterlisObjectEnvelope::eventType)
        .containsExactly(
            InterlisEventType.START_TRANSFER,
            InterlisEventType.START_BASKET,
            InterlisEventType.OBJECT,
            InterlisEventType.OBJECT,
            InterlisEventType.END_BASKET,
            InterlisEventType.END_TRANSFER);
  }

  @Test
  void detects_model_names_from_xtf_header() throws Exception {
    try (XtfTransferReader reader =
        XtfTransferReader.open(TestResources.path("/data/HopIli_Geometry_V1_valid.xtf"))) {
      reader.next(); // START_TRANSFER
      assertThat(reader.detectedModelNames()).containsExactly("HopIli_Geometry_V1");
    }
  }

  @Test
  void exposes_object_tag_tid_basket_and_topic() throws Exception {
    List<InterlisObjectEnvelope> objects = readObjects("HopIli_Geometry_V1_valid.xtf");

    InterlisObjectEnvelope first = objects.get(0);
    assertThat(first.className()).isEqualTo("HopIli_Geometry_V1.Data.TestObject");
    assertThat(first.objectId()).isEqualTo("o1");
    assertThat(first.basketId()).isEqualTo("b1");
    assertThat(first.topicName()).isEqualTo("HopIli_Geometry_V1.Data");
    assertThat(first.modelName()).isEqualTo("HopIli_Geometry_V1");
  }

  @Test
  void exposes_attribute_values() throws Exception {
    List<InterlisObjectEnvelope> objects = readObjects("HopIli_Geometry_V1_valid.xtf");

    assertThat(objects.get(0).object().getattrvalue("Name")).isEqualTo("A");
    assertThat(objects.get(1).object().getattrvalue("Name")).isEqualTo("B");
  }

  @Test
  void exposes_geometry_values_as_iom_structures() throws Exception {
    List<InterlisObjectEnvelope> objects = readObjects("HopIli_Geometry_V1_valid.xtf");

    IomObject first = objects.get(0).object();
    assertThat(first.getattrvaluecount("Center")).isEqualTo(1);
    IomObject center = first.getattrobj("Center", 0);
    assertThat(center.getobjecttag()).isEqualTo("COORD");
    assertThat(center.getattrvalue("C1")).isEqualTo("2600000.000");

    IomObject axis = first.getattrobj("Axis", 0);
    assertThat(axis.getobjecttag()).isEqualTo("POLYLINE");

    IomObject boundary = first.getattrobj("Boundary", 0);
    assertThat(boundary.getobjecttag()).isIn("SURFACE", "MULTISURFACE");

    // The second object's axis contains an ARC segment.
    IomObject arcAxis = objects.get(1).object().getattrobj("Axis", 0);
    assertThat(arcHasArcSegment(arcAxis)).isTrue();
  }

  @Test
  void reads_inherited_attribute_and_structure_from_transfer() throws Exception {
    List<InterlisObjectEnvelope> objects = readObjects("HopIli_Spike_V1_valid.xtf");

    IomObject building = objects.get(0).object();
    assertThat(building.getobjecttag()).isEqualTo("HopIli_Spike_V1.Data.Building");
    assertThat(building.getattrvalue("Note")).isEqualTo("inherited note");
    assertThat(building.getattrvalue("Code")).isEqualTo("42");

    IomObject address = building.getattrobj("Address", 0);
    assertThat(address.getobjecttag()).isEqualTo("HopIli_Spike_V1.Data.Address");
    assertThat(address.getattrvalue("Street")).isEqualTo("Main Street");
    assertThat(address.getattrvalue("Number")).isEqualTo("10");
  }

  private static boolean arcHasArcSegment(IomObject polyline) {
    IomObject sequence = polyline.getattrobj("sequence", 0);
    if (sequence == null) {
      return false;
    }
    for (int i = 0; i < sequence.getattrvaluecount("segment"); i++) {
      IomObject segment = sequence.getattrobj("segment", i);
      if ("ARC".equals(segment.getobjecttag())) {
        return true;
      }
    }
    return false;
  }

  private static List<InterlisObjectEnvelope> readAll(String resource) throws Exception {
    try (XtfTransferReader reader =
        XtfTransferReader.open(TestResources.path("/data/" + resource))) {
      List<InterlisObjectEnvelope> events = new ArrayList<>();
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        events.add(event);
      }
      return events;
    }
  }

  private static List<InterlisObjectEnvelope> readObjects(String resource) throws Exception {
    return readAll(resource).stream()
        .filter(e -> e.eventType() == InterlisEventType.OBJECT)
        .toList();
  }
}
