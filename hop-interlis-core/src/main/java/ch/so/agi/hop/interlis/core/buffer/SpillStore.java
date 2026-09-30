package ch.so.agi.hop.interlis.core.buffer;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** A single-owner sequence and keyed lookup, including its indexes, spill to an H2 file. */
public final class SpillStore<T> implements AutoCloseable {
  private record Entry(String key, long order, byte[] bytes) {}

  private final RecordCodec<T> codec;
  private final SpillOptions options;
  private final NavigableMap<Long, Entry> memory = new TreeMap<>();
  private final Map<String, Long> keys = new HashMap<>();
  private long sequence, charged, peakMemory, peakDisk, count;
  private Path workDir;
  private Connection connection;
  private boolean closed;

  public SpillStore(RecordCodec<T> codec, SpillOptions options) {
    this.codec = Objects.requireNonNull(codec);
    this.options = Objects.requireNonNull(options);
  }

  public long size() {
    return count;
  }

  public boolean spilled() {
    return connection != null;
  }

  public long peakMemoryBytes() {
    return peakMemory;
  }

  public long peakDiskBytes() {
    return peakDisk;
  }

  public Path workDirectory() {
    return workDir;
  }

  public void append(T value) {
    insert(null, sequence, value);
  }

  public void appendOrdered(long order, T value) {
    insert(null, order, value);
  }

  public boolean putIfAbsent(String key, T value) {
    if (containsKey(key)) return false;
    insert(Objects.requireNonNull(key), sequence, value);
    return true;
  }

