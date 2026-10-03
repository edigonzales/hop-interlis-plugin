package ch.so.agi.hop.interlis.migration;

import guru.interlis.transformer.api.*;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.editor.IlimapEditorDocument;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Native, focused mapping editor. All semantic work is delegated to ilitransformer services. */
public final class IlimapEditor {
  private final Composite root;
  private final IlimapEditorDocument document;
  private final Runnable changed;
  private final IlimapSourceEditor sourceEditor;
  private final Tree rules, sourceTree, targetTree;
  private final Table assignments;
  private final ExpressionGraph graph;
  private final Text diagnostics, preview;
  private final Label state, contextLabel;
  private final TabFolder tabs;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "ilimap-editor");
            t.setDaemon(true);
            return t;
          });
  private Future<?> running;
  private long generation;
  private Path path;
  private boolean syncing;
  private MigrationDraftService.Schema sourceSchema, targetSchema;
  private Context context;
  private Node drag;
  private String loadedModels;

  private record Node(String entity, MigrationDraftService.Field field) {}

  private record Context(
      String rule,
      String path,
      String alias,
      String source,
      String target,
      IlimapAssignmentBlock assign) {}

  public IlimapEditor(Composite parent, Path path, String text, Runnable changed) {
    this.path = path;
    this.changed = changed;
    document = new IlimapEditorDocument(text);
    parent.setLayout(new FillLayout());
    root = new Composite(parent, SWT.NONE);
    root.setLayout(new GridLayout(1, false));
    Composite toolbar = new Composite(root, SWT.NONE);
    toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    toolbar.setLayout(new RowLayout());
    button(toolbar, "Prepare migration…", this::prepareDraft);
    button(toolbar, "Load models", this::loadModels);
    button(toolbar, "Check mapping", this::checkMapping);
    button(toolbar, "Confirm review", this::confirmReview);
    button(toolbar, "Preview complete XTF…", this::preview);
    button(toolbar, "Stop", this::stop);
    button(toolbar, "Undo", this::undo);
    button(toolbar, "Redo", this::redo);
    state = new Label(root, SWT.WRAP);
    state.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    state.setText("Load models to enable schema-aware editing");
    SashForm vertical = new SashForm(root, SWT.VERTICAL);
    vertical.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    SashForm upper = new SashForm(vertical, SWT.HORIZONTAL);
    rules = new Tree(upper, SWT.BORDER | SWT.SINGLE);
    rules.setToolTipText("Rules and structure contexts; technical rule IDs stay stable");
    Composite mapping = new Composite(upper, SWT.NONE);
    mapping.setLayout(new GridLayout(1, false));
    contextLabel = new Label(mapping, SWT.WRAP);
    contextLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    contextLabel.setText("Drag a source class onto a target class, or select a rule");
    SashForm schemas = new SashForm(mapping, SWT.HORIZONTAL);
    schemas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    sourceTree = tree(schemas, "Source");
    targetTree = tree(schemas, "Target");
    Composite actions = new Composite(mapping, SWT.NONE);
    actions.setLayout(new RowLayout());
    button(actions, "Edit expression…", this::editExpression);
    button(actions, "Insert function…", this::insertFunction);
    button(actions, "Enumeration table…", this::enumTable);
    button(actions, "Reference…", this::reference);
    assignments = new Table(mapping, SWT.BORDER | SWT.FULL_SELECTION);
    assignments.setHeaderVisible(true);
    assignments.setLinesVisible(true);
    for (String name : List.of("Target", "Expression / function", "Context")) {
      var column = new TableColumn(assignments, SWT.NONE);
      column.setText(name);
      column.setWidth(name.startsWith("Expression") ? 400 : 190);
    }
    GridData rowsData = new GridData(SWT.FILL, SWT.FILL, true, true);
    rowsData.heightHint = 120;
    assignments.setLayoutData(rowsData);
    assignments.addListener(SWT.DefaultSelection, e -> editExpression());
    graph = new ExpressionGraph(mapping, this::editExpression);
    GridData graphData = new GridData(SWT.FILL, SWT.FILL, true, false);
    graphData.heightHint = 150;
    graph.canvas.setLayoutData(graphData);
    assignments.addListener(
        SWT.Selection,
        e -> {
          if (assignments.getSelectionCount() > 0)
            graph.show(
                assignments.getSelection()[0].getText(0), assignments.getSelection()[0].getText(1));
        });
    graph.show("", "");
    upper.setWeights(22, 78);
    tabs = new TabFolder(vertical, SWT.NONE);
    Composite sourcePane = new Composite(tabs, SWT.NONE);
    sourcePane.setLayout(new FillLayout());
    sourceEditor = new IlimapSourceEditor(sourcePane);
    tab("DSL", sourcePane);
    diagnostics = new Text(tabs, SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
    diagnostics.setFont(GuiResource.getInstance().getFontFixed());
    tab("Diagnostics", diagnostics);
    preview = new Text(tabs, SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
    preview.setFont(GuiResource.getInstance().getFontFixed());
    tab("Data preview", preview);
    vertical.setWeights(65, 35);
    sourceEditor.text.setText(text);
    sourceEditor.highlight();
    rebuildRules();
    sourceEditor.text.addModifyListener(
        e -> {
          if (syncing) return;
          document.setText(sourceEditor.text.getText());
          changed.run();
          sourceEditor.highlight();
          generation++;
          invalidateChangedModels();
          state.setText("Text changed — mapping and data validation pending");
          rebuildRules();
        });
    sourceEditor.text.addListener(
        SWT.KeyDown,
        e -> {
          if ((e.stateMask & SWT.MOD1) != 0 && (e.keyCode == 'z' || e.keyCode == 'Z')) {
            e.doit = false;
            if ((e.stateMask & SWT.SHIFT) != 0) redo();
            else undo();
          }
        });
    rules.addListener(
        SWT.Selection,
        e -> {
          context = (Context) e.item.getData();
          refreshContext();
        });

    targetTree.addListener(SWT.DefaultSelection, e -> editExpression());
    targetTree.addListener(
        SWT.Selection,
        e -> {
          assignments.deselectAll();
          graph.show("", "");
        });
    installDragAndDrop();
    sourceEditor.text.setFocus();
    root.addDisposeListener(
        e -> {
          generation++;
          if (running != null) running.cancel(true);
          executor.shutdownNow();
        });
  }

  private void stop() {
    generation++;
    if (running != null) running.cancel(true);
    state.setText("Cancelled — no preview result accepted");
  }

  public String text() {
    return document.text();
  }

  public StyledText code() {
    return sourceEditor.text;
  }

  public void setPath(Path path) {
    this.path = path;
  }

  public void undo() {
    document.undo();
    sync();
  }

  public void redo() {
    document.redo();
    sync();
  }

  private String modelKey() {
    var doc = document.document();
    return doc.inputs().stream().map(i -> i.model()).toList()
        + "|"
        + doc.outputs().stream().map(o -> o.model()).toList()
        + "|"
        + (doc.job() == null ? "" : doc.job().modeldirs());
  }

  private void invalidateChangedModels() {
    try {
      if (!java.util.Objects.equals(loadedModels, modelKey())) {
        sourceSchema = null;
        targetSchema = null;
      }
    } catch (RuntimeException incomplete) {
      // Keep the model cache while the parser pauses graphical changes.
    }
  }

  private void sync() {
    invalidateChangedModels();
    syncing = true;
    int caret = code().getCaretOffset();
    code().setText(document.text());
    code().setCaretOffset(Math.min(caret, code().getCharCount()));
    syncing = false;
    generation++;
    changed.run();
    sourceEditor.highlight();
    rebuildRules();
    state.setText("Mapping changed — run Check mapping and preview");
  }

  private void mutate(Runnable edit) {
    try {
      document.atomicEdit(edit);
      sync();
    } catch (Exception ex) {
      MigrationUi.error(root.getShell(), ex);
    }
  }

  private void button(Composite parent, String label, Runnable command) {
    Button b = new Button(parent, SWT.PUSH);
    b.setText(label);
    b.addListener(
        SWT.Selection,
        e -> {
          try {
            command.run();
          } catch (Exception ex) {
            MigrationUi.error(root.getShell(), ex);
          }
        });
  }

  private Tree tree(Composite parent, String title) {
    Tree tree = new Tree(parent, SWT.BORDER | SWT.SINGLE);
    tree.setToolTipText(title);
    return tree;
  }

  private void tab(String label, Control control) {
    TabItem item = new TabItem(tabs, SWT.NONE);
    item.setText(label);
    item.setControl(control);
  }

  private void rebuildRules() {
    String selectedRule = context == null ? null : context.rule();
    String selectedPath = context == null ? "" : context.path();
    rules.removeAll();
    try {
      for (var rule : document.document().rules()) {
        var source =
            rule.elements().stream()
                .filter(IlimapSourceStmt.class::isInstance)
                .map(IlimapSourceStmt.class::cast)
                .findFirst()
                .orElse(null);
        var target =
            rule.elements().stream()
                .filter(IlimapTargetStmt.class::isInstance)
                .map(IlimapTargetStmt.class::cast)
                .findFirst()
                .orElse(null);
        if (source == null || target == null) continue;
        var assignment =
            rule.elements().stream()
                .filter(IlimapAssignmentBlock.class::isInstance)
                .map(IlimapAssignmentBlock.class::cast)
                .findFirst()
                .orElse(null);
        var ctx =
            new Context(
                rule.id(),
                "",
                source.alias(),
                source.sourceClass(),
                target.targetClass(),
                assignment);
        TreeItem item = new TreeItem(rules, SWT.NONE);
        item.setText(shortName(source.sourceClass()) + " → " + shortName(target.targetClass()));
        item.setData(ctx);
        for (var element : rule.elements())
          if (element instanceof IlimapBagBlock bag) addBag(item, ctx, bag);
        item.setExpanded(true);
        selectContext(item, selectedRule, selectedPath);
      }
      if (rules.getSelectionCount() == 0 && rules.getItemCount() > 0)
        rules.setSelection(rules.getItem(0));
      context = rules.getSelectionCount() == 0 ? null : (Context) rules.getSelection()[0].getData();
      refreshContext();
    } catch (RuntimeException ex) {
      context = null;
      assignments.removeAll();
      graph.show("", "");
      contextLabel.setText("DSL has an incomplete edit — graphical changes paused");
      diagnostics.setText(ex.getMessage());
    }
  }

  private void selectContext(TreeItem item, String rule, String path) {
    Context c = (Context) item.getData();
    if (c.rule().equals(rule) && c.path().equals(path)) rules.setSelection(item);
    for (TreeItem child : item.getItems()) selectContext(child, rule, path);
  }

  private void addBag(TreeItem parent, Context enclosing, IlimapBagBlock bag) {
    if (bag.from() == null || bag.from().attributePath() == null) return;
    String source = structureType(sourceSchema, enclosing.source(), bag.from().attributePath());
    String target =
        structureType(
            targetSchema,
            enclosing.target(),
            bag.targetAttribute() == null ? bag.id() : bag.targetAttribute());
    var ctx =
        new Context(
            enclosing.rule(),
            enclosing.path().isEmpty() ? bag.id() : enclosing.path() + "/" + bag.id(),
            bag.from().alias(),
            source,
            target,
            bag.assign());
    TreeItem item = new TreeItem(parent, SWT.NONE);
    item.setText(bag.id() + " (structure)");
    item.setData(ctx);
    for (var nested : bag.nestedBags()) addBag(item, ctx, nested);
  }

  private String structureType(MigrationDraftService.Schema schema, String type, String path) {
    if (schema == null || type == null) return null;
    try {
      for (String name : path.split("\\.")) {
        var entity = schema.entity(type);
        type =
            entity.fields().stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow()
                .structureType();
      }
      return type;
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private void refreshContext() {
    String selected =
        assignments.getSelectionCount() == 0 ? null : assignments.getSelection()[0].getText(0);
    assignments.removeAll();
    graph.show("", "");
    if (context != null) {
      contextLabel.setText(
          context.source()
              + " → "
              + context.target()
              + (context.path().isEmpty() ? "" : " / " + context.path()));
      if (context.assign() != null)
        for (var a : context.assign().assignments()) {
          TableItem item = new TableItem(assignments, SWT.NONE);
          item.setText(
              new String[] {
                a.targetAttribute(),
                a.expression().text(),
                context.path().isEmpty() ? "Object" : context.path()
              });
          item.setData(a);
          if (a.targetAttribute().equals(selected)) {
            assignments.setSelection(item);
            graph.show(a.targetAttribute(), a.expression().text());
          }
        }
    }
    populate(sourceTree, sourceSchema, context == null ? null : context.source());
    populate(targetTree, targetSchema, context == null ? null : context.target());
  }

  private void populate(Tree tree, MigrationDraftService.Schema schema, String selected) {
    tree.removeAll();
    if (schema == null) return;
    for (var entity : schema.entities()) {
      if (entity.structure() && !entity.name().equals(selected)) continue;
      TreeItem item = new TreeItem(tree, SWT.NONE);
      item.setText(entity.name());
      item.setData(new Node(entity.name(), null));
      for (var field : entity.fields()) {
        TreeItem child = new TreeItem(item, SWT.NONE);
        child.setText(field.name() + " : " + field.type() + (field.required() ? " *" : ""));
        child.setData(new Node(entity.name(), field));
      }
      if (entity.name().equals(selected)) {
        item.setExpanded(true);
        tree.showItem(item);
      }
    }
  }

  private static String shortName(String value) {
    return value == null ? "?" : value.substring(value.lastIndexOf('.') + 1);
  }

  private void installDragAndDrop() {
    DragSource source = new DragSource(sourceTree, DND.DROP_COPY);
    source.setTransfer(TextTransfer.getInstance());
    source.addDragListener(
        new DragSourceAdapter() {
          @Override
          public void dragStart(DragSourceEvent e) {
            drag =
                sourceTree.getSelectionCount() == 0
                    ? null
                    : (Node) sourceTree.getSelection()[0].getData();
            e.doit = drag != null;
          }

          @Override
          public void dragSetData(DragSourceEvent e) {
            e.data =
                "ilimap:" + drag.entity() + (drag.field() == null ? "" : "." + drag.field().name());
          }

          @Override
          public void dragFinished(DragSourceEvent e) {
            drag = null;
          }
        });
    DropTarget target = new DropTarget(targetTree, DND.DROP_COPY);
    target.setTransfer(TextTransfer.getInstance());
    target.addDropListener(
        new DropTargetAdapter() {
          @Override
          public void dragOver(DropTargetEvent e) {
            e.detail = drag == null ? DND.DROP_NONE : DND.DROP_COPY;
            e.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL;
          }

          @Override
          public void drop(DropTargetEvent e) {
            TreeItem item = targetTree.getItem(targetTree.toControl(e.x, e.y));
            if (drag == null || item == null) return;
            Node destination = (Node) item.getData();
            Node origin = drag;
            mutate(() -> connect(origin, destination));
          }
        });
  }

  private void connect(Node from, Node to) {
    if (from.field() == null && to.field() == null) {
      var doc = document.document();
      if (doc.inputs().size() != 1 || doc.outputs().size() != 1)
        throw new IllegalArgumentException("Graphical migration requires one input and one output");
      String id = MigrationDraftService.ruleId(from.entity());
      var existing =
          doc.rules().stream()
              .filter(
                  r ->
                      r.elements().stream()
                          .anyMatch(
                              e ->
                                  e instanceof IlimapSourceStmt source
                                      && source.sourceClass().equals(from.entity())))
              .toList();
      if (!existing.isEmpty()) {
        if (existing.size() != 1)
          throw new IllegalArgumentException(
              "Several rules use this source; edit the target in the DSL");
        if (MigrationUi.confirm(
            root.getShell(),
            "Change the target class of the existing rule? Its ID and mappings remain; check them"
                + " against the new target."))
          document.targetClass(existing.getFirst().id(), to.entity());
        return;
      }
      document.insertTopLevel(
          "  rule "
              + id
              + " {\n    target "
              + doc.outputs().getFirst().id()
              + " class "
              + q(to.entity())
              + ";\n    source s from "
              + doc.inputs().getFirst().id()
              + " class "
              + q(from.entity())
              + ";\n  }");
      return;
    }
    requireContext();
    if (from.field() == null
        || to.field() == null
        || !from.entity().equals(context.source())
        || !to.entity().equals(context.target()))
      throw new IllegalArgumentException("Select the matching class or structure rule first");
    if (to.field().referenceTarget() != null || from.field().referenceTarget() != null) {
      referenceFor(from.field(), to.field());
      return;
    }
    if (to.field().structureType() != null || from.field().structureType() != null) {
      if (!context.path().isEmpty())
        throw new IllegalArgumentException(
            "Create deeper structure mappings in the DSL; existing mappings remain visible");
      if (to.field().structureType() == null || from.field().structureType() == null)
        throw new IllegalArgumentException("A structure requires a structure source");
      if (document.rule(context.rule()).elements().stream()
          .anyMatch(
              e ->
                  e instanceof IlimapBagBlock bag
                      && to.field()
                          .name()
                          .equals(
                              bag.targetAttribute() == null ? bag.id() : bag.targetAttribute())))
        throw new IllegalArgumentException(
            "A mapping exists for this structure. Select its structure context to edit it.");
      document.insertRuleElement(
          context.rule(),
          "    bag "
              + to.field().name()
              + " {\n      from c in "
              + context.alias()
              + " attribute "
              + q(from.field().name())
              + ";\n    }");
      return;
    }
    String expression = context.alias() + "." + from.field().name();
    var compatibility =
        new MigrationDraftService()
            .compatibility(
                sourceSchema,
                targetSchema,
                from.entity(),
                to.entity(),
                from.field().name(),
                to.field().name());
    if (compatibility == MigrationDraftService.Status.DECISION) {
      var values =
          MigrationUi.form(
              root.getShell(),
              "Conversion required — " + from.field().type() + " → " + to.field().type(),
              MigrationUi.fields("Expression", expression));
      if (values.isEmpty()) return;
      expression = values.get("Expression");
    }
    document.assign(context.rule(), context.path(), to.field().name(), expression);
  }

  private void requireContext() {
    if (context == null)
      throw new IllegalArgumentException("Select a rule or structure context first");
    if (context.path().contains("/"))
      throw new IllegalArgumentException(
          "Deeper structure mappings are preserved. Edit them in the DSL.");
  }

  private String targetAttribute() {
    if (assignments.getSelectionCount() > 0) return assignments.getSelection()[0].getText(0);
    if (targetTree.getSelectionCount() > 0
        && ((Node) targetTree.getSelection()[0].getData()).field() != null)
      return ((Node) targetTree.getSelection()[0].getData()).field().name();
    throw new IllegalArgumentException("Select a target attribute or assignment");
  }

  private String expression(String target) {
    return context.assign() == null
        ? ""
        : context.assign().assignments().stream()
            .filter(a -> a.targetAttribute().equals(target))
            .map(a -> a.expression().text())
            .findFirst()
            .orElse("");
  }

  private void editExpression() {
    requireContext();
    String target = targetAttribute();
    var values =
        MigrationUi.form(
            root.getShell(),
            "Expression for " + target,
            MigrationUi.fields("Expression", expression(target)));
    if (!values.isEmpty())
      mutate(
          () -> document.assign(context.rule(), context.path(), target, values.get("Expression")));
  }

  private void insertFunction() {
    requireContext();
    String target = targetAttribute();
    var registry = FunctionRegistry.defaultRegistry();
    String name =
        MigrationChoice.choose(
            root.getShell(), "Insert function", registry.all().keySet().stream().sorted().toList());
    if (name == null) return;
    var function = registry.resolve(name).orElseThrow();
    var fields = new java.util.LinkedHashMap<String, String>();
    int index = 0;
    for (var parameter : function.parameters())
      fields.put(
          parameter.name() + " : " + parameter.type(), index++ == 0 ? expression(target) : "");
    if (fields.isEmpty()) fields.put("Arguments (DSL)", expression(target));
    var values =
        MigrationUi.form(
            root.getShell(), function.name() + " — " + function.evaluationMode(), fields);
    if (!values.isEmpty())
      mutate(
          () ->
              document.assign(
                  context.rule(),
                  context.path(),
                  target,
                  function.name() + "(" + String.join(", ", values.values()) + ")"));
  }

  private void enumTable() {
    requireContext();
    String target = targetAttribute();
    if (sourceSchema == null || targetSchema == null)
      throw new IllegalArgumentException("Load models first");
    String sourceName =
        MigrationChoice.choose(
            root.getShell(),
            "Source enumeration",
            sourceSchema.entity(context.source()).fields().stream()
                .filter(f -> !f.enumValues().isEmpty())
                .map(MigrationDraftService.Field::name)
                .toList());
    if (sourceName == null) return;
    var source =
        sourceSchema.entity(context.source()).fields().stream()
            .filter(f -> f.name().equals(sourceName))
            .findFirst()
            .orElseThrow();
    var dest =
        targetSchema.entity(context.target()).fields().stream()
            .filter(f -> f.name().equals(target))
            .findFirst()
            .orElseThrow();
    if (dest.enumValues().isEmpty())
      throw new IllegalArgumentException("Target is not an enumeration");
    var values =
        MigrationChoice.enumeration(root.getShell(), source.enumValues(), dest.enumValues());
    if (values.isEmpty()) return;
    mutate(
        () -> {
          String id = "enum_" + java.util.UUID.randomUUID().toString().replace('-', '_');
          document.enumeration(
              context.rule(),
              context.path(),
              target,
              context.alias() + "." + sourceName,
              id,
              values);
        });
  }

  private void reference() {
    requireContext();
    if (sourceSchema == null || targetSchema == null)
      throw new IllegalArgumentException("Load models first");
    String target = targetAttribute();
    var destination =
        targetSchema.entity(context.target()).fields().stream()
            .filter(f -> f.name().equals(target))
            .findFirst()
            .orElseThrow();
    String source =
        MigrationChoice.choose(
            root.getShell(),
            "Source reference",
            sourceSchema.entity(context.source()).fields().stream()
                .filter(f -> f.referenceTarget() != null)
                .map(MigrationDraftService.Field::name)
                .toList());
    if (source == null) return;
    mutate(
        () ->
            referenceFor(
                sourceSchema.entity(context.source()).fields().stream()
                    .filter(f -> f.name().equals(source))
                    .findFirst()
                    .orElseThrow(),
                destination));
  }

  private void referenceFor(MigrationDraftService.Field from, MigrationDraftService.Field to) {
    if (!context.path().isEmpty())
      throw new IllegalArgumentException(
          "Reference editing inside structures requires DSL in this version");
    if (from.referenceTarget() == null || to.referenceTarget() == null)
      throw new IllegalArgumentException("Select a source and target reference");
    List<String> candidates =
        document.document().rules().stream()
            .filter(
                r ->
                    r.elements().stream()
                        .anyMatch(
                            e ->
                                e instanceof IlimapTargetStmt t
                                    && t.targetClass().equals(to.referenceTarget())))
            .map(IlimapRuleBlock::id)
            .toList();
    var labels =
        candidates.stream()
            .map(
                id -> {
                  var source =
                      document.rule(id).elements().stream()
                          .filter(IlimapSourceStmt.class::isInstance)
                          .map(IlimapSourceStmt.class::cast)
                          .findFirst()
                          .orElseThrow();
                  return shortName(source.sourceClass())
                      + " → "
                      + shortName(to.referenceTarget())
                      + " ["
                      + id
                      + "]";
                })
            .toList();
    String chosen = MigrationChoice.choose(root.getShell(), "Use target created by rule", labels);
    if (chosen == null) return;
    String rule = candidates.get(labels.indexOf(chosen));
    document.reference(context.rule(), to.name(), rule, context.alias() + "." + from.name());
  }

  private void prepareDraft() {
    var values =
        MigrationUi.form(
            root.getShell(),
            "Prepare XTF model migration",
            MigrationUi.fields(
                "Source model",
                "",
                "Target model",
                "",
                "Model directories (;)",
                path.getParent().toString(),
                "Original XTF",
                "",
                "Target XTF",
                ""));
    if (values.isEmpty()) return;
    if (!document.document().rules().isEmpty()
        && !MigrationUi.confirm(
            root.getShell(),
            "Replace the current mapping with a newly prepared draft? Undo remains available."))
      return;
    background(
        "Comparing models",
        () ->
            new MigrationDraftService()
                .create(
                    values.get("Source model"),
                    values.get("Target model"),
                    values.get("Model directories (;)"),
                    values.get("Original XTF"),
                    values.get("Target XTF")),
        draft -> {
          sourceSchema = draft.source();
          targetSchema = draft.target();
          document.setText(draft.text());
          loadedModels = modelKey();
          sync();
          diagnostics.setText(
              draft.differences().stream()
                  .map(
                      d ->
                          d.status()
                              + "  "
                              + d.sourceClass()
                              + "."
                              + d.attribute()
                              + " → "
                              + d.targetClass()
                              + "\n  "
                              + d.detail())
                  .collect(java.util.stream.Collectors.joining("\n")));
          state.setText(
              "Draft prepared — review mappings and all information losses before confirming");
          tabs.setSelection(1);
        });
  }

  private void loadModels() {
    var doc = document.document();
    if (doc.inputs().size() != 1 || doc.outputs().size() != 1)
      throw new IllegalArgumentException("Select one source and one target model in the DSL");
    String dirs =
        doc.job() == null
            ? ""
            : String.join(
                ";",
                doc.job().modeldirs().stream()
                    .map(
                        d ->
                            d.contains("://")
                                ? d
                                : path.getParent().resolve(d).normalize().toString())
                    .toList());
    background(
        "Loading models",
        () -> {
          var service = new MigrationDraftService();
          return List.of(
              service.inspect(doc.inputs().getFirst().model(), dirs),
              service.inspect(doc.outputs().getFirst().model(), dirs));
        },
        schemas -> {
          loadedModels = modelKey();
          sourceSchema = schemas.get(0);
          targetSchema = schemas.get(1);
          rebuildRules();
          state.setText(
              "Models loaded — drag classes or attributes; double-click an assignment to edit");
        });
  }

  private void confirmReview() {
    var doc = document.document();
    if (doc.inputs().size() != 1) throw new IllegalArgumentException("One input required");
    if (!MigrationUi.confirm(
        root.getShell(),
        "Confirm that you have reviewed the mappings, new mandatory attributes, and unmapped source"
            + " contents. Only explicitly mapped contents will be migrated. Full target validation"
            + " remains required.")) return;
    var input = doc.inputs().getFirst();
    String block =
        document.text().substring(input.range().start().offset(), input.range().end().offset());
    if (block.contains("migrationReviewed false"))
      mutate(
          () ->
              document.edit(
                  document.revision(),
                  input.range().start().offset(),
                  input.range().end().offset(),
                  block.replace("migrationReviewed false", "migrationReviewed true")));
  }

  private void checkMapping() {
    String snapshot = document.text();
    Path base = path.getParent();
    background(
        "Checking mapping",
        () ->
            withSnapshot(
                base,
                snapshot,
                file ->
                    new MigrationService()
                        .prepare(
                            new MigrationService.Request(
                                file, null, null, List.of(), true, false, null))
                        .job()
                        .plan()
                        .diagnostics()),
        result -> {
          diagnostics.setText(
              result.all().stream()
                  .map(
                      d ->
                          d.severity()
                              + " "
                              + d.sourcePath()
                              + ": "
                              + d.message()
                              + "\n"
                              + (d.suggestion() == null ? "" : d.suggestion()))
                  .collect(java.util.stream.Collectors.joining("\n")));
          state.setText(
              result.hasErrors()
                  ? "Mapping errors — see diagnostics"
                  : "Mapping checked — data validation pending");
          tabs.setSelection(1);
        });
  }

  private void preview() {
    FileDialog dialog = new FileDialog(root.getShell(), SWT.OPEN);
    dialog.setText("Select a complete, small XTF for preview (all objects are processed)");
    dialog.setFilterExtensions(new String[] {"*.xtf", "*.xml"});
    String selected = dialog.open();
    if (selected == null) return;
    String snapshot = document.text();
    Path base = path.getParent();
    background(
        "Running complete preview and validation",
        () ->
            withSnapshot(
                base,
                snapshot,
                file -> {
                  Path work = Files.createTempDirectory("ilimap-preview-");
                  try {
                    Path output = work.resolve("preview.xtf");
                    var request =
                        new MigrationService.Request(
                            file, Path.of(selected), output, List.of(), true, false, work);
                    var prepared = new MigrationService().prepare(request);
                    var result = new MigrationService().execute(request, ignored -> {});
                    if (result.hasErrors()) return result.all().toString();
                    return "Complete preview file validated. This does not validate another"
                        + " production input.\n\n"
                        + MigrationPreview.compare(
                            Path.of(selected),
                            prepared
                                .job()
                                .plan()
                                .inputsById()
                                .values()
                                .iterator()
                                .next()
                                .transferDescription(),
                            output,
                            prepared
                                .job()
                                .plan()
                                .outputsById()
                                .values()
                                .iterator()
                                .next()
                                .transferDescription());
                  } finally {
                    try (var paths = Files.walk(work)) {
                      for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                        Files.deleteIfExists(p);
                    }
                  }
                }),
        result -> {
          preview.setText(result);
          tabs.setSelection(2);
          state.setText("Preview finished — production input validation is a separate run");
        });
  }

  private interface CheckedSupplier<T> {
    T get() throws Exception;
  }

  private interface CheckedFile<T> {
    T apply(Path file) throws Exception;
  }

  private <T> T withSnapshot(Path base, String text, CheckedFile<T> operation) throws Exception {
    Path file = Files.createTempFile(base, ".ilimap-check-", ".ilimap");
    try {
      Files.writeString(file, text);
      return operation.apply(file);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private <T> void background(String message, CheckedSupplier<T> work, Consumer<T> accept) {
    Display display = root.getDisplay();
    long request = ++generation;
    if (running != null) running.cancel(true);
    state.setText(message + "…");
    running =
        executor.submit(
            () -> {
              try {
                T value = work.get();
                display.asyncExec(
                    () -> {
                      if (!root.isDisposed() && request == generation) accept.accept(value);
                    });
              } catch (Exception ex) {
                if (!display.isDisposed())
                  display.asyncExec(
                      () -> {
                        if (!root.isDisposed() && request == generation) {
                          diagnostics.setText(ex.toString());
                          tabs.setSelection(1);
                          state.setText("Failed: " + ex.getMessage());
                        }
                      });
              }
            });
  }

  private static String q(String value) {
    return IlimapEditorDocument.quote(value);
  }
}
