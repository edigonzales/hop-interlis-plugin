package ch.so.agi.hop.interlis.migration;

import java.io.IOException;
import java.nio.file.*;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.explorer.*;
import org.apache.hop.ui.hopgui.perspective.explorer.file.types.base.BaseExplorerFileTypeHandler;
import org.eclipse.swt.widgets.Composite;

public class IlimapFileHandler extends BaseExplorerFileTypeHandler {
  private MappingFile file;
  private IlimapEditor editor;

  public IlimapFileHandler(HopGui gui, ExplorerPerspective perspective, ExplorerFile explorerFile)
      throws IOException {
    super(gui, perspective, explorerFile);
    file = new MappingFile(MigrationPaths.local(explorerFile.getFilename()));
  }

  @Override
  public void renderFile(Composite parent) {
    editor =
        new IlimapEditor(
            parent,
            file.path(),
            file.text(),
            () -> {
              setChanged();
              updateGui();
            });
  }

  @Override
  public void save() throws HopException {
    try {
      file.save(editor.text());
      clearChanged();
      updateGui();
    } catch (IOException ex) {
      throw new HopException(ex.getMessage(), ex);
    }
  }

  @Override
  public void saveAs(String filename) throws HopException {
    try {
      Path path = MigrationPaths.local(filename).toAbsolutePath().normalize();
      if (!path.equals(file.path()) && Files.exists(path))
        throw new IOException("Target already exists: " + path);
      var replacement = new MappingFile(path);
      replacement.save(editor.text());
      file = replacement;
      setFilename(path.toString());
      setName(path.getFileName().toString());
      editor.setPath(path);
      clearChanged();
      updateGui();
    } catch (IOException ex) {
      throw new HopException(ex.getMessage(), ex);
    }
  }

  @Override
  public void undo() {
    editor.undo();
  }

  @Override
  public void redo() {
    editor.redo();
  }

  @Override
  public void copySelectedToClipboard() {
    editor.code().copy();
  }

  @Override
  public void cutSelectedToClipboard() {
    editor.code().cut();
  }

  @Override
  public void pasteFromClipboard() {
    editor.code().paste();
  }
}
