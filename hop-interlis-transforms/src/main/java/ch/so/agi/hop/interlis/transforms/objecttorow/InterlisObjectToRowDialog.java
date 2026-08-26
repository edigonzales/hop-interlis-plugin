package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** INTERLIS Object to Row dialog: model-aware class selection with live schema preview. */
public class InterlisObjectToRowDialog extends BaseTransformDialog {

  private final InterlisObjectToRowMeta input;
  private final InterlisObjectToRowDialogController controller =
      new InterlisObjectToRowDialogController();

  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wClassName;
  private TextVar wObjectField;
  private Button wIncludeTid;
  private Button wIncludeBid;
  private Button wAppendEnvelopeFields;
  private TextVar wDefaultSrid;
  private Label wStatus;
  private Text wPreview;

  private List<InterlisClassDescriptor> classes = List.of();
  private boolean suppressRefresh;

  public InterlisObjectToRowDialog(
      Shell parent,
      IVariables variables,
      InterlisObjectToRowMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("INTERLIS Object to Row");
    shell.setMinimumSize(860, 560);

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

    wModelNames = addTextRow("Models", wTransformName, 0);
    wModelDirectories = addTextRow("Model dirs", wModelNames, 0);
    Button wReload = new Button(shell, SWT.PUSH);
    wReload.setText("Reload");
    PropsUi.setLook(wReload);
    FormData fdReload = new FormData();
    fdReload.right = new FormAttachment(100, 0);
    fdReload.top = new FormAttachment(wModelDirectories, margin);
    wReload.setLayoutData(fdReload);

    wClassName = addComboRow("Class", wModelDirectories, margin);
    wObjectField = addTextRow("Object field", wClassName, margin);
    wDefaultSrid = addTextRow("Default SRID", wObjectField, 0);

    wIncludeTid = new Button(shell, SWT.CHECK);
    wIncludeTid.setText("Include _ili_tid");
    PropsUi.setLook(wIncludeTid);
    FormData fdTid = new FormData();
    fdTid.left = new FormAttachment(props.getMiddlePct(), 0);
    fdTid.top = new FormAttachment(wDefaultSrid, margin);
    wIncludeTid.setLayoutData(fdTid);

    wIncludeBid = new Button(shell, SWT.CHECK);
    wIncludeBid.setText("Include _ili_bid");
    PropsUi.setLook(wIncludeBid);
    FormData fdBid = new FormData();
    fdBid.left = new FormAttachment(props.getMiddlePct(), 0);
    fdBid.top = new FormAttachment(wIncludeTid, margin);
    wIncludeBid.setLayoutData(fdBid);

    wAppendEnvelopeFields = new Button(shell, SWT.CHECK);
    wAppendEnvelopeFields.setText("Append typed fields to envelope fields");
    PropsUi.setLook(wAppendEnvelopeFields);
    FormData fdAppend = new FormData();
    fdAppend.left = new FormAttachment(props.getMiddlePct(), 0);
    fdAppend.top = new FormAttachment(wIncludeBid, margin);
    wAppendEnvelopeFields.setLayoutData(fdAppend);

    // Status + preview
    wStatus = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wStatus);
    FormData fdStatus = new FormData();
    fdStatus.left = new FormAttachment(0, 0);
    fdStatus.right = new FormAttachment(100, 0);
    fdStatus.top = new FormAttachment(wAppendEnvelopeFields, margin);
    wStatus.setLayoutData(fdStatus);

    wPreview = new Text(shell, SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    PropsUi.setLook(wPreview, PropsUi.WIDGET_STYLE_FIXED);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, 0);
    fdPreview.right = new FormAttachment(100, 0);
    fdPreview.top = new FormAttachment(wStatus, margin);
    fdPreview.bottom = new FormAttachment(wOk, -2 * margin);
    wPreview.setLayoutData(fdPreview);

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

    wReload.addListener(SWT.Selection, e -> refresh());
    wClassName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refresh();
          }
        });
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    getData();
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
      wModelNames.setText(input.getModelNames() == null ? "" : input.getModelNames());
      wModelDirectories.setText(
          input.getModelDirectories() == null ? "" : input.getModelDirectories());
      wClassName.setText(input.getClassName() == null ? "" : input.getClassName());
      wObjectField.setText(input.getObjectFieldName() == null ? "" : input.getObjectFieldName());
      wIncludeTid.setSelection(input.isIncludeTid());
      wIncludeBid.setSelection(input.isIncludeBid());
      wAppendEnvelopeFields.setSelection(input.isAppendEnvelopeFields());
      wDefaultSrid.setText(input.getDefaultSrid() == null ? "" : input.getDefaultSrid());
    } finally {
      suppressRefresh = false;
    }
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void refresh() {
    syncMetaFromWidgets();
    try {
      InterlisProjectionResult result = controller.probe(input, variables);
      classes = controller.classes(result);
      populateClassCombo();
      if (result == null) {
        wStatus.setText("Schema preview unavailable: configuration incomplete or models unresolved.");
        wPreview.setText("");
      } else {
        wStatus.setText("Model loaded; " + result.plan().fieldCount() + " fields projected");
        wPreview.setText(controller.formatSchemaPreview(result));
      }
    } catch (Exception e) {
      wStatus.setText(InterlisObjectToRowDialogController.rootCauseMessage(e));
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

  private void syncMetaFromWidgets() {
    if (wModelNames != null) {
      input.setModelNames(wModelNames.getText());
      input.setModelDirectories(wModelDirectories.getText());
      input.setClassName(wClassName.getText());
      input.setObjectFieldName(wObjectField.getText());
      input.setIncludeTid(wIncludeTid.getSelection());
      input.setIncludeBid(wIncludeBid.getSelection());
      input.setAppendEnvelopeFields(wAppendEnvelopeFields.getSelection());
      input.setDefaultSrid(wDefaultSrid.getText());
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
