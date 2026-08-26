package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisValueKind;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Converts INTERLIS lexical values (as read from the transfer) to typed Hop values and back.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>TEXT/MTEXT/NAME/URI/ENUM pass through as {@link String};
 *   <li>BOOLEAN uses the INTERLIS lexical forms {@code true}/{@code false} only;
 *   <li>INTEGER parses as {@link Long};
 *   <li>DECIMAL parses as {@link BigDecimal} – never through {@code double};
 *   <li>DATE parses ISO {@code yyyy-MM-dd} (XMLDate) to a {@link Date} at UTC midnight;
 *   <li>DATETIME parses ISO {@code yyyy-MM-dd'T'HH:mm:ss[.SSS]} without time zone to a
 *       {@link Timestamp} interpreted in the JVM default time zone; formatting is always the
 *       plain ISO form without zone, so values stay stable for round trips;
 *   <li>TIME passes through as {@link String}.
 * </ul>
 *
 * <p>Parsing and formatting are locale-independent.
 */
public final class InterlisPrimitiveCodec {

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]");

  public InterlisPrimitiveCodec() {}

  /**
   * Parses a raw INTERLIS lexical value into the typed Hop value for the given attribute.
   *
   * @param raw the lexical value from the transfer, may be {@code null}
   * @param descriptor the attribute descriptor driving the conversion
   * @return the typed value or {@code null} if {@code raw} was {@code null}
   * @throws InterlisMappingException if the value is invalid for the domain
   */
  public Object parse(String raw, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if (raw == null) {
      return null;
    }
    InterlisValueKind kind = descriptor.kind();
    try {
      return switch (kind) {
        case TEXT, MTEXT, NAME, URI, ENUM, TIME -> raw;
        case BOOLEAN -> parseBoolean(raw, descriptor);
        case INTEGER -> parseInteger(raw, descriptor);
        case DECIMAL -> parseDecimal(raw, descriptor);
        case DATE -> parseDate(raw, descriptor);
        case DATETIME -> parseDateTime(raw, descriptor);
        case GEOMETRY, STRUCTURE ->
            throw new InterlisMappingException(
                "Cannot parse a " + kind + " attribute as primitive: " + descriptor.name());
      };
    } catch (InterlisMappingException e) {
      throw e;
    } catch (NumberFormatException | DateTimeParseException e) {
      throw new InterlisMappingException(
          "Invalid " + kind + " value <" + raw + "> for attribute " + descriptor.name() + ": "
              + e.getMessage(),
          e);
    }
  }

  /**
   * Formats a typed Hop value into the INTERLIS lexical form for the given attribute.
   *
   * @param value the typed value, may be {@code null}
   * @param descriptor the attribute descriptor driving the conversion
   * @return the lexical value or {@code null} if {@code value} was {@code null}
   * @throws InterlisMappingException if the value cannot be formatted for the domain
   */
  public String format(Object value, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if (value == null) {
      return null;
    }
    InterlisValueKind kind = descriptor.kind();
    return switch (kind) {
      case TEXT, MTEXT, NAME, URI, ENUM, TIME -> requireString(value, descriptor);
      case BOOLEAN -> requireBoolean(value, descriptor) ? "true" : "false";
      case INTEGER -> requireLong(value, descriptor).toString();
      case DECIMAL -> requireDecimal(value, descriptor).toPlainString();
      case DATE -> DATE_FORMAT.format(
          LocalDate.ofInstant(((Date) requireDate(value, descriptor)).toInstant(),
              java.time.ZoneOffset.UTC));
      case DATETIME -> {
        Timestamp timestamp = requireTimestamp(value, descriptor);
        yield DATE_TIME_FORMAT.format(timestamp.toLocalDateTime());
      }
      case GEOMETRY, STRUCTURE ->
          throw new InterlisMappingException(
              "Cannot format a " + kind + " attribute as primitive: " + descriptor.name());
    };
  }

  private Boolean parseBoolean(String raw, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if ("true".equals(raw)) {
      return Boolean.TRUE;
    }
    if ("false".equals(raw)) {
      return Boolean.FALSE;
    }
    throw new InterlisMappingException(
        "Invalid BOOLEAN value <" + raw + "> for attribute " + descriptor.name()
            + "; expected 'true' or 'false'");
  }

  private Long parseInteger(String raw, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    try {
      return Long.valueOf(raw.trim());
    } catch (NumberFormatException e) {
      throw new InterlisMappingException(
          "Invalid integer value <" + raw + "> for attribute " + descriptor.name(), e);
    }
  }

  private BigDecimal parseDecimal(String raw, InterlisAttributeDescriptor descriptor) {
    return new BigDecimal(raw.trim());
  }

  private Date parseDate(String raw, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    try {
      LocalDate date = LocalDate.parse(raw.trim(), DATE_FORMAT);
      return Date.from(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    } catch (DateTimeParseException e) {
      throw new InterlisMappingException(
          "Invalid date value <" + raw + "> for attribute " + descriptor.name()
              + "; expected yyyy-MM-dd",
          e);
    }
  }

  private Timestamp parseDateTime(String raw, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    try {
      LocalDateTime dateTime = LocalDateTime.parse(raw.trim(), DATE_TIME_FORMAT);
      return Timestamp.valueOf(dateTime);
    } catch (DateTimeParseException e) {
      throw new InterlisMappingException(
          "Invalid date/time value <" + raw + "> for attribute " + descriptor.name()
              + "; expected yyyy-MM-dd'T'HH:mm:ss[.SSS]",
          e);
    }
  }

  private String requireString(Object value, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if (!(value instanceof String string)) {
      throw new InterlisMappingException(
          "Expected a String value for attribute " + descriptor.name() + " but got "
              + value.getClass().getName());
    }
    return string;
  }

  private Boolean requireBoolean(Object value, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if (!(value instanceof Boolean bool)) {
      throw new InterlisMappingException(
          "Expected a Boolean value for attribute " + descriptor.name() + " but got "
              + value.getClass().getName());
    }
    return bool;
  }

  private Long requireLong(Object value, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if (!(value instanceof Long longValue)) {
      throw new InterlisMappingException(
          "Expected a Long value for attribute " + descriptor.name() + " but got "
              + value.getClass().getName());
    }
    return longValue;
  }

  private BigDecimal requireDecimal(Object value, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if (!(value instanceof BigDecimal decimal)) {
      throw new InterlisMappingException(
          "Expected a BigDecimal value for attribute " + descriptor.name() + " but got "
              + value.getClass().getName());
    }
    return decimal;
  }

  private Date requireDate(Object value, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if (!(value instanceof Date date)) {
      throw new InterlisMappingException(
          "Expected a Date value for attribute " + descriptor.name() + " but got "
              + value.getClass().getName());
    }
    return date;
  }

  private Timestamp requireTimestamp(Object value, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    if (!(value instanceof Timestamp timestamp)) {
      throw new InterlisMappingException(
          "Expected a Timestamp value for attribute " + descriptor.name() + " but got "
              + value.getClass().getName());
    }
    return timestamp;
  }
}
