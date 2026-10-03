package ch.so.agi.hop.interlis.migration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

final class MigrationChoice {
  static String choose(Shell owner, String title, List<String> values) {
    if (values.isEmpty())
      throw new IllegalArgumentException("No compatible choices available: " + title);
    Shell shell = new Shell(owner, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    shell.setText(title);
    shell.setLayout(new GridLayout());
    org.eclipse.swt.widgets.List list =
        new org.eclipse.swt.widgets.List(shell, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
    list.setItems(values.toArray(String[]::new));
    GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
    data.widthHint = 520;
    data.heightHint = 260;
    list.setLayoutData(data);
    String[] result = {null};
    Runnable select =
        () -> {
          if (list.getSelectionCount() > 0) {
            result[0] = list.getSelection()[0];
            shell.dispose();
          }
        };
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText("Select");
    ok.addListener(SWT.Selection, e -> select.run());
    list.addListener(SWT.DefaultSelection, e -> select.run());
    Button cancel = new Button(shell, SWT.PUSH);
    cancel.setText("Cancel");
    cancel.addListener(SWT.Selection, e -> shell.dispose());
    shell.setDefaultButton(ok);
    shell.addTraverseListener(
        e -> {
          if (e.detail == SWT.TRAVERSE_ESCAPE) shell.dispose();
        });
    shell.pack();
    shell.open();
    loop(shell);
    return result[0];
  }

  static Map<String, String> enumeration(Shell owner, List<String> source, List<String> target) {
    Shell shell = new Shell(owner, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    shell.setText("Enumeration values — every source value needs a target");
    shell.setLayout(new GridLayout());
    Table table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
    table.setHeaderVisible(true);
    table.setLinesVisible(true);
    for (String name : List.of("Source", "Target")) {
      TableColumn col = new TableColumn(table, SWT.NONE);
      col.setText(name);
      col.setWidth(270);
    }
    GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
    data.heightHint = 300;
    table.setLayoutData(data);
    Map<String, TableItem> choices = new LinkedHashMap<>();
    for (String value : source) {
      TableItem item = new TableItem(table, SWT.NONE);
      item.setText(new String[] {value, target.contains(value) ? value : "Choose a target…"});
      if (target.contains(value)) item.setData(value);
      choices.put(value, item);
    }
    Runnable chooseValue =
        () -> {
          if (table.getSelectionCount() == 0) return;
          TableItem item = table.getSelection()[0];
          String value = choose(shell, "Target for " + item.getText(0), target);
          if (value != null) {
            item.setText(1, value);
            item.setData(value);
          }
        };
    Button select = new Button(shell, SWT.PUSH);
    select.setText("Choose target for selected value…");
    select.addListener(SWT.Selection, e -> chooseValue.run());
    table.addListener(SWT.DefaultSelection, e -> chooseValue.run());
    if (table.getItemCount() > 0) table.setSelection(0);
    Map<String, String> result = new LinkedHashMap<>();
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText("Apply value mapping");
    ok.addListener(
        SWT.Selection,
        e -> {
          if (choices.values().stream().anyMatch(c -> c.getData() == null)) {
            MigrationUi.error(
                shell, new IllegalArgumentException("Choose a target for every source value"));
            return;
          }
          choices.forEach((key, value) -> result.put(key, value.getText(1)));
          shell.dispose();
        });
    Button cancel = new Button(shell, SWT.PUSH);
    cancel.setText("Cancel");
    cancel.addListener(SWT.Selection, e -> shell.dispose());
    shell.addTraverseListener(
        e -> {
          if (e.detail == SWT.TRAVERSE_ESCAPE) shell.dispose();
        });
    shell.pack();
    shell.open();
    loop(shell);
    return result;
  }

  private static void loop(Shell shell) {
    while (!shell.isDisposed())
      if (!shell.getDisplay().readAndDispatch()) shell.getDisplay().sleep();
  }
}
