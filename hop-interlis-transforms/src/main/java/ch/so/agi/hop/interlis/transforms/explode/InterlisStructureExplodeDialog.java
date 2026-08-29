package ch.so.agi.hop.interlis.transforms.explode;

import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.InterlisStructureProbeResult;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;

/**
 * INTERLIS Structure Explode dialog: model-aware selection of the structure to explode plus a
 * live preview of the child row schema.
 *
 * <p>All model interpretation happens in {@link InterlisStructureExplodeDialogController}; this
 * class only renders widgets and delegates. Probing failures are shown in the preview area and
 * never make the dialog unusable.
 */
public class InterlisStructureExplodeDialog extends BaseTransformDialog {

  private final InterlisStructureExplodeMeta input;
  private final InterlisStructureExplodeDialogController controller =
      new InterlisStructureExplodeDialogController();

  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wClassName;
  private ComboVar wStructurePath;
  private TextVar wSourceObjectField;
  private TextVar wParentTidField;
  private TextVar wParentBidField;
  private Button wEmitParentBid;
  private TextVar wParentKeyField;
  private TextVar wIndexField;
  private Button wEmitIndexForBag;
  private TextVar wIncludeParentFields;
  private InterlisDialogUiSupport.StatusArea wStatus;
  private Label wPreviewDiagnostics;
  private Table wPreview;

  private List<InterlisClassDescriptor> classes = List.of();
  private List<String> structurePaths = List.of();
  private boolean suppressRefresh;

  public InterlisStructureExplodeDialog(
      Shell parent,
      IVariables variables,
      InterlisStructureExplodeMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("INTERLIS Structure Explode");
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

    // Model configuration
    wModelNames = addTextRow("Models", wTransformName, 0, null);
    wModelDirectories = addTextRow("Model dirs", wModelNames, 0, null);
    // Model status and class reload
    wStatus = InterlisDialogUiSupport.createStatusArea(shell, wModelDirectories, props.getMiddlePct(), margin);
    Composite classRow = InterlisDialogUiSupport.createRow(shell, wStatus.control(), margin);
    Button wReload = new Button(classRow, SWT.PUSH);
    wClassName = new ComboVar(variables, classRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        classRow, "Class", wClassName, wReload, "Reload", props.getMiddlePct(), margin);

    // Structure
    wStructurePath = addComboRow("Structure", classRow, margin, null);

    // Fields
    wSourceObjectField = addTextRow("Source object field", wStructurePath, margin, null);
    wParentTidField = addTextRow("Parent TID field", wSourceObjectField, 0, null);
    wParentBidField = addTextRow("Parent BID field", wParentTidField, 0, null);
    wParentKeyField = addTextRow("Parent key field", wParentBidField, 0, null);
    wIndexField = addTextRow("Index field", wParentKeyField, 0, null);
    wIncludeParentFields = addTextRow("Copy parent fields", wIndexField, 0, null);

    wEmitParentBid = new Button(shell, SWT.CHECK);
    wEmitParentBid.setText("Emit parent BID field");
    PropsUi.setLook(wEmitParentBid);
    FormData fdEmitBid = new FormData();
    fdEmitBid.left = new FormAttachment(props.getMiddlePct(), 0);
    fdEmitBid.top = new FormAttachment(wParentBidField, margin);
    wEmitParentBid.setLayoutData(fdEmitBid);

    wEmitIndexForBag = new Button(shell, SWT.CHECK);
    wEmitIndexForBag.setText("Emit technical index for BAG");
    PropsUi.setLook(wEmitIndexForBag);
    FormData fdEmitIndex = new FormData();
    fdEmitIndex.left = new FormAttachment(props.getMiddlePct(), 0);
    fdEmitIndex.top = new FormAttachment(wEmitParentBid, margin);
    wEmitIndexForBag.setLayoutData(fdEmitIndex);

    // Schema preview diagnostics and table
    wPreviewDiagnostics = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wPreviewDiagnostics);
    FormData fdPreviewDiagnostics = new FormData();
    fdPreviewDiagnostics.left = new FormAttachment(0, 0);
    fdPreviewDiagnostics.right = new FormAttachment(100, 0);
    fdPreviewDiagnostics.top = new FormAttachment(wEmitIndexForBag, margin);
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
    wReload.addListener(SWT.Selection, e -> reloadAndPreview());
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

