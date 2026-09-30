package ch.so.agi.hop.interlis.core.io;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.*;
import ch.interlis.iox_j.IoxIliReader;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.utility.ReaderFactory;
import ch.interlis.iox_j.validator.*;
import java.nio.file.Path;
import java.util.function.*;

/** Shared full-transfer validation, including cancellation, error limits and the second pass. */
public final class InterlisValidationService {
  public record Result(long errors, String incomplete) {}

  private static final class Aborted extends RuntimeException {
    Aborted(String message) {
      super(message, null, false, false);
    }
  }

  private static final class Diagnostics extends LogEventFactory implements IoxLogging {
    final Consumer<IoxLogEvent> sink;
    final BooleanSupplier cancelled;
    final long limit;
    long errors;
    boolean active;

    Diagnostics(Consumer<IoxLogEvent> sink, BooleanSupplier cancelled, long limit) {
      this.sink = sink;
      this.cancelled = cancelled;
      this.limit = limit;
      setLogger(this);
    }

    void check() {
      if (cancelled.getAsBoolean()) throw new Aborted("user cancellation");
      if (limit > 0 && errors >= limit) throw new Aborted("error limit " + limit + " reached");
    }

    public void addEvent(IoxLogEvent event) {
      if (active) check();
      if (limit > 0 && errors >= limit) return;
      sink.accept(event);
      if (event.getEventKind() == IoxLogEvent.ERROR) {
        errors++;
        if (active) check();
      }
    }
  }

  public Result validate(
      Path file,
      TransferDescription model,
      String configFile,
      BooleanSupplier cancelled,
      Consumer<IoxLogEvent> findings)
      throws Exception {
    XtfTransferReader.rejectUnsupportedFormat(file);
    IoxReader reader = new ReaderFactory().createReader(file.toFile(), null);
    try (AutoCloseable resource = reader::close) {
      if (reader instanceof IoxIliReader ili) ili.setModel(model);
      var config = new ValidationConfig();
      config.mergeIliMetaAttrs(model);
      if (configFile != null && !configFile.isBlank())
        config.mergeConfigFile(new java.io.File(configFile));
      return validate(reader, reader.read(), model, config, 0, cancelled, findings);
    }
  }

  public Result validate(
      IoxReader reader,
      IoxEvent first,
      TransferDescription model,
      ValidationConfig config,
      long limit,
      BooleanSupplier cancelled,
      Consumer<IoxLogEvent> findings)
      throws Exception {
    var diagnostics = new Diagnostics(findings, cancelled, limit);
    var references = new IoxReferenceCanonicalizer(model);
    var validator =
        new Validator(
            model,
            config,
            diagnostics,
            diagnostics,
            new ch.interlis.iox_j.PipelinePool(),
            new ch.ehi.basics.settings.Settings());
    String incomplete = null;
    try (AutoCloseable resource = validator::close) {
      validator.setAutoSecondPass(false);
      diagnostics.active = true;
      try {
        diagnostics.check();
        boolean complete = false;
        for (IoxEvent event = first; event != null; event = reader.read()) {
          diagnostics.check();
          validator.validate(references.repair(event));
          if (event instanceof ch.interlis.iox.EndTransferEvent) {
            complete = true;
            break;
          }
        }
        if (!complete) throw new IoxException("Unexpected end of INTERLIS transfer");
        diagnostics.check();
        validator.doSecondPass();
        diagnostics.check();
      } catch (Aborted stop) {
        incomplete = stop.getMessage();
      }
    }
    return new Result(diagnostics.errors, incomplete);
  }
}
