package ch.so.agi.hop.interlis.transforms.output;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.testutil.*;
import java.lang.reflect.*;
import java.nio.file.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.config.*;
import org.apache.hop.pipeline.engine.*;
import org.apache.hop.pipeline.engines.local.*;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class InterlisOutputCloseFailureTest {
  @TempDir Path temp;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void close_error_keeps_existing_file_and_removes_prepared_output() throws Exception {
    var target = temp.resolve("output.xtf");
    Files.writeString(target, "previous");
    var source = new RegressionRowsMeta();
    source.schema = new RowMeta();
    source.schema.addValueMeta(new ValueMetaString("_ili_tid"));
    source.schema.addValueMeta(new ValueMetaString("_ili_bid"));
    source.schema.addValueMeta(new ValueMetaString("Name"));
    source.rows.add(new Object[] {"t1", "b1", "target"});
    var writer = new InterlisOutputMeta();
    writer.setDefault();
    writer.setFileName(target.toString());
    writer.setOverwrite(true);
    writer.setModelNames("HopIli_Collections_V1");
    writer.setModelDirectories(TestData.path("/models").toString());
    writer.setClassName("HopIli_Collections_V1.Data.Target");
    var pm = new PipelineMeta();
    var a = new TransformMeta("source", source);
    var b = new TransformMeta("writer", writer);
    pm.addTransform(a);
    pm.addTransform(b);
    pm.addPipelineHop(new PipelineHopMeta(a, b));
    var config = new PipelineRunConfiguration();
    var local = new LocalPipelineRunConfiguration();
    local.setEnginePluginId("Local");
    config.setEngineRunConfiguration(local);
    var engine = (Pipeline) PipelineEngineFactory.createPipelineEngine(config, pm);
    engine.prepareExecution();
    var runtime = (InterlisOutput) engine.getTransform("writer", 0);
    runtime.addRowListener(
        new RowAdapter() {
          @Override
          public void rowWrittenEvent(IRowMeta meta, Object[] row) {
            var data = runtime.getData();
            var original = data.writer;
            data.writer =
                (InterlisTransferWriter)
                    Proxy.newProxyInstance(
                        InterlisTransferWriter.class.getClassLoader(),
                        new Class<?>[] {InterlisTransferWriter.class},
                        (proxy, method, args) -> {
                          try {
                            var result = method.invoke(original, args);
                            if (method.getName().equals("close"))
                              throw new InterlisWriteException("injected close failure");
                            return result;
                          } catch (InvocationTargetException error) {
                            throw error.getCause();
                          }
                        });
          }
        });
    engine.startThreads();
    engine.waitUntilFinished();
    assertThat(engine.getErrors()).isPositive();
    assertThat(Files.readString(target)).isEqualTo("previous");
    try (var files = Files.list(temp)) {
      assertThat(files.toList()).containsExactly(target);
    }
  }
}
