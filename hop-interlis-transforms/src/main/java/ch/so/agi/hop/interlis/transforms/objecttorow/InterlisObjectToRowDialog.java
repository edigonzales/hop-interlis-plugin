package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.transforms.InterlisDialogMetadata;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport;
import ch.so.agi.hop.interlis.transforms.InterlisDialogUiSupport.StatusSeverity;
import ch.so.agi.hop.interlis.transforms.InterlisOptionsDialog;
import ch.so.agi.hop.interlis.transforms.InterlisOptionsDialog.Option;
import ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator;
import ch.so.agi.hop.interlis.transforms.InterlisProbeResult;
import ch.so.agi.hop.interlis.transforms.InterlisProbeStatus;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreviewSupport;
import ch.so.agi.hop.interlis.transforms.InterlisStructureDialogSupport;
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

/** INTERLIS Object to Row dialog: model-aware class selection with live schema preview. */
public class InterlisObjectToRowDialog extends BaseTransformDialog {

  private final InterlisObjectToRowMeta input;
  private InterlisObjectToRowMeta draft;

  private TextVar wModelNames;
  private TextVar wModelDirectories;
  private ComboVar wClassName;
  private TextVar wObjectField;
  private Button wIncludeTid;
  private Button wIncludeBid;
  private Button wAppendEnvelopeFields;
  private TextVar wDefaultSrid;
  private InterlisDialogUiSupport.StatusArea wStatus;
  private Label wPreviewDiagnostics;
  private Table wPreview;

  private boolean suppressRefresh;
  private InterlisProbeCoordinator probeCoordinator;

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
    shell.setText("INTERLIS Object to Row");

    draft = (InterlisObjectToRowMeta) input.clone();
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
    Button advanced = new Button(shell, SWT.PUSH);
    advanced.setText("Options…");
    PropsUi.setLook(advanced);
    FormData fdAdvanced = new FormData();
    fdAdvanced.right = new FormAttachment(100, 0);
    fdAdvanced.top = new FormAttachment(0, margin);
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

    wModelNames = addTextRow("Models", wTransformName, 0);
    wModelDirectories = addTextRow("Model dirs", wModelNames, 0);
    wStatus =
        InterlisDialogUiSupport.createStatusArea(
            shell, wModelDirectories, props.getMiddlePct(), margin);
    Composite classRow = InterlisDialogUiSupport.createRow(shell, wStatus.control(), margin);
    Button wReload = new Button(classRow, SWT.PUSH);
    wClassName = new ComboVar(variables, classRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    InterlisDialogUiSupport.buildRowControlWithButton(
        classRow, "Class", wClassName, wReload, "Reload", props.getMiddlePct(), margin);

    wObjectField = addTextRow("Object field", classRow, margin);
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

    // Schema preview diagnostics and table
    wPreviewDiagnostics = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wPreviewDiagnostics);
    FormData fdPreviewDiagnostics = new FormData();
    fdPreviewDiagnostics.left = new FormAttachment(0, 0);
    fdPreviewDiagnostics.right = new FormAttachment(100, 0);
    fdPreviewDiagnostics.top = new FormAttachment(wAppendEnvelopeFields, margin);
    wPreviewDiagnostics.setLayoutData(fdPreviewDiagnostics);

