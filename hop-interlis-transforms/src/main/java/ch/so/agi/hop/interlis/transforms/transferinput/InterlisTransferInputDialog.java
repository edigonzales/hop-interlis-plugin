package ch.so.agi.hop.interlis.transforms.transferinput;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** INTERLIS Transfer Input dialog: transfer file, model source and mode. */
public class InterlisTransferInputDialog extends BaseTransformDialog {

  private final InterlisTransferInputMeta input;

  private TextVar wFileName;
  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wMode;

  public InterlisTransferInputDialog(
      Shell parent,
      IVariables variables,
      InterlisTransferInputMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("INTERLIS Transfer Input");
    shell.setMinimumSize(720, 380);

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
    Label wlFile = new Label(shell, SWT.RIGHT);
    wlFile.setText("Data file");
    PropsUi.setLook(wlFile);
    FormData fdlFile = new FormData();
    fdlFile.left = new FormAttachment(0, 0);
    fdlFile.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlFile.top = new FormAttachment(wTransformName, margin);
    wlFile.setLayoutData(fdlFile);

    Button wbFile = new Button(shell, SWT.PUSH | SWT.CENTER);
    wbFile.setText("Browse");
    PropsUi.setLook(wbFile);
    FormData fdbFile = new FormData();
    fdbFile.right = new FormAttachment(100, 0);
    fdbFile.top = new FormAttachment(wTransformName, margin);
    wbFile.setLayoutData(fdbFile);

    wFileName = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFileName);
    FormData fdFile = new FormData();
    fdFile.left = new FormAttachment(props.getMiddlePct(), 0);
    fdFile.right = new FormAttachment(wbFile, -margin);
    fdFile.top = new FormAttachment(wTransformName, margin);
    wFileName.setLayoutData(fdFile);

    // Models
    Label wlModels = new Label(shell, SWT.RIGHT);
    wlModels.setText("Models");
    PropsUi.setLook(wlModels);
    FormData fdlModels = new FormData();
    fdlModels.left = new FormAttachment(0, 0);
    fdlModels.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlModels.top = new FormAttachment(wFileName, margin);
    wlModels.setLayoutData(fdlModels);

    wModelNames = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelNames);
    FormData fdModels = new FormData();
    fdModels.left = new FormAttachment(props.getMiddlePct(), 0);
    fdModels.right = new FormAttachment(100, 0);
    fdModels.top = new FormAttachment(wFileName, margin);
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

    // Mode
    Label wlMode = new Label(shell, SWT.RIGHT);
    wlMode.setText("Mode");
    PropsUi.setLook(wlMode);
    FormData fdlMode = new FormData();
    fdlMode.left = new FormAttachment(0, 0);
    fdlMode.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlMode.top = new FormAttachment(wModelDirectories, margin);
    wlMode.setLayoutData(fdlMode);

    wMode = new ComboVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wMode);
    FormData fdMode = new FormData();
    fdMode.left = new FormAttachment(props.getMiddlePct(), 0);
    fdMode.right = new FormAttachment(100, 0);
    fdMode.top = new FormAttachment(wModelDirectories, margin);
    wMode.setLayoutData(fdMode);
    for (TransferInputMode mode : TransferInputMode.values()) {
      wMode.add(mode.name());
    }

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
    FileDialog dialog = new FileDialog(shell, SWT.OPEN);
    dialog.setFilterExtensions(new String[] {"*.xtf", "*.*"});
    dialog.setFilterNames(new String[] {"INTERLIS transfer files", "All files"});
    String current = wFileName.getText();
    if (!Utils.isEmpty(current)) {
      dialog.setFilterPath(current);
    }
    String selected = dialog.open();
    if (selected != null) {
      wFileName.setText(selected);
    }
  }

  private void getData() {
    wFileName.setText(input.getFileName() == null ? "" : input.getFileName());
    wModelNames.setText(input.getModelNames() == null ? "" : input.getModelNames());
    wModelDirectories.setText(input.getModelDirectories() == null ? "" : input.getModelDirectories());
    wMode.setText(input.resolvedMode().name());
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
    input.setMode(wMode.getText());
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
