package ch.so.agi.hop.interlis.transforms.objecttorow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.WINDOWS})
class InterlisObjectToRowDialogTest {
  private static final String MODEL = "HopIli_Primitives_V1";
  private static final String CLASS_NAME = MODEL + ".Data.Primitive";
  private static final String TRANSFORM_NAME = "Project objects";

  @Test
  void discoversClassesBeforeSelectionAndRefreshesTheVisiblePreview(@TempDir Path emptyDirectory)
      throws Exception {
    var meta = configuredMeta();
    String originalXml = meta.getXml();

    assertThat(open(meta, shell -> {
      var combo = controls(shell).filter(ComboVar.class::isInstance)
          .map(ComboVar.class::cast).findFirst().orElseThrow().getCComboWidget();
      var preview = controls(shell).filter(Table.class::isInstance)
          .map(Table.class::cast).findFirst().orElseThrow();
      await(shell, "classes for the model without a selected class",
          () -> Arrays.asList(combo.getItems()).contains(CLASS_NAME));
      assertThat(combo.getText()).isEmpty();
      assertThat(preview.getItemCount()).isZero();
      assertThat(meta.hasChanged()).isFalse();

      combo.select(combo.indexOf(CLASS_NAME));
      combo.notifyListeners(SWT.Selection, new Event());
      click(shell, "Reload");
      await(shell, "projected primitive fields", () -> fields(preview).anyMatch("Text"::equals));
      assertThat(fields(preview)).contains("_ili_tid", "_ili_bid", "Text");
      assertThat(preview.getSize().y).isGreaterThan(preview.getHeaderHeight() + preview.getItemHeight());
      assertThat(preview.getBounds().y + preview.getSize().y)
          .isLessThanOrEqualTo(button(shell, "OK").getBounds().y);

      click(shell, "Reload");
      Button includeTid = button(shell, "Include _ili_tid");
      includeTid.setSelection(false);
      includeTid.notifyListeners(SWT.Selection, new Event());
      await(shell, "preview without TID", () -> fields(preview).noneMatch("_ili_tid"::equals));
      assertThat(fields(preview)).contains("_ili_bid", "Text");

      var directories = text(shell, "Model dirs");
      directories.setText(emptyDirectory.toString());
      click(shell, "Reload");
      await(shell, "model resolution error", () -> controls(shell).filter(Label.class::isInstance)
          .map(Label.class::cast).map(Label::getText).anyMatch(label -> label.contains(MODEL)));
      assertThat(preview.getItemCount()).isZero();
      assertThat(combo.getText()).isEqualTo(CLASS_NAME);
      directories.setText(meta.getModelDirectories());
      click(shell, "Reload");
      await(shell, "preview after correcting the model source",
          () -> fields(preview).anyMatch("Text"::equals));
      click(shell, "Cancel");
    })).isNull();
    assertThat(meta.getXml()).isEqualTo(originalXml);
    assertThat(meta.hasChanged()).isFalse();
  }

  @Test
  void commitsOrdinaryFieldsAndKeepsSpillOptionsPrivateUntilOk() throws Exception {
    var meta = configuredMeta();
    meta.setClassName(CLASS_NAME);
    String originalXml = meta.getXml();

    assertThat(open(meta, shell -> {
      text(shell, "Object field").setText("payload");
      text(shell, "Default SRID").setText("2056");
      editSpillBudget(shell, "Cancel");
      click(shell, "OK");
    })).isEqualTo(TRANSFORM_NAME);
    assertThat(meta.getObjectFieldName()).isEqualTo("payload");
    assertThat(meta.getDefaultSrid()).isEqualTo("2056");
    assertThat(meta.getBufferMemoryMiB()).isEqualTo(64);
    assertThat(meta.hasChanged()).isTrue();
    assertThat(meta.getXml()).isNotEqualTo(originalXml);

    meta.setChanged(false);
    String savedXml = meta.getXml();
    assertThat(open(meta, shell -> {
      text(shell, "Object field").setText("discarded");
      editSpillBudget(shell, "OK");
      assertThat(meta.hasChanged()).isFalse();
      assertThat(meta.getBufferMemoryMiB()).isEqualTo(64);
      click(shell, "Cancel");
    })).isNull();
    assertThat(meta.getXml()).isEqualTo(savedXml);
    assertThat(meta.hasChanged()).isFalse();

    assertThat(open(meta, shell -> click(shell, "OK"))).isEqualTo(TRANSFORM_NAME);
    assertThat(meta.hasChanged()).isFalse();
    assertThat(open(meta, shell -> {
      editSpillBudget(shell, "OK");
      click(shell, "OK");
    })).isEqualTo(TRANSFORM_NAME);
    assertThat(meta.getBufferMemoryMiB()).isEqualTo(16);
    assertThat(meta.hasChanged()).isTrue();
    assertThat(open(meta, shell -> click(shell, "Cancel"))).isNull();
    assertThat(meta.hasChanged()).isTrue();
  }