    wPreview = InterlisDialogUiSupport.createPreviewTable(shell);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, 0);
    fdPreview.right = new FormAttachment(100, 0);
    fdPreview.top = new FormAttachment(wPreviewDiagnostics, margin);
    fdPreview.height = 160;
    wPreview.setLayoutData(fdPreview);

    // OK / Cancel
    wOk = new Button(shell, SWT.PUSH);
    wOk.setText("OK");
    PropsUi.setLook(wOk);
    FormData fdOk = new FormData();
    fdOk.left = new FormAttachment(50, -2 * margin);
    fdOk.bottom = new FormAttachment(100, 0);
    wOk.setLayoutData(fdOk);
    fdPreview.bottom = new FormAttachment(wOk, -2 * margin);

    wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText("Cancel");
    PropsUi.setLook(wCancel);
    FormData fdCancel = new FormData();
    fdCancel.left = new FormAttachment(wOk, margin);
    fdCancel.bottom = new FormAttachment(100, 0);
    wCancel.setLayoutData(fdCancel);

    wReload.addListener(SWT.Selection, e -> refresh(true));
    wClassName.addModifyListener(e -> refresh(false));
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    wModelNames.addModifyListener(e -> refresh(false));
    wModelDirectories.addModifyListener(e -> refresh(false));
    wDefaultSrid.addModifyListener(e -> refresh(false));
    wIncludeTid.addListener(SWT.Selection, e -> refresh(false));
    wIncludeBid.addListener(SWT.Selection, e -> refresh(false));
    getData();
    refresh(false);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel(), false);
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

  private void refresh(boolean immediate) {
    if (suppressRefresh) return;
    readWidgets();
    var snapshot = (InterlisObjectToRowMeta) draft.clone();
    var vars = InterlisProbeCoordinator.snapshot(variables);
    probeCoordinator.submit(
        immediate,
        () -> probe(snapshot, vars),
        this::applyRefresh,
        this::probeFailed);
  }

  private static InterlisProbeResult probe(InterlisObjectToRowMeta snapshot, IVariables vars)
      throws InterlisModelException {
    var request = snapshot.modelRequest(vars);
    if (request.isEmpty()) {
      return new InterlisProbeResult(
          false, "Configure the INTERLIS models and model directories.", null,
          List.of(), List.of(), InterlisProbeStatus.INFO);
    }
    var service = new InterlisProjectionService();
    var model = service.loadModel(request.get());
    var classes = model.schema().selectableClasses();
    String selectedClass = snapshot.resolvedClassName(vars);
    if (selectedClass.isBlank() || selectedClass.contains("${")) {
      return new InterlisProbeResult(
          true, "Model loaded. Select an INTERLIS class for the schema preview.", null,
          classes, List.of(), InterlisProbeStatus.INFO);
    }
    try {
      var projection = service.project(model, selectedClass, snapshot.projectionOptions(vars));
      return new InterlisProbeResult(
          true, "Model loaded; " + projection.plan().fieldCount() + " fields projected",
          projection, classes, List.of(), InterlisProbeStatus.SUCCESS);
    } catch (InterlisModelException | InterlisMappingException e) {
      return new InterlisProbeResult(
          true, InterlisStructureDialogSupport.rootCauseMessage(e), null,
          classes, List.of(), InterlisProbeStatus.ERROR);
    }
  }

  private void applyRefresh(InterlisProbeResult result) {
    populateClassCombo(result.classes());
    renderPreview(
        StatusSeverity.valueOf(result.status().name()), result.message(), result.projection());
  }

  private void renderPreview(
      StatusSeverity severity, String message, InterlisProjectionResult projection) {
    if (projection == null) {
      wStatus.set(severity, message);
      InterlisDialogUiSupport.populatePreviewTable(wPreview, List.of());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, "");
    } else {
      var preview = InterlisSchemaPreviewSupport.create(projection.plan());
      wStatus.set(InterlisDialogUiSupport.statusSeverity(severity, preview), message);
      InterlisDialogUiSupport.populatePreviewTable(wPreview, preview.rows());
      InterlisDialogUiSupport.setPreviewDiagnostics(wPreviewDiagnostics, preview);
    }
    shell.layout(true, true);
  }

  private void populateClassCombo(List<InterlisClassDescriptor> classes) {
    suppressRefresh = true;
    try {
      String current = wClassName.getText();
      wClassName.setItems(
          classes.stream().filter(c -> !c.isAbstract()).map(InterlisClassDescriptor::scopedName)
              .toArray(String[]::new));
      wClassName.setText(current);
    } finally {
      suppressRefresh = false;
    }
  }

  private void readWidgets() {
    draft.setModelNames(wModelNames.getText());
    draft.setModelDirectories(wModelDirectories.getText());
    draft.setClassName(wClassName.getText());
    draft.setObjectFieldName(wObjectField.getText());
    draft.setIncludeTid(wIncludeTid.getSelection());
    draft.setIncludeBid(wIncludeBid.getSelection());
    draft.setAppendEnvelopeFields(wAppendEnvelopeFields.getSelection());
    draft.setDefaultSrid(wDefaultSrid.getText());
  }

  private void probeFailed(Exception error) {
    populateClassCombo(List.of());
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
