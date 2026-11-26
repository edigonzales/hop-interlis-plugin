package ch.so.agi.hop.interlis.transforms.rolejoin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.hop.interlis.transforms.TestData;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.WINDOWS})
class InterlisRoleJoinDialogTest {
  private static final String MODEL = "HopIli_Associations_V1";
  private static final String PERSON = MODEL + ".Data.Person";
  private static final String ADDRESS = MODEL + ".Data.Address";
  private static final String TRANSFORM_NAME = "Join address";

  @Test
  void offersClassesAndRolesBeforeSelectionAndRestoresThePreviewAfterReload(
      @TempDir Path emptyDirectory) throws Exception {
    var meta = configuredMeta();
    String originalDirectory = meta.getModelDirectories();
    assertThat(open(meta, shell -> {
      var mainClass = ((ComboVar) field(shell, "Main class")).getCComboWidget();
      var role = ((ComboVar) field(shell, "Role")).getCComboWidget();
      var preview = controls(shell).filter(Table.class::isInstance)
          .map(Table.class::cast).findFirst().orElseThrow();

      await(shell, "classes before selection", () -> mainClass.indexOf(PERSON) >= 0);
      assertThat(mainClass.getText()).isEmpty();
      assertThat(role.getItemCount()).isZero();
      assertThat(preview.getItemCount()).isZero();
      choose(mainClass, PERSON);
      await(shell, "roles before selection",
          () -> Arrays.stream(role.getItems()).anyMatch(label -> label.startsWith("Address -> ")));
      assertThat(role.getText()).isEmpty();
      assertThat(preview.getItemCount()).isZero();
      String address = Arrays.stream(role.getItems())
          .filter(label -> label.startsWith("Address -> " + ADDRESS)).findFirst().orElseThrow();
      choose(role, address);
      await(shell, "Address preview", () -> hasStreet(preview));

      shell.setSize(shell.getMinimumSize());
      shell.layout(true, true);
      int previewHeight = preview.getSize().y;
      assertThat(shell.getMinimumSize().x).isEqualTo(860);
      assertThat(shell.getMinimumSize().y).isEqualTo(600);
      assertThat(previewHeight).isGreaterThan(preview.getHeaderHeight() + preview.getItemHeight());
      assertThat(preview.getBounds().y + previewHeight)
          .isLessThanOrEqualTo(button(shell, "OK").getBounds().y);
      shell.setSize(shell.getSize().x, shell.getSize().y + 120);
      shell.layout(true, true);
      assertThat(preview.getSize().y).isGreaterThan(previewHeight);

      choose(mainClass, ADDRESS);
      await(shell, "invalid role for the new class",
          () -> shows(shell, "not found on class " + ADDRESS) && preview.getItemCount() == 0);
      assertThat(role.getText()).startsWith("Address");
      assertThat(Arrays.asList(role.getItems())).doesNotContain(address);
      choose(mainClass, PERSON);
      await(shell, "preview after returning to Person", () -> hasStreet(preview));

      click(shell, "Reload");
      await(shell, "preview after reloading",
          () -> shows(shell, "Model loaded; role Address") && hasStreet(preview));
      assertThat(mainClass.getText()).isEqualTo(PERSON);
      assertThat(role.getText()).isEqualTo(address);

      var directories = text(shell, "Model dirs");
      directories.setText(emptyDirectory.toString());
      click(shell, "Reload");
      await(shell, "model resolution error",
          () -> shows(shell, MODEL) && preview.getItemCount() == 0);
      assertThat(mainClass.getText()).isEqualTo(PERSON);
      assertThat(role.getText()).isEqualTo(address);
      assertThat(mainClass.indexOf(PERSON)).isNotNegative();
      assertThat(role.indexOf(address)).isNotNegative();
      directories.setText(originalDirectory);
      click(shell, "Reload");
      await(shell, "preview after correcting the model source", () -> hasStreet(preview));
      assertThat(mainClass.getText()).isEqualTo(PERSON);
      assertThat(role.getText()).isEqualTo(address);
      click(shell, "Cancel");
    })).isNull();
  }

  @Test
  void cancelDiscardsReloadedFieldsAndSpillOptions() throws Exception {
    var meta = configuredMeta();
    meta.setMainClassName(PERSON);
    meta.setRoleName("Address");
    meta.setChanged(true);
    String originalXml = meta.getXml();

    assertThat(open(meta, shell -> {
      ((ComboVar) field(shell, "Main class")).setText(ADDRESS);
      ((ComboVar) field(shell, "Role")).setText("Person");
      ((ComboVar) field(shell, "Main input")).setText("changed source");
      ((ComboVar) field(shell, "Lookup input")).setText("changed lookup");
      text(shell, "Main ref field").setText("changed ref");
      text(shell, "Max lookup rows").setText("17");
      button(shell, "Fail on duplicate lookup key (BID + TID)").setSelection(false);
      editSpillOptions(shell);
      click(shell, "Reload");
      assertThat(meta.getMainClassName()).as("original class while the dialog is open")
          .isEqualTo(PERSON);
      assertThat(meta.getBufferMemoryMiB()).as("original spill budget").isEqualTo(64);
      click(shell, "Cancel");
    })).isNull();
    assertThat(meta.getXml()).isEqualTo(originalXml);
    assertThat(meta.hasChanged()).isTrue();
  }

