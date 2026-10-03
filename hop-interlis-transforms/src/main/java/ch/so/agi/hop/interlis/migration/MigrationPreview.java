package ch.so.agi.hop.interlis.migration;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox_j.ObjectEvent;
import java.nio.file.Path;

/** Display limit applies after the complete sample was migrated and validated. */
final class MigrationPreview {
  static String compare(
      Path source,
      ch.interlis.ili2c.metamodel.TransferDescription sourceModel,
      Path target,
      ch.interlis.ili2c.metamodel.TransferDescription targetModel)
      throws Exception {
    return "SOURCE\n" + objects(source, sourceModel) + "\nTARGET\n" + objects(target, targetModel);
  }

  private static String objects(Path file, ch.interlis.ili2c.metamodel.TransferDescription model)
      throws Exception {
    StringBuilder result = new StringBuilder();
    int count = 0;
    var reader =
        new guru.interlis.transformer.interlis.InterlisIoFactory().createReader(file, model);
    try {
      IoxEvent event;
      while ((event = reader.read()) != null) {
        if (Thread.currentThread().isInterrupted())
          throw new java.util.concurrent.CancellationException();
        if (event instanceof ObjectEvent object) {
          if (++count > 100) {
            result.append("Display limited to 100 objects; the complete file was processed.\n");
            break;
          }
          append(result, object.getIomObject(), "");
        }
      }
    } finally {
      reader.close();
    }
    return result.toString();
  }

  private static void append(StringBuilder out, IomObject object, String indent) {
    out.append(indent)
        .append(object.getobjecttag())
        .append(" TID=")
        .append(object.getobjectoid())
        .append('\n');
    for (int a = 0; a < object.getattrcount(); a++) {
      String name = object.getattrname(a);
      for (int i = 0; i < object.getattrvaluecount(name); i++) {
        IomObject child = object.getattrobj(name, i);
        out.append(indent).append("  ").append(name).append('[').append(i).append("] = ");
        if (child == null) out.append(object.getattrprim(name, i)).append('\n');
        else if (child.getobjectrefoid() != null)
          out.append("REF ").append(child.getobjectrefoid()).append('\n');
        else {
          out.append('\n');
          append(out, child, indent + "    ");
        }
      }
    }
  }
}
