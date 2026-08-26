package ch.so.agi.hop.interlis.transforms.collect;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.output.InterlisOutputMeta;
import ch.so.agi.hop.interlis.transforms.testutil.SyntheticChildRowsMeta;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.engines.local.LocalPipelineRunConfiguration;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterlisStructureCollectPipelineTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private InterlisInputMeta inputMeta() {
    InterlisInputMeta input = new InterlisInputMeta();
    input.setFileName(TestData.path("/data/HopIli_Structures_V1_valid.xtf").toString());
    input.setModelNames(InterlisInputMeta.MODELS_FROM_DATA);
    input.setModelDirectories(TestData.path("/models").toString());
    input.setClassName("HopIli_Structures_V1.Data.Person");
    input.setIncludeTid(true);
    input.setIncludeBid(true);
    input.setKeepSourceObject(true);
    return input;
  }

  private InterlisStructureExplodeMeta explodeMeta(String structurePath) {
    InterlisStructureExplodeMeta explode = new InterlisStructureExplodeMeta();
    explode.setDefault();
    explode.setModelNames("HopIli_Structures_V1");
    explode.setModelDirectories(TestData.path("/models").toString());
    explode.setClassName("HopIli_Structures_V1.Data.Person");
    explode.setStructureAttributePath(structurePath);
    return explode;
  }

  private InterlisStructureCollectMeta collectMeta(String structurePath) {
    InterlisStructureCollectMeta collect = new InterlisStructureCollectMeta();
    collect.setDefault();
    collect.setParentInputTransform("INTERLIS Input");
    collect.setChildInputTransform("child rows");
    collect.setModelNames("HopIli_Structures_V1");
    collect.setModelDirectories(TestData.path("/models").toString());
    collect.setClassName("HopIli_Structures_V1.Data.Person");
    collect.setStructureAttributePath(structurePath);
    return collect;
  }

  private InterlisOutputMeta outputMeta(Path outputFile) {
    InterlisOutputMeta output = new InterlisOutputMeta();
    output.setFileName(outputFile.toString());
    output.setModelNames("HopIli_Structures_V1");
    output.setModelDirectories(TestData.path("/models").toString());
    output.setClassName("HopIli_Structures_V1.Data.Person");
    output.setObjectIdField("_ili_tid");
    output.setBasketIdField("_ili_bid");
    output.setBasketId("b1");
    output.setSourceObjectField(InterlisStructureCollectMeta.DEFAULT_SOURCE_OBJECT_FIELD);
    output.setOverwrite(true);
    return output;
  }

  private IPipelineEngine<PipelineMeta> run(
      InterlisInputMeta input,
      InterlisStructureExplodeMeta explode,
      InterlisStructureCollectMeta collect,
      InterlisOutputMeta output)
      throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS Input", input);
    // Fan-out: INTERLIS Input feeds both the explode transform and the collect transform;
    // Hop distributes rows round-robin by default, so the copy must be explicit.
    source.setDistributes(false);
    TransformMeta explodeTransform = new TransformMeta("child rows", explode);
    TransformMeta collectTransform = new TransformMeta("INTERLIS Structure Collect", collect);
    TransformMeta sink = new TransformMeta("INTERLIS Output", output);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(explodeTransform);
    pipelineMeta.addTransform(collectTransform);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, explodeTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, collectTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(explodeTransform, collectTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(collectTransform, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();
    return engine;
  }

  private IPipelineEngine<PipelineMeta> runWithSyntheticChildren(
      InterlisStructureCollectMeta collect, InterlisOutputMeta output, SyntheticChildRowsMeta children)
      throws Exception {
    PipelineMeta pipelineMeta = new PipelineMeta();
    TransformMeta source = new TransformMeta("INTERLIS Input", inputMeta());
    TransformMeta childTransform = new TransformMeta("child rows", children);
    TransformMeta collectTransform = new TransformMeta("INTERLIS Structure Collect", collect);
    TransformMeta sink = new TransformMeta("INTERLIS Output", output);
    pipelineMeta.addTransform(source);
    pipelineMeta.addTransform(childTransform);
    pipelineMeta.addTransform(collectTransform);
    pipelineMeta.addTransform(sink);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(source, collectTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(childTransform, collectTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(collectTransform, sink));

    PipelineRunConfiguration runConfiguration = new PipelineRunConfiguration();
    LocalPipelineRunConfiguration engineRunConfiguration = new LocalPipelineRunConfiguration();
    engineRunConfiguration.setEnginePluginId("Local");
    runConfiguration.setEngineRunConfiguration(engineRunConfiguration);
    IPipelineEngine<PipelineMeta> engine =
        PipelineEngineFactory.createPipelineEngine(runConfiguration, pipelineMeta);
    engine.prepareExecution();
    engine.startThreads();
    engine.waitUntilFinished();
    return engine;
  }

  private List<InterlisObjectEnvelope> readObjects(Path file) throws Exception {
    CompiledInterlisModel model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(TestData.path("/models/HopIli_Structures_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults());
    List<InterlisObjectEnvelope> objects = new ArrayList<>();
    try (XtfTransferReader reader =
        XtfTransferReader.open(file, model.transferDescription())) {
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        if (event.eventType() == InterlisEventType.OBJECT) {
          objects.add(event);
        }
      }
    }
    return objects;
  }

  private IomObject personByTid(List<InterlisObjectEnvelope> objects, String tid) {
    return objects.stream()
        .filter(e -> tid.equals(e.objectId()))
        .map(InterlisObjectEnvelope::object)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void full_list_roundtrip_through_explode_and_collect() throws Exception {
    Path outputFile = tempDir.resolve("structures-roundtrip.xtf");
    IPipelineEngine<PipelineMeta> engine =
        run(
            inputMeta(),
            explodeMeta("Addresses"),
            collectMeta("Addresses"),
            outputMeta(outputFile));

    assertThat(engine.getErrors()).isZero();

    List<InterlisObjectEnvelope> objects = readObjects(outputFile);
    assertThat(objects).extracting(InterlisObjectEnvelope::objectId)
        .containsExactly("p1", "p2", "p3");

    IomObject p1 = personByTid(objects, "p1");
    assertThat(p1.getattrvaluecount("Addresses")).isEqualTo(3);
    assertThat(p1.getattrobj("Addresses", 0).getattrvalue("Street")).isEqualTo("Main Street");
    assertThat(p1.getattrobj("Addresses", 1).getattrvalue("Street")).isEqualTo("Station Street");
    assertThat(p1.getattrobj("Addresses", 2).getattrvalue("Street")).isEqualTo("Village Road");
    IomObject location = p1.getattrobj("Addresses", 0).getattrobj("Location", 0);
    assertThat(location.getobjecttag()).isEqualTo("COORD");
    assertThat(Double.parseDouble(location.getattrvalue("C1"))).isEqualTo(2600000.0);
    IomObject postCode = p1.getattrobj("Addresses", 0).getattrobj("PostCode", 0);
    assertThat(postCode.getattrvalue("Town")).isEqualTo("Olten");

    assertThat(personByTid(objects, "p2").getattrvaluecount("Addresses")).isEqualTo(1);
    assertThat(personByTid(objects, "p3").getattrvaluecount("Addresses")).isZero();
  }

  @Test
  void full_bag_roundtrip_through_explode_and_collect() throws Exception {
    Path outputFile = tempDir.resolve("structures-bag-roundtrip.xtf");
    IPipelineEngine<PipelineMeta> engine =
        run(
            inputMeta(),
            explodeMeta("Contacts"),
            collectMeta("Contacts"),
            outputMeta(outputFile));

    assertThat(engine.getErrors()).isZero();

    List<InterlisObjectEnvelope> objects = readObjects(outputFile);
    IomObject p1 = personByTid(objects, "p1");
    assertThat(p1.getattrvaluecount("Contacts")).isEqualTo(2);
    assertThat(p1.getattrobj("Contacts", 0).getattrvalue("Kind")).isEqualTo("phone");
    assertThat(p1.getattrobj("Contacts", 1).getattrvalue("Kind")).isEqualTo("email");
  }

  @Test
  void collect_replaces_and_removes_structures_modified_downstream() throws Exception {
    // The child stream only carries a replacement address for p2: the collected structures of
    // p1 must be removed entirely and p2's structure replaced by the new child.
    Path outputFile = tempDir.resolve("structures-replaced.xtf");
    SyntheticChildRowsMeta children = new SyntheticChildRowsMeta();
    children.addRow("p2", 0L, "Changed Road", "99");

    IPipelineEngine<PipelineMeta> engine =
        runWithSyntheticChildren(
            collectMeta("Addresses"), outputMeta(outputFile), children);

    assertThat(engine.getErrors()).isZero();

    List<InterlisObjectEnvelope> objects = readObjects(outputFile);
    IomObject p1 = personByTid(objects, "p1");
    IomObject p2 = personByTid(objects, "p2");
    assertThat(p1.getattrvaluecount("Addresses")).isZero();
    assertThat(p2.getattrvaluecount("Addresses")).isEqualTo(1);
    assertThat(p2.getattrobj("Addresses", 0).getattrvalue("Street")).isEqualTo("Changed Road");
  }

  @Test
  void child_without_parent_fails_by_default() throws Exception {
    Path outputFile = tempDir.resolve("structures-orphan.xtf");
    SyntheticChildRowsMeta children = new SyntheticChildRowsMeta();
    children.addRow("zz", 0L, "Orphan Street", "1");

    IPipelineEngine<PipelineMeta> engine =
        runWithSyntheticChildren(
            collectMeta("Addresses"), outputMeta(outputFile), children);

    assertThat(engine.getErrors()).isGreaterThan(0);
  }

  @Test
  void missing_index_for_list_fails() throws Exception {
    Path outputFile = tempDir.resolve("structures-missing-index.xtf");
    SyntheticChildRowsMeta children = new SyntheticChildRowsMeta();
    children.addRow("p1", null, "No Index Street", "1");

    IPipelineEngine<PipelineMeta> engine =
        runWithSyntheticChildren(
            collectMeta("Addresses"), outputMeta(outputFile), children);

    assertThat(engine.getErrors()).isGreaterThan(0);
  }

  @Test
  void duplicate_index_for_list_fails() throws Exception {
    Path outputFile = tempDir.resolve("structures-duplicate-index.xtf");
    SyntheticChildRowsMeta children = new SyntheticChildRowsMeta();
    children.addRow("p1", 0L, "First Street", "1");
    children.addRow("p1", 0L, "Second Street", "2");

    IPipelineEngine<PipelineMeta> engine =
        runWithSyntheticChildren(
            collectMeta("Addresses"), outputMeta(outputFile), children);

    assertThat(engine.getErrors()).isGreaterThan(0);
  }

  @Test
  void nested_structure_path_collects_under_single_structures() throws Exception {
    // Home.Place.Phones explodes to Kind/Value children and collects back under Home -> Place.
    Path outputFile = tempDir.resolve("structures-nested-roundtrip.xtf");
    IPipelineEngine<PipelineMeta> engine =
        run(
            inputMeta(),
            explodeMeta("Home.Place.Phones"),
            collectMeta("Home.Place.Phones"),
            outputMeta(outputFile));

    assertThat(engine.getErrors()).isZero();

    List<InterlisObjectEnvelope> objects = readObjects(outputFile);
    IomObject p1 = personByTid(objects, "p1");
    IomObject home = p1.getattrobj("Home", 0);
    IomObject place = home.getattrobj("Place", 0);
    assertThat(place.getattrvaluecount("Phones")).isEqualTo(1);
    assertThat(place.getattrobj("Phones", 0).getattrvalue("Kind")).isEqualTo("phone");
    // The nested single structure content of Home survived the roundtrip.
    assertThat(place.getattrvalue("Name")).isEqualTo("Springfield");
    assertThat(home.getattrvalue("Since")).isEqualTo("2020-01-15");
  }
}
