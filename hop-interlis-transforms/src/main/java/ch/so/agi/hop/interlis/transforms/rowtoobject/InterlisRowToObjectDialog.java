package ch.so.agi.hop.interlis.transforms.rowtoobject;

import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
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
import org.eclipse.swt.widgets.Text;

/** INTERLIS Row to Object dialog: model-aware class selection for the inverse mapping. */
public class InterlisRowToObjectDialog extends BaseTransformDialog {

  private final InterlisRowToObjectMeta input;

  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wClassName;
  private TextVar wBasketIdField;
  private Label wStatus;

  private List<InterlisClassDescriptor> classes = List.of();
  private boolean suppressRefresh;

  public InterlisRowToObjectDialog(
      Shell parent, IVariables variables, InterlisRowToObjectMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("INTERLIS Row to Object");
    shell.setMinimumSize(760, 340);

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
    Composite classRow = InterlisDialogUiSupport.createRow(shell, wModelDirectories, margin);
    Button wReload = new Button(classRow, SWT.PUSH);
    wClassName = new ComboVar(variables, classRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        classRow, "Class", wClassName, wReload, "Reload", props.getMiddlePct(), margin);

    wBasketIdField = addTextRow("Basket ID field", classRow, margin);

    wStatus = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wStatus);
    FormData fdStatus = new FormData();
    fdStatus.left = new FormAttachment(0, 0);
    fdStatus.right = new FormAttachment(100, 0);
    fdStatus.top = new FormAttachment(wBasketIdField, margin);
    wStatus.setLayoutData(fdStatus);

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
      wBasketIdField.setText(input.getBasketIdField() == null ? "" : input.getBasketIdField());
    } finally {
      suppressRefresh = false;
    }
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void refresh() {
    syncMetaFromWidgets();
    try {
      InterlisProjectionResult result = controllerProbe();
      classes = result == null ? List.of() : result.schema().selectableClasses();
      populateClassCombo();
      wStatus.setText(
          result == null
              ? "Model probe unavailable: configuration incomplete or models unresolved."
              : "Model loaded; envelope rows will carry class " + input.getClassName());
    } catch (Exception e) {
      wStatus.setText(
          ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport.rootCauseMessage(e));
    }
  }

  private InterlisProjectionResult controllerProbe() throws Exception {
    return input.tryProject(variables).orElse(null);
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
      input.setBasketIdField(wBasketIdField.getText());
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
