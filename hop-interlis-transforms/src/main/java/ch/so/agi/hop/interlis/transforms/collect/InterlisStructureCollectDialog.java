package ch.so.agi.hop.interlis.transforms.collect;

import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.InterlisStructureProbeResult;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;

/**
 * INTERLIS Structure Collect dialog: selects the parent and child input streams and the model-aware
 * structure to collect, with a live configuration preview.
 *
 * <p>All model interpretation happens in {@link InterlisStructureCollectDialogController}; this
 * class only renders widgets and delegates. Probing failures are shown in the preview area and
 * never make the dialog unusable.
 */
public class InterlisStructureCollectDialog extends BaseTransformDialog {

  private final InterlisStructureCollectMeta input;
  private final InterlisStructureCollectDialogController controller =
      new InterlisStructureCollectDialogController();

  private ComboVar wParentInputTransform;
  private ComboVar wChildInputTransform;
  private TextVar wParentKeyField;
  private TextVar wChildParentKeyField;
  private TextVar wChildIndexField;
  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wClassName;
  private ComboVar wStructurePath;
  private TextVar wSourceObjectField;
  private Button wStrictOrdering;
  private Button wFailOnDuplicateIndex;
  private Button wFailOnChildWithoutParent;
  private InterlisDialogUiSupport.StatusArea wStatus;
  private Label wSummary;
  private Label wPreviewDiagnostics;
  private Table wPreview;

  private List<InterlisClassDescriptor> classes = List.of();
  private List<String> structurePaths = List.of();
  private boolean suppressRefresh;
  private boolean forceReload;
  private ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator probeCoordinator;

  public InterlisStructureCollectDialog(
      Shell parent,
      IVariables variables,
      InterlisStructureCollectMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    var display = shell.getDisplay();
    probeCoordinator =
        new ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator(
            action -> {
              if (!display.isDisposed())
                display.asyncExec(
                    () -> {
                      if (!shell.isDisposed()) action.run();
                    });
            });
    shell.addListener(SWT.Dispose, e -> probeCoordinator.close());
    setShellImage(shell, input);
    shell.setText("INTERLIS Structure Collect");
    shell.setMinimumSize(860, 620);

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

    // Streams
    wParentInputTransform = addComboRow("Parent input transform", wTransformName, 0);
    wChildInputTransform = addComboRow("Child input transform", wParentInputTransform, margin);
    wParentKeyField = addTextRow("Parent key field", wChildInputTransform, margin);
    wChildParentKeyField = addTextRow("Child parent key field", wParentKeyField, 0);
    wChildIndexField = addTextRow("Child index field", wChildParentKeyField, 0);

    // Model configuration
    wModelNames = addTextRow("Models", wChildIndexField, margin);
    wModelDirectories = addTextRow("Model dirs", wModelNames, 0);
    // Model status and class reload
    wStatus =
        InterlisDialogUiSupport.createStatusArea(
            shell, wModelDirectories, props.getMiddlePct(), margin);
    Composite classRow = InterlisDialogUiSupport.createRow(shell, wStatus.control(), margin);
    Button wReload = new Button(classRow, SWT.PUSH);
    wClassName = new ComboVar(variables, classRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        classRow, "Class", wClassName, wReload, "Reload", props.getMiddlePct(), margin);

    wStructurePath = addComboRow("Structure", classRow, margin);
    wSourceObjectField = addTextRow("Source object field", wStructurePath, margin);

    // Options
    wStrictOrdering = new Button(shell, SWT.CHECK);
    wStrictOrdering.setText("Strict LIST ordering (contiguous indexes)");
    PropsUi.setLook(wStrictOrdering);
    FormData fdStrict = new FormData();
    fdStrict.left = new FormAttachment(props.getMiddlePct(), 0);
    fdStrict.top = new FormAttachment(wSourceObjectField, margin);
    wStrictOrdering.setLayoutData(fdStrict);

    wFailOnDuplicateIndex = new Button(shell, SWT.CHECK);
    wFailOnDuplicateIndex.setText("Fail on duplicate index");
    PropsUi.setLook(wFailOnDuplicateIndex);
    FormData fdDuplicate = new FormData();
    fdDuplicate.left = new FormAttachment(props.getMiddlePct(), 0);
    fdDuplicate.top = new FormAttachment(wStrictOrdering, margin);
    wFailOnDuplicateIndex.setLayoutData(fdDuplicate);

    wFailOnChildWithoutParent = new Button(shell, SWT.CHECK);
    wFailOnChildWithoutParent.setText("Fail on child without parent");
    PropsUi.setLook(wFailOnChildWithoutParent);
    FormData fdOrphan = new FormData();
    fdOrphan.left = new FormAttachment(props.getMiddlePct(), 0);
    fdOrphan.top = new FormAttachment(wFailOnDuplicateIndex, margin);
    wFailOnChildWithoutParent.setLayoutData(fdOrphan);

    // Mapping summary, diagnostics and table
    wSummary = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wSummary);
    FormData fdSummary = new FormData();
    fdSummary.left = new FormAttachment(0, 0);
    fdSummary.right = new FormAttachment(100, 0);
    fdSummary.top = new FormAttachment(wFailOnChildWithoutParent, margin);
    wSummary.setLayoutData(fdSummary);

