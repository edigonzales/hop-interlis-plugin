package ch.so.agi.hop.interlis.core.model;

import java.util.List;
import java.util.Optional;

/**
 * Compiles INTERLIS models and exposes transfer-relevant model information.
 *
 * <p>This is the single entry point for model compilation; transform runtimes and UIs must not
 * invoke the ili2c compiler directly.
 */
public interface InterlisModelService {

  /**
   * Compiles the requested models into a {@link CompiledInterlisModel}.
   *
   * <p>Equal requests return the cached compilation result; the cache key covers files, model
   * names and directories (including file timestamps for local files).
   *
   * @throws InterlisModelException if the models cannot be resolved or do not compile
   */
  CompiledInterlisModel compile(ModelSource source, ModelCompileOptions options)
      throws InterlisModelException;

  /** Lists the transferable (non-structure) classes of all compiled models. */
  List<InterlisClassDescriptor> listTransferableClasses(CompiledInterlisModel model);

  /** Finds a class by its qualified name, e.g. {@code Model.Topic.Class}. */
  Optional<InterlisClassDescriptor> findClass(CompiledInterlisModel model, String qualifiedName);

  /**
   * Detects the model names declared in the header of a transfer file.
   *
   * <p>This reads only the transfer header; the file is closed again afterwards.
   *
   * @param transferFile the transfer (XTF) file
   * @return model names in header order; empty if the header declares none
   * @throws InterlisModelException if the header cannot be read
   */
  java.util.List<String> detectModelNames(java.nio.file.Path transferFile)
      throws InterlisModelException;

  /** Drops all cached compilation results. */
  void clearCache();
}
