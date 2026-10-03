package ch.so.agi.hop.interlis.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/** Manual SWT acceptance entry point. Does not save or modify the supplied mapping. */
public final class MigrationEditorGuiHarness {
  public static void main(String[] args) throws Exception {
    Path mapping = Path.of(args[0]).toAbsolutePath();
    Display display = new Display();
    Shell shell = new Shell(display);
    shell.setText("INTERLIS mapping — SWT acceptance");
    shell.setLayout(new FillLayout());
    if (args.length > 1 && args[1].equals("--action")) {
      org.apache.hop.core.HopEnvironment.init();
      var action = new InterlisMigration();
      action.setName("Migration acceptance");
      action.setMappingFile(mapping.toString());
      String before = action.getXml();
      var result =
          new InterlisMigrationDialog(
                  shell,
                  action,
                  new org.apache.hop.workflow.WorkflowMeta(),
                  new org.apache.hop.core.variables.Variables())
              .open();
      var label = new org.eclipse.swt.widgets.Label(shell, org.eclipse.swt.SWT.WRAP);
      label.setText(
          result == null && before.equals(action.getXml())
              ? "Cancel verified: action metadata unchanged."
              : "Dialog accepted or metadata changed; inspect manually.");
    } else {
      new IlimapEditor(shell, mapping, Files.readString(mapping), () -> {});
    }
    shell.setSize(1200, 850);
    shell.open();
    while (!shell.isDisposed()) if (!display.readAndDispatch()) display.sleep();
    display.dispose();
  }
}
