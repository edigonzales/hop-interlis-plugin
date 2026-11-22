package ch.so.agi.hop.interlis.migration;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.StartBasketEvent;
import ch.interlis.iox_j.ObjectEvent;
import guru.interlis.transformer.api.MigrationService;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/** Display limit applies after the complete sample was migrated and validated. */
final class MigrationPreview {
  static String run(Path mapping, Path source) throws Exception {
    Path work = Files.createTempDirectory("ilimap-preview-");
    try {
      Path target = work.resolve("preview.xtf");
      var request = new MigrationService.Request(mapping, source, target, List.of(), true, false, work);
      var service = new MigrationService();
      var result = service.execute(request, ignored -> {});
      if (result.hasErrors()) {
        String message =
            result.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .map(d -> d.sourcePath() == null ? d.message() : d.sourcePath() + ": " + d.message())
                .collect(Collectors.joining("\n"));
        try (var logs = Files.newDirectoryStream(work, "*-validation.log")) {
          for (Path log : logs) {
            String details = Files.readString(log);
            if (!details.isBlank()) message += "\n\n" + details;
          }
        } catch (IOException | DirectoryIteratorException ignored) {
          // Keep the migration diagnostic if the validator log cannot be read.
        }
        throw new IOException(message);
      }
      var plan = service.prepare(request).job().plan();
      String preview =
          "Complete preview file validated. This does not validate another production input.\n\n"
              + compare(
                  source,
                  plan.inputsById().values().iterator().next().transferDescription(),
                  target,
                  plan.outputsById().values().iterator().next().transferDescription());
      String warnings =
          result.all().stream()
              .filter(d -> d.severity() == Severity.WARNING)
              .map(d -> d.code() + ": " + d.message())
              .collect(Collectors.joining("\n"));
      if (!warnings.isEmpty()) preview += "\nWARNINGS\n" + warnings + "\n";
      Path lossReport = work.resolve("lossiness-report.md");
      return Files.exists(lossReport)
          ? preview + "\nINFORMATION LOSS\n" + Files.readString(lossReport)
          : preview;
    } finally {
      try (var paths = Files.walk(work)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          Files.deleteIfExists(path);
        }
      }
    }
  }

  static String compare(
      Path source, TransferDescription sourceModel, Path target, TransferDescription targetModel)
      throws Exception {
    return "SOURCE\n" + objects(source, sourceModel) + "\nTARGET\n" + objects(target, targetModel);
  }

  private static String objects(Path file, TransferDescription model)
      throws Exception {
    StringBuilder result = new StringBuilder();
    int count = 0;
    String basketId = null;
    var reader = new InterlisIoFactory().createReader(file, model);
    try {
      IoxEvent event;
      while ((event = reader.read()) != null) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (event instanceof StartBasketEvent basket) {
          basketId = basket.getBid();
        } else if (event instanceof ObjectEvent object) {
          if (++count > 100) {
            result.append("Display limited to 100 objects; the complete file was processed.\n");
            break;
          }
          append(result, object.getIomObject(), "", basketId);
        }
      }
    } finally {
      reader.close();
    }
    return result.toString();
  }

  private static void append(StringBuilder out, IomObject object, String indent, String basketId) {
    out.append(indent).append(object.getobjecttag());
    if (object.getobjectoid() != null) out.append(" TID=").append(object.getobjectoid());
    if (basketId != null) out.append(" BID=").append(basketId);
    out.append('\n');
    for (int a = 0; a < object.getattrcount(); a++) {
      String name = object.getattrname(a);
      for (int i = 0; i < object.getattrvaluecount(name); i++) {
        IomObject child = object.getattrobj(name, i);
        out.append(indent).append("  ").append(name).append('[').append(i).append("] = ");
        if (child == null) out.append(object.getattrprim(name, i)).append('\n');
        else if (child.getobjectrefoid() != null) {
          out.append("REF ").append(child.getobjectrefoid());
          if (child.getobjectrefbid() != null) out.append(" BID=").append(child.getobjectrefbid());
          out.append('\n');
        } else {
          out.append('\n');
          append(out, child, indent + "    ", null);
        }
      }
    }
  }
}
