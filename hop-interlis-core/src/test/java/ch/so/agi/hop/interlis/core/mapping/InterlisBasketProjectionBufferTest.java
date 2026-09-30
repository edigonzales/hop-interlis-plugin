package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class InterlisBasketProjectionBufferTest {
  static final String TOPIC = "HopIli_Associations_V1.Data";

  static InterlisRowMappingPlan plan() throws Exception {
    var model = TestResources.path("/models/HopIli_Associations_V1.ili");
    return new InterlisProjectionService()
        .project(
            new InterlisModelRequest(
                null, List.of("HopIli_Associations_V1"), List.of(model.getParent().toString())),
            TOPIC + ".Person",
            ProjectionOptions.defaults())
        .plan();
  }

  static InterlisObjectEnvelope envelope(String bid, Iom_jObject object) {
    return new InterlisObjectEnvelope(
        InterlisEventType.OBJECT,
        "HopIli_Associations_V1",
        TOPIC,
        bid,
        object.getobjecttag(),
        object.getobjectoid(),
        InterlisObjectOperation.NONE,
        object);
  }

  static Iom_jObject link(String tid) {
    var link = new Iom_jObject(TOPIC + ".AddressOwnership", null);
    link.addattrobj("Person", "REF").setobjectrefoid(tid);
    link.addattrobj("Address", "REF").setobjectrefoid("address");
    return link;
  }

  @Test
  void drains_link_only_baskets_and_keeps_batches_independent() throws Exception {
    var buffer = new InterlisBasketProjectionBuffer<Object>(plan());
    var firstLink = link("p1");
    buffer.add(envelope("one", firstLink), new Object());
    assertThat(buffer.bufferedLinkCount()).isEqualTo(1);
    assertThat(buffer.bufferedRowCount()).isZero();
    var first = buffer.drain();
    assertThat(first.rows()).isEmpty();
    assertThat(first.lookup().find("p1", "Address")).hasToString(firstLink.toString());
    assertThat(buffer.bufferedLinkCount()).isZero();
    assertThat(buffer.startsNewBasket(envelope("two", firstLink))).isFalse();
    var secondLink = link("p2");
    buffer.add(envelope("two", secondLink), null);
    var second = buffer.drain();
    assertThat(first.lookup().find("p2", "Address")).isNull();
    assertThat(second.lookup().find("p1", "Address")).isNull();
    assertThat(second.lookup().find("p2", "Address")).hasToString(secondLink.toString());
    buffer.clear();
    buffer.clear();
    assertThatThrownBy(() -> first.lookup().find("p1", "Address"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void clear_releases_context_and_large_basket_state_and_allows_reuse() throws Exception {
    var buffer = new InterlisBasketProjectionBuffer<Object>(plan());
    Object context = "context";
    for (int i = 0; i < 10_000; i++)
      buffer.add(envelope("large", new Iom_jObject(TOPIC + ".Person", "p" + i)), context);
    buffer.add(envelope("large", link("p0")), context);
    buffer.add(envelope("large", new Iom_jObject(TOPIC + ".Project", "ignored")), context);
    assertThat(buffer.bufferedRowCount()).isEqualTo(10_000);
    buffer.clear();
    assertThat(buffer.bufferedRowCount()).isZero();
    assertThat(buffer.bufferedLinkCount()).isZero();
    assertThat(buffer.drain().rows()).isEmpty();
    for (int i = 0; i < 100; i++) {
      buffer.add(envelope("basket" + i, new Iom_jObject(TOPIC + ".Person", "p" + i)), context);
      var batch = buffer.drain();
      assertThat(batch.rows()).hasSize(1);
      assertThat(batch.rows().getFirst().context()).isEqualTo(context);
      assertThat(buffer.bufferedRowCount()).isZero();
      batch.close();
    }
  }

  @TempDir Path temp;

  @Test
  void shared_targets_are_allowed_but_conflicting_links_for_one_owner_are_rejected()
      throws Exception {
    for (long budget : List.of(1L, 1L << 20)) {
      var buffer =
          new InterlisBasketProjectionBuffer<Object>(
              plan(),
              new ch.so.agi.hop.interlis.core.buffer.JavaRecordCodec<>(),
              new ch.so.agi.hop.interlis.core.buffer.SpillOptions(budget, temp, 0));
      try {
        buffer.add(envelope("b1", link("p1")), null);
        buffer.add(envelope("b1", link("p2")), null); // same Address, different owner
        var conflicting = link("p1");
        conflicting.getattrobj("Address", 0).setobjectrefoid("other");
        assertThatThrownBy(() -> buffer.add(envelope("b1", conflicting), null))
            .hasMessageContaining("Ambiguous")
            .hasMessageContaining("p1")
            .hasMessageContaining("b1");
        try (var batch = buffer.drain()) {
          assertThat(
                  batch.lookup().find("p1", "Address").getattrobj("Address", 0).getobjectrefoid())
              .isEqualTo("address");
          assertThat(batch.lookup().find("p2", "Address")).isNotNull();
        }
      } finally {
        buffer.clear();
      }
    }
  }

  @Test
  void processes_512_mib_in_a_128_mib_heap() throws Exception {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var output = temp.resolve("heap.log");
    Process child =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx128m",
                "-cp",
                System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")),
                HeapWorker.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(child.waitFor(60, TimeUnit.SECONDS)).as("heap test must terminate").isTrue();
      assertThat(child.exitValue()).withFailMessage(Files.readString(output)).isZero();
      assertThat(Files.readString(output)).contains("HEAP_OK rows=16384 bytes=536870912");
    } finally {
      if (child.isAlive()) child.destroyForcibly();
    }
  }

  @Test
  void one_large_basket_and_lookup_spill_under_128_mib_heap() throws Exception {
    for (String mode : List.of("basket", "lookup")) {
      var output = temp.resolve(mode + "-heap.log");
      var child =
          new ProcessBuilder(
                  Path.of(
                          System.getProperty("java.home"),
                          "bin",
                          System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                      .toString(),
                  "-Xmx128m",
                  "-cp",
                  System.getProperty(
                      "surefire.test.class.path", System.getProperty("java.class.path")),
                  SpillHeapWorker.class.getName(),
                  mode,
                  temp.toString())
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
      try {
        assertThat(child.waitFor(180, TimeUnit.SECONDS))
            .as("large " + mode + " must terminate")
            .isTrue();
        assertThat(child.exitValue()).withFailMessage(Files.readString(output)).isZero();
        assertThat(Files.readString(output))
            .contains("SPILL_HEAP_OK " + mode, "rows=9000", "peakHeap=", "disk=", "firstRowMs=");
      } finally {
        if (child.isAlive()) child.destroyForcibly().waitFor();
      }
      // Retained in Surefire output for reproducible performance measurements.
      System.out.print(Files.readString(output));
    }
    try (var paths = Files.list(temp)) {
      assertThat(paths.map(Path::getFileName).map(Path::toString).toList())
          .noneMatch(name -> name.startsWith("hop-interlis-spill-"));
    }
  }

  public static class SpillHeapWorker {
    public static void main(String[] args) throws Exception {
      var options =
          new ch.so.agi.hop.interlis.core.buffer.SpillOptions(8L << 20, Path.of(args[1]), 0);
      long started = System.nanoTime(), first = 0, serialized = 0, rows = 0, disk = 0;
      var observedPeak = new java.util.concurrent.atomic.AtomicLong();
      Thread sampler =
          Thread.ofPlatform()
              .daemon()
              .start(
                  () -> {
                    while (!Thread.currentThread().isInterrupted()) {
                      observedPeak.accumulateAndGet(
                          java.lang.management.ManagementFactory.getMemoryMXBean()
                              .getHeapMemoryUsage()
                              .getUsed(),
                          Math::max);
                      try {
                        Thread.sleep(5);
                      } catch (InterruptedException e) {
                        return;
                      }
                    }
                  });
      int payloadSize = 40 * 1024;
      var mapping = plan();
      var codec = new ch.so.agi.hop.interlis.core.buffer.JavaRecordCodec<byte[]>();
      if (args[0].equals("lookup")) {
        try (var store =
            new ch.so.agi.hop.interlis.core.buffer.SpillStore<byte[]>(codec, options)) {
          for (int i = 0; i < 9000; i++) {
            var bytes = new byte[payloadSize];
            Arrays.fill(bytes, (byte) (i & 127));
            serialized += codec.encode(bytes).length;
            if (!store.putIfAbsent("id" + i, bytes)) throw new AssertionError("duplicate");
          }
          for (int i = 0; i < 9000; i++) {
            var bytes = store.get("id" + i);
            if (first == 0) first = System.nanoTime();
            if (bytes.length != payloadSize || bytes[0] != (byte) (i & 127))
              throw new AssertionError("lookup data lost");
            rows++;
          }
          disk = store.peakDiskBytes();
        }
      } else {
        var entryCodec =
            new ch.so.agi.hop.interlis.core.buffer.JavaRecordCodec<
                InterlisBasketProjectionBuffer.Entry<byte[]>>();
        var buffer = new InterlisBasketProjectionBuffer<byte[]>(mapping, entryCodec, options);
        for (int i = 0; i < 9000; i++) {
          var bytes = new byte[payloadSize];
          Arrays.fill(bytes, (byte) (i & 127));
          var object = new Iom_jObject(TOPIC + ".Person", "p" + i);
          object.setattrvalue("Name", "name");
          object.setattrvalue(
              "Payload", new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1));
          var e = envelope("single-large-basket", object);
          serialized +=
              entryCodec.encode(new InterlisBasketProjectionBuffer.Entry<>(e, bytes)).length;
          buffer.add(e, bytes);
        }
        // Do not collect projected rows: the consumer processes exactly one at a time.
        try (var batch = buffer.drain()) {
          for (var entry : batch.rows()) {
            if (first == 0) first = System.nanoTime();
            var values =
                new DefaultInterlisObjectToRowMapper()
                    .map(entry.envelope(), mapping, batch.lookup());
            if (values.length == 0 || entry.context().length != payloadSize)
              throw new AssertionError("projection data lost");
            rows++;
          }
          disk = batch.peakDiskBytes();
        }
        buffer.clear();
      }
      if (serialized <= 256L * 1024 * 1024 || rows != 9000)
        throw new AssertionError("insufficient test data");
      sampler.interrupt();
      sampler.join();
      long peakHeap = observedPeak.get();
      if (peakHeap > 128L * 1024 * 1024)
        throw new AssertionError("heap measurement exceeds process limit");
      System.out.println(
          "SPILL_HEAP_OK "
              + args[0]
              + " rows="
              + rows
              + " serialized="
              + serialized
              + " peakHeap="
              + peakHeap
              + " disk="
              + disk
              + " firstRowMs="
              + (first - started) / 1_000_000
              + " durationMs="
              + (System.nanoTime() - started) / 1_000_000);
    }
  }

  /** Executed in an isolated JVM; fixture generation itself never retains previous baskets. */
  public static class HeapWorker {
    public static void main(String[] args) throws Exception {
      var buffer = new InterlisBasketProjectionBuffer<byte[]>(plan());
      long rows = 0, checksum = 0, expected = 0;
      int payloadSize = 16 * 1024;
      for (int basket = 0; basket < 256; basket++) {
        for (int row = 0; row < 64; row++) {
          byte value = (byte) ((basket + row) & 127);
          var context = new byte[payloadSize];
          Arrays.fill(context, value);
          var object = new Iom_jObject(TOPIC + ".Person", "p" + row);
          object.setattrvalue(
              "Payload", new String(context, java.nio.charset.StandardCharsets.ISO_8859_1));
          buffer.add(envelope("b" + basket, object), context);
          expected += (long) value * payloadSize * 2;
        }
        var batch = buffer.drain();
        for (var entry : batch.rows()) {
          for (byte value : entry.context()) checksum += value;
          String text = entry.envelope().object().getattrvalue("Payload");
          for (int i = 0; i < text.length(); i++) checksum += text.charAt(i);
          rows++;
        }
        batch.close();
        if (buffer.bufferedRowCount() != 0 || buffer.bufferedLinkCount() != 0)
          throw new AssertionError("retained basket");
      }
      if (rows != 16384 || checksum != expected) throw new AssertionError("data lost");
      System.out.println("HEAP_OK rows=" + rows + " bytes=" + rows * payloadSize * 2);
    }
  }
}
