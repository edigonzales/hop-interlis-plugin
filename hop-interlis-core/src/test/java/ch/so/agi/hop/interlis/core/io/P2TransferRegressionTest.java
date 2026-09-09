package ch.so.agi.hop.interlis.core.io;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.xtf.XtfWriterBase;
import ch.interlis.iox.IoxException;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P2TransferRegressionTest {
  @TempDir Path dir;

  CompiledInterlisModel model() throws Exception {
    return new InterlisModelServiceImpl()
        .compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Spike_V1.ili")), List.of(), List.of()),
            ModelCompileOptions.defaults());
  }

  @Test
  void header_semantics_and_versioned_json_survive_transfer_roundtrip() throws Exception {
    var model = model();
    var metadata =
        new InterlisTransferMetadata(
            "P2 sender",
            "Comment ä \"test\"",
            "2.3",
            List.of(
                new InterlisTransferMetadata.ModelEntry(
                    "HopIli_Spike_V1", "https://example.org", "v1")),
            List.of(),
            List.of());
    assertThat(InterlisTransferMetadata.fromJson(metadata.toJson())).isEqualTo(metadata);
    Path file = dir.resolve("header.xtf");
    try (var writer =
        XtfTransferWriter.open(file, model.transferDescription(), model.compiledModelNames())) {
      writer.startTransfer(metadata);
      writer.endTransfer();
      writer.requireComplete();
      assertThatThrownBy(() -> writer.startTransfer("again"))
          .isInstanceOf(InterlisWriteException.class);
    }
    try (var reader = XtfTransferReader.open(file, model.transferDescription())) {
      assertThat(reader.next().transferMetadata()).isEqualTo(metadata);
    }
    assertThatThrownBy(() -> InterlisTransferMetadata.fromJson("{\"schemaVersion\":2}"))
        .hasMessageContaining("schemaVersion");
  }

  @Test
  void reader_reports_oid_space_name_loss_instead_of_claiming_preservation() throws Exception {
    var event = new ch.interlis.iom_j.xtf.XtfStartTransferEvent("sender");
    event.addOidSpace(new ch.interlis.iom_j.xtf.OidSpace("oidSpace0", "INTERLIS.STANDARDOID"));
    var metadata = InterlisTransferMetadata.fromEvent(event);
    assertThat(metadata.unsupportedHeaders()).anyMatch(s -> s.contains("OID-space"));
    var model = model();
    try (var writer =
        XtfTransferWriter.open(
            dir.resolve("unsupported.xtf"),
            model.transferDescription(),
            model.compiledModelNames())) {
      assertThatThrownBy(() -> writer.startTransfer(metadata)).hasMessageContaining("OID-space");
    }
  }

  @Test
  void missing_end_transfer_is_rejected() throws Exception {
    var model = model();
    try (var writer =
        XtfTransferWriter.open(
            dir.resolve("incomplete.xtf"),
            model.transferDescription(),
            model.compiledModelNames())) {
      writer.startTransfer("test");
      assertThatThrownBy(writer::requireComplete).hasMessageContaining("END_TRANSFER");
    }
  }

  @Test
  void close_is_attempted_after_flush_failure_and_both_errors_are_retained() throws Exception {
    boolean[] closed = {false};
    var output =
        new XtfWriterBase(
            new java.io.OutputStreamWriter(new java.io.ByteArrayOutputStream()), null, "2.3") {
          @Override
          public void flush() throws IoxException {
            throw new IoxException("flush failed");
          }

          @Override
          public void close() throws IoxException {
            closed[0] = true;
            throw new IoxException("close failed");
          }
        };
    var writer = new XtfTransferWriter(dir.resolve("fake.xtf"), output, "2.3");
    assertThatThrownBy(writer::close)
        .hasMessageContaining("flush failed")
        .satisfies(e -> assertThat(e.getCause().getSuppressed()).hasSize(1));
    assertThat(closed[0]).isTrue();
    writer.close();
  }
}
