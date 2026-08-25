package ch.so.agi.hop.interlis.transforms.test;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.writetolog.WriteToLogMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Generates the XML form of the Phase-0 demo pipeline (INTERLIS Test -&gt; Write to log).
 *
 * <p>Run this test with stdout capture to refresh {@code e2e/pipelines/01-interlis-test.hpl}.
 */
class E2ePipelineGeneratorTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void printDemoPipelineXml() throws Exception {
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

    System.out.println("===BEGIN .HPL===");
    System.out.println(pipelineMeta.getXml(new Variables()));
    System.out.println("===END .HPL===");
  }
}
