package ch.so.agi.hop.interlis.transforms.validate;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxLogEvent;
import ch.interlis.iox.IoxLogging;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.IoxIliReader;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.utility.ReaderFactory;
import ch.interlis.iox_j.validator.ValidationConfig;
import ch.interlis.iox_j.validator.Validator;
import ch.interlis.iom_j.xtf.XtfStartTransferEvent;
import ch.so.agi.hop.interlis.core.io.InterlisValidationRowLayout;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import ch.so.agi.hop.interlis.transforms.InterlisParallelCopies;
import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Validate: validates an XTF transfer file with the iox-ili streaming validator and
 * emits one error row per finding.
 *
 * <p>The validation findings are collected through a custom {@link LogEventFactory}; the transfer
 * objects themselves are not emitted (use INTERLIS Transfer Input for that). Optional:
 * {@code stopOnFirstError}, {@code maxErrors}, severity filters and {@code failOnErrors} (fail the
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
    if (!data.initialized) {
      runValidation();
    }

    if (data.pendingRows != null && data.pendingRows.hasNext()) {
      data.rowsEmitted++;
      putRow(
          InterlisEnvelopeSchemaFactory.validationRowMeta(), data.pendingRows.next());
      return true;
    }

    setOutputDone();
    if (isBasic()) {
      logBasic("Finished validating " + meta.getFileName() + ": " + data.rowsEmitted + " rows emitted");
    }
    return false;
  }

  private void runValidation() throws HopException {
    InterlisParallelCopies.rejectParallelCopies(getCopy(), getTransformName());
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
        config.setConfigValue(ValidationConfig.PARAMETER, ValidationConfig.MULTIPLICITY,
            ValidationConfig.OFF);
      }

      CollectingLogFactory logFactory = new CollectingLogFactory();
      Validator validator =
          new Validator(td, config, logFactory, logFactory, new ch.interlis.iox_j.PipelinePool(),
              new ch.ehi.basics.settings.Settings());
      validator.setAutoSecondPass(false);

      List<IoxLogEvent> findings = new ArrayList<>();
      IoxEvent event = first;
      while (event != null) {
        validator.validate(event);
        findings.addAll(logFactory.drain());
        if (meta.isStopOnFirstError()
            && findings.stream().anyMatch(e -> e.getEventKind() == IoxLogEvent.ERROR)) {
          break;
        }
        if (meta.getMaxErrors() > 0 && findings.size() >= meta.getMaxErrors()) {
          break;
        }
        if (event instanceof ch.interlis.iox.EndTransferEvent) {
          break;
        }
        event = reader.read();
      }
      reader.close();

      List<Object[]> rows = new ArrayList<>();
      long errorCount = 0;
      for (IoxLogEvent finding : findings) {
        if (!accepted(finding)) {
          continue;
        }
        if (finding.getEventKind() == IoxLogEvent.ERROR) {
          errorCount++;
        }
        rows.add(toRow(finding));
        if (meta.getMaxErrors() > 0 && rows.size() >= meta.getMaxErrors()) {
          break;
        }
      }
      data.pendingRows = rows.iterator();
      if (meta.isFailOnErrors() && errorCount > 0) {
        throw new HopException(
            "INTERLIS validation found " + errorCount + " error(s) in " + file);
      }
      if (isBasic()) {
        logBasic("Validation of " + file + " produced " + rows.size() + " row(s)");
      }
      data.initialized = true;
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

  private Object[] toRow(IoxLogEvent event) {
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
    Long line =
        event.getSourceLineNr() == null ? null : event.getSourceLineNr().longValue();
    return new Object[] {
      severity,
      event.getEventMsg(),
      event.getDataSource(),
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
    if (resolved.isBlank()
        || ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta.MODELS_FROM_DATA.equals(
            resolved.trim())) {
      return List.of();
    }
    return Arrays.stream(resolved.split(","))
        .map(String::trim)
        .filter(n -> !n.isEmpty())
        .toList();
  }

  private List<String> resolveModelDirectories() {
    String resolved = resolve(meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return Arrays.stream(resolved.split(";"))
        .map(String::trim)
        .filter(d -> !d.isEmpty())
        .toList();
  }

  /** Collects validation findings and doubles as the no-op logger expected by the factory. */
  private static final class CollectingLogFactory extends LogEventFactory implements IoxLogging {

    private final List<IoxLogEvent> findings = new ArrayList<>();

    private CollectingLogFactory() {
      setLogger(this);
    }

    @Override
    public void addEvent(IoxLogEvent event) {
      findings.add(event);
    }

    List<IoxLogEvent> drain() {
      List<IoxLogEvent> drained = new ArrayList<>(findings);
      findings.clear();
      return drained;
    }
  }
}
