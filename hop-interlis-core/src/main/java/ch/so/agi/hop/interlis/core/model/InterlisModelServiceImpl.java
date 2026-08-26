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
 * The cache is static: compiled models are shared by all service instances (and therefore all
 * transforms) in the JVM. A {@link CompiledInterlisModel} is immutable after construction, so
 * sharing it is safe; model compilation is a design-time operation.
 *
 * <p><b>Thread-safety rule:</b> the INTERLIS libraries (ili2c, iox-ili, ehibasics) are
 * single-threaded by design and not thread-safe. ili2c keeps static compiler state, therefore
 * every compilation must run under {@link #MODEL_LOCK}. The resulting
 * {@code TransferDescription} is treated as immutable afterwards and may be shared freely
 * (verified: its getters return fresh deep copies or are pure reads).
 */
public final class InterlisModelServiceImpl implements InterlisModelService {

  /**
   * Central serialization point for every ili2c use in the plugin. ili2c uses shared static
   * state internally and is not safe for concurrent compilation; all compiles are serialized
   * through this lock (model compilation is a design-time operation). Readers and writers on
   * the other hand are per-instance and never shared between threads, so they do not need this
   * lock.
   */
  private static final Object MODEL_LOCK = new Object();

  private static final ConcurrentHashMap<String, CompiledInterlisModel> cache =
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

    synchronized (MODEL_LOCK) {
      // Double-checked: a thread waiting for the lock may find the result another thread
      // compiled in the meantime; compiling again would break the one-compile guarantee and
      // could hand out a second TransferDescription instance for the same request.
      cached = cache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
      CompiledInterlisModel compiled = doCompile(source, options);
      cache.put(cacheKey, compiled);
      return compiled;
    }
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
   * are looked up as {@code <name>.ili} inside every model directory. When a model is not found
   * locally and a model directory is an INTERLIS model repository ({@code http(s)://...}), the
   * model is fetched through the ilirepository machinery into its local cache
   * ({@code ~/.ilicache} by default, 24h TTL, 15s/40s connect/read timeouts).
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
        if (isRepositoryUri(directory)) {
          continue;
        }
        Path candidate = Path.of(directory, modelName + ".ili");
        if (Files.isRegularFile(candidate)) {
          resolved = candidate.toAbsolutePath().normalize();
          break;
        }
      }
      if (resolved == null) {
        resolved = resolveFromRepository(modelName, source.modelDirectories());
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

  /** {@code true} for INTERLIS model repository URIs instead of plain directories. */
  private static boolean isRepositoryUri(String directory) {
    return directory.startsWith("http://") || directory.startsWith("https://");
  }

  /**
   * Finds the repository file containing the model and downloads it into the local repository
   * cache. The index carries the schema language per entry; version pinning happens by model
   * name.
   */
  private static java.io.File findModelFile(
      ch.interlis.ilirepository.impl.RepositoryAccess access,
      String repository,
      ch.interlis.ilirepository.IliFiles iliFiles,
      String modelName)
      throws Exception {
    for (java.util.Iterator<ch.interlis.ili2c.modelscan.IliFile> files =
            iliFiles.iteratorFile();
        files.hasNext(); ) {
      ch.interlis.ili2c.modelscan.IliFile file = files.next();
      for (java.util.Iterator<ch.interlis.ili2c.modelscan.IliModel> models =
              file.iteratorModel();
          models.hasNext(); ) {
        ch.interlis.ili2c.modelscan.IliModel model = models.next();
        if (!modelName.equals(model.getName())) {
          continue;
        }
        String remote = repository + (repository.endsWith("/") ? "" : "/") + file.getPath();
        java.io.File local = access.getLocalFileLocation(remote);
        if (local != null && local.isFile()) {
          return local;
        }
        return null;
      }
    }
    return null;
  }

  /**
   * The local download cache for model repositories. Defaults to the ilirepository standard cache
   * ({@code ~/.ilicache}); tests and deployments can override it with the system property
   * {@code hop.interlis.repository.cache}.
   */
  private static java.io.File repositoryCacheDir() {
    String override = System.getProperty("hop.interlis.repository.cache");
    if (override != null && !override.isBlank()) {
      return new java.io.File(override);
    }
    return null;
  }

  /**
   * Looks the model up in the configured model repositories. Returns {@code null} when there is no
   * repository to ask; fails with actionable diagnostics when a repository is unreachable or the
   * model is unknown there.
   */
  private Path resolveFromRepository(String modelName, List<String> directories)
      throws InterlisModelException {
    List<String> repositories = new ArrayList<>();
    for (String directory : directories) {
      if (isRepositoryUri(directory)) {
        repositories.add(directory);
      }
    }
    if (repositories.isEmpty()) {
      return null;
    }

    List<String> problems = new ArrayList<>();
    for (String repository : repositories) {
      try {
        ch.interlis.ilirepository.impl.RepositoryAccess access =
            new ch.interlis.ilirepository.impl.RepositoryAccess();
        java.io.File cacheOverride = repositoryCacheDir();
        if (cacheOverride != null) {
          access.setCache(cacheOverride);
        }
        ch.interlis.ilirepository.IliFiles iliFiles = access.getIliFiles(repository);
        if (iliFiles == null) {
          problems.add(
              repository
                  + " is unreachable (offline?); its model index could not be loaded");
          continue;
        }
        java.io.File modelFile = findModelFile(access, repository, iliFiles, modelName);
        if (modelFile != null) {
          return modelFile.toPath().toAbsolutePath().normalize();
        }
        problems.add(repository + " does not contain model " + modelName);
      } catch (Exception e) {
        problems.add(repository + " failed: " + e.getMessage());
      }
    }
    throw new InterlisModelException(
        "Model "
            + modelName
            + " was not found locally and could not be resolved from the model repositories: "
            + String.join("; ", problems)
            + ". Place "
            + modelName
            + ".ili in a model directory to override the repositories");
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
  public java.util.List<String> detectModelNames(java.nio.file.Path transferFile)
      throws InterlisModelException {
    try (ch.so.agi.hop.interlis.core.io.XtfTransferReader reader =
        ch.so.agi.hop.interlis.core.io.XtfTransferReader.open(transferFile)) {
      reader.next(); // START_TRANSFER carries the model header
      return reader.detectedModelNames();
    } catch (ch.so.agi.hop.interlis.core.io.InterlisReadException e) {
      throw new InterlisModelException(
          "Failed to detect model names from transfer file " + transferFile + ": " + e.getMessage(),
          e);
    }
  }

  @Override
  public void clearCache() {
    cache.clear();
  }
}
