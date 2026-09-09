package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport;
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
 * INTERLIS Role Join dialog: selects the two input streams, the model-derived role and the fields
 * to join, with a live configuration preview.
 *
 * <p>All model interpretation happens in {@link InterlisRoleJoinDialogController}; probing failures
 * are shown in the preview area and never make the dialog unusable.
 */
public class InterlisRoleJoinDialog extends BaseTransformDialog {

  private final InterlisRoleJoinMeta input;
  private final InterlisRoleJoinDialogController controller =
      new InterlisRoleJoinDialogController();

  private ComboVar wMainInputTransform;
  private ComboVar wLookupInputTransform;
  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wClassName;
  private ComboVar wRoleName;
  private TextVar wMainReferenceField;
  private TextVar wLookupTidField;
  private TextVar wPrefix;
  private TextVar wLookupFields;
  private TextVar wMaxLookupRows;
  private Button wFailOnMissingMandatoryReference;
  private Button wFailOnDuplicateTid;
  private InterlisDialogUiSupport.StatusArea wStatus;
  private Label wSummary;
  private Label wPreviewDiagnostics;
  private Table wPreview;

  private List<InterlisClassDescriptor> classes = List.of();
  private boolean suppressRefresh;
  private boolean forceReload;
  private ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator probeCoordinator;

