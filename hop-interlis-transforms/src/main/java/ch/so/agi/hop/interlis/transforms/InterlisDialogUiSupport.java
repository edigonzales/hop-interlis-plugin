package ch.so.agi.hop.interlis.transforms;

import java.util.List;
import java.util.Locale;
import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/** Shared row layout helpers for INTERLIS transform dialogs. */
public final class InterlisDialogUiSupport {
  private InterlisDialogUiSupport() {}

  /** Severity used by non-modal model/configuration diagnostics in INTERLIS dialogs. */
  public enum StatusSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
  }

  /**
   * Native SWT status row used for model probing diagnostics.
   *
   * <p>The row uses the same label/control split as the surrounding form and keeps its height
   * synchronized with the wrapped message as the dialog is resized. Dialogs only need to retain
   * this small view object and call
   * {@link #set(StatusSeverity, String)}.
   */
  public static final class StatusArea {
    private static final int PADDING = 3;

    private final Composite row;
    private final Composite card;
    private final Label message;
    private boolean adjustingHeight;

    private StatusArea(Composite row, Composite card, Label message) {
      this.row = row;
      this.card = card;
      this.message = message;
      row.addControlListener(
          new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent event) {
              adjustHeight();
            }
          });
    }

    /** Returns the outer row for attaching subsequent FormLayout rows. */
    public Composite control() {
      return row;
    }

    /** Sets the status color and wrapped diagnostic text. */
    public void set(StatusSeverity severity, String text) {
      StatusSeverity effectiveSeverity = severity == null ? StatusSeverity.INFO : severity;
      message.setText(text == null ? "" : text);
      Color color = statusColor(effectiveSeverity);
      if (color != null) {
        message.setForeground(color);
      }
      row.setVisible(true);
      row.getParent().layout(true, true);
      adjustHeight();
      row.getParent().layout(true, true);
    }

    private void adjustHeight() {
      if (adjustingHeight || row.isDisposed() || row.getParent().isDisposed()) {
        return;
      }
      int width = card.getClientArea().width;
      if (width <= 0) {
        return;
      }
      adjustingHeight = true;
      try {
        int messageWidth = Math.max(1, width - 2 * PADDING);
        Point messageSize = message.computeSize(messageWidth, SWT.DEFAULT);
        int desiredHeight =
            calculateStatusHeight(messageSize.y, PADDING, card.getBorderWidth());
        boolean changed = false;
        if (card.getLayoutData() instanceof FormData cardData
            && cardData.height != desiredHeight) {
          cardData.height = desiredHeight;
          changed = true;
        }
        if (row.getLayoutData() instanceof FormData rowData && rowData.height != desiredHeight) {
          rowData.height = desiredHeight;
          changed = true;
        }
        if (changed) {
          row.getParent().layout(true, true);
        }
      } finally {
        adjustingHeight = false;
      }
    }

    private static Color statusColor(StatusSeverity severity) {
      Display display = Display.getCurrent();
      if (display == null || display.isDisposed()) {
        return null;
      }
      return switch (severity) {
        case SUCCESS -> display.getSystemColor(SWT.COLOR_DARK_GREEN);
        case WARNING -> display.getSystemColor(SWT.COLOR_DARK_YELLOW);
        case ERROR -> display.getSystemColor(SWT.COLOR_DARK_RED);
        case INFO -> display.getSystemColor(SWT.COLOR_DARK_BLUE);
      };
    }
  }

  /** Creates a status row aligned with the surrounding form fields. */
  public static StatusArea createStatusArea(
      Composite parent, Control under, int middlePct, int margin) {
    Composite row = createRow(parent, under, margin);
    Label label = createLabel(row, "Model status");

    Composite card = new Composite(row, SWT.BORDER);
    PropsUi.setLook(card);
    FormLayout cardLayout = new FormLayout();
    cardLayout.marginWidth = 0;
    cardLayout.marginHeight = 0;
    card.setLayout(cardLayout);

    FormData fdCard = new FormData();
    fdCard.left = new FormAttachment(middlePct, 0);
    fdCard.right = new FormAttachment(100, 0);
    fdCard.top = new FormAttachment(0, 0);
    card.setLayoutData(fdCard);

    FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.right = new FormAttachment(middlePct, -margin);
    fdLabel.top = new FormAttachment(card, 0, SWT.TOP);
    label.setLayoutData(fdLabel);

    Label message = new Label(card, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(message);
    FormData fdMessage = new FormData();
    fdMessage.left = new FormAttachment(0, StatusArea.PADDING);
    fdMessage.right = new FormAttachment(100, -StatusArea.PADDING);
    fdMessage.top = new FormAttachment(0, StatusArea.PADDING);
    fdMessage.bottom = new FormAttachment(100, -StatusArea.PADDING);
    message.setLayoutData(fdMessage);

    int initialHeight =
        calculateStatusHeight(
            message.computeSize(SWT.DEFAULT, SWT.DEFAULT).y,
            StatusArea.PADDING,
            card.getBorderWidth());
    fdCard.height = initialHeight;
    if (row.getLayoutData() instanceof FormData fdRow) {
      fdRow.height = initialHeight;
    }
    return new StatusArea(row, card, message);
  }

  /** Calculates the outer status-row height including message padding and the native border. */
  static int calculateStatusHeight(int messageHeight, int padding, int borderWidth) {
    int effectiveMessageHeight = Math.max(0, messageHeight);
    int effectivePadding = Math.max(0, padding);
    int effectiveBorderWidth = Math.max(0, borderWidth);
    return Math.max(
        1, effectiveMessageHeight + 2 * effectivePadding + 2 * effectiveBorderWidth);
  }

  /** Maps a probe result to a non-modal status severity without changing its existing message. */
  public static StatusSeverity statusSeverity(
      boolean successful, boolean configured, String message) {
    if (successful) {
      return StatusSeverity.SUCCESS;
    }
    String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
    if (normalized.contains("not found")
        || normalized.contains("unresolved")
        || normalized.contains("failed")
        || normalized.contains("no such")
        || normalized.contains("exception")
        || normalized.contains("error")) {
      return StatusSeverity.ERROR;
    }
    if (normalized.contains("configuration is incomplete")
        || normalized.contains("probe unavailable")
        || normalized.contains("no interlis models configured")
        || normalized.contains("select an interlis class")
        || normalized.contains("loaded")) {
      return StatusSeverity.INFO;
    }
    return configured ? StatusSeverity.ERROR : StatusSeverity.INFO;
  }

  /**
   * Refines a model status with diagnostics from the central schema preview.
   *
   * <p>Preview diagnostics remain in their separate hint area, while the status area communicates
   * that a successful model probe produced warnings or could not build a usable schema.
   */
  public static StatusSeverity statusSeverity(
      StatusSeverity modelSeverity, InterlisSchemaPreview preview) {
    if (preview != null && preview.hasError()) {
      return StatusSeverity.ERROR;
    }
    if (preview != null
        && !preview.warnings().isEmpty()
        && modelSeverity == StatusSeverity.SUCCESS) {
      return StatusSeverity.WARNING;
    }
    return modelSeverity == null ? StatusSeverity.INFO : modelSeverity;
  }

  /** Creates a full-width row whose children share one local form layout. */
  public static Composite createRow(Composite parent, Control under, int margin) {
    Composite row = new Composite(parent, SWT.NONE);
    PropsUi.setLook(row);
    FormLayout rowLayout = new FormLayout();
    rowLayout.marginWidth = 0;
    rowLayout.marginHeight = 0;
    row.setLayout(rowLayout);

    FormData fdRow = new FormData();
    fdRow.left = new FormAttachment(0, 0);
    fdRow.right = new FormAttachment(100, 0);
    fdRow.top = under == null ? new FormAttachment(0, 0) : new FormAttachment(under, margin);
    row.setLayoutData(fdRow);
    return row;
  }

  /** Places a label and a control in a shared row. */
  public static void buildRowControl(
      Composite row, String labelText, Control control, int middlePct, int margin) {
    Label label = createLabel(row, labelText);

    PropsUi.setLook(control);
    FormData fdControl = new FormData();
    fdControl.left = new FormAttachment(middlePct, 0);
    fdControl.right = new FormAttachment(100, 0);
    fdControl.top = new FormAttachment(0, 0);
    control.setLayoutData(fdControl);

    FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.right = new FormAttachment(middlePct, -margin);
    fdLabel.top = new FormAttachment(control, 0, SWT.TOP);
    label.setLayoutData(fdLabel);
  }

  /** Places a label, line edit/combo and action button in one shared row. */
  public static void buildRowControlWithButton(
      Composite row,
      String labelText,
      Control control,
      Button actionButton,
      String buttonText,
      int middlePct,
      int margin) {
    Label label = createLabel(row, labelText);

    PropsUi.setLook(actionButton);
    actionButton.setText(buttonText);
    FormData fdAction = new FormData();
    fdAction.right = new FormAttachment(100, 0);
    fdAction.top = new FormAttachment(0, 0);
    actionButton.setLayoutData(fdAction);

    PropsUi.setLook(control);
    FormData fdControl = new FormData();
    fdControl.left = new FormAttachment(middlePct, 0);
    fdControl.right = new FormAttachment(actionButton, -margin);
    fdControl.top = new FormAttachment(0, margin);
    control.setLayoutData(fdControl);

    FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.right = new FormAttachment(middlePct, -margin);
    fdLabel.top = new FormAttachment(control, 0, SWT.TOP);
    label.setLayoutData(fdLabel);
  }

  /** Creates the native SWT table used for read-only INTERLIS schema previews. */
  public static Table createPreviewTable(Composite parent) {
    return createTable(
        parent,
        new String[] {"Field", "Hop type", "Source"},
        new int[] {240, 150, 480});
  }

  /** Creates a consistently configured read-only table for an INTERLIS dialog. */
  public static Table createTable(Composite parent, String[] columns, int[] widths) {
    Table table =
        new Table(
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
    table.setHeaderVisible(true);
    table.setLinesVisible(true);
    PropsUi.setLook(table);
    for (int i = 0; i < columns.length; i++) {
      TableColumn column = new TableColumn(table, SWT.LEFT);
      column.setText(columns[i]);
      column.setWidth(widths != null && i < widths.length ? widths[i] : 180);
    }
    return table;
  }

  /** Replaces all rows in a preview table. */
  public static void populatePreviewTable(Table table, List<InterlisPreviewRow> rows) {
    table.removeAll();
    if (rows == null) {
      return;
    }
    for (InterlisPreviewRow row : rows) {
      TableItem item = new TableItem(table, SWT.NONE);
      item.setText(new String[] {row.fieldName(), row.hopType(), row.source()});
    }
  }

  /** Shows preview diagnostics in a compact, separate label instead of adding them to the table. */
  public static void setPreviewDiagnostics(Label label, InterlisSchemaPreview preview) {
    StringBuilder message = new StringBuilder();
    if (preview != null && preview.hasError()) {
      message.append(preview.errorMessage());
    }
    if (preview != null) {
      for (String warning : preview.warnings()) {
        if (message.length() > 0) {
          message.append('\n');
        }
        message.append("Warning: ").append(warning);
      }
    }
    setPreviewDiagnostics(label, message.toString());
  }

  /** Shows or hides a diagnostic label and lets FormLayout recalculate its height. */
  public static void setPreviewDiagnostics(Label label, String message) {
    String text = message == null ? "" : message;
    label.setText(text);
    label.setVisible(!text.isBlank());
    if (label.getLayoutData() instanceof FormData formData) {
      formData.height = text.isBlank() ? 0 : SWT.DEFAULT;
    }
  }

  private static Label createLabel(Composite row, String text) {
    Label label = new Label(row, SWT.RIGHT);
    label.setText(text);
    PropsUi.setLook(label);
    return label;
  }
}