  @Test
  void okPersistsTheRoleNameAndOptionsAndOnlyMarksRealChanges() throws Exception {
    var meta = configuredMeta();
    assertThat(open(meta, shell -> {
      ((ComboVar) field(shell, "Main class")).setText(PERSON);
      ((ComboVar) field(shell, "Role")).setText("Address -> " + ADDRESS);
      text(shell, "Output prefix").setText("Joined_");
      text(shell, "Fields to add").setText("Street");
      button(shell, "Fail on duplicate lookup key (BID + TID)").setSelection(false);
      editSpillOptions(shell);
      click(shell, "OK");
    })).isEqualTo(TRANSFORM_NAME);
    assertThat(meta.hasChanged()).isTrue();

    String xml = meta.getXml();
    var restored = new InterlisRoleJoinMeta();
    restored.loadXml(XmlHandler.loadXmlString("<transform>" + xml + "</transform>")
        .getDocumentElement(), new MemoryMetadataProvider());
    assertThat(restored.getMainClassName()).isEqualTo(PERSON);
    assertThat(restored.getRoleName()).isEqualTo("Address");
    assertThat(restored.getPrefix()).isEqualTo("Joined_");
    assertThat(restored.getLookupFields()).containsExactly("Street");
    assertThat(restored.isFailOnDuplicateKey()).isFalse();
    assertThat(restored.getBufferMemoryMiB()).isEqualTo(16);
    assertThat(restored.getSpillDirectory()).isEqualTo("spill");
    assertThat(restored.getMaxSpillMiB()).isEqualTo(32);

    meta.setChanged(false);
    assertThat(open(meta, shell -> click(shell, "OK"))).isEqualTo(TRANSFORM_NAME);
    assertThat(meta.getXml()).isEqualTo(xml);
    assertThat(meta.hasChanged()).isFalse();
    assertThat(open(meta, shell -> {
      ((Text) field(shell, "Transform name")).setText("Renamed join");
      click(shell, "OK");
    })).isEqualTo("Renamed join");
    assertThat(meta.getXml()).isEqualTo(xml);
    assertThat(meta.hasChanged()).isTrue();
  }

  private static InterlisRoleJoinMeta configuredMeta() {
    var meta = new InterlisRoleJoinMeta();
    meta.setDefault();
    meta.setModelNames(MODEL);
    meta.setModelDirectories(TestData.path("/models/" + MODEL + ".ili").getParent().toString());
    return meta;
  }

  private static String open(InterlisRoleJoinMeta meta, Consumer<Shell> interact) throws Exception {
    assumeTrue(!OS.LINUX.isCurrentOs() || System.getenv("DISPLAY") != null, "SWT requires a display");
    HopClientEnvironment.init();
    PluginRegistry.getInstance().registerPluginClass(
        InterlisRoleJoinMeta.class.getName(), TransformPluginType.class, Transform.class);
    var pipeline = new PipelineMeta();
    pipeline.addTransform(new TransformMeta(TRANSFORM_NAME, meta));
    Display display = Display.getDefault();
    var failure = new AtomicReference<Throwable>();
    var parent = new Shell(display);
    try {
      var dialog = new InterlisRoleJoinDialog(parent, new Variables(), meta, pipeline);
      display.asyncExec(() -> {
        try {
          interact.accept(childShell(parent, "INTERLIS Role Join"));
        } catch (Throwable error) {
          failure.set(error);
          for (Shell child : parent.getShells()) {
            if (!child.isDisposed()) child.dispose();
          }
        }
      });
      String result = dialog.open();
      if (failure.get() != null) {
        throw new AssertionError("Role Join dialog interaction failed", failure.get());
      }
      return result;
    } finally {
      parent.dispose();
    }
  }

  private static void editSpillOptions(Shell shell) {
    shell.getDisplay().asyncExec(() -> {
      var options = childShell(shell, "INTERLIS options");
      try {
        text(options, "Buffer memory (MiB)").setText("16");
        text(options, "Spill directory (empty: system temp)").setText("spill");
        text(options, "Maximum spill (MiB, 0: unlimited)").setText("32");
        click(options, "OK");
      } finally {
        if (!options.isDisposed()) options.dispose();
      }
    });
    click(shell, "Options…");
  }

  private static void choose(CCombo combo, String label) {
    int index = combo.indexOf(label);
    assertThat(index).as(label).isNotNegative();
    combo.select(index);
    combo.notifyListeners(SWT.Selection, new Event());
  }

  private static boolean hasStreet(Table preview) {
    return Arrays.stream(preview.getItems()).anyMatch(row -> row.getText(0).equals("Address_Street"));
  }

  private static boolean shows(Shell shell, String message) {
    return controls(shell).filter(Label.class::isInstance).map(Label.class::cast)
        .anyMatch(label -> label.getText().contains(message));
  }

  private static void await(Shell shell, String description, BooleanSupplier ready) {
    long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
    while (!ready.getAsBoolean() && System.nanoTime() < deadline) {
      if (!shell.getDisplay().readAndDispatch()) LockSupport.parkNanos(5_000_000);
    }
    assertThat(ready.getAsBoolean()).as(description).isTrue();
  }

  private static Shell childShell(Shell parent, String title) {
    return Arrays.stream(parent.getShells()).filter(shell -> title.equals(shell.getText()))
        .findFirst().orElseThrow();
  }

  private static TextVar text(Composite parent, String label) {
    return (TextVar) field(parent, label);
  }

  private static Control field(Composite parent, String label) {
    var widget = controls(parent).filter(Label.class::isInstance).map(Label.class::cast)
        .filter(candidate -> candidate.getText().equals(label)).findFirst().orElseThrow();
    var siblings = Arrays.asList(widget.getParent().getChildren());
    if (label.equals("Main class")) {
      return siblings.stream().filter(ComboVar.class::isInstance).findFirst().orElseThrow();
    }
    return siblings.get(siblings.indexOf(widget) + 1);
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
