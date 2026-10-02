package ch.so.agi.hop.interlis.transforms.output;

import ch.interlis.ili2c.metamodel.*;
import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.buffer.MappedInputReader;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import java.util.*;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.transform.BaseTransform;

/** Runtime of the heterogeneous-input mode; no mixed Hop row schema is ever created. */
final class MappedOutputWriter {
  private record Bound(
      InterlisRowBindings fields, InterlisFieldBinding carrier, InterlisFieldBinding operation) {}

  static void run(BaseTransform<?, ?> owner, InterlisOutputMeta settings) throws Exception {
    var budget = settings.spillOptions(owner);
    var reader = new MappedInputReader(owner, settings.getInputs());
    try (var session = new MappedSinkSession(owner, settings, null);
        var grouped = new GroupedObjectStore(budget.divided(2))) {
      var projection = new InterlisProjectionService();
      var plans = new ArrayList<MappedClassPlan>();
      var bids = new HashMap<String, String>();
      for (var config : settings.getInputs()) {
        var available =
            projection
                .project(
                    session.model,
                    owner.resolve(config.getClassName()),
                    new ProjectionOptions(
                        true, true, false, false, false, true, "_", null, Set.of(), true, true))
                .plan();
        plans.add(new MappedClassPlan(available, config, owner, true));
        if (!config.getStructurePath().isBlank())
          throw new HopException("Structure paths belong to INTERLIS Update");
        if (settings.getBasketMode() == InterlisOutputMeta.BasketMode.PER_TOPIC) {
          String topic = available.root().topicScopedName();
          if (!bids.containsKey(topic)) {
            Topic definition =
                (Topic) session.model.model().transferDescription().getElement(topic);
            Domain domain = definition.getBasketOid();
            if (domain != null
                && domain != PredefinedModel.getInstance().ANYOID
                && domain != PredefinedModel.getInstance().UUIDOID)
              throw new HopException(
                  "Topic "
                      + topic
                      + " has a custom basket OID domain; select FROM_FIELD and supply a BID"
                      + " field");
            bids.put(topic, UUID.randomUUID().toString());
          }
        }
      }
      var bound = new IdentityHashMap<IRowSet, Bound>();
      var mapper = new RowToIomMapper();
      MappedInputReader.Row row;
      while ((row = reader.next()) != null) {
        var config = settings.getInputs().get(row.inputIndex());
        var compiled = plans.get(row.inputIndex());
        var plan = compiled.plan;
        Bound binding = bound.get(row.rowSet());
        if (binding == null) {
          String carrier = InterlisFieldBinding.resolve(owner, config.getSourceObjectField());
          String operation = InterlisFieldBinding.resolve(owner, config.getOperationField());
          binding =
              new Bound(
                  compiled.bind(row.meta(), config, owner, bids.get(plan.root().topicScopedName())),
                  InterlisFieldBinding.bind(
                      row.meta(),
                      config.getTransformName(),
                      carrier,
                      "source object",
                      0,
                      new ValueMetaInterlisObject(carrier),
                      !carrier.isBlank()),
                  InterlisFieldBinding.bind(
                      row.meta(),
                      config.getTransformName(),
                      operation,
                      "operation",
                      0,
                      new ValueMetaString(operation),
                      !operation.isBlank()));
          bound.put(row.rowSet(), binding);
        }
        Object[] values = binding.fields().values(row.values());
        String bid = null;
        for (var field : plan.fields()) {
          if (field.source() == InterlisFieldSource.OBJECT_ID
              || field.source() == InterlisFieldSource.BASKET_ID) {
            Object value = values[field.outputIndex()];
            if (value == null || value.toString().isBlank())
              throw new HopException(
                  "Empty " + field.hopFieldName() + " in " + config.getTransformName());
            if (field.source() == InterlisFieldSource.BASKET_ID) bid = value.toString();
          }
        }
        Object op = binding.operation().read(row.values());
        var operation =
            op == null || op.toString().isBlank()
                ? InterlisObjectOperation.NONE
                : InterlisObjectOperation.valueOf(op.toString().trim());
        var options = new RowWriteOptions(true, bid, operation);
        Object carrier = binding.carrier().read(row.values());
        if (binding.carrier().present()
            && !options.isDelete()
            && (!(carrier instanceof IomObject obj)
                || !plan.root().scopedName().equals(obj.getobjecttag())))
          throw new HopException(
              "Missing or incompatible source object for " + plan.root().scopedName());
        var result =
            carrier == null
                ? mapper.mapAll(values, plan, options)
                : mapper.mapAll((IomObject) carrier, values, plan, options);
        if (binding.operation().present()) result.object().setobjectoperation(operation.toIom());
        for (var object : result.allObjects())
          grouped.add(plan.root().topicScopedName(), bid, object);
      }
      var writer = session.openWriter(budget.divided(2));
      writer.startTransfer("hop-interlis-plugin");
      String bid = null;
      var objects = grouped.iterator();
      while (objects.hasNext()) {
        if (owner.isStopped()) throw new HopException("INTERLIS output was stopped");
        var object = objects.next();
        if (!object.bid().equals(bid)) {
          if (bid != null) writer.endBasket();
          writer.startBasket(object.topic(), object.bid());
          bid = object.bid();
        }
        writer.writeObject(object.object());
        owner.incrementLinesOutput();
      }
      if (bid != null) writer.endBasket();
      writer.endTransfer();
      session.finish();
      owner.logBasic("INTERLIS Output prepared " + grouped.size() + " objects");
    }
  }
}
