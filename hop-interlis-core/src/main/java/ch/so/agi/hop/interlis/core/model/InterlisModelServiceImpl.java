package ch.so.agi.hop.interlis.core.model;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.Main;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.Ili2cMetaAttrs;
import ch.interlis.ili2c.metamodel.TransferDescription;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles INTERLIS models through the ili2c compiler entry points.
 *
 * <p>Compilation results are cached per resolved request. Local model files contribute their
 * last-modified timestamp to the cache key so that model edits during development are picked up.
 */
public final class InterlisModelServiceImpl implements InterlisModelService {

  private final ConcurrentHashMap<String, CompiledInterlisModel> cache =
      new ConcurrentHashMap<>();
  private final InterlisSchemaExtractor schemaExtractor = new InterlisSchemaExtractor();

  @Override
  public CompiledInterlisModel compile(ModelSource source, ModelCompileOptions options)
      throws InterlisModelException {
    String cacheKey = cacheKey(source, options);
    CompiledInterlisModel cached = cache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    CompiledInterlisModel compiled = doCompile(source, options);
    cache.put(cacheKey, compiled);
    return compiled;
  }

  private CompiledInterlisModel doCompile(ModelSource source, ModelCompileOptions options)
      throws InterlisModelException {
    List<Path> resolvedFiles = resolveModelFiles(source);
    if (resolvedFiles.isEmpty()) {
      throw new InterlisModelException(
          "No INTERLIS models to compile: give at least one .ili file or a model name "
              + "resolvable through the model directories");
    }

    Configuration config = new Configuration();
    config.setAutoCompleteModelList(true);
    config.setGenerateWarnings(false);
    for (Path file : resolvedFiles) {
      config.addFileEntry(new FileEntry(file.toString(), FileEntryKind.ILIMODELFILE));
    }

    Settings settings = new Settings();
    if (options.iliLanguageVersion() != null) {
      settings.setValue(Ili2cSettings.ILI_LANGUAGE_VERSION, options.iliLanguageVersion());
    }
    if (!source.modelDirectories().isEmpty()) {
      settings.setValue(
          Ili2cSettings.ILIDIRS,
          String.join(Ili2cSettings.ILIDIR_SEPARATOR, source.modelDirectories()));
    }

    TransferDescription transferDescription;
    try {
      transferDescription = Main.runCompiler(config, settings, new Ili2cMetaAttrs());
    } catch (Exception e) {
      throw new InterlisModelException(
          "Failed to compile INTERLIS models "
              + source.modelNames()
              + " from "
              + source.iliFiles()
              + ": "
              + e.getMessage(),
          e);
    }
    if (transferDescription == null) {
      throw new InterlisModelException(
          "INTERLIS model compilation returned no TransferDescription for "
              + source.modelNames());
    }

    List<String> compiledNames = new ArrayList<>(source.modelNames());
    for (Path file : resolvedFiles) {
      String modelName = modelNameFromFile(file);
      if (!compiledNames.contains(modelName)) {
        compiledNames.add(modelName);
      }
    }
    verifyCompiledModels(transferDescription, compiledNames);
    return new CompiledInterlisModel(transferDescription, compiledNames);
  }

  /**
   * Guards against ili2c silently ignoring a requested model (e.g. because of a language version
   * mismatch): every explicitly requested model must be present in the compiled result.
   */
  private void verifyCompiledModels(
      TransferDescription transferDescription, List<String> expectedNames)
      throws InterlisModelException {
    java.util.HashSet<String> compiled = new java.util.HashSet<>();
    for (java.util.Iterator<ch.interlis.ili2c.metamodel.Model> it =
            transferDescription.iterator();
        it.hasNext(); ) {
      compiled.add(it.next().getName());
    }
    for (String expected : expectedNames) {
      if (!compiled.contains(expected)) {
        throw new InterlisModelException(
            "Model "
                + expected
                + " was not included in the compilation result "
                + compiled
                + "; check the model name, its INTERLIS language version and the model directories");
      }
    }
  }

  /**
   * Resolves the request into a list of model files. Explicit files are used as-is; model names
   * are looked up as {@code <name>.ili} inside every model directory.
   */
  private List<Path> resolveModelFiles(ModelSource source) throws InterlisModelException {
    LinkedHashSet<Path> files = new LinkedHashSet<>();

    for (Path file : source.iliFiles()) {
      if (!Files.isRegularFile(file)) {
        throw new InterlisModelException("INTERLIS model file does not exist: " + file);
      }
      files.add(file.toAbsolutePath().normalize());
    }

    for (String modelName : source.modelNames()) {
      Path resolved = null;
      for (String directory : source.modelDirectories()) {
        Path candidate = Path.of(directory, modelName + ".ili");
        if (Files.isRegularFile(candidate)) {
          resolved = candidate.toAbsolutePath().normalize();
          break;
        }
      }
      if (resolved == null) {
        throw new InterlisModelException(
            "Model "
                + modelName
                + " was not found in the model directories "
                + source.modelDirectories()
                + " and no .ili file for it was given");
      }
      files.add(resolved);
    }

    return new ArrayList<>(files);
  }

  private String modelNameFromFile(Path file) {
    String name = file.getFileName().toString();
    return name.endsWith(".ili") ? name.substring(0, name.length() - 4) : name;
  }

  private String cacheKey(ModelSource source, ModelCompileOptions options) {
    StringBuilder key = new StringBuilder();
    key.append("ili:").append(options.iliLanguageVersion()).append(';');
    for (Path file : source.iliFiles()) {
      key.append(file.toAbsolutePath().normalize());
      try {
        key.append('@').append(Files.getLastModifiedTime(file).toMillis());
      } catch (IOException e) {
        key.append("@missing");
      }
      key.append(';');
    }
    for (String modelName : source.modelNames()) {
      key.append("name:").append(modelName).append(';');
    }
    for (String directory : source.modelDirectories()) {
      key.append("dir:").append(directory).append(';');
    }
    return key.toString();
  }

  @Override
  public List<InterlisClassDescriptor> listTransferableClasses(CompiledInterlisModel model) {
    return schemaExtractor.extract(model.transferDescription()).classes();
  }

  @Override
  public Optional<InterlisClassDescriptor> findClass(
      CompiledInterlisModel model, String qualifiedName) {
    if (qualifiedName == null) {
      return Optional.empty();
    }
    return schemaExtractor.extract(model.transferDescription()).findClass(qualifiedName);
  }

  @Override
  public void clearCache() {
    cache.clear();
  }
}
