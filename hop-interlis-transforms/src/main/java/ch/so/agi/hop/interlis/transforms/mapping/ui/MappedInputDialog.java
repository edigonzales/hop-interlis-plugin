package ch.so.agi.hop.interlis.transforms.mapping.ui;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.transforms.InterlisProbeCoordinator;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import java.util.*;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Small per-input field mapper; edits a private copy and commits only on OK. */
final class MappedInputDialog {
  private final Shell shell;
  private final IVariables variables;
  private final PipelineMeta pipeline;
  private final String models, directories, original;
  private final boolean update;
  private final InterlisMappedInput draft;
  private final Map<String, TextVar> entries = new LinkedHashMap<>();
  private final ComboVar upstream, className;
  private final TableView fields;
  private final ColumnInfo targetColumn, sourceColumn;
  private final Label status;
  private final InterlisProbeCoordinator probes;
  private List<InterlisFieldPlan> available = List.of();
  private IRowMeta sourceMeta;
  private boolean accepted, rendering;

  MappedInputDialog(
      Shell parent,
      IVariables variables,
      PipelineMeta pipeline,
      String transform,
      MappedSinkSettings settings,
      String original,
      InterlisMappedInput input) {
    this.variables = variables;
    this.pipeline = pipeline;
    this.models = settings.getModelNames();
    this.directories = settings.getModelDirectories();
    this.original = original;
    this.update = original != null;
    this.draft = new InterlisMappedInput(input);
    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    shell.setText("INTERLIS input mapping");
    shell.setLayout(new GridLayout(2, false));
    PropsUi.setLook(shell);
    upstream =
        combo(
            "Input transform", input.getTransformName(), pipeline.getPrevTransformNames(transform));
    className = combo("INTERLIS class", input.getClassName(), new String[0]);
    if (update) {
      entry("Structure path (blank for object)", "path", input.getStructurePath());
      entry("Structure update reference", "reference", input.getUpdateReferenceField());
    }
    entry("TID field (object mode)", "tid", input.getObjectIdField());
    entry("BID field (object mode)", "bid", input.getBasketIdField());
    if (!update) {
      entry("Operation field (optional)", "operation", input.getOperationField());
      entry("Source object field (optional)", "carrier", input.getSourceObjectField());
    }
    var hint = new Label(shell, SWT.WRAP);
    hint.setText(
        update
            ? "Only mapped attributes change. A null value clears an attribute; rows omitted from"
                + " this input remain unchanged."
            : "Map target attributes to typed input fields. Prepare calculations and conversions in"
                + " the preceding pipeline.");
    hint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    targetColumn =
        new ColumnInfo("INTERLIS target path", ColumnInfo.COLUMN_TYPE_CCOMBO, new String[0], false);
    sourceColumn =
        new ColumnInfo("Hop source field", ColumnInfo.COLUMN_TYPE_CCOMBO, new String[0], false);
    fields =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION,
            new ColumnInfo[] {targetColumn, sourceColumn},
            0,
            e -> {},
            PropsUi.getInstance());
    var fd = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
    fd.heightHint = 200;
    fields.setLayoutData(fd);
    fields.table.removeAll();
    for (var field : input.getFields()) fields.add(field.getTargetPath(), field.getSourceField());
    fields.setRowNums();
    fields.optWidth(true);
    status = new Label(shell, SWT.WRAP);
    status.setText("Load a model to see target fields.");
    var sd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    sd.widthHint = 700;
    status.setLayoutData(sd);
    var buttons = new Composite(shell, SWT.NONE);
    buttons.setLayout(new RowLayout());
    buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    button(buttons, "Reload model", () -> probe(true));
    button(buttons, "Map same-named fields", this::autoMap);
    button(
        buttons,
        "OK",
        () -> {
          readDraft();
          accepted = true;
          shell.close();
        });
    button(buttons, "Cancel", shell::close);
    probes =
        new InterlisProbeCoordinator(
            action -> {
              if (!shell.getDisplay().isDisposed())
                shell
                    .getDisplay()
                    .asyncExec(
                        () -> {
                          if (!shell.isDisposed()) action.run();
                        });
            });
    shell.addListener(SWT.Dispose, e -> probes.close());
    upstream.addModifyListener(
        e -> {
          if (!rendering) probe(false);
        });
    className.addModifyListener(
        e -> {
          if (!rendering) probe(false);
        });
    if (update)
      entries
          .get("path")
          .addModifyListener(
              e -> {
                if (!rendering) probe(false);
              });
  }

  private ComboVar combo(String label, String text, String[] items) {
    new Label(shell, SWT.NONE).setText(label);
    var control = new ComboVar(variables, shell, SWT.BORDER);
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    control.setItems(items);
    control.setText(text == null ? "" : text);
    return control;
  }

  private void entry(String label, String key, String text) {
    new Label(shell, SWT.NONE).setText(label);
    var control = new TextVar(variables, shell, SWT.BORDER);
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    control.setText(text);
    entries.put(key, control);
  }

  private static void button(Composite parent, String text, Runnable action) {
    var button = new Button(parent, SWT.PUSH);
    button.setText(text);
    button.addListener(SWT.Selection, e -> action.run());
  }

  private void readDraft() {
    draft.setTransformName(upstream.getText());
    draft.setClassName(className.getText());
    draft.setObjectIdField(entries.get("tid").getText());
    draft.setBasketIdField(entries.get("bid").getText());
    if (update) {
      draft.setStructurePath(entries.get("path").getText());
      draft.setUpdateReferenceField(entries.get("reference").getText());
    } else {
      draft.setOperationField(entries.get("operation").getText());
      draft.setSourceObjectField(entries.get("carrier").getText());
    }
    fields.unEdit();
    var mappings = new ArrayList<InterlisFieldAssignment>();
    for (var row : fields.getNonEmptyItems())
      mappings.add(new InterlisFieldAssignment(row.getText(2), row.getText(1)));
    draft.setFields(mappings);
  }

  private record Probe(MappedInputSchemaService.Result target, IRowMeta source) {}

  private void probe(boolean reload) {
    readDraft();
    available = List.of();
    sourceMeta = null;
    var config = new InterlisMappedInput(draft);
    var vars = InterlisProbeCoordinator.snapshot(variables);
    status.setText("Checking model and input schema…");
    probes.submit(
        reload,
        () -> {
          var target =
              new MappedInputSchemaService().inspect(models, directories, original, config, vars);
          var source =
              config.getTransformName().isBlank()
                  ? null
                  : pipeline.getTransformFields(vars, config.getTransformName());
          return new Probe(target, source);
        },
        result -> {
          rendering = true;
          String selected = className.getText();
          className.setItems(result.target().classes().toArray(String[]::new));
          className.setText(selected);
          rendering = false;
          available = result.target().fields();
          sourceMeta = result.source();
          targetColumn.setComboValues(
              available.stream().map(MappedClassPlan::target).toArray(String[]::new));
          sourceColumn.setComboValues(
              sourceMeta == null ? new String[0] : sourceMeta.getFieldNames());
          status.setText(
              "Model loaded: "
                  + available.size()
                  + " target fields. Only the rows in the mapping table are assigned.");
          shell.layout();
        },
        error -> {
          status.setText("Schema unavailable: " + error.getMessage());
          shell.layout();
        });
  }

  private void autoMap() {
    if (sourceMeta == null) {
      status.setText("Load the model and input schema first.");
      return;
    }
    readDraft();
    var existing = new HashSet<String>();
    draft.getFields().forEach(f -> existing.add(f.getTargetPath()));
    for (var target : available) {
      if (update
          && target.source()
              != ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource.PRIMITIVE_ATTRIBUTE
          && target.source()
              != ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource.GEOMETRY_ATTRIBUTE
          && target.source()
              != ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource
                  .FLATTENED_STRUCTURE_ATTRIBUTE) continue;
      String path = MappedClassPlan.target(target);
      int i = sourceMeta.indexOfValue(target.hopFieldName());
      if (i < 0) i = sourceMeta.indexOfValue(path);
      if (i >= 0 && existing.add(path)) fields.add(path, sourceMeta.getValueMeta(i).getName());
    }
    fields.setRowNums();
    fields.optWidth(true);
  }

  InterlisMappedInput open() {
    shell.setSize(880, 760);
    shell.open();
    probe(false);
    var display = shell.getDisplay();
    while (!shell.isDisposed()) if (!display.readAndDispatch()) display.sleep();
    return accepted ? draft : null;
  }
}
