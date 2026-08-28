package ch.so.agi.hop.interlis.transforms.transferoutput;

import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** INTERLIS Transfer Output dialog: target file, models and write mode. */
public class InterlisTransferOutputDialog extends BaseTransformDialog {

  private final InterlisTransferOutputMeta input;

  private TextVar wFileName;
  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private Button wOverwrite;
  private Button wEventMode;

  public InterlisTransferOutputDialog(
      Shell parent,
      IVariables variables,
      InterlisTransferOutputMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("INTERLIS Transfer Output");
    shell.setMinimumSize(720, 360);

    changed = input.hasChanged();
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    // Transform name
    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText("Transform name");
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlTransformName.top = new FormAttachment(0, margin);
    wlTransformName.setLayoutData(fdlTransformName);

    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wTransformName.setText(transformName);
    PropsUi.setLook(wTransformName);
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(props.getMiddlePct(), 0);
    fdTransformName.right = new FormAttachment(100, 0);
    fdTransformName.top = new FormAttachment(0, margin);
    wTransformName.setLayoutData(fdTransformName);

    // File
    Composite fileRow = InterlisDialogUiSupport.createRow(shell, wTransformName, margin);
    Button wbFile = new Button(fileRow, SWT.PUSH | SWT.CENTER);
    wFileName = new TextVar(variables, fileRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        fileRow, "Data file", wFileName, wbFile, "Browse", props.getMiddlePct(), margin);

    wOverwrite = new Button(shell, SWT.CHECK);
    wOverwrite.setText("Overwrite existing file");
    PropsUi.setLook(wOverwrite);
    FormData fdOverwrite = new FormData();
    fdOverwrite.left = new FormAttachment(props.getMiddlePct(), 0);
    fdOverwrite.top = new FormAttachment(fileRow, margin);
    wOverwrite.setLayoutData(fdOverwrite);

    // Models
    Label wlModels = new Label(shell, SWT.RIGHT);
    wlModels.setText("Models");
    PropsUi.setLook(wlModels);
    FormData fdlModels = new FormData();
    fdlModels.left = new FormAttachment(0, 0);
    fdlModels.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlModels.top = new FormAttachment(wOverwrite, margin);
    wlModels.setLayoutData(fdlModels);

    wModelNames = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelNames);
    FormData fdModels = new FormData();
    fdModels.left = new FormAttachment(props.getMiddlePct(), 0);
    fdModels.right = new FormAttachment(100, 0);
    fdModels.top = new FormAttachment(wOverwrite, margin);
    wModelNames.setLayoutData(fdModels);

    Label wlDirs = new Label(shell, SWT.RIGHT);
    wlDirs.setText("Model dirs");
    PropsUi.setLook(wlDirs);
    FormData fdlDirs = new FormData();
    fdlDirs.left = new FormAttachment(0, 0);
    fdlDirs.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlDirs.top = new FormAttachment(wModelNames, margin);
    wlDirs.setLayoutData(fdlDirs);

    wModelDirectories = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelDirectories);
    FormData fdDirs = new FormData();
    fdDirs.left = new FormAttachment(props.getMiddlePct(), 0);
    fdDirs.right = new FormAttachment(100, 0);
    fdDirs.top = new FormAttachment(wModelNames, margin);
    wModelDirectories.setLayoutData(fdDirs);

    wEventMode = new Button(shell, SWT.CHECK);
    wEventMode.setText("Event mode (write the explicit event sequence)");
    PropsUi.setLook(wEventMode);
    FormData fdEventMode = new FormData();
    fdEventMode.left = new FormAttachment(props.getMiddlePct(), 0);
    fdEventMode.top = new FormAttachment(wModelDirectories, margin);
    wEventMode.setLayoutData(fdEventMode);

    // OK / Cancel
    wOk = new Button(shell, SWT.PUSH);
    wOk.setText("OK");
    PropsUi.setLook(wOk);
    FormData fdOk = new FormData();
    fdOk.left = new FormAttachment(50, -2 * margin);
    fdOk.bottom = new FormAttachment(100, 0);
    wOk.setLayoutData(fdOk);

    wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText("Cancel");
    PropsUi.setLook(wCancel);
    FormData fdCancel = new FormData();
    fdCancel.left = new FormAttachment(wOk, margin);
    fdCancel.bottom = new FormAttachment(100, 0);
    wCancel.setLayoutData(fdCancel);

    wbFile.addListener(SWT.Selection, e -> browse());
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    getData();
    input.setChanged(changed);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void browse() {
    FileDialog dialog = new FileDialog(shell, SWT.SAVE);
    dialog.setFilterExtensions(new String[] {"*.xtf", "*.*"});
    dialog.setFilterNames(new String[] {"INTERLIS transfer files", "All files"});
    String selected = dialog.open();
    if (selected != null) {
      wFileName.setText(selected);
    }
  }

  private void getData() {
    wFileName.setText(input.getFileName() == null ? "" : input.getFileName());
    wModelNames.setText(input.getModelNames() == null ? "" : input.getModelNames());
    wModelDirectories.setText(input.getModelDirectories() == null ? "" : input.getModelDirectories());
    wOverwrite.setSelection(input.isOverwrite());
    wEventMode.setSelection(input.isEventMode());
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    transformName = wTransformName.getText();
    input.setFileName(wFileName.getText());
    input.setModelNames(wModelNames.getText());
    input.setModelDirectories(wModelDirectories.getText());
    input.setOverwrite(wOverwrite.getSelection());
    input.setEventMode(wEventMode.getSelection());
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
