package ch.so.agi.hop.interlis.transforms.update;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.buffer.*;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.core.structures.*;
import ch.so.agi.hop.interlis.core.update.*;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.buffer.*;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import java.io.Serializable;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.transform.BaseTransform;

/** Two-pass update: spool typed patches, then replay the complete original event stream. */
final class InterlisUpdateRunner {
  private record Compiled(
      String className,
      String path,
      MappedClassPlan mapping,
      InterlisPatchPlan patch,
      InterlisStructurePlan structure,
      HopRowCodec codec) {}

  private record Patch(int input, byte[] values, UpdateReference reference)
      implements Serializable {}

  private record Bound(
      InterlisRowBindings fields,
      InterlisFieldBinding tid,
      InterlisFieldBinding bid,
      InterlisFieldBinding reference) {}

  private record Snapshot(long size, java.nio.file.attribute.FileTime modified, Object key) {
    static Snapshot read(Path path) throws Exception {
      var attrs = Files.readAttributes(path, BasicFileAttributes.class);
      return new Snapshot(attrs.size(), attrs.lastModifiedTime(), attrs.fileKey());
    }

    void requireUnchanged(Path path) throws Exception {
      if (!equals(read(path)))
        throw new HopException("Original INTERLIS file changed during update: " + path);
    }
  }

  private final BaseTransform<?, ?> owner;
  private final InterlisUpdateMeta settings;
  private final List<Compiled> plans = new ArrayList<>();
  private final Map<String, List<Compiled>> scopes = new LinkedHashMap<>();

  InterlisUpdateRunner(BaseTransform<?, ?> owner, InterlisUpdateMeta settings) {
    this.owner = owner;
    this.settings = settings;
  }

  void run() throws Exception {
    String source = InterlisFieldBinding.resolve(owner, settings.getOriginalFile());
    String target = InterlisFieldBinding.resolve(owner, settings.getFileName());
    if (source.isBlank() || target.isBlank())
      throw new HopException("Original and target XTF files are required");
    Path original = Path.of(source).toRealPath();
    Path output = Path.of(target).toAbsolutePath().normalize();
    if (original.equals(output) || (Files.exists(output) && Files.isSameFile(original, output)))
      throw new HopException("Original and target XTF must be different files");
    var snapshot = Snapshot.read(original);
    var budget = settings.spillOptions(owner).divided(3);
    var inputs = new MappedInputReader(owner, settings.getInputs());
    try (var session = new MappedSinkSession(owner, settings, original);
        var patches = new SpillStore<Patch>(new JavaRecordCodec<>(), budget);
        var matched = new SpillStore<String>(new JavaRecordCodec<>(), budget)) {
      session.output.requireBeforePublication(
          () -> {
            snapshot.requireUnchanged(original);
            snapshot.requireUnchanged(Path.of(source));
          });
      compile(session.model);
      gather(inputs, patches);
      snapshot.requireUnchanged(original);
      var writer = session.openWriter(budget);
      var events = new InterlisEventWriter(writer);
      try (var reader =
          XtfTransferReader.open(original, session.model.model().transferDescription())) {
        InterlisObjectEnvelope event;
        while ((event = reader.next()) != null) {
          if (owner.isStopped()) throw new HopException("INTERLIS Update was stopped");
          if (event.eventType() == InterlisEventType.OBJECT)
            event = update(event, patches, matched);
          events.write(event);
          if (event.eventType() == InterlisEventType.OBJECT) owner.incrementLinesOutput();
        }
      }
      snapshot.requireUnchanged(original);
      if (matched.size() != patches.size())
        throw new HopException(
            "Unmatched INTERLIS updates: "
                + (patches.size() - matched.size())
                + "; check class, BID, TID and structure references against "
                + original);
      session.finish();
      owner.logBasic(
          "INTERLIS Update applied " + matched.size() + " patches; original transfer preserved");
    }
  }

