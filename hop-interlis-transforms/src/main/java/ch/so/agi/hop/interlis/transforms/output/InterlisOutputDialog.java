package ch.so.agi.hop.interlis.transforms.output;

import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * INTERLIS Output dialog: target file, model source, class browser, identity/basket options and
 * a field mapping grid (INTERLIS property → Hop field → status).
 *
 * <p>All model interpretation happens in {@link InterlisOutputDialogController}; probing failures
 * are shown as messages and never make the dialog unusable.
 */
public class InterlisOutputDialog extends BaseTransformDialog {

  private final InterlisOutputMeta input;
  private final InterlisOutputDialogController controller = new InterlisOutputDialogController();

  private TextVar wFileName;
  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wClassName;
  private TextVar wObjectIdField;
  private TextVar wBasketIdField;
  private TextVar wBasketId;
  private TextVar wOperationField;
  private TextVar wSourceObjectField;
  private Button wOverwrite;
  private Label wStatus;
  private Table wMapping;

  private List<InterlisClassDescriptor> classes = List.of();
  private List<ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor> associations =
      List.of();
  private boolean suppressRefresh;

  public InterlisOutputDialog(
      Shell parent, IVariables variables, InterlisOutputMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("INTERLIS Output");
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

    // Output file
    Composite fileRow = InterlisDialogUiSupport.createRow(shell, wTransformName, margin);
    Button wbFile = new Button(fileRow, SWT.PUSH | SWT.CENTER);
    wFileName = new TextVar(variables, fileRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        fileRow, "XTF file", wFileName, wbFile, "Browse", props.getMiddlePct(), margin);

    wOverwrite = new Button(shell, SWT.CHECK);
    wOverwrite.setText("Overwrite existing file");
    PropsUi.setLook(wOverwrite);
    FormData fdOverwrite = new FormData();
    fdOverwrite.left = new FormAttachment(props.getMiddlePct(), 0);
    fdOverwrite.top = new FormAttachment(fileRow, margin);
    wOverwrite.setLayoutData(fdOverwrite);

    // Models
    Label wlModels = new Label(shell, SWT.RIGHT);
    wlModels.setText("Models");
    PropsUi.setLook(wlModels);
    wlModels.setLayoutData(labelData(wOverwrite, margin));

    wModelNames = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wModelNames);
    FormData fdModels = new FormData();
    fdModels.left = new FormAttachment(props.getMiddlePct(), 0);
    fdModels.right = new FormAttachment(100, 0);
    fdModels.top = new FormAttachment(wOverwrite, margin);
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

    // Identity and basket
    Label wlTid = new Label(shell, SWT.RIGHT);
    wlTid.setText("Object ID field");
    PropsUi.setLook(wlTid);
    wlTid.setLayoutData(labelData(classRow, margin));

