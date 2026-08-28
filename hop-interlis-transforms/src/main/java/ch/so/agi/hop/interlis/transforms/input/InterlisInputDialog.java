package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
import ch.so.agi.hop.interlis.transforms.InterlisProbeResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.eclipse.swt.widgets.Composite;
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

  private static final String ASSOCIATION_DISPLAY_SUFFIX = " (association)";

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
  private Button wIncludeOperation;
  private TextVar wDefaultSrid;
  private Button wKeepSourceObject;
  private TextVar wSourceObjectField;
  private Label wStatus;
  private Text wPreview;

  private List<InterlisClassDescriptor> classes = List.of();
  private List<ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor> associations =
      List.of();
  private final Map<String, String> classChoiceToScopedName = new LinkedHashMap<>();
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
    Composite fileRow = InterlisDialogUiSupport.createRow(shell, wTransformName, margin);
    Button wbFile = new Button(fileRow, SWT.PUSH | SWT.CENTER);
    wFileName = new TextVar(variables, fileRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        fileRow, "Data file", wFileName, wbFile, "Browse", props.getMiddlePct(), margin);

    // Models
    Label wlModels = new Label(shell, SWT.RIGHT);
    wlModels.setText("Models");
    PropsUi.setLook(wlModels);
    wlModels.setLayoutData(labelData(fileRow, margin));

    wModelNames = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelNames);
    FormData fdModels = new FormData();
    fdModels.left = new FormAttachment(props.getMiddlePct(), 0);
    fdModels.right = new FormAttachment(100, 0);
    fdModels.top = new FormAttachment(fileRow, margin);
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

    // Class and model reload
    Composite classRow = InterlisDialogUiSupport.createRow(shell, wModelDirectories, margin);
    Button wReload = new Button(classRow, SWT.PUSH);
    wClassName = new ComboVar(variables, classRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        classRow, "Class", wClassName, wReload, "Reload model", props.getMiddlePct(), margin);

    // Options: reserved fields + default SRID
    Label wlTid = new Label(shell, SWT.RIGHT);
    wlTid.setText("Reserved fields");
    PropsUi.setLook(wlTid);
    FormData fdlTid = labelData(classRow, margin);
    wlTid.setLayoutData(fdlTid);

    wIncludeTid = checkbox("_ili_tid", classRow, 0);
    wIncludeBid = checkbox("_ili_bid", wIncludeTid, 0);
    wIncludeClassName = checkbox("_ili_class", wIncludeBid, 0);
    wIncludeTopicName = checkbox("_ili_topic", wIncludeClassName, 0);
    wIncludeOperation = checkbox("_ili_operation (INSERT/UPDATE/DELETE)", wIncludeTopicName, 0);

    Label wlSrid = new Label(shell, SWT.RIGHT);
    wlSrid.setText("Default SRID");
    PropsUi.setLook(wlSrid);
    FormData fdlSrid = labelData(wIncludeOperation, margin);
    wlSrid.setLayoutData(fdlSrid);

    wDefaultSrid = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDefaultSrid);
    FormData fdSrid = new FormData();
    fdSrid.left = new FormAttachment(props.getMiddlePct(), 0);
    fdSrid.right = new FormAttachment(100, 0);
    fdSrid.top = new FormAttachment(wIncludeOperation, margin);
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
      wIncludeOperation.setSelection(input.isIncludeOperation());
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
    associations = result.associations();
    populateClassCombo();
    // The combo may have cleared a stale class after a model change. Keep the metadata in sync
    // without probing the model a second time.
    syncMetaFromWidgets();
    renderProbeResult(result);
  }

  private void populateClassCombo() {
    suppressRefresh = true;
    try {
      String current = wClassName.getText();
      String currentScopedName =
          classChoiceToScopedName.getOrDefault(current, selectedClassName());
      wClassName.removeAll();
      classChoiceToScopedName.clear();
      for (InterlisClassDescriptor descriptor : classes) {
        if (!descriptor.isAbstract()) {
          addClassChoice(descriptor.scopedName(), descriptor.scopedName());
        }
      }
      for (ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor association :
          associations) {
        addClassChoice(
            association.scopedName() + ASSOCIATION_DISPLAY_SUFFIX, association.scopedName());
      }
      String displayName = displayNameForScopedName(currentScopedName);
      wClassName.setText(displayName == null ? "" : displayName);
    } finally {
      suppressRefresh = false;
    }
  }

  private void addClassChoice(String displayName, String scopedName) {
    wClassName.add(displayName);
    classChoiceToScopedName.put(displayName, scopedName);
  }

  private String displayNameForScopedName(String scopedName) {
    if (scopedName == null || scopedName.isBlank()) {
      return null;
    }
    for (Map.Entry<String, String> entry : classChoiceToScopedName.entrySet()) {
      if (entry.getValue().equals(scopedName)) {
        return entry.getKey();
      }
    }
    return null;
  }

  private void refreshPreview() {
    syncMetaFromWidgets();
    InterlisProbeResult result = controller.probe(input, variables);
    classes = result.classes();
    associations = result.associations();
    renderProbeResult(result);
  }

  private void renderProbeResult(InterlisProbeResult result) {
    wStatus.setText(result.message());
    if (result.projection() == null) {
      wPreview.setText("");
    } else {
      wPreview.setText(controller.formatSchemaPreview(result.projection().plan()));
    }
  }

  private String selectedClassName() {
    String displayName = wClassName.getText();
    String scopedName = classChoiceToScopedName.get(displayName);
    if (scopedName != null) {
      return scopedName;
    }
    // Migrate association labels persisted by older dialog versions.
    if (displayName.endsWith(ASSOCIATION_DISPLAY_SUFFIX)) {
      return displayName.substring(0, displayName.length() - ASSOCIATION_DISPLAY_SUFFIX.length());
    }
    return displayName;
  }

  /** Copies the current widget values into the meta so probing uses the latest configuration. */
  private void syncMetaFromWidgets() {
    if (wFileName != null) {
      input.setFileName(wFileName.getText());
      input.setModelNames(wModelNames.getText());
      input.setModelDirectories(wModelDirectories.getText());
      input.setClassName(selectedClassName());
      input.setIncludeTid(wIncludeTid.getSelection());
      input.setIncludeBid(wIncludeBid.getSelection());
      input.setIncludeClassName(wIncludeClassName.getSelection());
      input.setIncludeTopicName(wIncludeTopicName.getSelection());
      input.setIncludeOperation(wIncludeOperation.getSelection());
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
