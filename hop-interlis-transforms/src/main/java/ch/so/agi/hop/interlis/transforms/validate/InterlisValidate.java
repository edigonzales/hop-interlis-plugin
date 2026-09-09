package ch.so.agi.hop.interlis.transforms.validate;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom_j.xtf.XtfStartTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxLogEvent;
import ch.interlis.iox.IoxLogging;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.IoxIliReader;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.utility.ReaderFactory;
import ch.interlis.iox_j.validator.ValidationConfig;
import ch.interlis.iox_j.validator.Validator;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisParallelCopies;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Validate: validates an XTF transfer file with the iox-ili streaming validator and emits
 * one error row per finding.
 *
 * <p>The validation findings are collected through a custom {@link LogEventFactory}; the transfer
 * objects themselves are not emitted (use INTERLIS Transfer Input for that). Optional: {@code
 * stopOnFirstError}, {@code maxErrors}, severity filters and {@code failOnErrors} (fail the
 * pipeline after emitting the rows).
 */
public class InterlisValidate extends BaseTransform<InterlisValidateMeta, InterlisValidateData> {

  public InterlisValidate(
      TransformMeta transformMeta,
      InterlisValidateMeta meta,
      InterlisValidateData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (data.phase == InterlisValidateData.Phase.VALIDATE) {
      runValidation();
    }

    if (data.pendingRows != null && data.pendingRows.hasNext()) {
      data.rowsEmitted++;
      putRow(InterlisEnvelopeSchemaFactory.validationRowMeta(), data.pendingRows.next());
      return true;
    }

    if (data.phase == InterlisValidateData.Phase.COMPLETE) return false;
    if (meta.isFailOnErrors() && data.errorCount > 0) {
      long errors = data.errorCount;
      // Hop's transform-finished listener stops the pipeline on a transform error counter.
      // Defer that counter until all consumers have finished, including their final flush.
      getPipeline().addExecutionFinishedListener(pipeline -> setErrors(getErrors() + errors));
    }
    data.phase = InterlisValidateData.Phase.COMPLETE;
    setOutputDone();
    if (isBasic()) {
      logBasic(
          "Finished validating " + meta.getFileName() + ": " + data.rowsEmitted + " rows emitted");
    }
    return false;
  }