  public boolean containsKey(String key) {
    ensureOpen();
    if (connection == null) return keys.containsKey(key);
    try (var query = connection.prepareStatement("SELECT 1 FROM records WHERE record_key=?")) {
      query.setString(1, key);
      try (var rows = query.executeQuery()) {
        return rows.next();
      }
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  public T get(String key) {
    ensureOpen();
    if (connection == null) {
      Long id = keys.get(key);
      return id == null ? null : decode(memory.get(id).bytes());
    }
    try (var query =
        connection.prepareStatement("SELECT payload FROM records WHERE record_key=?")) {
      query.setString(1, key);
      try (var rows = query.executeQuery()) {
        return rows.next() ? decode(rows.getBytes(1)) : null;
      }
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  private void insert(String key, long order, T value) {
    ensureOpen();
    try {
      byte[] bytes = codec.encode(value);
      long charge = bytes.length + 256L + (key == null ? 0 : 2L * key.length());
      if (connection == null && charged + charge > options.memoryBytes()) spill();
      long id = sequence++;
      if (connection == null) {
        memory.put(id, new Entry(key, order, bytes));
        if (key != null) keys.put(key, id);
        charged += charge;
        peakMemory = Math.max(peakMemory, charged);
      } else {
        write(id, new Entry(key, order, bytes));
        checkDisk();
      }
      count++;
    } catch (Exception e) {
      throw failure(e);
    }
  }

  private void spill() throws Exception {
    Path directory = options.directory();
    if (directory != null) Files.createDirectories(directory);
    workDir =
        directory == null
            ? Files.createTempDirectory("hop-interlis-spill-")
            : Files.createTempDirectory(directory, "hop-interlis-spill-");
    // Explicit driver instance avoids DriverManager's caller-classloader visibility rules.
    var properties = new Properties();
    properties.setProperty("user", "sa");
    long cacheKiB = Math.max(16, Math.min(16384, options.memoryBytes() / 1024 / 4));
    connection =
        new org.h2.Driver()
            .connect(
                "jdbc:h2:file:"
                    + workDir.resolve("records").toAbsolutePath()
                    + ";DB_CLOSE_ON_EXIT=FALSE;CACHE_SIZE="
                    + cacheKiB
                    + ";MAX_MEMORY_ROWS=128",
                properties);
    try (var statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE records (id BIGINT PRIMARY KEY, record_key VARCHAR UNIQUE, sort_order"
              + " BIGINT, payload BLOB)");
      statement.execute("CREATE INDEX records_order ON records(sort_order,id)");
    }
    for (var entry : memory.entrySet()) write(entry.getKey(), entry.getValue());
    memory.clear();
    keys.clear();
    charged = 0;
    checkDisk();
  }

  private void write(long id, Entry entry) throws SQLException {
    try (var insert = connection.prepareStatement("INSERT INTO records VALUES(?,?,?,?)")) {
      insert.setLong(1, id);
      insert.setString(2, entry.key());
      insert.setLong(3, entry.order());
      insert.setBytes(4, entry.bytes());
      insert.executeUpdate();
    }
  }

  private void checkDisk() throws Exception {
    // With an explicit cap, include dirty pages in the filesystem measurement.
    if (options.maxDiskBytes() > 0)
      try (var checkpoint = connection.createStatement()) {
        checkpoint.execute("CHECKPOINT SYNC");
      }
    long bytes;
    try (var paths = Files.list(workDir)) {
      bytes =
          paths
              .mapToLong(
                  p -> {
                    try {
                      return Files.size(p);
                    } catch (Exception e) {
                      throw failure(e);
                    }
                  })
              .sum();
    }
    peakDisk = Math.max(peakDisk, bytes);
    options.diskBudget().measure(this, bytes, workDir.toString());
  }

  /** Destructive FIFO read; used to drain bounded-queue inputs fairly. */
  public T poll() {
    ensureOpen();
    if (count == 0) return null;
    if (connection == null) {
      var first = memory.pollFirstEntry();
      var entry = first.getValue();
      if (entry.key() != null) keys.remove(entry.key());
      count--;
      charged -=
          entry.bytes().length + 256L + (entry.key() == null ? 0 : 2L * entry.key().length());
      return decode(entry.bytes());
    }
    try (var query =
        connection.prepareStatement("SELECT id,payload FROM records ORDER BY id LIMIT 1")) {
      long id;
      byte[] bytes;
      try (var rows = query.executeQuery()) {
        rows.next();
        id = rows.getLong(1);
        bytes = rows.getBytes(2);
      }
      try (var delete = connection.prepareStatement("DELETE FROM records WHERE id=?")) {
        delete.setLong(1, id);
        delete.executeUpdate();
      }
      count--;
      return decode(bytes);
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  /** Snapshot boundary, lazy payload decoding. Entries may be released as they are consumed. */
  public Iterator<T> iterator(boolean sorted) {
    ensureOpen();
    if (connection == null) {
      var ids = new ArrayList<>(memory.keySet());
      if (sorted)
        ids.sort(
            Comparator.comparingLong((Long id) -> memory.get(id).order())
                .thenComparingLong(Long::longValue));
      var it = ids.iterator();
      return new Iterator<>() {
        public boolean hasNext() {
          return it.hasNext();
        }

        public T next() {
          return decode(memory.get(it.next()).bytes());
        }
      };
    }
    // Keyset iteration keeps no JDBC result set or complete ID list alive.
    long boundary = sequence;
    return new Iterator<>() {
      long lastId = -1, lastOrder = Long.MIN_VALUE;
      byte[] next;
      long nextId, nextOrder;
      boolean exhausted;

      public boolean hasNext() {
        if (next != null) return true;
        if (exhausted) return false;
        String condition = sorted ? "(sort_order>? OR (sort_order=? AND id>?))" : "id>?";
        String order = sorted ? "sort_order,id" : "id";
        try (var query =
            connection.prepareStatement(
                "SELECT id,sort_order,payload FROM records WHERE "
                    + condition
                    + " AND id<? ORDER BY "
                    + order
                    + " LIMIT 1")) {
          int n = 1;
          if (sorted) {
            query.setLong(n++, lastOrder);
            query.setLong(n++, lastOrder);
          }
          query.setLong(n++, lastId);
          query.setLong(n, boundary);
          try (var rows = query.executeQuery()) {
            if (!rows.next()) {
              exhausted = true;
              return false;
            }
            nextId = rows.getLong(1);
            nextOrder = rows.getLong(2);
            next = rows.getBytes(3);
            return true;
          }
        } catch (SQLException e) {
          throw failure(e);
        }
      }

      public T next() {
        if (!hasNext()) throw new NoSuchElementException();
        var bytes = next;
        next = null;
        lastId = nextId;
        lastOrder = nextOrder;
        return decode(bytes);
      }
    };
  }

  private T decode(byte[] bytes) {
    try {
      return codec.decode(bytes);
    } catch (Exception e) {
      throw failure(e);
    }
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("Spill store is closed");
  }

  private static IllegalStateException failure(Exception e) {
    return new IllegalStateException("INTERLIS spill storage failed: " + e.getMessage(), e);
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    memory.clear();
    keys.clear();
    Exception failure = null;
    if (connection != null)
      try {
        connection.close();
      } catch (Exception e) {
        failure = e;
      }
    if (workDir != null)
      try (var paths = Files.walk(workDir)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      } catch (Exception e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
    if (failure != null) throw failure(failure);
    options.diskBudget().release(this);
  }
}
