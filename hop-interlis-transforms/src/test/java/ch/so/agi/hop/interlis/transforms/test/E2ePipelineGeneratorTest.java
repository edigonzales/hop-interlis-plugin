package ch.so.agi.hop.interlis.transforms.test;

import ch.so.agi.hop.interlis.transforms.TestData;
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
 * Generates the XML form of the E2E demo pipelines into a directory given by the {@code
 * e2e.outputDir} system property (default: {@code target/e2e-output}).
 *
 * <p>With {@code -De2e.parameterized=true} the generated pipelines use {@code
 * ${E2E_INPUT_DIR}}/{@code ${E2E_OUTPUT_DIR}} variables instead of absolute paths so the files can
 * be committed and executed with {@code hop-run -p E2E_INPUT_DIR=... -p E2E_OUTPUT_DIR=...}.
 * Otherwise the input fixtures are taken from {@code e2e.inputDir} (default: the test resources of
 * this module).
 */
class E2ePipelineGeneratorTest {

  private static final String INPUT_GEOMETRY_XTF = "HopIli_Geometry_V1_valid.xtf";
  private static final String INPUT_SPIKE_XTF = "HopIli_Spike_V1_valid.xtf";
  private static final String OUTPUT_CSV = "interlis-input";
  private static final String OUTPUT_SPIKE_CSV = "interlis-input-structures";

