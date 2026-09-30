package ch.so.agi.hop.interlis.core.io;

import ch.interlis.ili2c.generator.XSD24Generator;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.xtf.*;
import ch.interlis.iom_j.xtf.impl.Xtf24WriterAlt;
import ch.interlis.iox.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.xml.stream.XMLStreamWriter;

/**
 * Workaround for iox-ili 1.24.4 omitting ili:bid on XTF 2.4 references. Uses the library's
 * protected XML writer boundary; model interpretation and encoding stay in IOX.
 */
final class ReferencePreservingXtf24Writer implements IoxWriter {
  private final OutputStream output;
  private final ReferenceWriter delegate;
  private XtfModel[] models;
  private IoxFactoryCollection factory = new ch.interlis.iox_j.DefaultIoxFactoryCollection();

  ReferencePreservingXtf24Writer(Path file, TransferDescription td) throws Exception {
    output = Files.newOutputStream(file);
    try {
      delegate = new ReferenceWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), td);
    } catch (Exception e) {
      output.close();
      throw e;
    }
  }

  void setModels(XtfModel[] models) {
    this.models = models;
  }

  @Override
  public void write(IoxEvent event) throws IoxException {
    if (event instanceof StartTransferEvent e)
      delegate.writeStartTransfer(
          e.getSender(),
          e.getComment(),
          models,
          e instanceof XtfStartTransferEvent start ? start : null);
    else if (event instanceof StartBasketEvent e)
      delegate.writeStartBasket(
          e.getType(),
          e.getBid(),
          e.getConsistency(),
          e.getKind(),
          e.getStartstate(),
          e.getEndstate(),
          e.getTopicv(),
          e instanceof ch.interlis.iox_j.StartBasketEvent start
              ? XtfWriter.domainsToString(start.getDomains())
              : null);
    else if (event instanceof ObjectEvent e) delegate.writeObject(e.getIomObject());
    else if (event instanceof EndBasketEvent) delegate.writeEndBasket();
    else if (event instanceof EndTransferEvent) delegate.writeEndTransfer();
    else throw new IoxException("Unknown event: " + event.getClass().getName());
  }

  @Override
  public void flush() throws IoxException {
    delegate.flush();
  }

  @Override
  public void close() throws IoxException {
    Exception failure = null;
    try {
      delegate.close();
    } catch (Exception e) {
      failure = e;
    }
    try {
      output.close();
    } catch (Exception e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    if (failure != null) throw new IoxException(failure);
  }

  @Override
  public IomObject createIomObject(String type, String oid) throws IoxException {
    return factory.createIomObject(type, oid);
  }

  @Override
  public IoxFactoryCollection getFactory() {
    return factory;
  }

  @Override
  public void setFactory(IoxFactoryCollection factory) {
    this.factory = factory;
  }

  private static final class Frame {
    final IomObject object;
    final String attribute;
    final Map<String, Integer> occurrences = new HashMap<>();
    int index;

    Frame(IomObject object, String attribute, int index) {
      this.object = object;
      this.attribute = attribute;
      this.index = index;
    }

    IomObject reference() {
      return object == null || attribute == null ? null : object.getattrobj(attribute, index);
    }
  }

  private static final class ReferenceWriter extends Xtf24WriterAlt {
    private IomObject current;
    private final Deque<Frame> frames = new ArrayDeque<>();

    ReferenceWriter(OutputStreamWriter output, TransferDescription td) throws IoxException {
      super(output, Ili2cUtility.getIoxMappingTable(td));
      XMLStreamWriter original = xout;
      // Forward the public StAX interface. No reflection into IOX's private implementation.
      xout =
          (XMLStreamWriter)
              Proxy.newProxyInstance(
                  XMLStreamWriter.class.getClassLoader(),
                  new Class<?>[] {XMLStreamWriter.class},
                  (proxy, method, args) -> {
                    if (current != null && method.getName().equals("writeStartElement")) {
                      String local = (String) args[args.length == 3 ? 1 : args.length - 1];
                      start(local);
                    }
                    Object result;
                    try {
                      result = method.invoke(original, args);
                    } catch (InvocationTargetException e) {
                      throw e.getCause();
                    }
                    if (current != null
                        && method.getName().equals("writeAttribute")
                        && args.length == 3
                        && XSD24Generator.INTERLIS_XMLNS.equals(args[0])
                        && XSD24Generator.REF_ATTR.equals(args[1])) {
                      var ref = frames.peek().reference();
                      if (ref != null && ref.getobjectrefbid() != null)
                        original.writeAttribute(
                            XSD24Generator.INTERLIS_XMLNS,
                            XSD24Generator.BID_ATTR,
                            ref.getobjectrefbid());
                    }
                    if (current != null && method.getName().equals("writeEndElement")) frames.pop();
                    return result;
                  });
    }

    private void start(String name) {
      if (frames.isEmpty()) {
        frames.push(new Frame(current, null, 0));
        return;
      }
      var parent = frames.peek();
      if (parent.attribute == null) {
        int index = parent.occurrences.merge(name, 1, Integer::sum) - 1;
        frames.push(new Frame(parent.object, name, index));
      } else {
        IomObject child =
            parent.object == null
                ? null
                : parent.object.getattrobj(parent.attribute, parent.index++);
        frames.push(new Frame(child, null, 0));
      }
    }

    @Override
    public void writeObject(IomObject object) throws IoxException {
      current = object;
      frames.clear();
      try {
        super.writeObject(object);
      } finally {
        current = null;
        frames.clear();
      }
    }
  }
}
