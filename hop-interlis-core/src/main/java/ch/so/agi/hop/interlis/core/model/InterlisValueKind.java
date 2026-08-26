package ch.so.agi.hop.interlis.core.model;

/**
 * Fine-grained value kind of an INTERLIS attribute domain.
 *
 * <p>This drives both the Hop row schema types and the runtime value conversion. The enum is the
 * single source of truth for the type mapping; UI, {@code getFields()} and the runtime mapper all
 * derive their behaviour from the same descriptor.
 */
public enum InterlisValueKind {
  TEXT,
  MTEXT,
  NAME,
  URI,
  BOOLEAN,
  INTEGER,
  DECIMAL,
  DATE,
  DATETIME,
  TIME,
  ENUM,
  GEOMETRY,
  STRUCTURE;

  public boolean isTextual() {
    return this == TEXT || this == MTEXT || this == NAME || this == URI;
  }

  public boolean isNumeric() {
    return this == INTEGER || this == DECIMAL;
  }

  public boolean isTemporal() {
    return this == DATE || this == DATETIME || this == TIME;
  }

  public boolean isGeometry() {
    return this == GEOMETRY;
  }

  public boolean isStructure() {
    return this == STRUCTURE;
  }

  public boolean isEnum() {
    return this == ENUM;
  }
}