    getData();
    reloadAndPreview();
    input.setChanged(changed);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private TextVar addTextRow(
      String label, org.eclipse.swt.widgets.Control topControl, int offset, Button browseButton) {
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
      String label,
      org.eclipse.swt.widgets.Control topControl,
      int offset,
      org.eclipse.swt.widgets.Control rightControl) {
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
    fd.right =
        rightControl == null
            ? new FormAttachment(100, 0)
            : new FormAttachment(rightControl, -PropsUi.getMargin());
    fd.top = new FormAttachment(topControl, offset == 0 ? PropsUi.getMargin() : 0);
    combo.setLayoutData(fd);
    return combo;
  }

  private void getData() {
    suppressRefresh = true;
    try {
      wModelNames.setText(input.getModelNames() == null ? "" : input.getModelNames());
      wModelDirectories.setText(
          input.getModelDirectories() == null ? "" : input.getModelDirectories());
      wClassName.setText(input.getClassName() == null ? "" : input.getClassName());
      wStructurePath.setText(
          input.getStructureAttributePath() == null ? "" : input.getStructureAttributePath());
      wSourceObjectField.setText(
          input.getSourceObjectField() == null ? "" : input.getSourceObjectField());
      wParentTidField.setText(input.getParentTidField() == null ? "" : input.getParentTidField());
      wParentBidField.setText(input.getParentBidField() == null ? "" : input.getParentBidField());
      wParentKeyField.setText(
          input.getParentKeyFieldName() == null ? "" : input.getParentKeyFieldName());
      wIndexField.setText(input.getIndexFieldName() == null ? "" : input.getIndexFieldName());
      wIncludeParentFields.setText(
          input.getIncludeParentFields() == null
              ? ""
              : String.join(",", input.getIncludeParentFields()));
      wEmitParentBid.setSelection(input.isEmitParentBid());
      wEmitIndexForBag.setSelection(input.isEmitIndexForBag());
    } finally {
      suppressRefresh = false;
    }
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void reloadAndPreview() {
    syncMetaFromWidgets();
    InterlisStructureProbeResult result = controller.probe(input, variables);
    classes = result.classes();
    structurePaths = result.structurePaths();
    populateClassCombo();
    refreshStructureComboAndPreview();
  }

  private void refreshStructureComboAndPreview() {
    syncMetaFromWidgets();
    InterlisStructureProbeResult result = controller.probe(input, variables);
    classes = result.classes();
    structurePaths = result.structurePaths();
    populateClassCombo();
    populateStructureCombo();
    if (result.ok() && result.projection() != null) {
      InterlisSchemaPreview preview =
          controller.createSchemaPreview(result.projection().plan());
      setStatus(result, preview);
      InterlisDialogUiSupport.populatePreviewTable(wPreview, preview.rows());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, preview);
    } else {
      setStatus(result, null);
      InterlisDialogUiSupport.populatePreviewTable(wPreview, List.of());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, "");
    }
    shell.layout(true, true);
  }

  private void refreshPreview() {
    syncMetaFromWidgets();
    InterlisStructureProbeResult result = controller.probe(input, variables);
    if (result.ok() && result.projection() != null) {
      InterlisSchemaPreview preview =
          controller.createSchemaPreview(result.projection().plan());
      setStatus(result, preview);
      InterlisDialogUiSupport.populatePreviewTable(wPreview, preview.rows());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, preview);
    } else {
      setStatus(result, null);
      InterlisDialogUiSupport.populatePreviewTable(wPreview, List.of());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, "");
    }
    shell.layout(true, true);
  }

  private void setStatus(InterlisStructureProbeResult result, InterlisSchemaPreview preview) {
    wStatus.set(
        InterlisDialogUiSupport.statusSeverity(
            InterlisDialogUiSupport.statusSeverity(
                result.projection() != null, result.ok(), result.message()),
            preview),
        result.message());
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
      input.setModelNames(wModelNames.getText());
      input.setModelDirectories(wModelDirectories.getText());
      input.setClassName(wClassName.getText());
      input.setStructureAttributePath(wStructurePath.getText());
      input.setSourceObjectField(wSourceObjectField.getText());
      input.setParentTidField(wParentTidField.getText());
      input.setParentBidField(wParentBidField.getText());
      input.setParentKeyFieldName(wParentKeyField.getText());
      input.setIndexFieldName(wIndexField.getText());
      input.setIncludeParentFields(
          parseCommaSeparated(wIncludeParentFields.getText()));
      input.setEmitParentBid(wEmitParentBid.getSelection());
      input.setEmitIndexForBag(wEmitIndexForBag.getSelection());
    }
  }

  private static List<String> parseCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(n -> !n.isEmpty())
        .toList();
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
