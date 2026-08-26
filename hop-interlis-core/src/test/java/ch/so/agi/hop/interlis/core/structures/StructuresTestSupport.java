package ch.so.agi.hop.interlis.core.structures;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Compiles the structures test model and reads its XTF fixture. */
final class StructuresTestSupport {

  static final String MODEL_NAME = "HopIli_Structures_V1";
  static final String CLASS_PERSON = "HopIli_Structures_V1.Data.Person";

  private StructuresTestSupport() {}

  static CompiledInterlisModel compileModel() throws Exception {
    return new InterlisModelServiceImpl()
        .compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Structures_V1.ili")),
                List.of(),
                List.of()),
            ModelCompileOptions.defaults());
  }

  static InterlisSchemaDescriptor schema() throws Exception {
    return new InterlisSchemaExtractor().extract(compileModel().transferDescription());
  }

  static InterlisClassDescriptor person(InterlisSchemaDescriptor schema) {
    return schema
        .findClass(CLASS_PERSON)
        .orElseThrow(() -> new IllegalStateException("Person class missing from test model"));
  }

  /** Reads the valid fixture and returns the person objects by TID. */
  static Map<String, IomObject> personsByTid() throws Exception {
    try (XtfTransferReader reader =
        XtfTransferReader.open(
            TestResources.path("/data/HopIli_Structures_V1_valid.xtf"),
            compileModel().transferDescription())) {
      List<InterlisObjectEnvelope> objects = new ArrayList<>();
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        if (event.eventType() == ch.so.agi.hop.interlis.core.io.InterlisEventType.OBJECT) {
          objects.add(event);
        }
      }
      return objects.stream()
          .collect(Collectors.toMap(InterlisObjectEnvelope::objectId, InterlisObjectEnvelope::object));
    }
  }

  static <T> T personValue(Map<String, IomObject> persons, String tid, Function<IomObject, T> f) {
    IomObject person = persons.get(tid);
    if (person == null) {
      throw new IllegalStateException("No person with TID " + tid + " in fixture");
    }
    return f.apply(person);
  }
}
