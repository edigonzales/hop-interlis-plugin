package ch.so.agi.hop.interlis.migration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

final class MigrationUi {
  private MigrationUi() {}

  static void error(Shell shell, Exception ex) {
    MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
    box.setText("INTERLIS Migration");
    box.setMessage(ex.getMessage() == null ? ex.toString() : ex.getMessage());
    box.open();
  }

  static boolean confirm(Shell shell, String message) {
    MessageBox box = new MessageBox(shell, SWT.OK | SWT.CANCEL | SWT.ICON_WARNING);
    box.setText("Review migration");
    box.setMessage(message);
    return box.open() == SWT.OK;
  }

  static Map<String, String> form(Shell owner, String title, Map<String, String> initial) {
    Shell shell = new Shell(owner, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    shell.setText(title);
    shell.setLayout(new GridLayout(2, false));
    Map<String, Text> fields = new LinkedHashMap<>();
    initial.forEach(
        (name, value) -> {
          new Label(shell, SWT.NONE).setText(name);
          Text field = new Text(shell, SWT.BORDER);
          field.setText(value == null ? "" : value);
          GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
          data.widthHint = 540;
          field.setLayoutData(data);
          fields.put(name, field);
        });
    Map<String, String> result = new LinkedHashMap<>();
    Button cancel = new Button(shell, SWT.PUSH);
    cancel.setText("Cancel");
    cancel.addListener(SWT.Selection, e -> shell.dispose());
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText("Apply");
    ok.addListener(
        SWT.Selection,
        e -> {
          fields.forEach((name, field) -> result.put(name, field.getText()));
          shell.dispose();
        });
    shell.setDefaultButton(ok);
    shell.pack();
    shell.open();
    while (!shell.isDisposed())
      if (!shell.getDisplay().readAndDispatch()) shell.getDisplay().sleep();
    return result;
  }

  static LinkedHashMap<String, String> fields(String... pairs) {
    var result = new LinkedHashMap<String, String>();
    for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
    return result;
  }
}
