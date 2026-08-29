package ch.so.agi.hop.interlis.transforms;

import java.util.List;

/**
 * SWT-free result of building a projected Hop schema preview.
 *
 * <p>Warnings and errors are deliberately kept outside the table rows so that a diagnostic never
 * looks like a projected field.
 */
public record InterlisSchemaPreview(
    List<InterlisPreviewRow> rows, List<String> warnings, String errorMessage) {

  public InterlisSchemaPreview {
    rows = rows == null ? List.of() : List.copyOf(rows);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public boolean hasError() {
    return errorMessage != null && !errorMessage.isBlank();
  }
}