  private InterlisObjectToRowMeta configuredMeta() {
    var meta = new InterlisObjectToRowMeta();
    meta.setDefault();
    meta.setModelNames(MODEL);
    meta.setModelDirectories(Path.of("src/test/resources/fixtures/models/2.4")
        .toAbsolutePath().toString());
    return meta;
  }

  private String open(InterlisObjectToRowMeta meta, Consumer<Shell> interact) throws Exception {
    assumeTrue(!OS.LINUX.isCurrentOs() || System.getenv("DISPLAY") != null, "SWT requires a display");
    HopClientEnvironment.init();
    PluginRegistry.getInstance().registerPluginClass(
        InterlisObjectToRowMeta.class.getName(), TransformPluginType.class, Transform.class);
    var pipeline = new PipelineMeta();
    pipeline.addTransform(new TransformMeta(TRANSFORM_NAME, meta));
    Display display = Display.getDefault();
    Shell parent = new Shell(display);
    var failure = new AtomicReference<Throwable>();
    try {
      var dialog = new InterlisObjectToRowDialog(parent, new Variables(), meta, pipeline);
      display.asyncExec(() -> {
        try {
          interact.accept(childShell(parent, "INTERLIS Object to Row"));
        } catch (Throwable error) {
          failure.set(error);
          for (Shell child : parent.getShells()) {
            if (!child.isDisposed()) {
              child.dispose();
            }
          }
        }
      });
      String result = dialog.open();
      if (failure.get() != null) {
        throw new AssertionError("Object to Row dialog interaction failed", failure.get());
      }
      return result;
    } finally {
      parent.dispose();
    }
  }

  private static void editSpillBudget(Shell shell, String action) {
    shell.getDisplay().asyncExec(() -> {
      var options = childShell(shell, "INTERLIS options");
      try {
        text(options, "Buffer memory (MiB)").setText("16");
        click(options, action);
      } finally {
        if (!options.isDisposed()) {
          options.close();
        }
      }
    });
    click(shell, "Options…");
  }

  private static Shell childShell(Shell parent, String title) {
    return Arrays.stream(parent.getShells()).filter(shell -> title.equals(shell.getText()))
        .findFirst().orElseThrow();
  }

  private static void await(Shell shell, String description, BooleanSupplier ready) {
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(8).toNanos();
    while (!ready.getAsBoolean() && System.nanoTime() < deadline) {
      if (!shell.getDisplay().readAndDispatch()) {
        java.util.concurrent.locks.LockSupport.parkNanos(5_000_000);
      }
    }
    assertThat(ready.getAsBoolean()).as(description).isTrue();
  }

  private static Stream<String> fields(Table preview) {
    return Arrays.stream(preview.getItems()).map(item -> item.getText(0));
  }

  private static TextVar text(Composite parent, String label) {
    var children = Arrays.asList(parent.getChildren());
    var widget = controls(parent).filter(Label.class::isInstance).map(Label.class::cast)
        .filter(candidate -> candidate.getText().equals(label)).findFirst().orElseThrow();
    return (TextVar) children.get(children.indexOf(widget) + 1);
  }

  private static Button button(Composite parent, String label) {
    return controls(parent).filter(Button.class::isInstance).map(Button.class::cast)
        .filter(candidate -> candidate.getText().equals(label)).findFirst().orElseThrow();
  }

  private static void click(Composite parent, String label) {
    button(parent, label).notifyListeners(SWT.Selection, new Event());
  }

  private static Stream<Control> controls(Composite parent) {
    return Arrays.stream(parent.getChildren()).flatMap(
        child -> child instanceof Composite composite
            ? Stream.concat(Stream.of(child), controls(composite))
            : Stream.of(child));
  }
}
