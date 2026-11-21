package ch.so.agi.hop.interlis.transforms.transferinput;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.InterlisReadException;
import ch.so.agi.hop.interlis.core.io.InterlisTransferReader;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.TestData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.rowstoresult.RowsToResultMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InterlisTransferInputPipelineTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private List<RowMetaAndData> run(String fileName, TransferInputMode mode) throws Exception {
    return run(TestData.path(fileName), mode);
  }

  private List<RowMetaAndData> run(Path file, TransferInputMode mode) throws Exception {
    Pipeline engine = prepare(file, mode);
    engine.startThreads();
    engine.waitUntilFinished();
    assertThat(engine.getErrors()).isZero();
    return engine.getResultRows();
  }

  private Pipeline prepare(Path file, TransferInputMode mode) throws Exception {
    InterlisTransferInputMeta meta = new InterlisTransferInputMeta();
    meta.setFileName(file.toString());
    meta.setModelNames(InterlisModelSourceSupport.MODELS_FROM_DATA);
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setMode(mode.name());

    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source =
        new TransformMeta("INTERLIS_TRANSFER_INPUT", "INTERLIS Transfer Input", meta);
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    Pipeline engine =
        (Pipeline) PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    return engine;
  }

  @Test
  void emits_constant_envelope_schema_in_objects_mode() throws Exception {
    List<RowMetaAndData> rows =
        run("/data/HopIli_Associations_V1_mapping.xtf", TransferInputMode.OBJECTS);

    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactlyElementsOf(InterlisEnvelopeRowLayout.FIELD_NAMES);
    // Only OBJECT rows: the fixture contains 14 objects.
    assertThat(rows).hasSize(14);
    assertThat(rows)
        .allSatisfy(
            row ->
                assertThat(row.getData()[InterlisEnvelopeRowLayout.EVENT_TYPE_INDEX])
                    .isEqualTo("OBJECT"));
    RowMetaAndData first = rows.get(0);
    assertThat(first.getData()[InterlisEnvelopeRowLayout.CLASS_INDEX])
        .isEqualTo("HopIli_Associations_V1.Data.Person");
    assertThat(first.getData()[InterlisEnvelopeRowLayout.TID_INDEX]).isEqualTo("p1");
    assertThat(first.getData()[InterlisEnvelopeRowLayout.BID_INDEX]).isEqualTo("b1");
    assertThat(first.getData()[InterlisEnvelopeRowLayout.OBJECT_INDEX])
        .isInstanceOf(ch.interlis.iom.IomObject.class);
  }

  @Test
  void emits_the_exact_event_sequence_in_events_mode() throws Exception {
    List<RowMetaAndData> rows = run(transferWithEmptyBaskets(1, true), TransferInputMode.EVENTS);

    assertThat(rows)
        .extracting(row -> row.getData()[InterlisEnvelopeRowLayout.EVENT_TYPE_INDEX])
        .containsExactly(
            "START_TRANSFER",
            "START_BASKET",
            "END_BASKET",
            "START_BASKET",
            "OBJECT",
            "END_BASKET",
            "END_TRANSFER");
    assertThat(rows)
        .extracting(row -> row.getData()[InterlisEnvelopeRowLayout.BID_INDEX])
        .containsExactly(null, "b0", "b0", "last", "last", "last", null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void skips_twenty_thousand_empty_baskets_in_objects_mode(boolean withObject) throws Exception {
    List<RowMetaAndData> rows =
        run(transferWithEmptyBaskets(20_000, withObject), TransferInputMode.OBJECTS);

    if (withObject) {
      assertThat(rows)
          .singleElement()
          .satisfies(
              row -> {
                assertThat(row.getData()[InterlisEnvelopeRowLayout.TID_INDEX]).isEqualTo("m1");
                assertThat(row.getData()[InterlisEnvelopeRowLayout.BID_INDEX]).isEqualTo("last");
                assertThat(row.getData()[InterlisEnvelopeRowLayout.CLASS_INDEX])
                    .isEqualTo("HopIli_Spike_V1.Data.Municipality");
              });
    } else {
      assertThat(rows).isEmpty();
    }
  }

  @Test
  @Timeout(10)
  void stops_reading_during_a_run_of_empty_baskets_and_closes_the_reader() throws Exception {
    Path file = transferWithEmptyBaskets(20_000, true);
    Pipeline engine = prepare(file, TransferInputMode.OBJECTS);
    var transform = (InterlisTransferInput) engine.getTransform("INTERLIS Transfer Input", 0);
    var data = transform.getData();
    var basketEvents = new AtomicInteger();
    var closed = new AtomicBoolean();
    try (var reader = XtfTransferReader.open(file)) {
      assertThat(reader.next().eventType()).isEqualTo(InterlisEventType.START_TRANSFER);
      data.initialized = true;
      data.outputRowMeta = InterlisEnvelopeSchemaFactory.createRowMeta();
      data.reader =
          new InterlisTransferReader() {
            @Override
            public InterlisObjectEnvelope next() throws InterlisReadException {
              var event = reader.next();
              if (basketEvents.incrementAndGet() == 20) {
                engine.stopAll();
              }
              return event;
            }

            @Override
            public TransferDescription transferDescription() {
              return reader.transferDescription();
            }

            @Override
            public void close() throws InterlisReadException {
              reader.close();
              closed.set(true);
            }
          };
      engine.startThreads();
      engine.waitUntilFinished();
      assertThat(engine.getErrors()).isZero();
      assertThat(engine.getResultRows()).isEmpty();
      assertThat(basketEvents.get()).isEqualTo(20);
      assertThat(closed.get()).isTrue();
    }
  }

  private Path transferWithEmptyBaskets(int emptyBaskets, boolean withObject) throws Exception {
    Path file = tempDir.resolve("baskets.xtf");
    try (var writer = Files.newBufferedWriter(file)) {
      writer.write(
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
            <HEADERSECTION VERSION="2.3" SENDER="test">
              <MODELS>
                <MODEL NAME="HopIli_Spike_V1" VERSION="2026-08-25" URI="https://hop.interlis.example/models"/>
              </MODELS>
            </HEADERSECTION>
            <DATASECTION>
          """);
      for (int i = 0; i < emptyBaskets; i++) {
        writer.write("    <HopIli_Spike_V1.Data BID=\"b" + i + "\"/>\n");
      }
      if (withObject) {
        writer.write(
            """
                <HopIli_Spike_V1.Data BID="last">
                  <HopIli_Spike_V1.Data.Municipality TID="m1">
                    <Name>Meier</Name>
                  </HopIli_Spike_V1.Data.Municipality>
                </HopIli_Spike_V1.Data>
            """);
      }
      writer.write("  </DATASECTION>\n</TRANSFER>\n");
    }
    return file;
  }

  @Test
  void carries_delete_operations_in_object_rows() throws Exception {
    List<RowMetaAndData> rows =
        run("/data/HopIli_Associations_V1_delete.xtf", TransferInputMode.OBJECTS);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getData()[InterlisEnvelopeRowLayout.OPERATION_INDEX])
        .isEqualTo("DELETE");
  }

  @Test
  void mixed_classes_travel_in_one_stream() throws Exception {
    List<RowMetaAndData> rows =
        run("/data/HopIli_Associations_V1_mapping.xtf", TransferInputMode.OBJECTS);

    assertThat(rows)
        .extracting(row -> row.getData()[InterlisEnvelopeRowLayout.CLASS_INDEX])
        .contains(
            "HopIli_Associations_V1.Data.Person",
            "HopIli_Associations_V1.Data.Organisation",
            "HopIli_Associations_V1.Data.AddressOwnership",
            "HopIli_Associations_V1.Data.Membership",
            "HopIli_Associations_V1.Data.PersonTask");
  }
}
