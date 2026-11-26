package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.transforms.InterlisDialogMetadata;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport.StatusSeverity;
import ch.so.agi.hop.interlis.transforms.InterlisOptionsDialog;
import ch.so.agi.hop.interlis.transforms.InterlisOptionsDialog.Option;
import ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator;
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
  private InterlisRoleJoinMeta draft;

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
  private Button wFailOnDuplicateKey;
  private InterlisDialogUiSupport.StatusArea wStatus;
  private Table wPreview;

  private boolean suppressRefresh;
  private InterlisProbeCoordinator probeCoordinator;

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
    draft = (InterlisRoleJoinMeta) input.clone();
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    var display = shell.getDisplay();
    probeCoordinator =
        new InterlisProbeCoordinator(
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
    // Hop computes its own minimum before opening the shell.
    shell.addListener(SWT.Show, e -> shell.setMinimumSize(860, 600));

    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();
    int groupGap = Math.max(2, margin / 3);

    // Transform name
    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText("Transform name");
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlTransformName.top = new FormAttachment(0, groupGap);
    wlTransformName.setLayoutData(fdlTransformName);

    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wTransformName.setText(transformName);
    PropsUi.setLook(wTransformName);
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(props.getMiddlePct(), 0);
    fdTransformName.right = new FormAttachment(100, 0);
    fdTransformName.top = new FormAttachment(0, groupGap);
    wTransformName.setLayoutData(fdTransformName);
    Button advanced = new Button(shell, SWT.PUSH);
    advanced.setText("Options…");
    PropsUi.setLook(advanced);
    FormData fdAdvanced = new FormData();
    fdAdvanced.right = new FormAttachment(100, 0);
    fdAdvanced.top = new FormAttachment(0, groupGap);
    advanced.setLayoutData(fdAdvanced);
    fdTransformName.right = new FormAttachment(advanced, -margin);
    advanced.addListener(
        SWT.Selection,
        e ->
            InterlisOptionsDialog.open(
                shell,
                variables,
                List.of(
                    new Option(
                        "Buffer memory (MiB)",
                        Long.toString(draft.getBufferMemoryMiB()),
                        v -> draft.setBufferMemoryMiB(Long.parseLong(v))),
                    new Option(
                        "Spill directory (empty: system temp)",
                        draft.getSpillDirectory(),
                        draft::setSpillDirectory),
                    new Option(
                        "Maximum spill (MiB, 0: unlimited)",
                        Long.toString(draft.getMaxSpillMiB()),
                        v -> draft.setMaxSpillMiB(Long.parseLong(v))))));

    // Streams
    wMainInputTransform = addComboRow("Main input", advanced);
    wLookupInputTransform = addComboRow("Lookup input", wMainInputTransform);
    wModelNames = addTextRow("Models", wLookupInputTransform);
    wModelDirectories = addTextRow("Model dirs", wModelNames);

    wStatus =
        InterlisDialogUiSupport.createStatusArea(
            shell, wModelDirectories, props.getMiddlePct(), groupGap);
    Composite classRow = InterlisDialogUiSupport.createRow(shell, wStatus.control(), groupGap);
    Button wReload = new Button(classRow, SWT.PUSH);
    wClassName = new ComboVar(variables, classRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        classRow, "Main class", wClassName, wReload, "Reload", props.getMiddlePct(), groupGap);

    wRoleName = addComboRow("Role", classRow);
    wMainReferenceField = addTextRow("Main ref field", wRoleName);
    wLookupTidField = addTextRow("Lookup TID field", wMainReferenceField);
    wPrefix = addTextRow("Output prefix", wLookupTidField);
    wLookupFields = addTextRow("Fields to add", wPrefix);
    wMaxLookupRows = addTextRow("Max lookup rows", wLookupFields);

    wFailOnMissingMandatoryReference = new Button(shell, SWT.CHECK);
    wFailOnMissingMandatoryReference.setText("Fail on missing mandatory reference");
    PropsUi.setLook(wFailOnMissingMandatoryReference);
    FormData fdMissing = new FormData();
    fdMissing.left = new FormAttachment(0, 0);
    fdMissing.top = new FormAttachment(wMaxLookupRows, groupGap);
    wFailOnMissingMandatoryReference.setLayoutData(fdMissing);

    wFailOnDuplicateKey = new Button(shell, SWT.CHECK);
    wFailOnDuplicateKey.setText("Fail on duplicate lookup key (BID + TID)");
    PropsUi.setLook(wFailOnDuplicateKey);
    FormData fdDuplicate = new FormData();
    fdDuplicate.left = new FormAttachment(wFailOnMissingMandatoryReference, margin);
    fdDuplicate.top = new FormAttachment(wFailOnMissingMandatoryReference, 0, SWT.TOP);
    wFailOnDuplicateKey.setLayoutData(fdDuplicate);

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

    wPreview = InterlisDialogUiSupport.createPreviewTable(shell);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, 0);
    fdPreview.right = new FormAttachment(100, 0);
    fdPreview.top = new FormAttachment(wFailOnMissingMandatoryReference, groupGap);
    fdPreview.bottom = new FormAttachment(wOk, -groupGap);
    fdPreview.height = 160;
    wPreview.setLayoutData(fdPreview);

    wReload.addListener(SWT.Selection, e -> refresh(true));
    wClassName.addModifyListener(e -> refresh(false));
    wRoleName.addModifyListener(e -> refresh(false));
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    wModelNames.addModifyListener(e -> refresh(false));
    wModelDirectories.addModifyListener(e -> refresh(false));
    getData();
    populateStreamCombos();
    refresh(false);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel(), false);
    return transformName;
  }

  private TextVar addTextRow(String label, org.eclipse.swt.widgets.Control topControl) {
    Label wl = new Label(shell, SWT.RIGHT);
    wl.setText(label);
    PropsUi.setLook(wl);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(props.getMiddlePct(), -PropsUi.getMargin());
    fdl.top = new FormAttachment(topControl);
    wl.setLayoutData(fdl);

    TextVar text = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(props.getMiddlePct(), 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(topControl);
    text.setLayoutData(fd);
    return text;
  }

  private ComboVar addComboRow(
      String label, org.eclipse.swt.widgets.Control topControl) {
    Label wl = new Label(shell, SWT.RIGHT);
    wl.setText(label);
    PropsUi.setLook(wl);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(props.getMiddlePct(), -PropsUi.getMargin());
    fdl.top = new FormAttachment(topControl);
    wl.setLayoutData(fdl);

    ComboVar combo = new ComboVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(combo);
    FormData fd = new FormData();
    fd.left = new FormAttachment(props.getMiddlePct(), 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(topControl);
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
      wFailOnDuplicateKey.setSelection(input.isFailOnDuplicateKey());
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

  private void refresh(boolean immediate) {
    if (suppressRefresh) return;
    readWidgets();
    var snapshot = (InterlisRoleJoinMeta) draft.clone();
    var vars = InterlisProbeCoordinator.snapshot(variables);
    if (immediate) {
      wStatus.set(StatusSeverity.INFO, "Loading INTERLIS models…");
    }
    probeCoordinator.submit(
        immediate, () -> controller.probe(snapshot, vars),
        result -> applyRefresh(snapshot, vars, result), this::probeFailed);
  }

  private void applyRefresh(
      InterlisRoleJoinMeta snapshot,
      IVariables vars,
      InterlisRoleJoinDialogController.DialogProbe result) {
    populateChoices(result.classes(), result.roles());
    try {
      var selected = result.selectedRole();
      InterlisSchemaPreview preview = selected == null
          ? null : controller.createSchemaPreview(snapshot, selected, vars);
      renderPreview(StatusSeverity.valueOf(result.status().name()), result.message(), preview);
    } catch (Exception | LinkageError e) {
      renderPreview(StatusSeverity.ERROR, InterlisStructureDialogSupport.rootCauseMessage(e), null);
    }
  }

  private void renderPreview(
      StatusSeverity severity, String message, InterlisSchemaPreview preview) {
    wStatus.set(InterlisDialogUiSupport.statusSeverity(severity, preview), message);
    InterlisDialogUiSupport.populatePreviewTable(wPreview, preview == null ? List.of() : preview.rows());
    shell.layout(true, true);
  }

  private void populateChoices(List<String> classes, List<String> roles) {
    suppressRefresh = true;
    try {
      String currentClass = wClassName.getText();
      wClassName.setItems(classes.toArray(String[]::new));
      wClassName.setText(currentClass);
      String currentRole = wRoleName.getText();
      wRoleName.setItems(roles.toArray(String[]::new));
      wRoleName.setText(controller.selectedRoleLabel(roles, currentRole));
    } finally {
      suppressRefresh = false;
    }
  }

  private void readWidgets() {
    draft.setMainInputTransform(wMainInputTransform.getText());
    draft.setLookupInputTransform(wLookupInputTransform.getText());
    draft.setModelNames(wModelNames.getText());
    draft.setModelDirectories(wModelDirectories.getText());
    draft.setMainClassName(wClassName.getText());
    draft.setRoleName(InterlisRoleJoinDialogController.roleName(wRoleName.getText()));
    draft.setMainReferenceField(wMainReferenceField.getText());
    draft.setLookupTidField(wLookupTidField.getText());
    draft.setPrefix(wPrefix.getText());
    draft.setLookupFields(
        InterlisRoleJoinDialogController.parseCommaSeparated(wLookupFields.getText()));
    draft.setMaxLookupRows(
        parseLong(wMaxLookupRows.getText(), InterlisRoleJoinMeta.DEFAULT_MAX_LOOKUP_ROWS));
    draft.setFailOnMissingMandatoryReference(wFailOnMissingMandatoryReference.getSelection());
    draft.setFailOnDuplicateKey(wFailOnDuplicateKey.getSelection());
  }

  private static long parseLong(String value, long fallback) {
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private void probeFailed(Exception error) {
    renderPreview(StatusSeverity.ERROR, InterlisStructureDialogSupport.rootCauseMessage(error), null);
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    readWidgets();
    try {
      if (!input.getXml().equals(draft.getXml())) {
        InterlisDialogMetadata.commit(input, draft, metadataProvider);
      }
    } catch (Exception e) {
      wStatus.set(StatusSeverity.ERROR, InterlisStructureDialogSupport.rootCauseMessage(e));
      shell.layout(true, true);
      return;
    }
    if (!transformName.equals(wTransformName.getText())) {
      input.setChanged();
      transformName = wTransformName.getText();
    }
    dispose();
  }

  private void cancel() {
    transformName = null;
    dispose();
  }
}
