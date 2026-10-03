package ch.so.agi.hop.interlis.migration;

import guru.interlis.transformer.api.MigrationService;
import guru.interlis.transformer.diag.DiagnosticCollector;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.*;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.workflow.action.ActionBase;

/** A whole migration job, deliberately a workflow action rather than a per-row transform. */
@Action(
    id = "InterlisMigration",
    name = "INTERLIS Migration",
    description = "Execute an ilimap XTF model migration",
    image = "ch/so/agi/hop/interlis/migration/interlis.svg",
    categoryDescription = "INTERLIS",
    classLoaderGroup = "sogeo-geometry")
public class InterlisMigration extends ActionBase {
  @HopMetadataProperty private String mappingFile = "";
  @HopMetadataProperty private String inputFile = "";
  @HopMetadataProperty private String outputFile = "";
  @HopMetadataProperty private String modelDirectories = "";
  @HopMetadataProperty private String reportDirectory = "";
  @HopMetadataProperty private boolean validate = true;
  @HopMetadataProperty private boolean overwrite;

  public InterlisMigration() {
    super("INTERLIS Migration", "");
  }

  public String getMappingFile() {
    return mappingFile;
  }

  public void setMappingFile(String value) {
    mappingFile = value;
  }

  public String getInputFile() {
    return inputFile;
  }

  public void setInputFile(String value) {
    inputFile = value;
  }

  public String getOutputFile() {
    return outputFile;
  }

  public void setOutputFile(String value) {
    outputFile = value;
  }

  public String getModelDirectories() {
    return modelDirectories;
  }

  public void setModelDirectories(String value) {
    modelDirectories = value;
  }

  public String getReportDirectory() {
    return reportDirectory;
  }

  public void setReportDirectory(String value) {
    reportDirectory = value;
  }

  public boolean isValidate() {
    return validate;
  }

  public void setValidate(boolean value) {
    validate = value;
  }

  public boolean isOverwrite() {
    return overwrite;
  }

  public void setOverwrite(boolean value) {
    overwrite = value;
  }

  public MigrationService.Request request() {
    Path mapping = path(mappingFile);
    if (mapping == null) throw new IllegalArgumentException("Select an .ilimap mapping file");
    String dirs = resolved(modelDirectories);
    return new MigrationService.Request(
        mapping,
        path(inputFile),
        path(outputFile),
        Arrays.stream(dirs.split(";")).filter(s -> !s.isBlank()).toList(),
        validate,
        overwrite,
        path(reportDirectory));
  }

  private Path path(String value) {
    String s = resolved(value);
    return s.isBlank() ? null : MigrationPaths.local(s);
  }

  private String resolved(String value) {
    String s = resolve(value == null ? "" : value);
    if (s.contains("${")) throw new IllegalArgumentException("Unresolved variable: " + s);
    return s;
  }

  @Override
  public Result execute(Result result, int nr) {
    result.setResult(false);
    ExecutorService worker =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread thread = new Thread(r, "interlis-migration");
              thread.setDaemon(true);
              return thread;
            });
    Future<DiagnosticCollector> task = null;
    try {
      var request = request();
      task = worker.submit(() -> new MigrationService().execute(request, this::logBasic));
      while (true) {
        if (getParentWorkflow() != null && getParentWorkflow().isStopped())
          throw new CancellationException("Migration stopped");
        try {
          var diagnostics = task.get(100, TimeUnit.MILLISECONDS);
          diagnostics
              .all()
              .forEach(
                  d -> {
                    if (d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
                      logError(d.code() + ": " + d.message());
                    else logBasic(d.code() + ": " + d.message());
                  });
          result.setResult(!diagnostics.hasErrors());
          if (diagnostics.hasErrors()) result.setNrErrors(result.getNrErrors() + 1);
          break;
        } catch (TimeoutException waiting) {
          /* keep observing Hop cancellation */
        }
      }
    } catch (Exception ex) {
      if (task != null) task.cancel(true);
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      logError("INTERLIS migration failed", ex);
      result.setNrErrors(result.getNrErrors() + 1);
    } finally {
      worker.shutdownNow();
      // Do not let a cancelled job continue into publication after the action has returned.
      boolean interrupted = Thread.interrupted();
      while (!worker.isTerminated()) {
        try {
          worker.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
          interrupted = true;
        }
      }
      if (interrupted) Thread.currentThread().interrupt();
    }
    return result;
  }

  @Override
  public String getDialogClassName() {
    return InterlisMigrationDialog.class.getName();
  }

  @Override
  public boolean isUnconditional() {
    return false;
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }
}
