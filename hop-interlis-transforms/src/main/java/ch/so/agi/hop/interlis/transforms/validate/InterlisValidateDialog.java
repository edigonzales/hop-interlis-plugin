package ch.so.agi.hop.interlis.transforms.validate;

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

/** INTERLIS Validate dialog: transfer file, model source and validator options. */
public class InterlisValidateDialog extends BaseTransformDialog {

  private final InterlisValidateMeta input;

  private TextVar wFileName;
  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private TextVar wConfigFile;
  private TextVar wMaxErrors;
  private Button wValidateMultiplicity;
  private Button wStopOnFirstError;
  private Button wIncludeWarnings;
  private Button wIncludeInfo;
  private Button wFailOnErrors;

  public InterlisValidateDialog(
      Shell parent, IVariables variables, InterlisValidateMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("INTERLIS Validate");
    shell.setMinimumSize(760, 460);

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

    wModelNames = addTextRow("Models", fileRow, margin);
    wModelDirectories = addTextRow("Model dirs", wModelNames, 0);
    wConfigFile = addTextRow("Validator config", wModelDirectories, 0);
    wMaxErrors = addTextRow("Max errors", wConfigFile, 0);

    wValidateMultiplicity = new Button(shell, SWT.CHECK);
    wValidateMultiplicity.setText("Validate attribute/role multiplicity");
    PropsUi.setLook(wValidateMultiplicity);
    FormData fdMultiplicity = new FormData();
    fdMultiplicity.left = new FormAttachment(props.getMiddlePct(), 0);
    fdMultiplicity.top = new FormAttachment(wMaxErrors, margin);
    wValidateMultiplicity.setLayoutData(fdMultiplicity);

    wStopOnFirstError = new Button(shell, SWT.CHECK);
    wStopOnFirstError.setText("Stop after first error");
    PropsUi.setLook(wStopOnFirstError);
    FormData fdStop = new FormData();
    fdStop.left = new FormAttachment(props.getMiddlePct(), 0);
    fdStop.top = new FormAttachment(wValidateMultiplicity, margin);
    wStopOnFirstError.setLayoutData(fdStop);

    wIncludeWarnings = new Button(shell, SWT.CHECK);
    wIncludeWarnings.setText("Emit warnings");
    PropsUi.setLook(wIncludeWarnings);
    FormData fdWarnings = new FormData();
    fdWarnings.left = new FormAttachment(props.getMiddlePct(), 0);
    fdWarnings.top = new FormAttachment(wStopOnFirstError, margin);
    wIncludeWarnings.setLayoutData(fdWarnings);

    wIncludeInfo = new Button(shell, SWT.CHECK);
    wIncludeInfo.setText("Emit info findings");
    PropsUi.setLook(wIncludeInfo);
    FormData fdInfo = new FormData();
    fdInfo.left = new FormAttachment(props.getMiddlePct(), 0);
    fdInfo.top = new FormAttachment(wIncludeWarnings, margin);
    wIncludeInfo.setLayoutData(fdInfo);

    wFailOnErrors = new Button(shell, SWT.CHECK);
    wFailOnErrors.setText("Fail the pipeline when errors were found");
    PropsUi.setLook(wFailOnErrors);
    FormData fdFail = new FormData();
    fdFail.left = new FormAttachment(props.getMiddlePct(), 0);
    fdFail.top = new FormAttachment(wIncludeInfo, margin);
    wFailOnErrors.setLayoutData(fdFail);

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

  private TextVar addTextRow(String label, org.eclipse.swt.widgets.Control topControl, int offset) {
    Label wl = new Label(shell, SWT.RIGHT);
    wl.setText(label);
    PropsUi.setLook(wl);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(props.getMiddlePct(), -PropsUi.getMargin());
    fdl.top = new FormAttachment(topControl, offset == 0 ? PropsUi.getMargin() : 0);
    wl.setLayoutData(fdl);

    TextVar text = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(props.getMiddlePct(), 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(topControl, offset == 0 ? PropsUi.getMargin() : 0);
    text.setLayoutData(fd);
    return text;
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
    wConfigFile.setText(input.getConfigFile() == null ? "" : input.getConfigFile());
    wMaxErrors.setText(Long.toString(input.getMaxErrors()));
    wValidateMultiplicity.setSelection(input.isValidateMultiplicity());
    wStopOnFirstError.setSelection(input.isStopOnFirstError());
    wIncludeWarnings.setSelection(input.isIncludeWarnings());
    wIncludeInfo.setSelection(input.isIncludeInfo());
    wFailOnErrors.setSelection(input.isFailOnErrors());
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
    input.setConfigFile(wConfigFile.getText());
    input.setValidateMultiplicity(wValidateMultiplicity.getSelection());
    input.setStopOnFirstError(wStopOnFirstError.getSelection());
    input.setIncludeWarnings(wIncludeWarnings.getSelection());
    input.setIncludeInfo(wIncludeInfo.getSelection());
    input.setFailOnErrors(wFailOnErrors.getSelection());
    try {
      input.setMaxErrors(Long.parseLong(wMaxErrors.getText().trim()));
    } catch (NumberFormatException e) {
      input.setMaxErrors(InterlisValidateMeta.DEFAULT_MAX_ERRORS);
    }
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
