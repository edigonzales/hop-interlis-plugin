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
    assertThat(buffer.bufferedLinkCount()).isEqualTo(2);
    assertThat(buffer.bufferedRowCount()).isZero();
    var first = buffer.drain();
    assertThat(first.rows()).isEmpty();
    assertThat(first.lookup().find("p1", "Address")).isSameAs(firstLink);
    assertThat(buffer.bufferedLinkCount()).isZero();
    assertThat(buffer.startsNewBasket(envelope("two", firstLink))).isFalse();
    var secondLink = link("p2");
    buffer.add(envelope("two", secondLink), null);
    var second = buffer.drain();
    assertThat(first.lookup().find("p2", "Address")).isNull();
    assertThat(second.lookup().find("p1", "Address")).isNull();
    assertThat(second.lookup().find("p2", "Address")).isSameAs(secondLink);
    buffer.clear();
    buffer.clear();
    assertThat(first.lookup().find("p1", "Address")).isSameAs(firstLink);
  }

  @Test
  void clear_releases_context_and_large_basket_state_and_allows_reuse() throws Exception {
    var buffer = new InterlisBasketProjectionBuffer<Object>(plan());
    Object context = new Object();
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
      assertThat(batch.rows().getFirst().context()).isSameAs(context);
      assertThat(buffer.bufferedRowCount()).isZero();
    }
  }

  @TempDir Path temp;

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
        if (buffer.bufferedRowCount() != 0 || buffer.bufferedLinkCount() != 0)
          throw new AssertionError("retained basket");
      }
      if (rows != 16384 || checksum != expected) throw new AssertionError("data lost");
      System.out.println("HEAP_OK rows=" + rows + " bytes=" + rows * payloadSize * 2);
    }
  }
}