    wPreviewDiagnostics = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wPreviewDiagnostics);
    FormData fdPreviewDiagnostics = new FormData();
    fdPreviewDiagnostics.left = new FormAttachment(0, 0);
    fdPreviewDiagnostics.right = new FormAttachment(100, 0);
    fdPreviewDiagnostics.top = new FormAttachment(wSummary, margin);
    wPreviewDiagnostics.setLayoutData(fdPreviewDiagnostics);

    wPreview = InterlisDialogUiSupport.createPreviewTable(shell);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, 0);
    fdPreview.right = new FormAttachment(100, 0);
    fdPreview.top = new FormAttachment(wPreviewDiagnostics, margin);
    fdPreview.bottom = new FormAttachment(wOk, -2 * margin);
    wPreview.setLayoutData(fdPreview);

    // OK/Cancel
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

    // Listeners
    wReload.addListener(
        SWT.Selection,
        e -> {
          forceReload = true;
          reloadAndPreview();
        });
    wClassName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refreshStructureComboAndPreview();
          }
        });
    wStructurePath.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refreshPreview();
          }
        });
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    wModelNames.addModifyListener(
        e -> {
          if (!suppressRefresh) refreshStructureComboAndPreview();
        });
    wModelDirectories.addModifyListener(
        e -> {
          if (!suppressRefresh) refreshStructureComboAndPreview();
        });
    getData();
    populateStreamCombos();
    reloadAndPreview();
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

  private ComboVar addComboRow(
      String label, org.eclipse.swt.widgets.Control topControl, int offset) {
    Label wl = new Label(shell, SWT.RIGHT);
    wl.setText(label);
    PropsUi.setLook(wl);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(props.getMiddlePct(), -PropsUi.getMargin());
    fdl.top = new FormAttachment(topControl, offset == 0 ? PropsUi.getMargin() : 0);
    wl.setLayoutData(fdl);

    ComboVar combo = new ComboVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(combo);
    FormData fd = new FormData();
    fd.left = new FormAttachment(props.getMiddlePct(), 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(topControl, offset == 0 ? PropsUi.getMargin() : 0);
    combo.setLayoutData(fd);
    return combo;
  }

  private void getData() {
    suppressRefresh = true;
    try {
      wParentInputTransform.setText(
          input.getParentInputTransform() == null ? "" : input.getParentInputTransform());
      wChildInputTransform.setText(
          input.getChildInputTransform() == null ? "" : input.getChildInputTransform());
      wParentKeyField.setText(input.getParentKeyField() == null ? "" : input.getParentKeyField());
      wChildParentKeyField.setText(
          input.getChildParentKeyField() == null ? "" : input.getChildParentKeyField());
      wChildIndexField.setText(
          input.getChildIndexField() == null ? "" : input.getChildIndexField());
      wModelNames.setText(input.getModelNames() == null ? "" : input.getModelNames());
      wModelDirectories.setText(
          input.getModelDirectories() == null ? "" : input.getModelDirectories());
      wClassName.setText(input.getClassName() == null ? "" : input.getClassName());
      wStructurePath.setText(
          input.getStructureAttributePath() == null ? "" : input.getStructureAttributePath());
      wSourceObjectField.setText(
          input.getSourceObjectField() == null ? "" : input.getSourceObjectField());
      wStrictOrdering.setSelection(input.isStrictOrdering());
      wFailOnDuplicateIndex.setSelection(input.isFailOnDuplicateIndex());
      wFailOnChildWithoutParent.setSelection(input.isFailOnChildWithoutParent());
    } finally {
      suppressRefresh = false;
    }
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void populateStreamCombos() {
    suppressRefresh = true;
    try {
      for (TransformMeta transform : pipelineMeta.getTransforms()) {
        wParentInputTransform.add(transform.getName());
        wChildInputTransform.add(transform.getName());
      }
    } finally {
      suppressRefresh = false;
    }
  }

  private void reloadAndPreview() {
    refreshStructureComboAndPreview();
  }

  private void refreshStructureComboAndPreview() {
    if (suppressRefresh) return;
    syncMetaFromWidgets();
    var snapshot = (InterlisStructureCollectMeta) input.clone();
    var vars = ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator.snapshot(variables);
    boolean immediate = forceReload;
    forceReload = false;
    probeCoordinator.submit(
        immediate,
        () -> controller.probe(snapshot, vars),
        this::applyRefreshStructureComboAndPreview,
        this::probeFailed);
  }

  private void applyRefreshStructureComboAndPreview(InterlisStructureProbeResult result) {

    classes = result.classes();
    structurePaths = result.structurePaths();
    populateClassCombo();
    populateStructureCombo();
    refreshPreview(result);
  }

  private void refreshPreview() {
    refreshStructureComboAndPreview();
  }

  private void refreshPreview(InterlisStructureProbeResult result) {
    if (result.ok() && result.projection() != null) {
      wSummary.setText(
          controller.formatCollectPreview(
              result.projection().plan(),
              wParentInputTransform.getText(),
              wChildInputTransform.getText(),
              wParentKeyField.getText(),
              wChildParentKeyField.getText()));
      InterlisSchemaPreview preview =
          controller.createSchemaPreview(
              result.projection().plan(),
              wParentInputTransform.getText(),
              wChildInputTransform.getText(),
              wParentKeyField.getText(),
              wChildParentKeyField.getText());
      wStatus.set(
          InterlisDialogUiSupport.statusSeverity(
              InterlisDialogUiSupport.StatusSeverity.valueOf(result.status().name()), preview),
          result.message());
      InterlisDialogUiSupport.populatePreviewTable(wPreview, preview.rows());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, preview);
    } else {
      wStatus.set(
          InterlisDialogUiSupport.StatusSeverity.valueOf(result.status().name()), result.message());
      wSummary.setText("");
      InterlisDialogUiSupport.populatePreviewTable(wPreview, List.of());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, "");
    }
    shell.layout(true, true);
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

  private void populateStructureCombo() {
    suppressRefresh = true;
    try {
      String current = wStructurePath.getText();
      wStructurePath.removeAll();
      for (String path : structurePaths) {
        wStructurePath.add(path);
      }
      if (structurePaths.contains(current)) {
        wStructurePath.setText(current);
      }
    } finally {
      suppressRefresh = false;
    }
  }

  private void syncMetaFromWidgets() {
    if (wModelNames != null) {
      input.setParentInputTransform(wParentInputTransform.getText());
      input.setChildInputTransform(wChildInputTransform.getText());
      input.setParentKeyField(wParentKeyField.getText());
      input.setChildParentKeyField(wChildParentKeyField.getText());
      input.setChildIndexField(wChildIndexField.getText());
      input.setModelNames(wModelNames.getText());
      input.setModelDirectories(wModelDirectories.getText());
      input.setClassName(wClassName.getText());
      input.setStructureAttributePath(wStructurePath.getText());
      input.setSourceObjectField(wSourceObjectField.getText());
      input.setStrictOrdering(wStrictOrdering.getSelection());
      input.setFailOnDuplicateIndex(wFailOnDuplicateIndex.getSelection());
      input.setFailOnChildWithoutParent(wFailOnChildWithoutParent.getSelection());
    }
  }

  private void probeFailed(Exception error) {
    wStatus.set(
        ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport.StatusSeverity.ERROR,
        ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport.rootCauseMessage(error));
    shell.layout(true, true);
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
