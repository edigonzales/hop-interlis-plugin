package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.core.model.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P2MappingRegressionTest {
  @TempDir Path dir;

  private InterlisAttributeDescriptor attribute(InterlisValueKind kind) {
    return new InterlisAttributeDescriptor(
        "At",
        "M.T.C.At",
        new InterlisCardinality(0, 1),
        false,
        kind,
        kind.name(),
        false,
        null,
        null,
        false,
        null,
        false,
        -1,
        -1);
  }

  @Test
  void rejects_impossible_dates_and_time_precision() throws Exception {
    var codec = new InterlisPrimitiveCodec();
    var date = attribute(InterlisValueKind.DATE);
    assertThatThrownBy(() -> codec.parse("2025-02-31", date))
        .isInstanceOf(InterlisMappingException.class);
    assertThat(codec.format(codec.parse("2024-02-29", date), date)).isEqualTo("2024-02-29");
    var time = attribute(InterlisValueKind.DATETIME);
    assertThatThrownBy(() -> codec.parse("2025-02-31T10:00:00", time))
        .isInstanceOf(InterlisMappingException.class);
    assertThatThrownBy(() -> codec.format(Timestamp.valueOf("2025-01-01 10:00:00.123456"), time))
        .isInstanceOf(InterlisMappingException.class);
  }

  @Test
  void timezone_is_captured_and_dst_gap_does_not_shift_the_value() throws Exception {
    TimeZone before = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"));
      var codec = new InterlisPrimitiveCodec();
      var time = attribute(InterlisValueKind.DATETIME);
      assertThatThrownBy(() -> codec.parse("2025-03-30T02:30:00", time))
          .isInstanceOf(InterlisMappingException.class)
          .hasMessageContaining("At");
      var value = (Timestamp) codec.parse("2025-10-26T02:30:00.123", time);
      assertThat(value.toInstant()).isEqualTo(Instant.parse("2025-10-26T00:30:00.123Z"));
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      assertThat(codec.format(value, time)).isEqualTo("2025-10-26T02:30:00.123");
    } finally {
      TimeZone.setDefault(before);
    }
  }

  @Test
  void selected_deep_leaf_builds_and_overlays_all_parent_structures() throws Exception {
    Path file = dir.resolve("Deep.ili");
    Files.writeString(
        file,
        """
        INTERLIS 2.3;
        MODEL Deep (en) AT "https://example.org" VERSION "1" =
          TOPIC T =
            STRUCTURE Place = Name : TEXT*30; Note : TEXT*30; END Place;
            STRUCTURE Home = Place : Place; END Home;
            STRUCTURE Address = Home : Home; END Address;
            CLASS Item = Address : Address; END Item;
          END T;
        END Deep.
        """);
    var service = new InterlisProjectionService();
    var request = new InterlisModelRequest(null, List.of("Deep"), List.of(dir.toString()));
    var options =
        new ProjectionOptions(
            true, true, false, false, false, true, "_", null, Set.of("Address.Home.Place.Name"));
    var result = service.project(request, "Deep.T.Item", options);
    assertThat(result.plan().fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_tid", "_ili_bid", "Address_Home_Place_Name");
    var mapper = new RowToIomMapper();
    var object =
        mapper.map(new Object[] {"i1", "b1", "first"}, result.plan(), RowWriteOptions.defaults());
    var place = object.getattrobj("Address", 0).getattrobj("Home", 0).getattrobj("Place", 0);
    place.setattrvalue("Note", "keep");
    var changed =
        mapper.map(
            object, new Object[] {"i1", "b1", "second"}, result.plan(), RowWriteOptions.defaults());
    var actual = changed.getattrobj("Address", 0).getattrobj("Home", 0).getattrobj("Place", 0);
    assertThat(actual.getattrvalue("Name")).isEqualTo("second");
    assertThat(actual.getattrvalue("Note")).isEqualTo("keep");
    assertThat(place.getattrvalue("Name")).isEqualTo("first");
  }
}
