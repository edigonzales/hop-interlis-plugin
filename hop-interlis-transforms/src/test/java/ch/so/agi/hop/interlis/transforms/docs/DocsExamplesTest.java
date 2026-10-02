package ch.so.agi.hop.interlis.transforms.docs;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.validate.InterlisValidateMeta;
import com.atolcd.hop.gis.geometry.curve.CircularString;
import com.atolcd.hop.gis.geometry.curve.CompoundCurve;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.rowstoresult.RowsToResultMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class DocsExamplesTest {

  private static final String XTF_24 = "http://www.interlis.ch/xtf/2.4/INTERLIS";
  private static final Path EXAMPLES = TestData.path("/doc-examples");

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void all_documentation_transfers_are_2_4_and_models_compile() throws Exception {
    for (String example :
        List.of(
            "bogen",
            "demo",
            "liste",
            "rolle",
            "struktur",
            "validierung",
            "sammlungen",
            "erhaltung",
            "referenzen")) {
      Path model = EXAMPLES.resolve(example + "-modell.ili");
      Path transfer = EXAMPLES.resolve(example + "-transfer.xtf");

      assertThat(Files.readString(model)).startsWith("INTERLIS 2.4;");
      assertThat(Files.readString(transfer)).contains(XTF_24);
      var document = DocumentBuilderFactory.newInstance();
      document.setNamespaceAware(true);
      var root = document.newDocumentBuilder().parse(transfer.toFile()).getDocumentElement();
      assertThat(root.getLocalName()).isEqualTo("transfer");
      assertThat(root.getNamespaceURI()).isEqualTo(XTF_24);

      new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl()
          .compile(
              new ch.so.agi.hop.interlis.core.model.ModelSource(
                  List.of(model), List.of(), List.of()),
              ch.so.agi.hop.interlis.core.model.ModelCompileOptions.defaults());
    }
  }

  @Test
  void valid_documentation_transfers_complete_both_validation_passes() throws Exception {
    for (String[] example :
        List.of(
            new String[] {"demo", "DemoBodenbedeckung"},
            new String[] {"struktur", "DemoStruktur"},
            new String[] {"liste", "DemoListe"},
            new String[] {"rolle", "DemoRolle"},
            new String[] {"bogen", "DemoBogen"},
            new String[] {"sammlungen", "DemoSammlungen"},
            new String[] {"erhaltung", "DemoErhaltung"},
            new String[] {"referenzen", "DemoReferenzen"})) {
      assertThat(runValidation(example[0] + "-transfer.xtf", example[1]))
          .as("complete validation of %s", example[0])
          .isEmpty();
    }
  }

  @Test
  void collection_examples_expose_typed_children_and_no_scalar_collection_fields()
      throws Exception {
    var parents =
        runInput("sammlungen-transfer.xtf", "DemoSammlungen.Daten.Objekt", "DemoSammlungen");
    assertThat(parents).hasSize(2);
    assertThat(parents.get(0).getRowMeta().getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "Name");
    String[][] examples = {
      {"Texte", "String", "erster", "zweiter"},
      {"Zahlen", "Integer", "7", "7", "9"},
      {"Flags", "Boolean", "true", "false"},
      {"Farben", "String", "rot", "blau"}
    };
    for (String[] example : examples) {
      var children =
          runExplode(
              "sammlungen", "DemoSammlungen", "DemoSammlungen.Daten.Objekt", example[0], List.of());
      assertThat(children).hasSize(example.length - 2);
      var rowMeta = children.get(0).getRowMeta();
      assertThat(rowMeta.getFieldNames())
          .containsExactly(
              "_ili_parent_tid", "_ili_parent_bid", "_ili_index", "_ili_value", "_ili_update_ref");
      assertThat(rowMeta.getValueMeta(3).getTypeDesc()).isEqualTo(example[1]);
      for (int i = 0; i < children.size(); i++) {
        assertThat(children.get(i).getData()[0]).isEqualTo("o1");
        assertThat(children.get(i).getData()[1]).isEqualTo("b1");
        assertThat(children.get(i).getData()[2]).isEqualTo((long) i);
        assertThat(children.get(i).getData()[3].toString()).isEqualTo(example[i + 2]);
      }
    }
  }

  @Test
  void preservation_example_projects_only_name_and_retains_full_child_carrier() throws Exception {
    var children =
        runExplode(
            "erhaltung", "DemoErhaltung", "DemoErhaltung.Daten.Eltern", "Kinder", List.of("Name"));
    assertThat(children).hasSize(2);
    assertThat(children.get(0).getRowMeta().getFieldNames())
        .containsExactly(
            "_ili_parent_tid",
            "_ili_parent_bid",
            "_ili_index",
            "Name",
            "_ili_child_object",
            "_ili_update_ref");
    var carrier = (ch.interlis.iom.IomObject) children.get(0).getData()[4];
    assertThat(carrier.getobjecttag()).isEqualTo("DemoErhaltung.Daten.SpezialKind");
    assertThat(carrier.getattrvalue("Versteckt")).isEqualTo("erhalten");
    assertThat(carrier.getattrvalue("Zusatz")).isEqualTo("Untertyp");
    assertThat(carrier.getattrvaluecount("Details")).isEqualTo(2);
  }

  @Test
  void reference_example_exposes_tid_and_nullable_bid() throws Exception {
    var rows = runInput("referenzen-transfer.xtf", "DemoReferenzen.Daten.Objekt", "DemoReferenzen");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactly(
            "_ili_tid",
            "_ili_bid",
            "Name",
            "Intern_ref",
            "Intern_ref_bid",
            "Extern_ref",
            "Extern_ref_bid");
    assertThat(rows.get(0).getData())
        .containsExactly("o1", "b1", "verweist", "z1", null, "z2", "b2");
  }

  private List<RowMetaAndData> runExplode(
      String fixture, String model, String className, String attribute, List<String> fields)
      throws Exception {
    InterlisInputMeta input = new InterlisInputMeta();
    input.setDefault();
    input.setFileName(EXAMPLES.resolve(fixture + "-transfer.xtf").toString());
    input.setModelNames(model);
    input.setModelDirectories(EXAMPLES.toString());
    input.setClassName(className);
    input.setKeepSourceObject(true);
    InterlisStructureExplodeMeta explode = new InterlisStructureExplodeMeta();
    explode.setDefault();
    explode.setModelNames(model);
    explode.setModelDirectories(EXAMPLES.toString());
    explode.setClassName(className);
    explode.setStructureAttributePath(attribute);
    explode.setSelectedChildFields(fields);
    return run(
        new TransformMeta("INTERLIS_INPUT", "Input", input),
        new TransformMeta("INTERLIS_STRUCTURE_EXPLODE", "Explode", explode));
  }

  @Test
  void demo_and_structure_examples_produce_the_documented_rows() throws Exception {
    List<RowMetaAndData> buildings =
        runInput(
            "demo-transfer.xtf",
            "DemoBodenbedeckung.Bodenbedeckung.Gebaeude",
            "DemoBodenbedeckung");
    assertThat(buildings).hasSize(2);
    assertThat(buildings.get(0).getRowMeta().getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "Name", "Art", "Geometrie");
    assertThat(buildings.get(0).getData()[0]).isEqualTo("g1");
    assertThat(buildings.get(0).getData()[2]).isEqualTo("Rathaus");
    assertThat(buildings.get(1).getData()[0]).isEqualTo("g2");

    List<RowMetaAndData> persons =
        runInput("struktur-transfer.xtf", "DemoStruktur.Personen.Person", "DemoStruktur");
    assertThat(persons).hasSize(2);
    assertThat(persons.get(0).getRowMeta().getFieldNames())
        .containsExactly(
            "_ili_tid",
            "_ili_bid",
            "Name",
            "Vorname",
            "Adresse_Strasse",
            "Adresse_Hausnummer",
            "Adresse_PLZ",
            "Adresse_Ort");
    assertThat(persons.get(0).getData()[4]).isEqualTo("Hauptstrasse");
    assertThat(persons.get(1).getData()[7]).isEqualTo("Solothurn");
  }

  @Test
  void list_and_role_examples_expose_the_documented_relationships() throws Exception {
    List<RowMetaAndData> listRows =
        runInput("liste-transfer.xtf", "DemoListe.Gebaeude.Gebaeude", "DemoListe");
    assertThat(listRows).hasSize(1);
    assertThat(listRows.get(0).getRowMeta().getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "Name");

    List<RowMetaAndData> roleRows =
        runInput("rolle-transfer.xtf", "DemoRolle.Verwaltung.Gebaeude", "DemoRolle");
    assertThat(roleRows).hasSize(1);
    assertThat(roleRows.get(0).getRowMeta().getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "Name", "Geometrie", "Gemeinde_ref");
    assertThat(roleRows.get(0).getData()[4]).isEqualTo("gem1");
  }

  @Test
  void arc_example_keeps_a_curve_geometry() throws Exception {
    List<RowMetaAndData> rows =
        runInput("bogen-transfer.xtf", "DemoBogen.Verkehr.Strassenachse", "DemoBogen");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getRowMeta().getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "Name", "Geometrie");

    Geometry geometry = (Geometry) rows.get(0).getData()[3];
    assertThat(geometry).isInstanceOf(CompoundCurve.class);
    CompoundCurve curve = (CompoundCurve) geometry;
    CircularString arc =
        curve.getComponents().stream()
            .filter(CircularString.class::isInstance)
            .map(CircularString.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(arc.getControlPoints()).hasSize(3);
    assertThat(arc.getControlPoints()[1].getX()).isEqualTo(2600050.0);
    assertThat(arc.getControlPoints()[1].getY()).isEqualTo(1200050.0);
  }

  @Test
  void validation_example_emits_one_error_with_class_tid_and_constraint() throws Exception {
    List<RowMetaAndData> rows = runValidation("validierung-transfer.xtf", "DemoValidierung");

    assertThat(rows).hasSize(1);
    Object[] row = rows.get(0).getData();
    assertThat(row[0]).isEqualTo("ERROR");
    assertThat(String.valueOf(row[1])).contains("Temperaturbereich");
    assertThat(row[3]).isInstanceOf(Long.class);
    assertThat((Long) row[3]).isPositive();
    assertThat(row[8]).isEqualTo("DemoValidierung.Daten.Messung");
    assertThat(row[9]).isEqualTo("m2");
    assertThat(row[10]).isNull();
    assertThat(row[11]).isEqualTo("codeInternal");
  }

  private List<RowMetaAndData> runInput(String transfer, String className, String modelName)
      throws Exception {
    InterlisInputMeta meta = new InterlisInputMeta();
    meta.setFileName(EXAMPLES.resolve(transfer).toString());
    meta.setModelNames(modelName);
    meta.setModelDirectories(EXAMPLES.toString());
    meta.setClassName(className);
    meta.setIncludeTid(true);
    meta.setIncludeBid(true);
    return run(new TransformMeta("INTERLIS_INPUT", "INTERLIS Input", meta));
  }

  private List<RowMetaAndData> runValidation(String transfer, String modelName) throws Exception {
    InterlisValidateMeta meta = new InterlisValidateMeta();
    meta.setFileName(EXAMPLES.resolve(transfer).toString());
    meta.setModelNames(modelName);
    meta.setModelDirectories(EXAMPLES.toString());
    return run(new TransformMeta("INTERLIS_VALIDATE", "INTERLIS Validate", meta));
  }

  private List<RowMetaAndData> run(TransformMeta source, TransformMeta... intermediates)
      throws Exception {
    TransformMeta sink = new TransformMeta("Rows to result", new RowsToResultMeta());
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(sink);
    TransformMeta previous = source;
    for (TransformMeta intermediate : intermediates) {
      pipelineMeta.addTransform(intermediate);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(previous, intermediate));
      previous = intermediate;
    }
    pipelineMeta.addPipelineHop(new PipelineHopMeta(previous, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();

    assertThat(engine.getErrors()).isZero();
    return ((Pipeline) engine).getResultRows();
  }
}