  public InterlisRoleJoinDialog(
      Shell parent,
      IVariables variables,
      InterlisRoleJoinMeta transformMeta,
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
    shell.setText("INTERLIS Role Join");
    shell.setMinimumSize(860, 600);

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
    wMainInputTransform = addComboRow("Main input", wTransformName, 0);
    wLookupInputTransform = addComboRow("Lookup input", wMainInputTransform, margin);
    wModelNames = addTextRow("Models", wLookupInputTransform, margin);
    wModelDirectories = addTextRow("Model dirs", wModelNames, 0);

    wStatus =
        InterlisDialogUiSupport.createStatusArea(
            shell, wModelDirectories, props.getMiddlePct(), margin);
    Composite classRow = InterlisDialogUiSupport.createRow(shell, wStatus.control(), margin);
    Button wReload = new Button(classRow, SWT.PUSH);
    wClassName = new ComboVar(variables, classRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        classRow, "Main class", wClassName, wReload, "Reload", props.getMiddlePct(), margin);

    wRoleName = addComboRow("Role", classRow, margin);
    wMainReferenceField = addTextRow("Main ref field", wRoleName, margin);
    wLookupTidField = addTextRow("Lookup TID field", wMainReferenceField, 0);
    wPrefix = addTextRow("Output prefix", wLookupTidField, 0);
    wLookupFields = addTextRow("Fields to add", wPrefix, 0);
    wMaxLookupRows = addTextRow("Max lookup rows", wLookupFields, 0);

    wFailOnMissingMandatoryReference = new Button(shell, SWT.CHECK);
    wFailOnMissingMandatoryReference.setText("Fail on missing mandatory reference");
    PropsUi.setLook(wFailOnMissingMandatoryReference);
    FormData fdMissing = new FormData();
    fdMissing.left = new FormAttachment(props.getMiddlePct(), 0);
    fdMissing.top = new FormAttachment(wMaxLookupRows, margin);
    wFailOnMissingMandatoryReference.setLayoutData(fdMissing);

    wFailOnDuplicateTid = new Button(shell, SWT.CHECK);
    wFailOnDuplicateTid.setText("Fail on duplicate lookup TID");
    PropsUi.setLook(wFailOnDuplicateTid);
    FormData fdDuplicate = new FormData();
    fdDuplicate.left = new FormAttachment(props.getMiddlePct(), 0);
    fdDuplicate.top = new FormAttachment(wFailOnMissingMandatoryReference, margin);
    wFailOnDuplicateTid.setLayoutData(fdDuplicate);

    // Mapping summary, diagnostics and table
    wSummary = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wSummary);
    FormData fdSummary = new FormData();
    fdSummary.left = new FormAttachment(0, 0);
    fdSummary.right = new FormAttachment(100, 0);
    fdSummary.top = new FormAttachment(wFailOnDuplicateTid, margin);
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
          refresh();
        });
    wClassName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refresh();
          }
        });
    wRoleName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refresh();
          }
        });
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    wModelNames.addModifyListener(
        e -> {
          if (!suppressRefresh) refresh();
        });
    wModelDirectories.addModifyListener(
        e -> {
          if (!suppressRefresh) refresh();
        });
    getData();
    populateStreamCombos();
    refresh();
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
      wMainInputTransform.setText(
          input.getMainInputTransform() == null ? "" : input.getMainInputTransform());
      wLookupInputTransform.setText(
          input.getLookupInputTransform() == null ? "" : input.getLookupInputTransform());
      wModelNames.setText(input.getModelNames() == null ? "" : input.getModelNames());
      wModelDirectories.setText(
          input.getModelDirectories() == null ? "" : input.getModelDirectories());
      wClassName.setText(input.getMainClassName() == null ? "" : input.getMainClassName());
      wRoleName.setText(input.getRoleName() == null ? "" : input.getRoleName());
      wMainReferenceField.setText(
          input.getMainReferenceField() == null ? "" : input.getMainReferenceField());
      wLookupTidField.setText(input.getLookupTidField() == null ? "" : input.getLookupTidField());
      wPrefix.setText(input.getPrefix() == null ? "" : input.getPrefix());
      wLookupFields.setText(
          input.getLookupFields() == null ? "" : String.join(",", input.getLookupFields()));
      wMaxLookupRows.setText(Long.toString(input.getMaxLookupRows()));
      wFailOnMissingMandatoryReference.setSelection(input.isFailOnMissingMandatoryReference());
      wFailOnDuplicateTid.setSelection(input.isFailOnDuplicateTid());
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
        wMainInputTransform.add(transform.getName());
        wLookupInputTransform.add(transform.getName());
      }
    } finally {
      suppressRefresh = false;
    }
  }

  private void refresh() {
    if (suppressRefresh) return;
    syncMetaFromWidgets();
    var snapshot = (InterlisRoleJoinMeta) input.clone();
    var vars = ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator.snapshot(variables);
    boolean immediate = forceReload;
    forceReload = false;
    probeCoordinator.submit(
        immediate, () -> controller.probe(snapshot, vars), this::applyRefresh, this::probeFailed);
  }

  private void applyRefresh(InterlisRoleJoinProbeResult result) {
    try {

      classes = result.schema().selectableClasses();
      populateClassCombo();
      populateRoleCombo(result);
      wSummary.setText(
          "The lookup stream is loaded into memory once (max "
              + input.getMaxLookupRows()
              + " rows). Fields from the selected role target are added with the configured"
              + " prefix.");
      InterlisSchemaPreview preview = controller.createSchemaPreview(input, result);
      wStatus.set(
          InterlisDialogUiSupport.statusSeverity(
              InterlisDialogUiSupport.StatusSeverity.SUCCESS, preview),
          "Model loaded; role " + result.role().name() + " -> " + result.target().scopedName());
      InterlisDialogUiSupport.populatePreviewTable(wPreview, preview.rows());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, preview);
    } catch (Exception e) {
      wStatus.set(
          InterlisDialogUiSupport.StatusSeverity.ERROR,
          InterlisStructureDialogSupport.rootCauseMessage(e));
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

  private void populateRoleCombo(InterlisRoleJoinProbeResult result) {
    suppressRefresh = true;
    try {
      String current = wRoleName.getText();
      wRoleName.removeAll();
      for (String role : controller.roleNames(result)) {
        wRoleName.add(role);
      }
      if (controller.roleNames(result).contains(current)) {
        wRoleName.setText(current);
      } else if (!result.mainClass().roles().isEmpty()
          && result.mainClass().roles().stream().noneMatch(r -> r.name().equals(current))) {
        // keep as-is: the user may still be typing
      }
    } finally {
      suppressRefresh = false;
    }
  }

  private void syncMetaFromWidgets() {
    if (wModelNames != null) {
      input.setMainInputTransform(wMainInputTransform.getText());
      input.setLookupInputTransform(wLookupInputTransform.getText());
      input.setModelNames(wModelNames.getText());
      input.setModelDirectories(wModelDirectories.getText());
      input.setMainClassName(wClassName.getText());
      // The role combo shows "name -> target {card}"; extract the bare role name.
      String role = wRoleName.getText();
      if (role != null && role.contains(" -> ")) {
        role = role.substring(0, role.indexOf(" -> ")).trim();
      }
      input.setRoleName(role);
      input.setMainReferenceField(wMainReferenceField.getText());
      input.setLookupTidField(wLookupTidField.getText());
      input.setPrefix(wPrefix.getText());
      input.setLookupFields(
          InterlisRoleJoinDialogController.parseCommaSeparated(wLookupFields.getText()));
      input.setMaxLookupRows(
          parseLong(wMaxLookupRows.getText(), InterlisRoleJoinMeta.DEFAULT_MAX_LOOKUP_ROWS));
      input.setFailOnMissingMandatoryReference(wFailOnMissingMandatoryReference.getSelection());
      input.setFailOnDuplicateTid(wFailOnDuplicateTid.getSelection());
    }
  }

  private static long parseLong(String value, long fallback) {
    try {
      return Long.parseLong(value.trim());
    } catch (Exception e) {
      return fallback;
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
