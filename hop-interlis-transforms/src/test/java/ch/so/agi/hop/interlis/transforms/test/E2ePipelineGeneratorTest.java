package ch.so.agi.hop.interlis.transforms.test;

import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.output.InterlisOutputMeta;
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
    write(outputDir.resolve("05-xtf-roundtrip.hpl"),
        roundtripPipeline("05-xtf-roundtrip",
            inputFile(inputDir, INPUT_GEOMETRY_XTF), outputFile(outputDir, "roundtrip.xtf")));
    write(outputDir.resolve("06-roundtrip-check.hpl"),
        roundtripCheckPipeline("06-roundtrip-check",
            outputFile(outputDir, "roundtrip.xtf"), outputFile(outputDir, "interlis-roundtrip")));
    write(outputDir.resolve("07-structures-roundtrip.hpl"),
        structuresRoundtripPipeline("07-structures-roundtrip",
            inputFile(inputDir, "HopIli_Structures_V1_valid.xtf"),
            outputFile(outputDir, "structures-roundtrip.xtf")));
    write(outputDir.resolve("08-structures-roundtrip-check.hpl"),
        structuresRoundtripCheckPipeline("08-structures-roundtrip-check",
            outputFile(outputDir, "structures-roundtrip.xtf"),
            outputFile(outputDir, "interlis-structures-roundtrip")));
    write(outputDir.resolve("09-associations-roundtrip.hpl"),
        associationsRoundtripPipeline("09-associations-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_valid.xtf"),
            outputFile(outputDir, "associations-roundtrip.xtf")));
    write(outputDir.resolve("10-associations-roundtrip-check.hpl"),
        associationsRoundtripCheckPipeline("10-associations-roundtrip-check",
            outputFile(outputDir, "associations-roundtrip.xtf"),
            outputFile(outputDir, "interlis-associations-roundtrip")));
    write(outputDir.resolve("11-association-rows-roundtrip.hpl"),
        associationRowsRoundtripPipeline("11-association-rows-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_valid.xtf"),
            outputFile(outputDir, "association-rows-roundtrip.xtf")));
    write(outputDir.resolve("12-association-rows-roundtrip-check.hpl"),
        associationRowsRoundtripCheckPipeline("12-association-rows-roundtrip-check",
            outputFile(outputDir, "association-rows-roundtrip.xtf"),
            outputFile(outputDir, "interlis-association-rows-roundtrip")));
    write(outputDir.resolve("13-generic-transfer-roundtrip.hpl"),
        genericTransferRoundtripPipeline("13-generic-transfer-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_valid.xtf"),
            outputFile(outputDir, "generic-transfer-roundtrip.xtf")));
    write(outputDir.resolve("14-generic-transfer-check.hpl"),
        genericTransferCheckPipeline("14-generic-transfer-check",
            outputFile(outputDir, "generic-transfer-roundtrip.xtf"),
            outputFile(outputDir, "interlis-generic-transfer")));
    write(outputDir.resolve("15-generic-delete-roundtrip.hpl"),
        genericDeleteRoundtripPipeline("15-generic-delete-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_delete.xtf"),
            outputFile(outputDir, "generic-delete-roundtrip.xtf")));
    write(outputDir.resolve("16-generic-delete-check.hpl"),
        genericDeleteCheckPipeline("16-generic-delete-check",
            outputFile(outputDir, "generic-delete-roundtrip.xtf"),
            outputFile(outputDir, "interlis-generic-delete")));
    write(outputDir.resolve("17-validate.hpl"),
        validatePipeline("17-validate",
            inputFile(inputDir, "HopIli_Enums_V1_invalid.xtf"),
            outputFile(outputDir, "interlis-validate")));
    write(outputDir.resolve("18-enumerations.hpl"),
        enumerationsPipeline("18-enumerations",
            outputFile(outputDir, "interlis-enumerations")));
    write(outputDir.resolve("19-delete-roundtrip.hpl"),
        deleteRoundtripPipeline("19-delete-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_delete.xtf"),
            outputFile(outputDir, "delete-roundtrip.xtf")));
    write(outputDir.resolve("20-delete-check.hpl"),
        deleteCheckPipeline("20-delete-check",
            outputFile(outputDir, "delete-roundtrip.xtf"),
            outputFile(outputDir, "interlis-delete-roundtrip")));
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

  private static PipelineMeta roundtripPipeline(String name, String inputFile, String outputFile) {
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

    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setFileName(outputFile);
    output.setModelNames("HopIli_Geometry_V1");
    output.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    output.setClassName("HopIli_Geometry_V1.Data.TestObject");
    output.setObjectIdField("_ili_tid");
    output.setBasketIdField("_ili_bid");
    output.setBasketId("b1");
    output.setOverwrite(true);

    return twoStepPipeline(pipelineMeta, input, output);
  }

  private static PipelineMeta roundtripCheckPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames("HopIli_Geometry_V1");
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

  private static PipelineMeta structuresRoundtripPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta input =
        new ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Structures_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    input.setKeepSourceObject(true);

    ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta explode =
        new ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta();
    explode.setDefault();
    explode.setModelNames("HopIli_Structures_V1");
    explode.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    explode.setClassName("HopIli_Structures_V1.Data.Person");
    explode.setStructureAttributePath("Addresses");

    ch.so.agi.hop.interlis.transforms.collect.InterlisStructureCollectMeta collect =
        new ch.so.agi.hop.interlis.transforms.collect.InterlisStructureCollectMeta();
    collect.setDefault();
    collect.setParentInputTransform("INTERLIS Input");
    collect.setChildInputTransform("INTERLIS Structure Explode");
    collect.setModelNames("HopIli_Structures_V1");
    collect.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    collect.setClassName("HopIli_Structures_V1.Data.Person");
    collect.setStructureAttributePath("Addresses");

    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setFileName(outputFile);
    output.setModelNames("HopIli_Structures_V1");
    output.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    output.setClassName("HopIli_Structures_V1.Data.Person");
    output.setObjectIdField("_ili_tid");
    output.setBasketIdField("_ili_bid");
    output.setBasketId("b1");
    output.setSourceObjectField(
        ch.so.agi.hop.interlis.transforms.collect.InterlisStructureCollectMeta
            .DEFAULT_SOURCE_OBJECT_FIELD);
    output.setOverwrite(true);

    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    source.setLocation(100, 100);
    // Fan-out: the input feeds both the explode transform and the collect transform.
    // Hop distributes rows round-robin by default; disable it so all rows reach both targets.
    source.setDistributes(false);
    TransformMeta explodeTransform =
        new TransformMeta("INTERLIS_STRUCTURE_EXPLODE", "INTERLIS Structure Explode", explode);
    explodeTransform.setLocation(300, 100);
    TransformMeta collectTransform =
        new TransformMeta("INTERLIS_STRUCTURE_COLLECT", "INTERLIS Structure Collect", collect);
    collectTransform.setLocation(500, 100);
    TransformMeta sink = new TransformMeta("INTERLIS Output", output);
    sink.setLocation(700, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(explodeTransform);
    pipelineMeta.addTransform(collectTransform);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, explodeTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, collectTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(explodeTransform, collectTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(collectTransform, sink));
    return pipelineMeta;
  }

  private static PipelineMeta structuresRoundtripCheckPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames("HopIli_Structures_V1");
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Structures_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    input.setKeepSourceObject(true);

    ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta explode =
        new ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta();
    explode.setDefault();
    explode.setModelNames("HopIli_Structures_V1");
    explode.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    explode.setClassName("HopIli_Structures_V1.Data.Person");
    explode.setStructureAttributePath("Addresses");

    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    source.setLocation(100, 100);
    TransformMeta explodeTransform =
        new TransformMeta("INTERLIS_STRUCTURE_EXPLODE", "INTERLIS Structure Explode", explode);
    explodeTransform.setLocation(300, 100);
    TransformMeta stringify = new TransformMeta("Select values", stringifyGeometry(List.of("Location")));
    stringify.setLocation(500, 100);
    TransformMeta sink = new TransformMeta("Text file output", csvOutput(outputFile));
    sink.setLocation(700, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(explodeTransform);
    pipelineMeta.addTransform(stringify);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, explodeTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(explodeTransform, stringify));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(stringify, sink));
    return pipelineMeta;
  }

  private static PipelineMeta twoStepPipeline(
      PipelineMeta pipelineMeta, ITransformMeta inputMeta, ITransformMeta outputMeta) {
    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", inputMeta);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("INTERLIS Output", outputMeta);
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static PipelineMeta associationsRoundtripPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Associations_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);

    InterlisOutputMeta output = associationOutput(outputFile, "HopIli_Associations_V1.Data.Person",
        inputFile);
    return twoStepPipeline(pipelineMeta, input, output);
  }

  private static PipelineMeta associationsRoundtripCheckPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames("HopIli_Associations_V1");
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Associations_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);

    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("Text file output", csvOutput(outputFile));
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static PipelineMeta associationRowsRoundtripPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Associations_V1.Data.PersonTask");
    input.setIncludeTid(true);
    input.setIncludeBid(true);

    InterlisOutputMeta output =
        associationOutput(outputFile, "HopIli_Associations_V1.Data.PersonTask", inputFile);
    return twoStepPipeline(pipelineMeta, input, output);
  }

  private static PipelineMeta associationRowsRoundtripCheckPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames("HopIli_Associations_V1");
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Associations_V1.Data.PersonTask");
    input.setIncludeTid(true);
    input.setIncludeBid(true);

    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("Text file output", csvOutput(outputFile));
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static InterlisOutputMeta associationOutput(
      String outputFile, String className, String inputFile) {
    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setFileName(outputFile);
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    output.setClassName(className);
    output.setObjectIdField("_ili_tid");
    output.setBasketIdField("_ili_bid");
    output.setBasketId("b1");
    output.setOverwrite(true);
    return output;
  }

  private static PipelineMeta genericTransferRoundtripPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta input =
        genericTransferInput(inputFile, "EVENTS");

    ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta output =
        genericTransferOutput(outputFile, inputFile, true);

    TransformMeta source =
        new TransformMeta("INTERLIS_TRANSFER_INPUT", "INTERLIS Transfer Input", input);
    source.setLocation(100, 100);
    TransformMeta sink =
        new TransformMeta("INTERLIS Transfer Output", output);
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static PipelineMeta genericTransferCheckPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta input =
        genericTransferInput(inputFile, "OBJECTS");

    TransformMeta source =
        new TransformMeta("INTERLIS_TRANSFER_INPUT", "INTERLIS Transfer Input", input);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("Text file output", csvOutput(outputFile));
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static PipelineMeta genericDeleteRoundtripPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta input =
        genericTransferInput(inputFile, "EVENTS");
    ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta output =
        genericTransferOutput(outputFile, inputFile, true);

    TransformMeta source =
        new TransformMeta("INTERLIS_TRANSFER_INPUT", "INTERLIS Transfer Input", input);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("INTERLIS Transfer Output", output);
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static PipelineMeta genericDeleteCheckPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta input =
        genericTransferInput(inputFile, "OBJECTS");

    TransformMeta source =
        new TransformMeta("INTERLIS_TRANSFER_INPUT", "INTERLIS Transfer Input", input);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("Text file output", csvOutput(outputFile));
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta
      genericTransferInput(String inputFile, String mode) {
    ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta input =
        new ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta();
    input.setFileName(inputFile);
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setMode(mode);
    return input;
  }

  private static ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta
      genericTransferOutput(String outputFile, String inputFile, boolean eventMode) {
    ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta output =
        new ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta();
    output.setFileName(outputFile);
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    output.setOverwrite(true);
    output.setEventMode(eventMode);
    return output;
  }

  private static PipelineMeta validatePipeline(String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    ch.so.agi.hop.interlis.transforms.validate.InterlisValidateMeta validate =
        new ch.so.agi.hop.interlis.transforms.validate.InterlisValidateMeta();
    validate.setFileName(inputFile);
    validate.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    validate.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());

    TransformMeta source = new TransformMeta("INTERLIS_VALIDATE", "INTERLIS Validate", validate);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("Text file output", csvOutput(outputFile));
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static PipelineMeta enumerationsPipeline(String name, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    ch.so.agi.hop.interlis.transforms.enumerations.InterlisEnumerationsMeta enumerations =
        new ch.so.agi.hop.interlis.transforms.enumerations.InterlisEnumerationsMeta();
    enumerations.setModelNames("HopIli_Enums_V1");
    enumerations.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : TestDataDirectory());

    TransformMeta source =
        new TransformMeta("INTERLIS_ENUMERATIONS", "INTERLIS Enumerations", enumerations);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("Text file output", csvOutput(outputFile));
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static PipelineMeta deleteRoundtripPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Associations_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    input.setIncludeOperation(true);

    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setFileName(outputFile);
    output.setModelNames("HopIli_Associations_V1");
    output.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    output.setClassName("HopIli_Associations_V1.Data.Person");
    output.setObjectIdField("_ili_tid");
    output.setBasketIdField("_ili_bid");
    output.setBasketId("b1");
    output.setOperationField("_ili_operation");
    output.setOverwrite(true);

    return twoStepPipeline(pipelineMeta, input, output);
  }

  private static PipelineMeta deleteCheckPipeline(
      String name, String inputFile, String outputFile) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(name);

    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(inputFile);
    input.setModelNames("HopIli_Associations_V1");
    input.setModelDirectories(
        PARAMETERIZED ? "${E2E_INPUT_DIR}" : Path.of(inputFile).getParent().toString());
    input.setClassName("HopIli_Associations_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    input.setIncludeOperation(true);

    TransformMeta source = new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", input);
    source.setLocation(100, 100);
    TransformMeta sink = new TransformMeta("Text file output", csvOutput(outputFile));
    sink.setLocation(300, 100);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, sink));
    return pipelineMeta;
  }

  private static String TestDataDirectory() {
    if (PARAMETERIZED) {
      return "${E2E_INPUT_DIR}";
    }
    try {
      return Path.of(
              E2ePipelineGeneratorTest.class.getResource("/models/HopIli_Enums_V1.ili").toURI())
          .getParent()
          .toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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
