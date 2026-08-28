package ch.so.agi.hop.interlis.transforms;

import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

/** Shared row layout helpers for INTERLIS transform dialogs. */
public final class InterlisDialogUiSupport {
  private InterlisDialogUiSupport() {}

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

  private static Label createLabel(Composite row, String text) {
    Label label = new Label(row, SWT.RIGHT);
    label.setText(text);
    PropsUi.setLook(label);
    return label;
  }
}