  private static final boolean PARAMETERIZED = Boolean.getBoolean("e2e.parameterized");

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void generateE2ePipelines() throws Exception {
    Path inputDir = inputDirectory();
    Path outputDir = outputDirectory();
    Files.createDirectories(outputDir);

    write(
        outputDir.resolve("02-interlis-input-to-csv.hpl"),
        geometryToCsvPipeline(
            "02-interlis-input-to-csv",
            inputFile(inputDir, INPUT_GEOMETRY_XTF),
            outputFile(outputDir, OUTPUT_CSV)));
    write(
        outputDir.resolve("03-interlis-input-structures.hpl"),
        spikeToCsvPipeline(
            "03-interlis-input-structures",
            inputFile(inputDir, INPUT_SPIKE_XTF),
            outputFile(outputDir, OUTPUT_SPIKE_CSV)));
    write(
        outputDir.resolve("05-xtf-roundtrip.hpl"),
        roundtripPipeline(
            "05-xtf-roundtrip",
            inputFile(inputDir, INPUT_GEOMETRY_XTF),
            outputFile(outputDir, "roundtrip.xtf")));
    write(
        outputDir.resolve("06-roundtrip-check.hpl"),
        roundtripCheckPipeline(
            "06-roundtrip-check",
            outputFile(outputDir, "roundtrip.xtf"),
            outputFile(outputDir, "interlis-roundtrip")));
    write(
        outputDir.resolve("07-structures-roundtrip.hpl"),
        structuresRoundtripPipeline(
            "07-structures-roundtrip",
            inputFile(inputDir, "HopIli_Structures_V1_valid.xtf"),
            outputFile(outputDir, "structures-roundtrip.xtf")));
    write(
        outputDir.resolve("08-structures-roundtrip-check.hpl"),
        structuresRoundtripCheckPipeline(
            "08-structures-roundtrip-check",
            outputFile(outputDir, "structures-roundtrip.xtf"),
            outputFile(outputDir, "interlis-structures-roundtrip")));
    write(
        outputDir.resolve("09-associations-roundtrip.hpl"),
        associationsRoundtripPipeline(
            "09-associations-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_mapping.xtf"),
            outputFile(outputDir, "associations-roundtrip.xtf")));
    write(
        outputDir.resolve("10-associations-roundtrip-check.hpl"),
        associationsRoundtripCheckPipeline(
            "10-associations-roundtrip-check",
            outputFile(outputDir, "associations-roundtrip.xtf"),
            outputFile(outputDir, "interlis-associations-roundtrip")));
    write(
        outputDir.resolve("11-association-rows-roundtrip.hpl"),
        associationRowsRoundtripPipeline(
            "11-association-rows-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_mapping.xtf"),
            outputFile(outputDir, "association-rows-roundtrip.xtf")));
    write(
        outputDir.resolve("12-association-rows-roundtrip-check.hpl"),
        associationRowsRoundtripCheckPipeline(
            "12-association-rows-roundtrip-check",
            outputFile(outputDir, "association-rows-roundtrip.xtf"),
            outputFile(outputDir, "interlis-association-rows-roundtrip")));
    write(
        outputDir.resolve("13-generic-transfer-roundtrip.hpl"),
        genericTransferRoundtripPipeline(
            "13-generic-transfer-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_mapping.xtf"),
            outputFile(outputDir, "generic-transfer-roundtrip.xtf")));
    write(
        outputDir.resolve("14-generic-transfer-check.hpl"),
        genericTransferCheckPipeline(
            "14-generic-transfer-check",
            outputFile(outputDir, "generic-transfer-roundtrip.xtf"),
            outputFile(outputDir, "interlis-generic-transfer")));
    write(
        outputDir.resolve("15-generic-delete-roundtrip.hpl"),
        genericDeleteRoundtripPipeline(
            "15-generic-delete-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_delete.xtf"),
            outputFile(outputDir, "generic-delete-roundtrip.xtf")));
    write(
        outputDir.resolve("16-generic-delete-check.hpl"),
        genericDeleteCheckPipeline(
            "16-generic-delete-check",
            outputFile(outputDir, "generic-delete-roundtrip.xtf"),
            outputFile(outputDir, "interlis-generic-delete")));
    write(
        outputDir.resolve("17-validate.hpl"),
        validatePipeline(
            "17-validate",
            inputFile(inputDir, "HopIli_Enums_V1_invalid.xtf"),
            outputFile(outputDir, "interlis-validate")));
    write(
        outputDir.resolve("18-enumerations.hpl"),
        enumerationsPipeline("18-enumerations", outputFile(outputDir, "interlis-enumerations")));
    write(
        outputDir.resolve("19-delete-roundtrip.hpl"),
        deleteRoundtripPipeline(
            "19-delete-roundtrip",
            inputFile(inputDir, "HopIli_Associations_V1_delete.xtf"),
            outputFile(outputDir, "delete-roundtrip.xtf")));
    write(
        outputDir.resolve("20-delete-check.hpl"),
        deleteCheckPipeline(
            "20-delete-check",
            outputFile(outputDir, "delete-roundtrip.xtf"),
            outputFile(outputDir, "interlis-delete-roundtrip")));
  }

  @Test
  void generateP1PipelinesAndFixtures() throws Exception {
    Path out = outputDirectory();
    Files.createDirectories(out);
    String dirs = PARAMETERIZED ? "${E2E_INPUT_DIR}" : TestDataDirectory();
    Path fixtures = PARAMETERIZED ? out.getParent().resolve("fixtures") : out;
    Files.createDirectories(fixtures);
    var projection =
        new ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService()
            .project(
                new ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest(
                    null,
                    List.of("HopIli_P1_V1"),
                    List.of(ch.so.agi.hop.interlis.transforms.TestData.path("/models").toString())),
                "HopIli_P1_V1.Data.Item",
                ch.so.agi.hop.interlis.core.mapping.ProjectionOptions.defaults());
    var gf = new org.locationtech.jts.geom.GeometryFactory();
    var wkt = new org.locationtech.jts.io.WKTReader(gf);
    var geometries =
        java.util.Map.of(
            "Location",
            "POINT Z (1 2 3)",
            "Axis",
            "LINESTRING Z (1 2 3, 4 5 6)",
            "Face",
            "POLYGON Z ((1 1 3, 4 1 3, 4 4 3, 1 1 3))",
            "Axes",
            "MULTILINESTRING Z ((1 2 3, 4 5 6))",
            "Faces",
            "MULTIPOLYGON Z (((1 1 3, 4 1 3, 4 4 3, 1 1 3)))");
    for (boolean invalid : new boolean[] {false, true}) {
      Path file = fixtures.resolve(invalid ? "p1-missing-target.xtf" : "p1-3d.xtf");
      try (var writer =
          ch.so.agi.hop.interlis.core.io.XtfTransferWriter.open(
              file, projection.model().transferDescription(), projection.modelNames())) {
        writer.startTransfer("P1 regression");
        writer.startBasket("HopIli_P1_V1.Data", "b1");
        for (int n = 0; n < (invalid ? 10 : 1); n++) {
          Object[] values = new Object[projection.plan().fieldCount()];
          for (var field : projection.plan().fields()) {
            values[field.outputIndex()] =
                switch (field.hopFieldName()) {
                  case "_ili_tid" -> "i" + n;
                  case "_ili_bid" -> "b1";
                  case "Name" -> "before" + n;
                  case "Details_Code" -> invalid ? null : "new-id";
                  case "Details_Note" -> invalid ? null : "updated";
                  case "Target_ref" -> invalid ? "absent" + n : null;
                  default ->
                      !invalid && geometries.containsKey(field.hopFieldName())
                          ? wkt.read(geometries.get(field.hopFieldName()))
                          : null;
                };
          }
          var object =
              new ch.so.agi.hop.interlis.core.mapping.RowToIomMapper()
                  .map(
                      values,
                      projection.plan(),
                      ch.so.agi.hop.interlis.core.mapping.RowWriteOptions.defaults());
          if (!invalid) {
            var child = new ch.interlis.iom_j.Iom_jObject("HopIli_P1_V1.Data.Detail", null);
            child.setattrvalue("Code", "keep-child");
            object.addattrobj("Children", child);
          }
          writer.writeObject(object);
        }
        writer.endBasket();
        writer.endTransfer();
      }
    }
    String fixture =
        PARAMETERIZED ? "${E2E_INPUT_DIR}/p1-3d.xtf" : fixtures.resolve("p1-3d.xtf").toString();
    InterlisInputMeta input = new InterlisInputMeta();
    input.setDefault();
    input.setFileName(fixture);
    input.setModelNames("HopIli_P1_V1");
    input.setModelDirectories(dirs);
    input.setClassName("HopIli_P1_V1.Data.Item");
    input.setKeepSourceObject(true);
    input.setSourceObjectFieldName("_ili_object");
    var fields =
        new java.util.ArrayList<org.apache.hop.pipeline.transforms.selectvalues.SelectField>();
    for (var field : projection.plan().fields())
      fields.add(
          selectField(
              field.hopFieldName().equals("Name") ? "Details_Note" : field.hopFieldName(),
              field.hopFieldName()));
    fields.add(selectField("Details_Code", "custom_tid"));
    fields.add(selectField("_ili_object", "_ili_object"));
    var edit = new org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta();
    var options = new org.apache.hop.pipeline.transforms.selectvalues.SelectOptions();
    options.setSelectFields(fields);
    edit.setSelectOption(options);
    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setDefault();
    output.setFileName(outputFile(out, "p1-roundtrip.xtf"));
    output.setModelNames("HopIli_P1_V1");
    output.setModelDirectories(dirs);
    output.setClassName("HopIli_P1_V1.Data.Item");
    output.setSourceObjectField("_ili_object");
    output.setObjectIdField("custom_tid");
    output.setBasketIdField("");
    output.setBasketId("constant");
    output.setOverwrite(true);
    write(out.resolve("21-p1-overlay-3d.hpl"), chain("21-p1-overlay-3d", input, edit, output));

    var transfer = new ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta();
    transfer.setDefault();
    transfer.setFileName(fixture);
    transfer.setModelNames("%DATA");
    transfer.setModelDirectories(dirs);
    var project = new ch.so.agi.hop.interlis.transforms.objecttorow.InterlisObjectToRowMeta();
    project.setDefault();
    project.setModelNames("HopIli_P1_V1");
    project.setModelDirectories(dirs);
    project.setClassName("HopIli_P1_V1.Data.Item");
    project.setAppendEnvelopeFields(true);
    var select = new org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta();
    var selectOptions = new org.apache.hop.pipeline.transforms.selectvalues.SelectOptions();
    selectOptions.setSelectFields(
        List.of(
            selectField("_ili_event_type", "_ili_event_type"),
            selectField("_ili_tid", "_ili_tid"),
            selectField("_ili_bid", "_ili_bid"),
            selectField("Name", "Name")));
    select.setSelectOption(selectOptions);
    write(
        out.resolve("22-p1-append.hpl"),
        chain("22-p1-append", transfer, project, select, csvOutput(outputFile(out, "p1-append"))));
    for (int limit : new int[] {0, 2}) {
      var validate = new ch.so.agi.hop.interlis.transforms.validate.InterlisValidateMeta();
      validate.setDefault();
      validate.setFileName(
          PARAMETERIZED
              ? "${E2E_INPUT_DIR}/p1-missing-target.xtf"
              : fixtures.resolve("p1-missing-target.xtf").toString());
      validate.setModelNames("%DATA");
      validate.setModelDirectories(dirs);
      validate.setFailOnErrors(true);
      validate.setIncludeWarnings(false);
      validate.setMaxErrors(limit);
      String name = limit == 0 ? "23-p1-validation-failure" : "24-p1-validation-limit";
      write(out.resolve(name + ".hpl"), chain(name, validate, csvOutput(outputFile(out, name))));
    }
  }

  @Test
  void generateP2Pipelines() throws Exception {
    Path out = outputDirectory();
    Files.createDirectories(out);
    String dirs = PARAMETERIZED ? "${E2E_INPUT_DIR}" : TestDataDirectory();
    String fixture =
        PARAMETERIZED ? "${E2E_INPUT_DIR}/p1-3d.xtf" : out.resolve("p1-3d.xtf").toString();
    var transfer = new ch.so.agi.hop.interlis.transforms.transferinput.InterlisTransferInputMeta();
    transfer.setDefault();
    transfer.setFileName(fixture);
    transfer.setModelNames("HopIli_P1_V1");
    transfer.setModelDirectories(dirs);
    transfer.setMode("EVENTS");
    var reorder = new org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta();
    var selection = new org.apache.hop.pipeline.transforms.selectvalues.SelectOptions();
    var names =
        new java.util.ArrayList<>(
            ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout.FIELD_NAMES);
    java.util.Collections.reverse(names);
    selection.setSelectFields(names.stream().map(n -> selectField(n, n)).toList());
    reorder.setSelectOption(selection);
    var output = new ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta();
    output.setDefault();
    output.setModelNames("HopIli_P1_V1");
    output.setModelDirectories(dirs);
    output.setFileName(outputFile(out, "p2-header.xtf"));
    output.setEventMode(true);
    output.setOverwrite(true);
    write(
        out.resolve("25-p2-header-reordered.hpl"),
        chain("25-p2-header-reordered", transfer, reorder, output));

    var input = new InterlisInputMeta();
    input.setDefault();
    input.setFileName(fixture);
    input.setModelNames("HopIli_P1_V1");
    input.setModelDirectories(dirs);
    input.setClassName("HopIli_P1_V1.Data.Item");
    input.setKeepSourceObject(true);
    input.setSourceObjectFieldName("carrier");
    var plan =
        new ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService()
            .project(
                new ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest(
                    null,
                    List.of("HopIli_P1_V1"),
                    List.of(ch.so.agi.hop.interlis.transforms.TestData.path("/models").toString())),
                "HopIli_P1_V1.Data.Item",
                ch.so.agi.hop.interlis.core.mapping.ProjectionOptions.defaults())
            .plan();
    var edit = new org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta();
    var editOptions = new org.apache.hop.pipeline.transforms.selectvalues.SelectOptions();
    var fields =
        new java.util.ArrayList<org.apache.hop.pipeline.transforms.selectvalues.SelectField>();
    for (var field : plan.fields())
      fields.add(
          selectField(
              field.hopFieldName().equals("Name") ? "Details_Note" : field.hopFieldName(),
              field.hopFieldName()));
    fields.add(selectField("carrier", "carrier"));
    editOptions.setSelectFields(fields);
    edit.setSelectOption(editOptions);
    var inverse = new ch.so.agi.hop.interlis.transforms.rowtoobject.InterlisRowToObjectMeta();
    inverse.setDefault();
    inverse.setModelNames("HopIli_P1_V1");
    inverse.setModelDirectories(dirs);
    inverse.setClassName("HopIli_P1_V1.Data.Item");
    inverse.setSourceObjectField("carrier");
    var objectOutput =
        (ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta)
            output.clone();
    objectOutput.setEventMode(false);
    objectOutput.setFileName(outputFile(out, "p2-carrier.xtf"));
    write(
        out.resolve("26-p2-carrier.hpl"),
        chain("26-p2-carrier", input, edit, inverse, objectOutput));

    var filter = new org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta();
    filter.setDefault();
    filter.setCondition(
        new org.apache.hop.core.Condition(
            "_ili_event_type",
            org.apache.hop.core.Condition.Function.NOT_EQUAL,
            null,
            new org.apache.hop.core.row.ValueMetaAndData(
                new org.apache.hop.core.row.value.ValueMetaString("value"), "END_TRANSFER")));
    filter.setTrueTransformName("step2");
    var incompleteOutput =
        (ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta)
            output.clone();
    incompleteOutput.setFileName(outputFile(out, "p2-incomplete.xtf"));
    write(
        out.resolve("27-p2-incomplete-event.hpl"),
        chain("27-p2-incomplete-event", transfer, filter, incompleteOutput));

    var invalid = new org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta();
    var invalidOptions = new org.apache.hop.pipeline.transforms.selectvalues.SelectOptions();
    invalidOptions.setSelectFields(
        names.stream()
            .map(n -> selectField(n.equals("_ili_operation") ? "_ili_event_type" : n, n))
            .toList());
    invalid.setSelectOption(invalidOptions);
    var invalidOutput =
        (ch.so.agi.hop.interlis.transforms.transferoutput.InterlisTransferOutputMeta)
            output.clone();
    invalidOutput.setFileName(outputFile(out, "p2-invalid-operation.xtf"));
    write(
        out.resolve("28-p2-invalid-operation.hpl"),
        chain("28-p2-invalid-operation", transfer, invalid, invalidOutput));
  }

  @Test
  void generateBindingPipelines() throws Exception {
    Path out = outputDirectory();
    Files.createDirectories(out);
    String dirs = PARAMETERIZED ? "${E2E_INPUT_DIR}" : TestDataDirectory();
    var input = new InterlisInputMeta();
    input.setDefault();
    input.setFileName(inputFile(inputDirectory(), "p1-3d.xtf"));
    input.setModelNames("HopIli_P1_V1");
    input.setModelDirectories(dirs);
    input.setClassName("HopIli_P1_V1.Data.Item");
    input.setKeepSourceObject(true);
    input.setSourceObjectFieldName("carrier");
    var reordered =
        selectFields(
            List.of(
                selectField("Name", "Name"),
                selectField("carrier", "carrier"),
                selectField("_ili_bid", "basket_key"),
                selectField("_ili_tid", "parent_key")));
    var explode = new ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta();
    explode.setDefault();
    explode.setModelNames("HopIli_P1_V1");
    explode.setModelDirectories(dirs);
    explode.setClassName("HopIli_P1_V1.Data.Item");
    explode.setStructureAttributePath("Children");
    explode.setSourceObjectField("carrier");
    explode.setParentTidField("parent_key");
    explode.setParentBidField("basket_key");
    explode.setIncludeParentFields(List.of("Name"));
    var csvFields =
        selectFields(
            List.of(
                selectField("_ili_parent_tid", "_ili_parent_tid"),
                selectField("Code", "Code"),
                selectField("Name", "Name")));
    var structures = chain("29-binding-structures", input, reordered, explode);
    structures.findTransform("step1").setDistributes(false);
    var childFields =
        selectFields(
            List.of(
                    "Name",
                    "Location",
                    "Note",
                    "Code",
                    "_ili_index",
                    "_ili_parent_bid",
                    "_ili_parent_tid")
                .stream()
                .map(n -> selectField(n, n))
                .toList());
    var children = new TransformMeta("children", childFields);
    structures.addTransform(children);
    structures.addPipelineHop(new PipelineHopMeta(structures.findTransform("step2"), children));
    var collect = new ch.so.agi.hop.interlis.transforms.collect.InterlisStructureCollectMeta();
    collect.setDefault();
    collect.setModelNames("HopIli_P1_V1");
    collect.setModelDirectories(dirs);
    collect.setClassName("HopIli_P1_V1.Data.Item");
    collect.setStructureAttributePath("Children");
    collect.setSourceObjectField("carrier");
    collect.setParentKeyField("parent_key");
    collect.setParentInputTransform("step1");
    collect.setChildInputTransform("children");
    var collector = new TransformMeta("collect", collect);
    structures.addTransform(collector);
    structures.addPipelineHop(new PipelineHopMeta(structures.findTransform("step1"), collector));
    structures.addPipelineHop(new PipelineHopMeta(children, collector));
    var expanded = new TransformMeta("expand again", (ITransformMeta) explode.clone());
    structures.addTransform(expanded);
    structures.addPipelineHop(new PipelineHopMeta(collector, expanded));
    var select = new TransformMeta("csv fields", csvFields);
    structures.addTransform(select);
    structures.addPipelineHop(new PipelineHopMeta(expanded, select));
    var csv = new TransformMeta("csv", csvOutput(outputFile(out, "binding-structures")));
    structures.addTransform(csv);
    structures.addPipelineHop(new PipelineHopMeta(select, csv));
    write(out.resolve("29-binding-structures.hpl"), structures);

    var person = new InterlisInputMeta();
    person.setDefault();
    person.setModelNames("HopIli_Associations_V1");
    person.setModelDirectories(dirs);
    person.setFileName(inputFile(inputDirectory(), "HopIli_Associations_V1_mapping.xtf"));
    person.setClassName("HopIli_Associations_V1.Data.Person");
    var mainFields =
        selectFields(
            List.of(
                selectField("Name", "Name"),
                selectField("Address_ref", "reference"),
                selectField("_ili_tid", "_ili_tid")));
    var join = new ch.so.agi.hop.interlis.transforms.rolejoin.InterlisRoleJoinMeta();
    join.setDefault();
    join.setMainInputTransform("step1");
    join.setLookupInputTransform("lookup fields");
    join.setMainReferenceField("reference");
    join.setLookupTidField("key");
    join.setLookupFields(List.of("Street"));
    join.setModelNames("HopIli_Associations_V1");
    join.setModelDirectories(dirs);
    join.setMainClassName("HopIli_Associations_V1.Data.Person");
    join.setRoleName("Address");
    join.setFailOnMissingMandatoryReference(false);
    var joined =
        chain(
            "30-binding-join",
            person,
            mainFields,
            join,
            csvOutput(outputFile(out, "binding-join")));
    addBindingLookup(joined, person, "step2");
    write(out.resolve("30-binding-join.hpl"), joined);

    var missing =
        (ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta) explode.clone();
    missing.setIncludeParentFields(List.of("missing_parent_field"));
    write(
        out.resolve("31-binding-missing-field.hpl"),
        chain("31-binding-missing-field", input, reordered, missing));
    var numeric = (ch.so.agi.hop.interlis.transforms.rolejoin.InterlisRoleJoinMeta) join.clone();
    numeric.setMainInputTransform("step2");
    numeric.setMainReferenceField("_ili_index");
    var invalid = chain("32-binding-key-type", input, reordered, explode, numeric);
    addBindingLookup(invalid, person, "step3");
    write(out.resolve("32-binding-key-type.hpl"), invalid);
  }

  private static void addBindingLookup(
      PipelineMeta pipeline, InterlisInputMeta person, String target) {
    var address = (InterlisInputMeta) person.clone();
    address.setClassName("HopIli_Associations_V1.Data.Address");
    var source = new TransformMeta("lookup", address);
    pipeline.addTransform(source);
    var fields =
        new TransformMeta(
            "lookup fields",
            selectFields(List.of(selectField("Street", "Street"), selectField("_ili_tid", "key"))));
    pipeline.addTransform(fields);
    pipeline.addPipelineHop(new PipelineHopMeta(source, fields));
    pipeline.addPipelineHop(new PipelineHopMeta(fields, pipeline.findTransform(target)));
  }

  private static org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta selectFields(
      List<org.apache.hop.pipeline.transforms.selectvalues.SelectField> fields) {
    var meta = new org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta();
    var options = new org.apache.hop.pipeline.transforms.selectvalues.SelectOptions();
    options.setSelectFields(fields);
    meta.setSelectOption(options);
    return meta;
  }

  private static org.apache.hop.pipeline.transforms.selectvalues.SelectField selectField(
      String name, String rename) {
    var field = new org.apache.hop.pipeline.transforms.selectvalues.SelectField();
    field.setName(name);
    field.setRename(rename);
    field.setLength(-2);
    field.setPrecision(-2);
    return field;
  }

  private static PipelineMeta chain(String name, ITransformMeta... steps) {
    var pipeline = new PipelineMeta();
    pipeline.setName(name);
    TransformMeta previous = null;
    int i = 0;
    for (var step : steps) {
      var transform = new TransformMeta("step" + i, step);
      transform.setLocation(100 + 200 * i++, 100);
      pipeline.addTransform(transform);
      if (previous != null) pipeline.addPipelineHop(new PipelineHopMeta(previous, transform));
      previous = transform;
    }
    return pipeline;
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
    return PARAMETERIZED ? "${E2E_INPUT_DIR}/" + name : inputDir.resolve(name).toString();
  }

  private static String outputFile(Path outputDir, String name) {
    return PARAMETERIZED ? "${E2E_OUTPUT_DIR}/" + name : outputDir.resolve(name).toString();
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
    Path data = TestData.path("/data/HopIli_Geometry_V1_valid.xtf");
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
        pipelineMeta, input, stringifyGeometry(List.of("Location")), csvOutput(outputFile));
  }

  /**
   * A Select Values transform that converts the given geometry fields to their WKT string form,
   * because Text file output cannot render geometry values natively.
   */
  private static org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta stringifyGeometry(
      List<String> geometryFields) {
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
    TransformMeta stringify =
        new TransformMeta("Select values", stringifyGeometry(List.of("Location")));
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

    InterlisOutputMeta output =
        associationOutput(outputFile, "HopIli_Associations_V1.Data.Person", inputFile);
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
    TransformMeta sink = new TransformMeta("INTERLIS Transfer Output", output);
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
    enumerations.setModelDirectories(PARAMETERIZED ? "${E2E_INPUT_DIR}" : TestDataDirectory());

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
      return TestData.path("/models").toString();
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
