package ch.so.agi.hop.interlis.migration;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.*;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerFile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;

@HopFileTypePlugin(
    id = "Ilimap",
    name = "INTERLIS Mapping",
    description = "Graphical ilimap model migration editor",
    image = "ch/so/agi/hop/interlis/migration/interlis.svg")
public class IlimapFileType extends HopFileTypeBase {
  @Override
  public String getName() {
    return "INTERLIS Mapping";
  }

  @Override
  public String getDefaultFileExtension() {
    return "ilimap";
  }

  @Override
  public String[] getFilterExtensions() {
    return new String[] {"*.ilimap"};
  }

  @Override
  public String[] getFilterNames() {
    return new String[] {"INTERLIS Mapping"};
  }

  @Override
  public String getFileTypeImage() {
    return "ch/so/agi/hop/interlis/migration/interlis.svg";
  }

  @Override
  public List<IGuiContextHandler> getContextHandlers() {
    return List.of();
  }

  @Override
  public boolean supportsFile(IHasFilename file) {
    return file.getFilename() != null && file.getFilename().endsWith(".ilimap");
  }

  @Override
  public Properties getCapabilities() {
    var caps = new Properties();
    for (String key :
        List.of(
            CAPABILITY_NEW,
            CAPABILITY_SAVE,
            CAPABILITY_SAVE_AS,
            CAPABILITY_CLOSE,
            CAPABILITY_COPY,
            CAPABILITY_CUT,
            CAPABILITY_PASTE)) caps.setProperty(key, "true");
    return caps;
  }

  @Override
  public IHopFileTypeHandler openFile(HopGui gui, String filename, IVariables variables)
      throws HopException {
    try {
      String resolved = variables.resolve(filename);
      if (resolved == null || resolved.isBlank() || resolved.contains("${"))
        throw new IllegalArgumentException("Select a local .ilimap file with resolved variables");
      Path path = MigrationPaths.local(resolved).toAbsolutePath().normalize();
      var perspective = HopGui.getExplorerPerspective();
      var existing = perspective.findFileTypeHandlerByFilename(path.toString());
      if (existing != null) {
        perspective.setActiveFileTypeHandler(existing);
        return existing;
      }
      // File-type annotations cannot declare a classloader group in Hop 2.19.
      // Construct the editor through the Action plugin loader so both use one INTERLIS runtime.
      var registry = org.apache.hop.core.plugins.PluginRegistry.getInstance();
      var plugin =
          registry.findPluginWithId(
              org.apache.hop.core.plugins.ActionPluginType.class, "InterlisMigration");
      if (plugin == null)
        throw new IllegalStateException("INTERLIS Migration action is not installed");
      var handlerClass =
          registry
              .getClassLoader(plugin)
              .loadClass("ch.so.agi.hop.interlis.migration.IlimapFileHandler");
      var handler =
          (org.apache.hop.ui.hopgui.perspective.explorer.file.IExplorerFileTypeHandler)
              handlerClass
                  .getConstructor(
                      HopGui.class,
                      org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective.class,
                      ExplorerFile.class)
                  .newInstance(
                      gui,
                      perspective,
                      new ExplorerFile(path.getFileName().toString(), path.toString(), this));
      perspective.addFile(handler);
      perspective.setActiveFileTypeHandler(handler);
      return handler;
    } catch (Exception ex) {
      throw new HopException("Cannot open INTERLIS mapping: " + ex.getMessage(), ex);
    }
  }

  @Override
  public IHopFileTypeHandler newFile(HopGui gui, IVariables variables) throws HopException {
    FileDialog dialog = new FileDialog(gui.getShell(), SWT.SAVE);
    dialog.setFilterExtensions(getFilterExtensions());
    dialog.setFileName("migration.ilimap");
    String filename = dialog.open();
    return filename == null ? null : openFile(gui, filename, variables);
  }
}