  private void compile(InterlisModelContext context) throws Exception {
    for (var config : settings.getInputs()) {
      String cls = InterlisFieldBinding.resolve(owner, config.getClassName());
      var parent =
          context
              .schema()
              .findClass(cls)
              .orElseThrow(() -> new HopException("Update requires a class: " + cls));
      String path = InterlisFieldBinding.resolve(owner, config.getStructurePath());
      var selected =
          ProjectionOptions.selected(
              config.getFields().stream()
                  .map(f -> f.getTargetPath().trim())
                  .collect(java.util.stream.Collectors.toSet()));
      InterlisStructurePlan structure =
          path.isBlank()
              ? null
              : new InterlisStructureProjectionService()
                  .project(context, cls, path, selected)
                  .plan();
      var available =
          structure == null
              ? new InterlisProjectionService().project(context, cls, selected).plan()
              : new InterlisRowMappingPlan(
                  parent, structure.childFields(), structure.warnings(), null, Map.of());
      var mapping = new MappedClassPlan(available, config, owner, false);
      var properties =
          structure == null
              ? parent.effectiveProperties()
              : structure.primitive()
                  ? List.<ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor>of(
                      structure.structureAttribute())
                  : new ArrayList<ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor>(
                      structure.structure().attributes());
      var patch =
          new InterlisPatchPlan(
              cls + (path.isBlank() ? "" : "." + path), mapping.plan.fields(), properties);
      var rowMeta = new RowMeta();
      for (var field : mapping.plan.fields())
        rowMeta.addValueMeta(new HopRowSchemaFactory().createValueMeta(field));
      var compiled = new Compiled(cls, path, mapping, patch, structure, new HopRowCodec(rowMeta));
      plans.add(compiled);
      var classScopes = scopes.computeIfAbsent(cls, ignored -> new ArrayList<>());
      if (classScopes.stream().noneMatch(p -> p.path().equals(path))) classScopes.add(compiled);
    }
    // Reject an object attribute edit overlapping a collection edit, independent of arrival order.
    for (var object : plans)
      if (object.structure() == null)
        for (var child : plans)
          if (child.structure() != null && object.className().equals(child.className()))
            for (var field : object.mapping().plan.fields()) {
              String path = field.propertyPath().dotted();
              if (path.equals(child.path())
                  || path.startsWith(child.path() + ".")
                  || child.path().startsWith(path + "."))
                throw new HopException(
                    "Overlapping INTERLIS update paths: " + path + " and " + child.path());
            }
  }

  private static InterlisFieldBinding key(
      org.apache.hop.core.row.IRowMeta row, String context, String name, boolean required)
      throws Exception {
    return InterlisFieldBinding.bind(
        row, context, name, name, 0, new ValueMetaString(name), required);
  }

  private static String required(InterlisFieldBinding field, Object[] row, String context)
      throws Exception {
    Object value = field.read(row);
    if (value == null || value.toString().isBlank()) throw new HopException("Missing " + context);
    return value.toString();
  }

  private void gather(MappedInputReader inputs, SpillStore<Patch> patches) throws Exception {
    var bindings = new IdentityHashMap<IRowSet, Bound>();
    MappedInputReader.Row row;
    while ((row = inputs.next()) != null) {
      var plan = plans.get(row.inputIndex());
      var config = settings.getInputs().get(row.inputIndex());
      Bound binding = bindings.get(row.rowSet());
      if (binding == null) {
        boolean object = plan.structure() == null;
        binding =
            new Bound(
                plan.mapping().bind(row.meta(), config, owner, null),
                key(
                    row.meta(),
                    config.getTransformName(),
                    object ? owner.resolve(config.getObjectIdField()) : "",
                    object),
                key(
                    row.meta(),
                    config.getTransformName(),
                    object ? owner.resolve(config.getBasketIdField()) : "",
                    object),
                key(
                    row.meta(),
                    config.getTransformName(),
                    object ? "" : owner.resolve(config.getUpdateReferenceField()),
                    !object));
        bindings.put(row.rowSet(), binding);
      }
      UpdateReference reference = null;
      String bid, tid;
      int index = -1;
      if (plan.structure() == null) {
        bid = required(binding.bid(), row.values(), "BID in " + config.getTransformName());
        tid = required(binding.tid(), row.values(), "TID in " + config.getTransformName());
      } else {
        reference =
            UpdateReference.decode(
                required(binding.reference(), row.values(), UpdateReference.FIELD));
        if (!reference.className().equals(plan.className())
            || !reference.path().equals(plan.path()))
          throw new HopException(
              "Structure update reference does not match " + plan.className() + "." + plan.path());
        bid = reference.bid();
        tid = reference.tid();
        index = reference.index();
      }
      String key = patchKey(plan.className(), bid, tid, plan.path(), index);
      var patch =
          new Patch(
              row.inputIndex(),
              plan.codec().encode(binding.fields().values(row.values())),
              reference);
      if (!patches.putIfAbsent(key, patch))
        throw new HopException(
            "Duplicate INTERLIS update: "
                + plan.className()
                + " BID "
                + bid
                + " TID "
                + tid
                + " path "
                + plan.path()
                + " index "
                + index);
    }
  }

