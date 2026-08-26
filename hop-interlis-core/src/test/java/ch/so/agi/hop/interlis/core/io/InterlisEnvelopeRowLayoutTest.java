package ch.so.agi.hop.interlis.core.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.TestResources;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterlisEnvelopeRowLayoutTest {

  @Test
  void maps_envelope_to_row_values_in_layout_order() {
    Iom_jObject object = new Iom_jObject("Model.Topic.ClassA", "t1");
    object.setattrvalue("Name", "X");
    InterlisObjectEnvelope envelope =
        new InterlisObjectEnvelope(
            InterlisEventType.OBJECT,
            "Model",
            "Model.Topic",
            "b1",
            "Model.Topic.ClassA",
            "t1",
            InterlisObjectOperation.DELETE,
            object);

    Object[] row = InterlisEnvelopeRowLayout.toRow(envelope);

    assertThat(row)
        .containsExactly(
            "OBJECT", "Model", "Model.Topic", "b1", "Model.Topic.ClassA", "t1", "DELETE", object,
            null, null, null, null, null, null);
    assertThat(InterlisEnvelopeRowLayout.fieldCount()).isEqualTo(14);
  }

  @Test
  void maps_row_values_back_to_envelope() {
    Iom_jObject object = new Iom_jObject("Model.Topic.ClassA", "t1");
    Object[] row =
        new Object[] {
          "OBJECT", "Model", "Model.Topic", "b1", "Model.Topic.ClassA", "t1", "DELETE", object,
          null, null, null, null, null, null
        };

    InterlisObjectEnvelope envelope = InterlisEnvelopeRowLayout.fromRow(row);

    assertThat(envelope.eventType()).isEqualTo(InterlisEventType.OBJECT);
    assertThat(envelope.modelName()).isEqualTo("Model");
    assertThat(envelope.topicName()).isEqualTo("Model.Topic");
    assertThat(envelope.basketId()).isEqualTo("b1");
    assertThat(envelope.className()).isEqualTo("Model.Topic.ClassA");
    assertThat(envelope.objectId()).isEqualTo("t1");
    assertThat(envelope.operation()).isEqualTo(InterlisObjectOperation.DELETE);
    assertThat(envelope.object()).isSameAs(object);
  }

  @Test
  void tolerates_unknown_event_and_operation_values() {
    Object[] row =
        new Object[] {
          "SOMETHING_NEW", null, null, null, null, null, "WHATEVER", null, null, null,
          null, null, null, null
        };

    InterlisObjectEnvelope envelope = InterlisEnvelopeRowLayout.fromRow(row);

    assertThat(envelope.eventType()).isEqualTo(InterlisEventType.OBJECT);
    assertThat(envelope.operation()).isEqualTo(InterlisObjectOperation.NONE);
  }

  @Test
  void object_row_carries_topic_and_basket() {
    Iom_jObject object = new Iom_jObject("Model.Topic.ClassA", "t1");

    Object[] row =
        InterlisEnvelopeRowLayout.objectRow(object, "b1", "Model.Topic", InterlisObjectOperation.INSERT);

    assertThat(InterlisEnvelopeRowLayout.eventType(row)).isEqualTo("OBJECT");
    assertThat(InterlisEnvelopeRowLayout.basketId(row)).isEqualTo("b1");
    assertThat(InterlisEnvelopeRowLayout.objectId(row)).isEqualTo("t1");
    assertThat(InterlisEnvelopeRowLayout.className(row)).isEqualTo("Model.Topic.ClassA");
    assertThat(InterlisEnvelopeRowLayout.object(row)).isSameAs(object);
    assertThat(row[InterlisEnvelopeRowLayout.MODEL_INDEX]).isEqualTo("Model");
  }

  @Test
  void reader_reports_delete_operation() throws Exception {
    ch.so.agi.hop.interlis.core.model.CompiledInterlisModel model =
        new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl()
            .compile(
                new ch.so.agi.hop.interlis.core.model.ModelSource(
                    List.of(TestResources.path("/models/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ch.so.agi.hop.interlis.core.model.ModelCompileOptions.defaults());
    try (XtfTransferReader reader =
        XtfTransferReader.open(
            TestResources.path("/data/HopIli_Associations_V1_delete.xtf"),
            model.transferDescription())) {
      List<InterlisObjectEnvelope> events = new java.util.ArrayList<>();
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        events.add(event);
      }

      InterlisObjectEnvelope object =
          events.stream()
              .filter(e -> e.eventType() == InterlisEventType.OBJECT)
              .findFirst()
              .orElseThrow();
      assertThat(object.operation()).isEqualTo(InterlisObjectOperation.DELETE);
      assertThat(object.object().getobjectoperation())
          .isEqualTo(ch.interlis.iom.IomConstants.IOM_OP_DELETE);
    }
  }

  @Test
  void operation_roundtrips_through_iom_codes() {
    for (InterlisObjectOperation operation : InterlisObjectOperation.values()) {
      if (operation == InterlisObjectOperation.NONE) {
        // NONE has no IOM code; writing it defaults to INSERT.
        assertThat(operation.toIom()).isEqualTo(ch.interlis.iom.IomConstants.IOM_OP_INSERT);
        continue;
      }
      assertThat(InterlisObjectOperation.fromIom(operation.toIom())).isEqualTo(operation);
    }
    assertThat(InterlisObjectOperation.fromIom(99)).isEqualTo(InterlisObjectOperation.NONE);
  }
}
