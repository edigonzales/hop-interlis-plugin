package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.InterlisProbeResult;
import java.util.List;
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

/**
 * INTERLIS Input dialog: model source, class browser (combo with all transferable classes) and a
 * live schema preview.
 *
 * <p>All model interpretation happens in {@link InterlisInputDialogController}; this class only
 * renders widgets and delegates. Probing failures are shown in the preview area and never make
 * the dialog unusable.
 */
public class InterlisInputDialog extends BaseTransformDialog {

  private final InterlisInputMeta input;
  private final InterlisInputDialogController controller = new InterlisInputDialogController();

  private TextVar wFileName;
  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wClassName;
  private Button wIncludeTid;
  private Button wIncludeBid;
  private Button wIncludeClassName;
  private Button wIncludeTopicName;
  private TextVar wDefaultSrid;
  private Button wKeepSourceObject;
  private TextVar wSourceObjectField;
  private Label wStatus;
  private Text wPreview;

  private List<InterlisClassDescriptor> classes = List.of();
  private boolean suppressRefresh;

  public InterlisInputDialog(
      Shell parent, IVariables variables, InterlisInputMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("INTERLIS Input");
    shell.setMinimumSize(860, 640);

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

    // Transfer file
    Label wlFile = new Label(shell, SWT.RIGHT);
    wlFile.setText("Data file");
    PropsUi.setLook(wlFile);
    FormData fdlFile = labelData(wTransformName, margin);
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
    wlModels.setLayoutData(labelData(wFileName, margin));

    wModelNames = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelNames);
    FormData fdModels = new FormData();
    fdModels.left = new FormAttachment(props.getMiddlePct(), 0);
    fdModels.right = new FormAttachment(100, 0);
    fdModels.top = new FormAttachment(wFileName, margin);
    wModelNames.setLayoutData(fdModels);

    // Model directories
    Label wlDirs = new Label(shell, SWT.RIGHT);
    wlDirs.setText("Model dirs");
    PropsUi.setLook(wlDirs);
    wlDirs.setLayoutData(labelData(wModelNames, margin));

