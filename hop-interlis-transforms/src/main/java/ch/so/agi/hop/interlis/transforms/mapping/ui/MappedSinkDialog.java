package ch.so.agi.hop.interlis.transforms.mapping.ui;

import ch.so.agi.hop.interlis.transforms.mapping.*;
import ch.so.agi.hop.interlis.transforms.output.*;
import ch.so.agi.hop.interlis.transforms.update.InterlisUpdateMeta;
import java.util.*;
import java.util.List;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Shared output/update configuration; all controls operate on a private metadata draft. */
public class MappedSinkDialog extends BaseTransformDialog {
  private final BaseTransformMeta<?, ?> original, draftMeta;
  private final MappedSinkSettings draft;
  private final Map<String, TextVar> entries = new LinkedHashMap<>();
  private Button overwrite, validate;
  private Combo basket;
  private Table inputs;
  private Label status;

  public MappedSinkDialog(
      Shell parent, IVariables vars, BaseTransformMeta<?, ?> meta, PipelineMeta pipeline) {
    super(parent, vars, meta, pipeline);
    original = meta;
    draftMeta = (BaseTransformMeta<?, ?>) meta.clone();
    draft = (MappedSinkSettings) draftMeta;
    if (draftMeta instanceof InterlisOutputMeta output)
      output.setMode(InterlisOutputMeta.Mode.MAPPED_INPUTS);
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, original);
    // Hop's help button is created with FormData; this dialog uses GridLayout.
    for (var control : shell.getChildren())
      control.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 3, 1));
    shell.setText(
        draft instanceof InterlisUpdateMeta ? "INTERLIS Update" : "INTERLIS Output — class inputs");
    var layout = new GridLayout(3, false);
    layout.marginWidth = 12;
    layout.marginHeight = 12;
    shell.setLayout(layout);
    new Label(shell, SWT.NONE).setText("Transform name");
    wTransformName = new Text(shell, SWT.BORDER);
    wTransformName.setText(transformName);
    wTransformName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    if (draft instanceof InterlisUpdateMeta update)
      entry("Original XTF", "original", update.getOriginalFile(), true);
    entry("Target XTF", "file", draft.getFileName(), true);
    entry("Models", "models", draft.getModelNames(), false);
    entry("Model directories", "dirs", draft.getModelDirectories(), false);
    overwrite = check("Overwrite separate target file", draft.isOverwrite());
    validate = check("Validate before publication", draft.isValidateBeforePublish());
    entry("Validation configuration", "validation", draft.getValidationConfigFile(), true);
    entry("Buffer memory (MiB)", "memory", Long.toString(draft.getBufferMemoryMiB()), false);
    entry("Spill directory (blank: system)", "spill", draft.getSpillDirectory(), false);
    entry(
        "Maximum spill (MiB; 0: unlimited)", "disk", Long.toString(draft.getMaxSpillMiB()), false);
    if (draft instanceof InterlisOutputMeta output) {
      new Label(shell, SWT.NONE).setText("Basket assignment");
      basket = new Combo(shell, SWT.READ_ONLY);
      basket.setItems("One basket per topic", "From input BID fields");
      basket.select(output.getBasketMode() == InterlisOutputMeta.BasketMode.PER_TOPIC ? 0 : 1);
      basket.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    }
    inputs = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
    inputs.setHeaderVisible(true);
    inputs.setLinesVisible(true);
    for (String column :
        List.of("Input transform", "INTERLIS class", "Structure path", "Mapped fields")) {
      var tc = new TableColumn(inputs, SWT.NONE);
      tc.setText(column);
      tc.setWidth(column.equals("INTERLIS class") ? 300 : 160);
    }
    var tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
    tableData.heightHint = 170;
    inputs.setLayoutData(tableData);
    refreshInputs();
    inputs.addListener(SWT.DefaultSelection, e -> edit(false));
    var actions = new Composite(shell, SWT.NONE);
    actions.setLayout(new RowLayout());
    actions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
    button(actions, "Add input…", () -> edit(true));
    button(actions, "Edit input…", () -> edit(false));
    button(
        actions,
        "Remove input",
        () -> {
          int i = inputs.getSelectionIndex();
          if (i >= 0) {
            draft.getInputs().remove(i);
            refreshInputs();
          }
        });
    if (draft instanceof InterlisOutputMeta)
      button(actions, "Legacy single-class mode…", this::legacy);
    status = new Label(shell, SWT.WRAP);
    status.setText(
        draft instanceof InterlisUpdateMeta
            ? "Original and target must differ. Missing update rows preserve the original content."
            : "Connect typed class streams directly. The writer groups baskets and publishes only"
                + " on success.");
    var statusData = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
    statusData.widthHint = 800;
    status.setLayoutData(statusData);
    var buttons = new Composite(shell, SWT.NONE);
    buttons.setLayout(new RowLayout());
    buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 3, 1));
    button(buttons, "OK", this::ok);
    button(buttons, "Cancel", this::cancel);
    shell.addListener(SWT.Close, e -> transformName = null);
    shell.setMinimumSize(880, 700);
    shell.setSize(1020, 860);
    shell.open();
    var display = shell.getDisplay();
    while (!shell.isDisposed()) if (!display.readAndDispatch()) display.sleep();
    return transformName;
  }

  private void entry(String label, String key, String value, boolean browse) {
    new Label(shell, SWT.NONE).setText(label);
    var text = new TextVar(variables, shell, SWT.BORDER);
    text.setText(value == null ? "" : value);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, browse ? 1 : 2, 1));
    entries.put(key, text);
    if (browse)
      button(
          shell,
          "Browse…",
          () -> {
            var dialog = new FileDialog(shell, key.equals("file") ? SWT.SAVE : SWT.OPEN);
            String file = dialog.open();
            if (file != null) text.setText(file);
          });
  }

  private Button check(String text, boolean selected) {
    var button = new Button(shell, SWT.CHECK);
    button.setText(text);
    button.setSelection(selected);
    button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
    return button;
  }

  private static void button(Composite parent, String label, Runnable action) {
    var button = new Button(parent, SWT.PUSH);
    button.setText(label);
    button.addListener(SWT.Selection, e -> action.run());
  }

  private void readSettings() {
    draft.setFileName(entries.get("file").getText());
    draft.setModelNames(entries.get("models").getText());
    draft.setModelDirectories(entries.get("dirs").getText());
    draft.setOverwrite(overwrite.getSelection());
    draft.setValidateBeforePublish(validate.getSelection());
    draft.setValidationConfigFile(entries.get("validation").getText());
    draft.setBufferMemoryMiB(Long.parseLong(entries.get("memory").getText().trim()));
    draft.setMaxSpillMiB(Long.parseLong(entries.get("disk").getText().trim()));
    draft.setSpillDirectory(entries.get("spill").getText());
    draft.spillOptions(variables);
    if (draft instanceof InterlisUpdateMeta update)
      update.setOriginalFile(entries.get("original").getText());
    if (draft instanceof InterlisOutputMeta output)
      output.setBasketMode(
          basket.getSelectionIndex() == 0
              ? InterlisOutputMeta.BasketMode.PER_TOPIC
              : InterlisOutputMeta.BasketMode.FROM_FIELD);
  }

  private void edit(boolean add) {
    try {
      readSettings();
      int index = inputs.getSelectionIndex();
      if (!add && index < 0) return;
      var config = add ? new InterlisMappedInput() : draft.getInputs().get(index);
      var edited =
          new MappedInputDialog(
                  shell,
                  variables,
                  pipelineMeta,
                  transformName,
                  draft,
                  draft instanceof InterlisUpdateMeta update ? update.getOriginalFile() : null,
                  config)
              .open();
      if (edited != null) {
        if (add) draft.getInputs().add(edited);
        else draft.getInputs().set(index, edited);
        refreshInputs();
      }
    } catch (Exception e) {
      error(e);
    }
  }

  private void refreshInputs() {
    inputs.removeAll();
    for (var input : draft.getInputs())
      new TableItem(inputs, SWT.NONE)
          .setText(
              new String[] {
                input.getTransformName(),
                input.getClassName(),
                input.getStructurePath(),
                Integer.toString(input.getFields().size())
              });
  }

  private void commit(BaseTransformMeta<?, ?> value, String name) throws Exception {
    original.loadXml(
        XmlHandler.loadXmlString("<transform>" + value.getXml() + "</transform>")
            .getDocumentElement(),
        metadataProvider);
    original.setChanged();
    transformName = name;
    shell.dispose();
  }

  private void ok() {
    try {
      readSettings();
      if (wTransformName.getText().isBlank())
        throw new IllegalArgumentException("Transform name is required");
      if (draft.getInputs().isEmpty())
        throw new IllegalArgumentException("Add at least one input mapping");
      draft.setInputs(draft.getInputs());
      commit(draftMeta, wTransformName.getText());
    } catch (Exception e) {
      error(e);
    }
  }

  private void legacy() {
    try {
      readSettings();
      var legacy = (InterlisOutputMeta) draftMeta.clone();
      legacy.setMode(InterlisOutputMeta.Mode.SINGLE_SCHEMA);
      String name = new InterlisOutputDialog(shell, variables, legacy, pipelineMeta).open();
      if (name != null) commit(legacy, name);
    } catch (Exception e) {
      error(e);
    }
  }

  private void error(Exception e) {
    status.setText(e.getMessage() == null ? e.toString() : e.getMessage());
    shell.layout();
  }

  private void cancel() {
    transformName = null;
    shell.dispose();
  }
}