    wObjectIdField = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wObjectIdField);
    FormData fdTid = new FormData();
    fdTid.left = new FormAttachment(props.getMiddlePct(), 0);
    fdTid.right = new FormAttachment(100, 0);
    fdTid.top = new FormAttachment(classRow, margin);
    wObjectIdField.setLayoutData(fdTid);

    Label wlBidField = new Label(shell, SWT.RIGHT);
    wlBidField.setText("BID field");
    PropsUi.setLook(wlBidField);
    wlBidField.setLayoutData(labelData(wObjectIdField, margin));

    wBasketIdField = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wBasketIdField);
    FormData fdBidField = new FormData();
    fdBidField.left = new FormAttachment(props.getMiddlePct(), 0);
    fdBidField.right = new FormAttachment(100, 0);
    fdBidField.top = new FormAttachment(wObjectIdField, margin);
    wBasketIdField.setLayoutData(fdBidField);

    Label wlBid = new Label(shell, SWT.RIGHT);
    wlBid.setText("Default BID");
    PropsUi.setLook(wlBid);
    wlBid.setLayoutData(labelData(wBasketIdField, margin));

    wBasketId = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wBasketId);
    FormData fdBid = new FormData();
    fdBid.left = new FormAttachment(props.getMiddlePct(), 0);
    fdBid.right = new FormAttachment(100, 0);
    fdBid.top = new FormAttachment(wBasketIdField, margin);
    wBasketId.setLayoutData(fdBid);

    Label wlSourceObject = new Label(shell, SWT.RIGHT);
    wlSourceObject.setText("Source object field");
    PropsUi.setLook(wlSourceObject);
    wlSourceObject.setToolTipText(
        "Optional technical field (_ili_source_object) kept by INTERLIS Input and updated by "
            + "INTERLIS Structure Collect; when set, the row values are overlaid onto that object "
            + "so multi-valued structures are preserved");
    wlSourceObject.setLayoutData(labelData(wBasketId, margin));

    Label wlOperation = new Label(shell, SWT.RIGHT);
    wlOperation.setText("Operation field");
    PropsUi.setLook(wlOperation);
    wlOperation.setToolTipText(
        "Optional field carrying the transfer operation (INSERT/UPDATE/DELETE); when set, the "
            + "operation is applied to the written object");
    wlOperation.setLayoutData(labelData(wBasketId, margin));

    wOperationField = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wOperationField);
    FormData fdOperation = new FormData();
    fdOperation.left = new FormAttachment(props.getMiddlePct(), 0);
    fdOperation.right = new FormAttachment(100, 0);
    fdOperation.top = new FormAttachment(wBasketId, margin);
    wOperationField.setLayoutData(fdOperation);

    wSourceObjectField = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceObjectField);
    FormData fdSourceObject = new FormData();
    fdSourceObject.left = new FormAttachment(props.getMiddlePct(), 0);
    fdSourceObject.right = new FormAttachment(100, 0);
    fdSourceObject.top = new FormAttachment(wOperationField, margin);
    wSourceObjectField.setLayoutData(fdSourceObject);

    // Status
    wStatus = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wStatus);
    FormData fdStatus = new FormData();
    fdStatus.left = new FormAttachment(0, 0);
    fdStatus.right = new FormAttachment(100, 0);
    fdStatus.top = new FormAttachment(wSourceObjectField, margin);
    wStatus.setLayoutData(fdStatus);

    // Mapping grid
    wMapping = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
    wMapping.setHeaderVisible(true);
    wMapping.setLinesVisible(true);
    PropsUi.setLook(wMapping);
    String[] columns = {"INTERLIS property", "Hop field", "Type", "Status"};
    for (String column : columns) {
      TableColumn tableColumn = new TableColumn(wMapping, SWT.LEFT);
      tableColumn.setText(column);
      tableColumn.setWidth(180);
    }
    FormData fdMapping = new FormData();
    fdMapping.left = new FormAttachment(0, 0);
    fdMapping.right = new FormAttachment(100, 0);
    fdMapping.top = new FormAttachment(wStatus, margin);
    fdMapping.bottom = new FormAttachment(100, -margin * 8);
    wMapping.setLayoutData(fdMapping);

    // OK / Cancel
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText("OK");
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText("Cancel");
    setButtonPositions(new Button[] {wOk, wCancel}, margin, null);

    wTransformName.addModifyListener(e -> input.setChanged());
    wFileName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refresh();
          }
        });
    wModelNames.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refresh();
          }
        });
    wModelDirectories.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refresh();
          }
        });
    wClassName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressRefresh) {
            refresh();
          }
        });
    wObjectIdField.addModifyListener(e -> input.setChanged());
    wBasketIdField.addModifyListener(e -> input.setChanged());
    wBasketId.addModifyListener(e -> input.setChanged());
    wOverwrite.addListener(SWT.Selection, e -> input.setChanged());
    wbFile.addListener(SWT.Selection, e -> browse());
    wReload.addListener(SWT.Selection, e -> refresh());
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    getData();
    refresh();
    input.setChanged(changed);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private FormData labelData(org.eclipse.swt.widgets.Control topControl, int margin) {
    FormData data = new FormData();
    data.left = new FormAttachment(0, 0);
    data.right = new FormAttachment(props.getMiddlePct(), -margin);
    data.top = new FormAttachment(topControl, margin);
    return data;
  }

  private void browse() {
    FileDialog dialog = new FileDialog(shell, SWT.SAVE);
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
      wObjectIdField.setText(input.getObjectIdField() == null ? "" : input.getObjectIdField());
      wBasketIdField.setText(input.getBasketIdField() == null ? "" : input.getBasketIdField());
      wBasketId.setText(input.getBasketId() == null ? "" : input.getBasketId());
      wOperationField.setText(input.getOperationField() == null ? "" : input.getOperationField());
      wSourceObjectField.setText(
          input.getSourceObjectField() == null ? "" : input.getSourceObjectField());
      wOverwrite.setSelection(input.isOverwrite());
    } finally {
      suppressRefresh = false;
    }
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void refresh() {
    syncMetaFromWidgets();
    InterlisProbeResult result = controller.probe(input, variables);
    classes = result.classes();
    associations = result.associations();
    populateClassCombo();
    if (result.successful()) {
      wStatus.setText(result.message());
      populateMapping(controller.mapping(result.projection().plan()));
    } else {
      wStatus.setText(result.message());
      wMapping.removeAll();
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
      for (ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor association :
          associations) {
        wClassName.add(association.scopedName() + " (association)");
      }
      if (classes.stream().anyMatch(c -> !c.isAbstract() && c.scopedName().equals(current))) {
        wClassName.setText(current);
      }
    } finally {
      suppressRefresh = false;
    }
  }

  private void populateMapping(List<InterlisFieldMapping> mappings) {
    wMapping.removeAll();
    for (InterlisFieldMapping mapping : mappings) {
      TableItem item = new TableItem(wMapping, SWT.NONE);
      item.setText(0, mapping.property());
      item.setText(1, mapping.hopField());
      item.setText(2, mapping.type());
      item.setText(3, mapping.status());
    }
  }

  private void syncMetaFromWidgets() {
    if (wFileName != null) {
      input.setFileName(wFileName.getText());
      input.setModelNames(wModelNames.getText());
      input.setModelDirectories(wModelDirectories.getText());
      input.setClassName(wClassName.getText());
      input.setObjectIdField(wObjectIdField.getText());
      input.setBasketIdField(wBasketIdField.getText());
      input.setBasketId(wBasketId.getText());
      input.setOperationField(wOperationField.getText());
      input.setSourceObjectField(wSourceObjectField.getText());
      input.setOverwrite(wOverwrite.getSelection());
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
