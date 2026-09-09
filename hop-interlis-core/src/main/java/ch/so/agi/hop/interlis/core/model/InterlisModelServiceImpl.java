package ch.so.agi.hop.interlis.core.model;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.Main;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.Ili2cMetaAttrs;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ilirepository.IliManager;
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
 * content fingerprints to dependency validation so that model edits during development are picked
 * up. The cache is static: compiled models are shared by all service instances (and therefore all
 * transforms) in the JVM. A {@link CompiledInterlisModel} is immutable after construction, so
 * sharing it is safe; model compilation is a design-time operation.
 *
 * <p><b>Thread-safety rule:</b> the INTERLIS libraries (ili2c, iox-ili, ehibasics) are
 * single-threaded by design and not thread-safe. ili2c keeps static compiler state, therefore every
 * compilation must run under {@link #MODEL_LOCK}. The resulting {@code TransferDescription} is
 * treated as immutable afterwards and may be shared freely (verified: its getters return fresh deep
 * copies or are pure reads).
 */
public final class InterlisModelServiceImpl implements InterlisModelService {

  /**
   * Central serialization point for every ili2c use in the plugin. ili2c uses shared static state
   * internally and is not safe for concurrent compilation; all compiles are serialized through this
   * lock (model compilation is a design-time operation). Readers and writers on the other hand are
   * per-instance and never shared between threads, so they do not need this lock.
   */
  private static final Object MODEL_LOCK = new Object();

  private record CacheEntry(
      CompiledInterlisModel model,
      java.util.Map<Path, String> files,
      List<String> directoryFiles) {}

  private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
  private final InterlisSchemaExtractor schemaExtractor = new InterlisSchemaExtractor();

  @Override
  public CompiledInterlisModel compile(ModelSource source, ModelCompileOptions options)
      throws InterlisModelException {
    synchronized (MODEL_LOCK) {
      String key = cacheKey(source, options);
      List<String> listing = directoryFiles(source);
      CacheEntry entry = cache.get(key);
      if (entry != null
          && entry.directoryFiles().equals(listing)
          && entry.files().entrySet().stream()
              .allMatch(f -> f.getValue().equals(fingerprint(f.getKey())))) {
        return entry.model();
      }
      cache.remove(key);
      CompiledInterlisModel model = doCompile(source, options);
      java.util.Map<Path, String> files = new java.util.LinkedHashMap<>();
      for (var it = model.transferDescription().iterator(); it.hasNext(); ) {
        var declared = it.next();
        if (!(declared instanceof ch.interlis.ili2c.metamodel.PredefinedModel)
            && declared.getFileName() != null) {
          Path file = Path.of(declared.getFileName()).toAbsolutePath().normalize();
          files.put(file, fingerprint(file));
        }
      }
      for (Path file : source.iliFiles())
        files.put(file.toAbsolutePath().normalize(), fingerprint(file));
      cache.put(key, new CacheEntry(model, java.util.Map.copyOf(files), listing));
      return model;
    }
  }

  /** Explicit reload; callers with an existing model keep their immutable instance. */
  public CompiledInterlisModel reload(ModelSource source, ModelCompileOptions options)
      throws InterlisModelException {
    synchronized (MODEL_LOCK) {
      cache.remove(cacheKey(source, options));
      return compile(source, options);
    }
  }

  private static String fingerprint(Path file) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(file)) {
        byte[] buffer = new byte[8192];
        for (int count; (count = input.read(buffer)) != -1; ) digest.update(buffer, 0, count);
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (IOException e) {
      return "unavailable";
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Detect added/removed local candidates that can change repository override resolution. */
  private static List<String> directoryFiles(ModelSource source) throws InterlisModelException {
    List<String> files = new ArrayList<>();
    for (String directory : source.modelDirectories()) {
      if (directory.contains("://")) continue;
      Path path = Path.of(directory).toAbsolutePath().normalize();
      if (!Files.isDirectory(path)) continue;
      try (var paths = Files.walk(path)) {
        files.addAll(
            paths
                .filter(
                    p ->
                        p.toString().endsWith(".ili")
                            || p.getFileName().toString().equals("ilimodels.xml"))
                .map(p -> p.toString().endsWith(".xml") ? p + "@" + fingerprint(p) : p.toString())
                .sorted()
                .toList());
      } catch (IOException e) {
        throw new InterlisModelException("Cannot inspect model directory " + path, e);
      }
    }
    return List.copyOf(files);
  }

  private CompiledInterlisModel doCompile(ModelSource source, ModelCompileOptions options)
      throws InterlisModelException {
    List<Path> resolvedFiles = resolveModelFiles(source, options);
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
          "INTERLIS model compilation returned no TransferDescription for " + source.modelNames());
    }

    // IliManager returns the complete configuration, including imported model files.  Only the
    // names explicitly requested by the caller are verification targets; imported files are
    // dependencies and their cache filenames are not necessarily the INTERLIS model names (for
    // example, versioned repository filenames such as Units-20120220.ili).
    verifyCompiledModels(transferDescription, source.modelNames());
    List<String> compiledNames = new ArrayList<>(source.modelNames());
    var explicitFiles =
        source.iliFiles().stream().map(p -> p.toAbsolutePath().normalize()).toList();
    for (var it = transferDescription.iterator(); it.hasNext(); ) {
      var model = it.next();
      if (model.getFileName() != null
          && explicitFiles.contains(Path.of(model.getFileName()).toAbsolutePath().normalize())
          && !compiledNames.contains(model.getName())) compiledNames.add(model.getName());
    }
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
    for (java.util.Iterator<ch.interlis.ili2c.metamodel.Model> it = transferDescription.iterator();
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
                + "; check the model name, its INTERLIS language version and the model"
                + " directories");
      }
    }
  }

  /**
   * Resolves the request into a list of model files. Explicit files are used as-is; model names are
   * looked up as {@code <name>.ili} inside every model directory. When a model is not found locally
   * and a model directory is an INTERLIS model repository ({@code http(s)://...}), the model is
   * fetched through the ilirepository machinery into its local cache ({@code ~/.ilicache} by
   * default, 24h TTL, 15s/40s connect/read timeouts).
   */
  private List<Path> resolveModelFiles(ModelSource source, ModelCompileOptions options)
      throws InterlisModelException {
    LinkedHashSet<Path> files = new LinkedHashSet<>();

    for (Path file : source.iliFiles()) {
      if (!Files.isRegularFile(file)) {
        throw new InterlisModelException("INTERLIS model file does not exist: " + file);
      }
      files.add(file.toAbsolutePath().normalize());
    }

    List<String> unresolvedModelNames = new ArrayList<>();
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
        unresolvedModelNames.add(modelName);
      } else {
        files.add(resolved);
      }
    }

    if (!unresolvedModelNames.isEmpty()) {
      files.addAll(
          resolveFromRepositories(unresolvedModelNames, source.modelDirectories(), options));
    }

    return new ArrayList<>(files);
  }

  /** {@code true} for INTERLIS model repository URIs instead of plain directories. */
  private static boolean isRepositoryUri(String directory) {
    return directory.startsWith("http://") || directory.startsWith("https://");
  }

  /**
   * The local download cache for model repositories. Defaults to the ilirepository standard cache
   * ({@code ~/.ilicache}); tests and deployments can override it with the system property {@code
   * hop.interlis.repository.cache}.
   */
  private static java.io.File repositoryCacheDir() {
    String override = System.getProperty("hop.interlis.repository.cache");
    if (override != null && !override.isBlank()) {
      return new java.io.File(override);
    }
    return null;
  }

  /**
   * Resolves named models through ili2c's repository manager. IliManager delegates the search to
   * RepositoryVisitor/ModelFinder, which follows the repository site graph (including subsidiary
   * and parent sites) and returns the complete dependency configuration with cached local files.
   */
  private List<Path> resolveFromRepositories(
      List<String> modelNames, List<String> directories, ModelCompileOptions options)
      throws InterlisModelException {
    List<String> searchPaths =
        directories.stream()
            .filter(directory -> directory != null && !directory.isBlank())
            .toList();
    if (searchPaths.isEmpty()) {
      throw new InterlisModelException(
          "Models "
              + modelNames
              + " were not found locally and no model directories or repositories were configured"
              + " in "
              + directories
              + ". Place the .ili files in a model directory to override repository lookup");
    }

    IliManager manager = new IliManager();
    manager.setRepositories(searchPaths.toArray(String[]::new));
    java.io.File cacheOverride = repositoryCacheDir();
    if (cacheOverride != null) {
      manager.setCache(cacheOverride);
    }

    double iliVersion = iliVersion(options);
    try {
      Configuration configuration = manager.getConfig(new ArrayList<>(modelNames), iliVersion);
      List<Path> resolved = new ArrayList<>();
      for (java.util.Iterator<?> entries = configuration.iteratorFileEntry(); entries.hasNext(); ) {
        FileEntry entry = (FileEntry) entries.next();
        resolved.add(Path.of(entry.getFilename()).toAbsolutePath().normalize());
      }
      if (resolved.isEmpty()) {
        throw new InterlisModelException(
            "The model repositories returned no .ili files for models "
                + modelNames
                + ": "
                + searchPaths);
      }
      return resolved;
    } catch (ch.interlis.ili2c.Ili2cException e) {
      String modelStatus =
          modelNames.stream()
              .map(
                  modelName ->
                      "The model "
                          + modelName
                          + " was not found (a repository may be unreachable or does not contain"
                          + " model "
                          + modelName
                          + ")")
              .collect(java.util.stream.Collectors.joining("; "));
      throw new InterlisModelException(
          "Models "
              + modelNames
              + " were not found locally and could not be resolved through the model repository "
              + "network "
              + searchPaths
              + ". "
              + modelStatus
              + ". The repository lookup reported: "
              + e.getMessage()
              + ". Place the .ili files in a model directory to override repository lookup",
          e);
    }
  }

  private static double iliVersion(ModelCompileOptions options) throws InterlisModelException {
    if (options == null || options.iliLanguageVersion() == null) {
      return 0.0;
    }
    try {
      return Double.parseDouble(options.iliLanguageVersion());
    } catch (NumberFormatException e) {
      throw new InterlisModelException(
          "Unsupported INTERLIS language version " + options.iliLanguageVersion(), e);
    }
  }

  private String cacheKey(ModelSource source, ModelCompileOptions options) {
    StringBuilder key = new StringBuilder();
    key.append("ili:").append(options.iliLanguageVersion()).append(';');
    for (Path file : source.iliFiles()) {
      key.append(file.toAbsolutePath().normalize());
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
    return schemaExtractor.extract(model.transferDescription()).selectableClasses();
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
    synchronized (MODEL_LOCK) {
      cache.clear();
    }
  }
}
