package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisCardinality;
import ch.so.agi.hop.interlis.core.model.InterlisValueKind;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import org.junit.jupiter.api.Test;

class InterlisPrimitiveCodecTest {

  private final InterlisPrimitiveCodec codec = new InterlisPrimitiveCodec();

  private static InterlisAttributeDescriptor attribute(String name, InterlisValueKind kind) {
    return new InterlisAttributeDescriptor(
        name, "Model.Topic.Class." + name, new InterlisCardinality(0, 1), false, kind,
        kind.name(), false, null, null, false, null, false, -1,
        kind == InterlisValueKind.DECIMAL ? 3 : -1);
  }

  @Test
  void text_is_mapped_to_string() throws Exception {
    assertThat(codec.parse("Haus A", attribute("Text", InterlisValueKind.TEXT)))
        .isEqualTo("Haus A");
    assertThat(codec.parse("", attribute("Text", InterlisValueKind.TEXT))).isEqualTo("");
  }

  @Test
  void name_uri_and_mtext_are_strings() throws Exception {
    assertThat(codec.parse("Zürich", attribute("Label", InterlisValueKind.NAME)))
        .isEqualTo("Zürich");
    assertThat(
            codec.parse("https://example.com/a", attribute("Link", InterlisValueKind.URI)))
        .isEqualTo("https://example.com/a");
    assertThat(codec.parse("x", attribute("M", InterlisValueKind.MTEXT))).isEqualTo("x");
  }

  @Test
  void boolean_true_and_false_are_mapped_strictly() throws Exception {
    assertThat(codec.parse("true", attribute("Flag", InterlisValueKind.BOOLEAN)))
        .isEqualTo(Boolean.TRUE);
    assertThat(codec.parse("false", attribute("Flag", InterlisValueKind.BOOLEAN)))
        .isEqualTo(Boolean.FALSE);
    assertThatThrownBy(() -> codec.parse("1", attribute("Flag", InterlisValueKind.BOOLEAN)))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Flag");
  }

  @Test
  void integer_is_mapped_to_long_without_truncation() throws Exception {
    assertThat(codec.parse("42", attribute("Count", InterlisValueKind.INTEGER)))
        .isEqualTo(42L);
    assertThat(
            codec.parse("9999999999", attribute("Count", InterlisValueKind.INTEGER)))
        .isEqualTo(9999999999L);
    assertThatThrownBy(() -> codec.parse("4.2", attribute("Count", InterlisValueKind.INTEGER)))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Count");
  }

  @Test
  void decimal_is_mapped_to_big_decimal_without_precision_loss() throws Exception {
    BigDecimal value =
        (BigDecimal)
            codec.parse(
                "12345678901234567890.123456789", attribute("Value", InterlisValueKind.DECIMAL));

    assertThat(value).isEqualTo(new BigDecimal("12345678901234567890.123456789"));
    // Prove that no double round trip happened: the exact decimal places must survive.
    assertThat(value.toPlainString()).isEqualTo("12345678901234567890.123456789");
  }

  @Test
  void enum_is_mapped_to_its_lexical_value() throws Exception {
    assertThat(codec.parse("one", attribute("Kind", InterlisValueKind.ENUM))).isEqualTo("one");
  }

  @Test
  void date_is_mapped_to_utc_midnight() throws Exception {
    Date date = (Date) codec.parse("2026-08-25", attribute("At", InterlisValueKind.DATE));

    assertThat(date).isNotNull();
    assertThat(
            java.time.LocalDate.ofInstant(date.toInstant(), java.time.ZoneOffset.UTC).toString())
        .isEqualTo("2026-08-25");
    assertThatThrownBy(() -> codec.parse("25.08.2026", attribute("At", InterlisValueKind.DATE)))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("At");
  }

  @Test
  void datetime_is_mapped_to_timestamp() throws Exception {
    Timestamp timestamp =
        (Timestamp)
            codec.parse("2026-08-25T10:11:12.345", attribute("Ts", InterlisValueKind.DATETIME));

    assertThat(timestamp.toLocalDateTime().toString()).isEqualTo("2026-08-25T10:11:12.345");
  }

  @Test
  void time_passes_through_as_string() throws Exception {
    assertThat(codec.parse("10:11:12", attribute("T", InterlisValueKind.TIME)))
        .isEqualTo("10:11:12");
  }

  @Test
  void null_stays_null() throws Exception {
    assertThat(codec.parse(null, attribute("Text", InterlisValueKind.TEXT))).isNull();
    assertThat(codec.format(null, attribute("Text", InterlisValueKind.TEXT))).isNull();
  }

  @Test
  void values_roundtrip_through_lexical_form() throws Exception {
    InterlisAttributeDescriptor decimal = attribute("Value", InterlisValueKind.DECIMAL);
    assertThat(codec.format(codec.parse("-12.340", decimal), decimal)).isEqualTo("-12.340");

    InterlisAttributeDescriptor flag = attribute("Flag", InterlisValueKind.BOOLEAN);
    assertThat(codec.format(codec.parse("true", flag), flag)).isEqualTo("true");

    InterlisAttributeDescriptor count = attribute("Count", InterlisValueKind.INTEGER);
    assertThat(codec.format(codec.parse("9999999999", count), count)).isEqualTo("9999999999");

    InterlisAttributeDescriptor at = attribute("At", InterlisValueKind.DATE);
    assertThat(codec.format(codec.parse("2026-08-25", at), at)).isEqualTo("2026-08-25");
  }

  @Test
  void wrong_hop_type_is_rejected_with_attribute_name() {
    InterlisAttributeDescriptor decimal = attribute("Value", InterlisValueKind.DECIMAL);

    assertThatThrownBy(() -> codec.format(3.14, decimal))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Value")
        .hasMessageContaining("BigDecimal");
  }
}