  private void runValidation() throws HopException {
    InterlisParallelCopies.requireSingleCopy(
        getTransformMeta(), this, "file processing or enumeration emission requires one copy");
    if (meta.getMaxErrors() < 0)
      throw new HopException("maxErrors must be zero (unlimited) or positive");
    InterlisRuntimeSupport.initialize();

    String resolvedFile = resolve(meta.getFileName());
    if (resolvedFile.isBlank()) {
      throw new HopException("INTERLIS transfer file is not configured");
    }
    Path file = Path.of(resolvedFile);
    if (!java.nio.file.Files.isRegularFile(file)) {
      throw new HopException("INTERLIS transfer file does not exist: " + file);
    }
    try {
      ch.so.agi.hop.interlis.core.io.XtfTransferReader.rejectUnsupportedFormat(file);
    } catch (ch.so.agi.hop.interlis.core.io.InterlisReadException e) {
      throw new HopException(e.getMessage(), e);
    }

    try {
      IoxReader reader = new ReaderFactory().createReader(file.toFile(), null);
      try (AutoCloseable readerResource = reader::close) {
        IoxEvent first = reader.read();
        List<String> modelNames = resolveModelNames();
        if (modelNames.isEmpty() && first instanceof XtfStartTransferEvent startTransfer) {
          modelNames = XtfTransferReader.detectModelNames(startTransfer.getHeaderObjects());
        }
        if (modelNames.isEmpty()) {
          throw new HopException(
              "No INTERLIS models configured and none could be detected from the transfer file");
        }
        List<String> modelDirectories = new ArrayList<>(resolveModelDirectories());
        if (modelDirectories.contains("%XTF_DIR")) {
          modelDirectories.replaceAll(
              d -> "%XTF_DIR".equals(d) ? file.toAbsolutePath().getParent().toString() : d);
        }

        InterlisModelService modelService = new InterlisModelServiceImpl();
        CompiledInterlisModel model =
            modelService.compile(
                new ModelSource(List.of(), modelNames, modelDirectories),
                ModelCompileOptions.defaults());
        TransferDescription td = model.transferDescription();
        if (reader instanceof IoxIliReader ioxIliReader) {
          ioxIliReader.setModel(td);
        }

        ValidationConfig config = new ValidationConfig();
        config.mergeIliMetaAttrs(td);
        String configFile = resolve(meta.getConfigFile() == null ? "" : meta.getConfigFile());
        if (!configFile.isBlank()) {
          config.mergeConfigFile(new java.io.File(configFile));
        }
        if (!meta.isValidateMultiplicity()) {
          config.setConfigValue(
              ValidationConfig.PARAMETER, ValidationConfig.MULTIPLICITY, ValidationConfig.OFF);
        }

        long limit = meta.isStopOnFirstError() ? 1 : meta.getMaxErrors();
        CollectingLogFactory logFactory = new CollectingLogFactory(limit, this::isStopped);
        String incomplete = null;
        Validator validator =
            new Validator(
                td,
                config,
                logFactory,
                logFactory,
                new ch.interlis.iox_j.PipelinePool(),
                new ch.ehi.basics.settings.Settings());
        try (AutoCloseable validatorResource = validator::close) {
          validator.setAutoSecondPass(false);
          try {
            logFactory.begin();
            IoxEvent event = first;
            boolean completeTransfer = false;
            while (event != null) {
              logFactory.checkCancelled();
              validator.validate(event);
              if (event instanceof ch.interlis.iox.EndTransferEvent) {
                completeTransfer = true;
                break;
              }
              event = reader.read();
            }
            if (!completeTransfer)
              throw new HopException("Unexpected end of INTERLIS transfer: " + file);
            logFactory.checkCancelled();
            // The merged validator configuration also governs these cross-object checks.
            validator.doSecondPass();
          } catch (ValidationAborted e) {
            incomplete = e.getMessage();
          }
        }
        List<Object[]> rows = new ArrayList<>();
        for (IoxLogEvent finding : logFactory.findings) {
          if (accepted(finding)) rows.add(toRow(finding, file));
        }
        if (incomplete != null) {
          rows.add(
              new Object[] {
                "WARNING",
                "Validation incomplete: "
                    + incomplete
                    + "; "
                    + logFactory.errorCount
                    + " error(s) reached",
                file.toString(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "INTERLIS_VALIDATION_INCOMPLETE",
                null
              });
        }
        data.errorCount = logFactory.errorCount;
        data.pendingRows = rows.iterator();
        data.phase = InterlisValidateData.Phase.EMIT;
        if (isBasic()) logBasic("Validation of " + file + " produced " + rows.size() + " row(s)");
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Failed to validate INTERLIS transfer: " + e.getMessage(), e);
    }
  }

  private boolean accepted(IoxLogEvent event) {
    return switch (event.getEventKind()) {
      case IoxLogEvent.ERROR -> true;
      case IoxLogEvent.WARNING -> meta.isIncludeWarnings();
      case IoxLogEvent.INFO -> meta.isIncludeInfo();
      default -> meta.isIncludeInfo();
    };
  }

  private Object[] toRow(IoxLogEvent event, Path file) {
    String severity =
        switch (event.getEventKind()) {
          case IoxLogEvent.ERROR -> "ERROR";
          case IoxLogEvent.WARNING -> "WARNING";
          case IoxLogEvent.INFO -> "INFO";
          default -> "DETAIL_INFO";
        };
    String className = event.getSourceObjectTag();
    String modelName = modelOf(className != null ? className : event.getModelEleQName());
    String topicName = topicOf(className);
    Long line = event.getSourceLineNr() == null ? null : event.getSourceLineNr().longValue();
    return new Object[] {
      severity,
      event.getEventMsg(),
      event.getDataSource() == null ? file.toString() : event.getDataSource(),
      line,
      null,
      modelName,
      topicName,
      null,
      className,
      event.getSourceObjectXtfId(),
      event.getModelEleQName(),
      event.getEventId(),
      null
    };
  }

  private static String modelOf(String scopedName) {
    if (scopedName == null) {
      return null;
    }
    int separator = scopedName.indexOf('.');
    return separator < 0 ? scopedName : scopedName.substring(0, separator);
  }

  private static String topicOf(String scopedName) {
    if (scopedName == null) {
      return null;
    }
    int first = scopedName.indexOf('.');
    int second = scopedName.indexOf('.', first + 1);
    if (first < 0 || second < 0) {
      return null;
    }
    return scopedName.substring(0, second);
  }

  private List<String> resolveModelNames() {
    String resolved = resolve(meta.getModelNames());
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private List<String> resolveModelDirectories() {
    String resolved = resolve(meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return InterlisModelSourceSupport.parseModelDirectories(resolved);
  }

  /** A private control signal, caught only at the validation boundary (also in the second pass). */
  private static final class ValidationAborted extends RuntimeException {
    ValidationAborted(String reason) {
      super(reason, null, false, false);
    }
  }

  static final class CollectingLogFactory extends LogEventFactory implements IoxLogging {
    private final List<IoxLogEvent> findings = new ArrayList<>();
    private final long limit;
    private final java.util.function.BooleanSupplier cancelled;
    private long errorCount;
    private ValidationAborted aborted;
    private boolean active;

    CollectingLogFactory(long limit, java.util.function.BooleanSupplier cancelled) {
      this.limit = limit;
      this.cancelled = cancelled;
      setLogger(this);
    }

    void begin() {
      active = true;
      checkCancelled();
      checkLimit();
    }

    private void checkLimit() {
      if (limit > 0 && errorCount >= limit) {
        aborted = new ValidationAborted("error limit " + limit + " reached");
        if (active) throw aborted;
      }
    }

    void checkCancelled() {
      if (aborted != null) throw aborted;
      if (cancelled.getAsBoolean()) {
        aborted = new ValidationAborted("user cancellation");
        throw aborted;
      }
    }

    @Override
    public void addEvent(IoxLogEvent event) {
      if (active) checkCancelled();
      if (aborted != null) return; // Constructor diagnostics stop at the same limit.
      findings.add(event);
      if (event.getEventKind() == IoxLogEvent.ERROR) {
        errorCount++;
        checkLimit();
      }
    }
  }
}
