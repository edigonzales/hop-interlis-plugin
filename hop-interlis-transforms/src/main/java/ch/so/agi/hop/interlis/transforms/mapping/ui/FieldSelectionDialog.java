package ch.so.agi.hop.interlis.transforms.mapping.ui;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Explicit all/selected field choice, with asynchronous model probing and cancel-safe state. */
public final class FieldSelectionDialog {
  public record Selection(boolean selected, List<String> paths) {}

  private FieldSelectionDialog() {}

  public static Selection open(
      Shell parent,
      boolean selected,
      List<String> paths,
      boolean allowEmpty,
      Callable<List<InterlisFieldPlan>> load) {
    var shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    shell.setText("INTERLIS field selection");
    shell.setLayout(new GridLayout(1, false));
    var explicit = new Button(shell, SWT.CHECK);
    explicit.setText("Project only selected attributes");
    explicit.setSelection(selected);
    var table = new Table(shell, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION);
    table.setHeaderVisible(true);
    table.setLinesVisible(true);
    var name = new TableColumn(table, SWT.NONE);
    name.setText("Attribute path");
    name.setWidth(430);
    var type = new TableColumn(table, SWT.NONE);
    type.setText("INTERLIS type");
    type.setWidth(200);
    table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    for (String path : paths) {
      var row = new TableItem(table, SWT.NONE);
      row.setText(path);
      row.setChecked(true);
    }
    var status = new Label(shell, SWT.WRAP);
    status.setText("Loading model fields…");
    var sd = new GridData(SWT.FILL, SWT.CENTER, true, false);
    sd.widthHint = 630;
    status.setLayoutData(sd);
    var buttons = new Composite(shell, SWT.NONE);
    buttons.setLayout(new RowLayout());
    var ok = new Button(buttons, SWT.PUSH);
    ok.setText("OK");
    var cancel = new Button(buttons, SWT.PUSH);
    cancel.setText("Cancel");
    Selection[] result = new Selection[1];
    ok.addListener(
        SWT.Selection,
        e -> {
          var chosen = new ArrayList<String>();
          for (var row : table.getItems()) if (row.getChecked()) chosen.add(row.getText());
          if (explicit.getSelection() && chosen.isEmpty() && !allowEmpty) {
            status.setText("Select at least one attribute or disable the selection.");
            return;
          }
          result[0] =
              new Selection(explicit.getSelection(), explicit.getSelection() ? chosen : List.of());
          shell.dispose();
        });
    cancel.addListener(SWT.Selection, e -> shell.dispose());
    var display = shell.getDisplay();
    var probes =
        new InterlisProbeCoordinator(
            action -> {
              if (!display.isDisposed())
                display.asyncExec(
                    () -> {
                      if (!shell.isDisposed()) action.run();
                    });
            });
    shell.addListener(SWT.Dispose, e -> probes.close());
    probes.submit(
        false,
        load,
        fields -> {
          for (var field : fields) {
            if (field.propertyPath() == null || field.attributeDescriptor() == null) continue;
            String path = field.propertyPath().dotted();
            TableItem item = null;
            for (var row : table.getItems())
              if (row.getText().equals(path)) {
                item = row;
                break;
              }
            if (item == null) {
              item = new TableItem(table, SWT.NONE);
              item.setText(0, path);
              item.setChecked(!selected);
            }
            item.setText(1, field.attributeDescriptor().typeName());
          }
          status.setText(
              allowEmpty
                  ? "An empty selection keeps only enabled technical fields and the source object."
                  : "Unselected child attributes remain available in the source object.");
          shell.layout();
        },
        error -> {
          status.setText("Schema unavailable: " + error.getMessage());
          shell.layout();
        });
    shell.setSize(720, 550);
    shell.open();
    while (!shell.isDisposed()) if (!display.readAndDispatch()) display.sleep();
    return result[0];
  }
}
