package ch.so.agi.hop.interlis.transforms.mapping;

import ch.so.agi.hop.interlis.core.buffer.SpillOptions;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.*;
import java.nio.file.Path;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.transform.BaseTransform;

/** Common model loading, safe staging, validation and pipeline-owned publication. */
public final class MappedSinkSession implements AutoCloseable {
  private final BaseTransform<?, ?> owner;
  private final MappedSinkSettings settings;
  public final InterlisModelContext model;
  public final PreparedXtfOutput output;
  private XtfTransferWriter writer;
  private boolean prepared;

  public MappedSinkSession(BaseTransform<?, ?> owner, MappedSinkSettings settings, Path original)
      throws Exception {
    this.owner = owner;
    this.settings = settings;
    InterlisParallelCopies.requireSingleCopy(
        owner.getTransformMeta(), owner, "INTERLIS file processing requires one copy");
    InterlisRuntimeSupport.initialize();
    model =
        new InterlisProjectionService()
            .loadModel(
                new InterlisModelRequest(
                    original,
                    InterlisModelSourceSupport.parseModelNames(
                        owner.resolve(settings.getModelNames())),
                    InterlisModelSourceSupport.parseModelDirectories(
                        owner.resolve(settings.getModelDirectories()))));
    String file = InterlisFieldBinding.resolve(owner, settings.getFileName());
    if (file.isBlank()) throw new HopException("INTERLIS output file is required");
    output = new PreparedXtfOutput(Path.of(file), settings.isOverwrite());
    try {
      InterlisPipelineCompletion.forPipeline(owner.getPipeline()).register(output, owner);
    } catch (Exception e) {
      output.close();
      throw e;
    }
  }

  public XtfTransferWriter openWriter(SpillOptions options) throws Exception {
    if (writer != null) throw new IllegalStateException("Writer already opened");
    writer =
        XtfTransferWriter.open(
            output.temporary(), model.model().transferDescription(), model.modelNames(), options);
    return writer;
  }

  public void finish() throws Exception {
    if (owner.isStopped()) throw new HopException("INTERLIS output was stopped");
    writer.requireComplete();
    closeWriter();
    if (settings.isValidateBeforePublish()) {
      var result =
          new InterlisValidationService()
              .validate(
                  output.temporary(),
                  model.model().transferDescription(),
                  InterlisFieldBinding.resolve(owner, settings.getValidationConfigFile()),
                  owner::isStopped,
                  finding -> {
                    if (finding.getEventKind() == ch.interlis.iox.IoxLogEvent.ERROR)
                      owner.logError(finding.getEventMsg());
                  });
      if (result.errors() > 0 || result.incomplete() != null)
        throw new HopException(
            "INTERLIS validation prevents publication: "
                + result.errors()
                + " error(s), "
                + result.incomplete());
    }
    if (owner.isStopped()) throw new HopException("INTERLIS output was stopped");
    output.prepare();
    prepared = true;
  }

  private void closeWriter() throws InterlisWriteException {
    var current = writer;
    writer = null;
    if (current != null) current.close();
  }

  public void close() throws Exception {
    try {
      closeWriter();
    } finally {
      if (!prepared) output.close();
    }
  }
}
