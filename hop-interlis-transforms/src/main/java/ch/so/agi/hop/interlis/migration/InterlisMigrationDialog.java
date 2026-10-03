package ch.so.agi.hop.interlis.migration;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class InterlisMigrationDialog extends ActionDialog {
  private InterlisMigration action;

  public InterlisMigrationDialog(
      Shell parent, InterlisMigration action, WorkflowMeta meta, IVariables variables) {
    super(parent, meta, variables);
    this.action = action;
  }

  @Override
  public IAction open() {
    createShell("INTERLIS Migration", action);
    wName.setText(action.getName() == null ? "" : action.getName());
    Composite body = new Composite(shell, SWT.NONE);
    body.setLayout(new GridLayout(3, false));
    FormData data = new FormData();
    data.top = new FormAttachment(wSpacer, margin);
    data.left = new FormAttachment(0);
    data.right = new FormAttachment(100);
    body.setLayoutData(data);
    TextVar mapping = field(body, "Mapping (.ilimap)", action.getMappingFile(), false);
    TextVar input = field(body, "Original XTF (optional override)", action.getInputFile(), false);
    TextVar output = field(body, "Target XTF (optional override)", action.getOutputFile(), true);
    TextVar dirs =
        field(body, "Additional model directories (;)", action.getModelDirectories(), false);
    TextVar reports = field(body, "Report directory", action.getReportDirectory(), false);
    Button validate =
        check(body, "Validate complete output before publishing", action.isValidate());
    Button overwrite = check(body, "Overwrite existing target", action.isOverwrite());
    Button editor = new Button(body, SWT.PUSH);
    editor.setText("Open mapping editor…");
    editor.addListener(
        SWT.Selection,
        e -> {
          try {
            new IlimapFileType()
                .openFile(HopGui.getInstance(), variables.resolve(mapping.getText()), variables);
          } catch (Exception ex) {
            MigrationUi.error(shell, ex);
          }
        });
    Label note = new Label(body, SWT.WRAP);
    note.setText(
        "Runs a complete XTF migration using ilitransformer. The current engine keeps indexes and"
            + " target objects in memory.");
    GridData noteData = new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1);
    noteData.widthHint = 650;
    note.setLayoutData(noteData);
    Runnable accept =
        () -> {
          if (wName.getText().isBlank() || mapping.getText().isBlank()) {
            MigrationUi.error(
                shell, new IllegalArgumentException("Name and mapping file are required"));
            return;
          }
          action.setName(wName.getText());
          action.setMappingFile(mapping.getText());
          action.setInputFile(input.getText());
          action.setOutputFile(output.getText());
          action.setModelDirectories(dirs.getText());
          action.setReportDirectory(reports.getText());
          action.setValidate(validate.getSelection());
          action.setOverwrite(overwrite.getSelection());
          action.setChanged();
          dispose();
        };
    Runnable cancel =
        () -> {
          action = null;
          dispose();
        };
    buildButtonBar().ok(e -> accept.run()).cancel(e -> cancel.run()).build();
    data.bottom = new FormAttachment(wCancel, -margin);
    BaseDialog.defaultShellHandling(shell, c -> accept.run(), c -> cancel.run());
    return action;
  }

  private TextVar field(Composite parent, String label, String value, boolean save) {
    new Label(parent, SWT.NONE).setText(label);
    TextVar text = new TextVar(variables, parent, SWT.BORDER);
    text.setText(value == null ? "" : value);
    GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
    data.widthHint = 420;
    text.setLayoutData(data);
    Button browse = new Button(parent, SWT.PUSH);
    browse.setText("…");
    browse.addListener(
        SWT.Selection,
        e -> {
          if (label.contains("directories") || label.contains("directory")) {
            String path = new DirectoryDialog(shell).open();
            if (path != null) text.setText(path);
          } else {
            String path = new FileDialog(shell, save ? SWT.SAVE : SWT.OPEN).open();
            if (path != null) text.setText(path);
          }
        });
    return text;
  }

  private Button check(Composite parent, String label, boolean value) {
    Button result = new Button(parent, SWT.CHECK);
    result.setText(label);
    result.setSelection(value);
    result.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
    return result;
  }

  @Override
  protected void onActionNameModified() {
    /* commit only on OK */
  }
}