  private static String patchKey(String cls, String bid, String tid, String path, int index) {
    return UpdateReference.key(cls, bid, tid, path, Integer.toString(index));
  }

  private InterlisObjectEnvelope update(
      InterlisObjectEnvelope event, SpillStore<Patch> patches, SpillStore<String> matched)
      throws Exception {
    var classScopes = scopes.get(event.className());
    if (classScopes == null) return event;
    Iom_jObject target = null;
    String hash = null;
    for (var scope : classScopes) {
      IomObject originalOwner =
          scope.structure() == null
              ? null
              : new IomFieldReader()
                  .navigateSingleStructure(event.object(), scope.structure().pathSegments());
      int count =
          scope.structure() == null
              ? 1
              : originalOwner == null
                  ? 0
                  : originalOwner.getattrvaluecount(scope.structure().attributeName());
      for (int i = 0; i < count; i++) {
        String key =
            patchKey(
                event.className(),
                event.basketId(),
                event.objectId(),
                scope.path(),
                scope.structure() == null ? -1 : i);
        Patch patch = patches.get(key);
        if (patch == null) continue;
        if (event.operation() == InterlisObjectOperation.DELETE)
          throw new HopException("Cannot update deleted object " + event.objectId());
        if (!matched.putIfAbsent(key, key))
          throw new HopException(
              "Duplicate original object identity: "
                  + event.className()
                  + " BID "
                  + event.basketId()
                  + " TID "
                  + event.objectId());
        var plan = plans.get(patch.input());
        if (patch.reference() != null) {
          if (hash == null) hash = UpdateReference.fingerprint(event.object());
          if (!hash.equals(patch.reference().fingerprint()))
            throw new HopException(
                "Stale structure update reference: "
                    + event.className()
                    + " BID "
                    + event.basketId()
                    + " TID "
                    + event.objectId()
                    + " path "
                    + scope.path());
        }
        if (target == null) target = new Iom_jObject(event.object());
        Object[] values = plan.codec().decode(patch.values());
        if (scope.structure() == null) plan.patch().apply(target, values);
        else applyChild(target, plan, i, values);
      }
    }
    return target == null
        ? event
        : new InterlisObjectEnvelope(
            event.eventType(),
            event.modelName(),
            event.topicName(),
            event.basketId(),
            event.className(),
            event.objectId(),
            event.operation(),
            target,
            event.basket(),
            event.transferMetadata());
  }

  private static void applyChild(Iom_jObject target, Compiled plan, int index, Object[] values)
      throws Exception {
    var structure = plan.structure();
    var parent =
        (Iom_jObject)
            new IomFieldReader().navigateSingleStructure(target, structure.pathSegments());
    String attr = structure.attributeName();
    if (structure.primitive()) {
      if (values.length != 1 || values[0] == null)
        throw new HopException("Undefined collection element in " + plan.path());
      var wrapper = new Iom_jObject("value", null);
      plan.patch().apply(wrapper, values);
      String primitive = wrapper.getattrprim(attr, 0);
      if (primitive != null) parent.setattrvalue(attr, index, primitive);
      else parent.changeattrobj(attr, index, wrapper.getattrobj(attr, 0));
    } else {
      var child = parent.getattrobj(attr, index);
      if (child == null || !structure.allowedChildTypes().contains(child.getobjecttag()))
        throw new HopException("Incompatible structure child: " + plan.path() + " index " + index);
      plan.patch().apply((Iom_jObject) child, values);
    }
  }
}
