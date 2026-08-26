package ch.so.agi.hop.interlis.core.io;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterlisBasketMetadataTest {

  @Test
  void maps_iom_codes_to_names_and_back() {
    InterlisBasketMetadata metadata =
        InterlisBasketMetadata.fromIom(
            ch.interlis.iom.IomConstants.IOM_INCOMPLETE,
            ch.interlis.iom.IomConstants.IOM_UPDATE,
            "started",
            null);

    assertThat(metadata.consistency()).isEqualTo("INCOMPLETE");
    assertThat(metadata.kind()).isEqualTo("UPDATE");
    assertThat(metadata.startState()).isEqualTo("started");
    assertThat(metadata.endState()).isNull();
    assertThat(metadata.consistencyIom()).isEqualTo(ch.interlis.iom.IomConstants.IOM_INCOMPLETE);
    assertThat(metadata.kindIom()).isEqualTo(ch.interlis.iom.IomConstants.IOM_UPDATE);
  }

  @Test
  void unset_values_are_null_and_empty() {
    InterlisBasketMetadata metadata =
        InterlisBasketMetadata.fromIom(0, 0, null, null);

    assertThat(metadata.consistency()).isNull();
    assertThat(metadata.kind()).isNull();
    assertThat(metadata.isEmpty()).isTrue();
    assertThat(metadata.consistencyIom()).isEqualTo(-1);
    assertThat(metadata.kindIom()).isEqualTo(-1);
  }

  @Test
  void reader_carries_basket_metadata_on_basket_and_object_envelopes() throws Exception {
    CompiledInterlisModel model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(TestResources.path("/models/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());

    List<InterlisObjectEnvelope> events = new ArrayList<>();
    try (XtfTransferReader reader =
        XtfTransferReader.open(
            TestResources.path("/data/HopIli_Associations_V1_basketmeta.xtf"),
            model.transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        events.add(event);
      }
    }

    InterlisObjectEnvelope startBasket =
        events.stream()
            .filter(e -> e.eventType() == InterlisEventType.START_BASKET)
            .findFirst()
            .orElseThrow();
    assertThat(startBasket.basket().consistency()).isEqualTo("INCOMPLETE");
    assertThat(startBasket.basket().kind()).isEqualTo("UPDATE");
    assertThat(startBasket.basket().startState()).isEqualTo("started");
    assertThat(startBasket.basket().endState()).isEqualTo("finished");

    InterlisObjectEnvelope object =
        events.stream()
            .filter(e -> e.eventType() == InterlisEventType.OBJECT)
            .findFirst()
            .orElseThrow();
    assertThat(object.basket().consistency()).isEqualTo("INCOMPLETE");
    assertThat(object.basket().kind()).isEqualTo("UPDATE");
  }

  @Test
  void writer_roundtrips_basket_metadata() throws Exception {
    CompiledInterlisModel model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(TestResources.path("/models/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());
    java.nio.file.Path out =
        java.nio.file.Files.createTempFile("basketmeta", ".xtf");

    InterlisBasketMetadata metadata =
        new InterlisBasketMetadata("INCOMPLETE", "UPDATE", "started", "finished");
    try (InterlisTransferWriter writer =
        XtfTransferWriter.open(out, model.transferDescription(), List.of("HopIli_Associations_V1"))) {
      writer.startTransfer("test");
      writer.startBasket("HopIli_Associations_V1.Data", "b1", metadata);
      ch.interlis.iom_j.Iom_jObject person =
          new ch.interlis.iom_j.Iom_jObject("HopIli_Associations_V1.Data.Person", "p1");
      person.setattrvalue("Name", "Meier");
      writer.writeObject(person);
      writer.endBasket();
      writer.endTransfer();
    }

    List<InterlisObjectEnvelope> events = new ArrayList<>();
    try (XtfTransferReader reader =
        XtfTransferReader.open(out, model.transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        events.add(event);
      }
    }
    InterlisObjectEnvelope startBasket =
        events.stream()
            .filter(e -> e.eventType() == InterlisEventType.START_BASKET)
            .findFirst()
            .orElseThrow();
    assertThat(startBasket.basket().consistency()).isEqualTo("INCOMPLETE");
    assertThat(startBasket.basket().kind()).isEqualTo("UPDATE");
    assertThat(startBasket.basket().startState()).isEqualTo("started");
    assertThat(startBasket.basket().endState()).isEqualTo("finished");
  }

  @Test
  void writer_rejects_update_baskets_without_states_with_a_clear_error() throws Exception {
    CompiledInterlisModel model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(TestResources.path("/models/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());
    java.nio.file.Path out =
        java.nio.file.Files.createTempFile("basketmeta", ".xtf");

    try (InterlisTransferWriter writer =
        XtfTransferWriter.open(out, model.transferDescription(), List.of("HopIli_Associations_V1"))) {
      writer.startTransfer("test");
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  writer.startBasket(
                      "HopIli_Associations_V1.Data",
                      "b1",
                      new InterlisBasketMetadata("INCOMPLETE", "UPDATE", null, null)))
          .isInstanceOf(InterlisWriteException.class)
          .hasMessageContaining("require");
    }
  }
}
