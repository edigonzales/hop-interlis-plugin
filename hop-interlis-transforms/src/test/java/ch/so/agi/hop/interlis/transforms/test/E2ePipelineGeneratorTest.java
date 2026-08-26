package ch.so.agi.hop.interlis.transforms.test;

import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileOutputMeta;
import org.apache.hop.pipeline.transforms.writetolog.WriteToLogMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Generates the XML form of the E2E demo pipelines into a directory given by the
 * {@code e2e.outputDir} system property (default: {@code target/e2e-output}).
 *
 * <p>With {@code -De2e.parameterized=true} the generated pipelines use
 * {@code ${E2E_INPUT_DIR}}/{@code ${E2E_OUTPUT_DIR}} variables instead of absolute paths so the
 * files can be committed and executed with {@code hop-run -p E2E_INPUT_DIR=... -p E2E_OUTPUT_DIR=...}.
 * Otherwise the input fixtures are taken from {@code e2e.inputDir} (default: the test resources
 * of this module).
 */
class E2ePipelineGeneratorTest {

  private static final String INPUT_GEOMETRY_XTF =
      "HopIli_Geometry_V1_valid.xtf";
  private static final String INPUT_SPIKE_XTF = "HopIli_Spike_V1_valid.xtf";
  private static final String OUTPUT_CSV = "interlis-input";
  private static final String OUTPUT_SPIKE_CSV = "interlis-input-structures";

  private static final boolean PARAMETERIZED =
      Boolean.getBoolean("e2e.parameterized");

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void generateE2ePipelines() throws Exception {
    Path inputDir = inputDirectory();
    Path outputDir = outputDirectory();
    Files.createDirectories(outputDir);

    write(outputDir.resolve("02-interlis-input-to-csv.hpl"),
        geometryToCsvPipeline("02-interlis-input-to-csv",
            inputFile(inputDir, INPUT_GEOMETRY_XTF), outputFile(outputDir, OUTPUT_CSV)));
    write(outputDir.resolve("03-interlis-input-structures.hpl"),
        spikeToCsvPipeline("03-interlis-input-structures",
            inputFile(inputDir, INPUT_SPIKE_XTF), outputFile(outputDir, OUTPUT_SPIKE_CSV)));
  }

  @Test
  void generatePhase0DemoPipeline() throws Exception {
    Path outputDir = outputDirectory();
    Files.createDirectories(outputDir);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("01-interlis-test");
    TransformMeta source =
        new TransformMeta("INTERLIS_TEST", "INTERLIS Test", new InterlisTestMeta());
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("Write to log", new WriteToLogMeta());
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));

    write(outputDir.resolve("01-interlis-test.hpl"), pipelineMeta);
  }

  private static String inputFile(Path inputDir, String name) {
    return PARAMETERIZED
        ? "${E2E_INPUT_DIR}/" + name
        : inputDir.resolve(name).toString();
  }

  private static String outputFile(Path outputDir, String name) {
    return PARAMETERIZED
        ? "${E2E_OUTPUT_DIR}/" + name
        : outputDir.resolve(name).toString();
  }

  private static Path outputDirectory() {
    return Path.of(System.getProperty("e2e.outputDir", "target/e2e-output")).toAbsolutePath();
  }

  private static Path inputDirectory() throws Exception {
    String configured = System.getProperty("e2e.inputDir");
    if (configured != null && !configured.isBlank()) {
      return Path.of(configured).toAbsolutePath();
    }
    // Default: test resources data directory of this module.
    Path data = Path.of(E2ePipelineGeneratorTest.class.getResource("/data/HopIli_Geometry_V1_valid.xtf").toURI());
    return data.getParent().toAbsolutePath();
  }

  private static PipelineMeta geometryToCsvPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Geometry_V1.Data.TestObject");
    input.setIncludeTid(true);
    input.setIncludeBid(true);

    return threeStepPipeline(
        pipelineMeta,
        input,
        stringifyGeometry(
            List.of("Center", "Points", "Axis", "Axes", "Boundary", "Area", "Surfaces")),
        csvOutput(outputFile));
  }

  private static PipelineMeta spikeToCsvPipeline(String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Spike_V1.Data.Building");
    input.setIncludeTid(true);
    input.setIncludeBid(true);

    return threeStepPipeline(
        pipelineMeta, input, stringifyGeometry(List.of("Location")),
        csvOutput(outputFile));
  }

  /**
   * A Select Values transform that converts the given geometry fields to their WKT string form,
   * because Text file output cannot render geometry values natively.
   */
  private static org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta
      stringifyGeometry(List<String> geometryFields) {
    org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta select =
        new org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta();
    org.apache.hop.pipeline.transforms.selectvalues.SelectOptions options =
        new org.apache.hop.pipeline.transforms.selectvalues.SelectOptions();
    options.setSelectingAndSortingUnspecifiedFields(false);
    List<org.apache.hop.pipeline.transforms.selectvalues.SelectField> selectFields =
        new java.util.ArrayList<>();
    List<org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange> metaChanges =
        new java.util.ArrayList<>();
    for (String geometryField : geometryFields) {
      org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange change =
          new org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange();
      change.setName(geometryField);
      change.setType("String");
      metaChanges.add(change);
    }
    // Empty select fields keep all fields in their original order.
    options.setSelectFields(selectFields);
    options.setMeta(metaChanges);
    select.setSelectOption(options);
    return select;
  }

  private static TextFileOutputMeta csvOutput(String fileName) {
    TextFileOutputMeta output = new TextFileOutputMeta();
    TextFileOutputMeta.FileSettings fileSettings = new TextFileOutputMeta.FileSettings();
    // fileName without extension: the writer appends "." + extension unless already present.
    fileSettings.setFileName(fileName);
    fileSettings.setExtension("csv");
    fileSettings.setPadded(false);
    output.setFileSettings(fileSettings);
    output.setSeparator(";");
    output.setEnclosure("");
    output.setFileFormat("DOS");
    output.setCreateParentFolder(true);
    output.setHeaderEnabled(true);
    output.setFileNameInField(false);
    return output;
  }

  private static PipelineMeta threeStepPipeline(
      PipelineMeta pipelineMeta,
      ITransformMeta inputMeta,
      ITransformMeta middleMeta,
      ITransformMeta outputMeta) {
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", inputMeta);
    source.setLocation(100, 100);
    TransformMeta middle = new TransformMeta("Select values", middleMeta);
    middle.setLocation(300, 100);
    TransformMeta sink = new TransformMeta("Text file output", outputMeta);
    sink.setLocation(500, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(middle);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, middle));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(middle, sink));
    return pipelineMeta;
  }

  private static void write(Path file, PipelineMeta pipelineMeta) throws Exception {
    Files.writeString(file, pipelineMeta.getXml(new Variables()) + "\n");
  }
}