    wModelDirectories = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelDirectories);
    FormData fdDirs = new FormData();
    fdDirs.left = new FormAttachment(props.getMiddlePct(), 0);
    fdDirs.right = new FormAttachment(100, 0);
    fdDirs.top = new FormAttachment(wModelNames, margin);
    wModelDirectories.setLayoutData(fdDirs);

    // Reload
    Button wReload = new Button(shell, SWT.PUSH);
    wReload.setText("Reload model");
    PropsUi.setLook(wReload);
    FormData fdReload = new FormData();
    fdReload.right = new FormAttachment(100, 0);
    fdReload.top = new FormAttachment(wModelDirectories, margin);
    wReload.setLayoutData(fdReload);

    // Class
    Label wlClass = new Label(shell, SWT.RIGHT);
    wlClass.setText("Class");
    PropsUi.setLook(wlClass);
    FormData fdlClass = labelData(wModelDirectories, margin);
    wlClass.setLayoutData(fdlClass);

    wClassName = new ComboVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wClassName);
    FormData fdClass = new FormData();
    fdClass.left = new FormAttachment(props.getMiddlePct(), 0);
    fdClass.right = new FormAttachment(wReload, -margin);
    fdClass.top = new FormAttachment(wModelDirectories, margin);
    wClassName.setLayoutData(fdClass);

    // Options: reserved fields + default SRID
    Label wlTid = new Label(shell, SWT.RIGHT);
    wlTid.setText("Reserved fields");
    PropsUi.setLook(wlTid);
    FormData fdlTid = labelData(wClassName, margin);
    wlTid.setLayoutData(fdlTid);

    wIncludeTid = checkbox("_ili_tid", wClassName, 0);
    wIncludeBid = checkbox("_ili_bid", wIncludeTid, 0);
    wIncludeClassName = checkbox("_ili_class", wIncludeBid, 0);
    wIncludeTopicName = checkbox("_ili_topic", wIncludeClassName, 0);

    Label wlSrid = new Label(shell, SWT.RIGHT);
    wlSrid.setText("Default SRID");
    PropsUi.setLook(wlSrid);
    FormData fdlSrid = labelData(wIncludeTopicName, margin);
    wlSrid.setLayoutData(fdlSrid);

    wDefaultSrid = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDefaultSrid);
    FormData fdSrid = new FormData();
    fdSrid.left = new FormAttachment(props.getMiddlePct(), 0);
    fdSrid.right = new FormAttachment(100, 0);
    fdSrid.top = new FormAttachment(wIncludeTopicName, margin);
    wDefaultSrid.setLayoutData(fdSrid);

    // Structures: keep the source object for downstream Structure Explode
    wKeepSourceObject = checkbox("Keep source object for Structure Explode", wDefaultSrid, margin);

    Label wlSourceObject = new Label(shell, SWT.RIGHT);
    wlSourceObject.setText("Source object field");
    PropsUi.setLook(wlSourceObject);
    FormData fdlSourceObject = labelData(wKeepSourceObject, margin);
    wlSourceObject.setLayoutData(fdlSourceObject);

    wSourceObjectField = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceObjectField);
    FormData fdSourceObject = new FormData();
    fdSourceObject.left = new FormAttachment(props.getMiddlePct(), 0);
    fdSourceObject.right = new FormAttachment(100, 0);
    fdSourceObject.top = new FormAttachment(wKeepSourceObject, margin);
    wSourceObjectField.setLayoutData(fdSourceObject);

    // Status
    wStatus = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wStatus);
    FormData fdStatus = new FormData();
    fdStatus.left = new FormAttachment(0, 0);
    fdStatus.right = new FormAttachment(100, 0);
    fdStatus.top = new FormAttachment(wSourceObjectField, margin);
    wStatus.setLayoutData(fdStatus);

    // Schema preview
    wPreview = new Text(shell, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
    PropsUi.setLook(wPreview);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, 0);
    fdPreview.right = new FormAttachment(100, 0);
    fdPreview.top = new FormAttachment(wStatus, margin);
    fdPreview.bottom = new FormAttachment(100, -margin * 8);
    wPreview.setLayoutData(fdPreview);

    // OK / Cancel
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText("OK");
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText("Cancel");
    setButtonPositions(new Button[] {wOk, wCancel}, margin, null);

    // Listeners
    wTransformName.addModifyListener(e -> input.setChanged());
    wFileName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            reloadClassesAndPreview();
          }
        });
    wModelNames.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            reloadClassesAndPreview();
          }
        });
    wModelDirectories.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            reloadClassesAndPreview();
          }
        });
    wClassName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refreshPreview();
          }
        });
    wIncludeTid.addListener(SWT.Selection, e -> refreshPreview());
    wIncludeBid.addListener(SWT.Selection, e -> refreshPreview());
    wIncludeClassName.addListener(SWT.Selection, e -> refreshPreview());
    wIncludeTopicName.addListener(SWT.Selection, e -> refreshPreview());
    wDefaultSrid.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refreshPreview();
          }
        });
    wbFile.addListener(SWT.Selection, e -> browse());
    wReload.addListener(SWT.Selection, e -> reloadClassesAndPreview());
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    getData();
    reloadClassesAndPreview();
    input.setChanged(changed);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private Button checkbox(String label, org.eclipse.swt.widgets.Control topControl, int offset) {
    Button button = new Button(shell, SWT.CHECK);
    button.setText(label);
    PropsUi.setLook(button);
    FormData fd = new FormData();
    fd.left = new FormAttachment(props.getMiddlePct(), 0);
    fd.top = new FormAttachment(topControl, offset == 0 ? PropsUi.getMargin() : 0);
    button.setLayoutData(fd);
    return button;
  }

  private FormData labelData(org.eclipse.swt.widgets.Control topControl, int margin) {
    FormData data = new FormData();
    data.left = new FormAttachment(0, 0);
    data.right = new FormAttachment(props.getMiddlePct(), -margin);
    data.top = new FormAttachment(topControl, margin);
    return data;
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
    suppressRefresh = true;
    try {
      wFileName.setText(input.getFileName() == null ? "" : input.getFileName());
      wModelNames.setText(input.getModelNames() == null ? "" : input.getModelNames());
      wModelDirectories.setText(
          input.getModelDirectories() == null ? "" : input.getModelDirectories());
      wClassName.setText(input.getClassName() == null ? "" : input.getClassName());
      wIncludeTid.setSelection(input.isIncludeTid());
      wIncludeBid.setSelection(input.isIncludeBid());
      wIncludeClassName.setSelection(input.isIncludeClassName());
      wIncludeTopicName.setSelection(input.isIncludeTopicName());
      wDefaultSrid.setText(input.getDefaultSrid() == null ? "" : input.getDefaultSrid());
      wKeepSourceObject.setSelection(input.isKeepSourceObject());
      wSourceObjectField.setText(
          input.getSourceObjectFieldName() == null ? "" : input.getSourceObjectFieldName());
    } finally {
      suppressRefresh = false;
    }
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void reloadClassesAndPreview() {
    syncMetaFromWidgets();
    InterlisProbeResult result = controller.probe(input, variables);
    classes = result.classes();
    if (result.successful()) {
      populateClassCombo();
      refreshPreview();
    } else {
      populateClassCombo();
      wStatus.setText(result.message());
      wPreview.setText("");
    }
  }

  private void populateClassCombo() {
    suppressRefresh = true;
    try {
      String current = wClassName.getText();
      wClassName.removeAll();
      for (InterlisClassDescriptor descriptor : classes) {
        if (!descriptor.isAbstract()) {
          wClassName.add(descriptor.scopedName());
        }
      }
      if (classes.stream().anyMatch(c -> !c.isAbstract() && c.scopedName().equals(current))) {
        wClassName.setText(current);
      }
    } finally {
      suppressRefresh = false;
    }
  }

  private void refreshPreview() {
    syncMetaFromWidgets();
    InterlisProbeResult result = controller.probe(input, variables);
    classes = result.classes();
    if (result.successful()) {
      wStatus.setText(result.message());
      wPreview.setText(controller.formatSchemaPreview(result.projection().plan()));
    } else {
      wStatus.setText(result.message());
      wPreview.setText("");
    }
  }

  /** Copies the current widget values into the meta so probing uses the latest configuration. */
  private void syncMetaFromWidgets() {
    if (wFileName != null) {
      input.setFileName(wFileName.getText());
      input.setModelNames(wModelNames.getText());
      input.setModelDirectories(wModelDirectories.getText());
      input.setClassName(wClassName.getText());
      input.setIncludeTid(wIncludeTid.getSelection());
      input.setIncludeBid(wIncludeBid.getSelection());
      input.setIncludeClassName(wIncludeClassName.getSelection());
      input.setIncludeTopicName(wIncludeTopicName.getSelection());
      input.setDefaultSrid(wDefaultSrid.getText());
      input.setKeepSourceObject(wKeepSourceObject.getSelection());
      input.setSourceObjectFieldName(wSourceObjectField.getText());
    }
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    transformName = wTransformName.getText();
    syncMetaFromWidgets();
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
