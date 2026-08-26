package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisPlanRoot;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Compiles the associations test model and reads its XTF fixture. */
final class AssociationsTestSupport {

  static final String MODEL_NAME = "HopIli_Associations_V1";
  static final String CLASS_PERSON = "HopIli_Associations_V1.Data.Person";
  static final String ASSOC_MEMBERSHIP = "HopIli_Associations_V1.Data.Membership";
  static final String ASSOC_PERSON_TASK = "HopIli_Associations_V1.Data.PersonTask";
  static final String ASSOC_ADDRESS_OWNERSHIP = "HopIli_Associations_V1.Data.AddressOwnership";

  private AssociationsTestSupport() {}

  static CompiledInterlisModel compileModel() throws Exception {
    return new InterlisModelServiceImpl()
        .compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Associations_V1.ili")),
                List.of(),
                List.of()),
            ModelCompileOptions.defaults());
  }

  static InterlisSchemaDescriptor schema() throws Exception {
    return new InterlisSchemaExtractor().extract(compileModel().transferDescription());
  }

  static InterlisPlanRoot root(InterlisSchemaDescriptor schema, String scopedName) {
    return schema
        .findPlanRoot(scopedName)
        .orElseThrow(() -> new IllegalStateException(scopedName + " missing from test model"));
  }

  /** Reads the fixture and returns all objects by their class/tag. */
  static List<InterlisObjectEnvelope> readObjects(String scopedName) throws Exception {
    try (XtfTransferReader reader =
        XtfTransferReader.open(
            TestResources.path("/data/HopIli_Associations_V1_valid.xtf"),
            compileModel().transferDescription())) {
      List<InterlisObjectEnvelope> objects = new ArrayList<>();
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        if (event.eventType() == InterlisEventType.OBJECT
            && scopedName.equals(event.className())) {
          objects.add(event);
        }
      }
      return objects;
    }
  }

  /** All objects of the fixture keyed by scoped name. */
  static Map<String, List<InterlisObjectEnvelope>> allObjects() throws Exception {
    return readAllObjects("/data/HopIli_Associations_V1_valid.xtf");
  }

  /** Objects of the external-reference fixture (the Task role carries an ili:bid). */
  static Map<String, List<InterlisObjectEnvelope>> allObjectsExtRef() throws Exception {
    return readAllObjects("/data/HopIli_Associations_V1_extref.xtf");
  }

  private static Map<String, List<InterlisObjectEnvelope>> readAllObjects(String resource)
      throws Exception {
    try (XtfTransferReader reader =
        XtfTransferReader.open(
            TestResources.path(resource),
            compileModel().transferDescription())) {
      List<InterlisObjectEnvelope> objects = new ArrayList<>();
      InterlisObjectEnvelope event;
      while ((event = reader.next()) != null) {
        if (event.eventType() == InterlisEventType.OBJECT) {
          objects.add(event);
        }
      }
      return objects.stream().collect(Collectors.groupingBy(InterlisObjectEnvelope::className));
    }
  }

  static IomObject firstObject(String scopedName) throws Exception {
    return readObjects(scopedName).get(0).object();
  }

  static <T> T value(IomObject object, Function<IomObject, T> f) {
    return f.apply(object);
  }
}
