package ch.so.agi.hop.interlis.transforms;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Small shared editor for feature options; no model interpretation lives here. */
public final class InterlisOptionsDialog {
  public record Option(String label, String value, Consumer<String> save, List<String> choices) {
    public Option(String label, String value, Consumer<String> save) {
      this(label, value, save, List.of());
    }
  }

  private InterlisOptionsDialog() {}

  public static boolean open(Shell parent, IVariables variables, List<Option> options) {
    Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    shell.setText("INTERLIS options");
    shell.setLayout(new GridLayout(2, false));
    PropsUi.setLook(shell);
    var readers = new ArrayList<java.util.function.Supplier<String>>();
    for (var option : options) {
      var label = new Label(shell, SWT.NONE);
      label.setText(option.label());
      PropsUi.setLook(label);
      if (option.choices().isEmpty()) {
        var text = new TextVar(variables, shell, SWT.BORDER);
        text.setText(option.value() == null ? "" : option.value());
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        readers.add(text::getText);
      } else {
        var combo = new Combo(shell, SWT.READ_ONLY);
        combo.setItems(option.choices().toArray(String[]::new));
        combo.setText(option.value());
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        readers.add(combo::getText);
      }
    }
    Label diagnostic = new Label(shell, SWT.WRAP);
    diagnostic.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    var buttons = new Composite(shell, SWT.NONE);
    buttons.setLayout(new RowLayout());
    buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
    boolean[] accepted = {false};
    Button ok = new Button(buttons, SWT.PUSH);
    ok.setText("OK");
    ok.addListener(
        SWT.Selection,
        e -> {
          try {
            // Validate numeric controls before applying any setters.
            for (int i = 0; i < options.size(); i++)
              if (options.get(i).label().contains("MiB")) {
                long value = Long.parseLong(readers.get(i).get().trim());
                Math.multiplyExact(value, 1L << 20);
                if (value < 0 || (options.get(i).label().startsWith("Buffer") && value == 0))
                  throw new IllegalArgumentException("Invalid memory/disk budget");
              }
            for (int i = 0; i < options.size(); i++)
              options.get(i).save().accept(readers.get(i).get().trim());
            accepted[0] = true;
            shell.dispose();
          } catch (RuntimeException error) {
            diagnostic.setText(error.getMessage());
            shell.layout();
          }
        });
    Button cancel = new Button(buttons, SWT.PUSH);
    cancel.setText("Cancel");
    cancel.addListener(SWT.Selection, e -> shell.dispose());
    shell.setDefaultButton(ok);
    shell.pack();
    shell.setSize(Math.max(660, shell.getSize().x), shell.getSize().y);
    shell.open();
    var display = parent.getDisplay();
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) display.sleep();
    }
    return accepted[0];
  }
}
