package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * INTERLIS Role Join dialog: selects the two input streams, the model-derived role and the fields
 * to join, with a live configuration preview.
 *
 * <p>All model interpretation happens in {@link InterlisRoleJoinDialogController}; probing
 * failures are shown in the preview area and never make the dialog unusable.
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
  private Label wStatus;
  private Text wPreview;

  private List<InterlisClassDescriptor> classes = List.of();
  private boolean suppressRefresh;

  public InterlisRoleJoinDialog(
      Shell parent, IVariables variables, InterlisRoleJoinMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
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

    Button wReload = new Button(shell, SWT.PUSH);
    wReload.setText("Reload");
    PropsUi.setLook(wReload);
    FormData fdReload = new FormData();
    fdReload.right = new FormAttachment(100, 0);
    fdReload.top = new FormAttachment(wModelDirectories, margin);
    wReload.setLayoutData(fdReload);

    wClassName = addComboRow("Main class", wModelDirectories, margin);
    wRoleName = addComboRow("Role", wClassName, margin);
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

    // Status + preview
    wStatus = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wStatus);
    FormData fdStatus = new FormData();
    fdStatus.left = new FormAttachment(0, 0);
    fdStatus.right = new FormAttachment(100, 0);
    fdStatus.top = new FormAttachment(wFailOnDuplicateTid, margin);
    wStatus.setLayoutData(fdStatus);

    wPreview = new Text(shell, SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    PropsUi.setLook(wPreview, PropsUi.WIDGET_STYLE_FIXED);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, 0);
    fdPreview.right = new FormAttachment(100, 0);
    fdPreview.top = new FormAttachment(wStatus, margin);
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
    wReload.addListener(SWT.Selection, e -> refresh());
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
      wLookupTidField.setText(
          input.getLookupTidField() == null ? "" : input.getLookupTidField());
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
    syncMetaFromWidgets();
    try {
      InterlisRoleJoinProbeResult result = controller.probe(input, variables);
      classes = result.schema().classes();
      populateClassCombo();
      populateRoleCombo(result);
      wStatus.setText(
          "Model loaded; role " + result.role().name() + " -> " + result.target().scopedName());
      wPreview.setText(controller.formatPreview(input, result));
    } catch (Exception e) {
      wStatus.setText(InterlisStructureDialogSupport.rootCauseMessage(e));
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
      input.setLookupFields(InterlisRoleJoinDialogController.parseCommaSeparated(wLookupFields.getText()));
      input.setMaxLookupRows(parseLong(wMaxLookupRows.getText(), InterlisRoleJoinMeta.DEFAULT_MAX_LOOKUP_ROWS));
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
