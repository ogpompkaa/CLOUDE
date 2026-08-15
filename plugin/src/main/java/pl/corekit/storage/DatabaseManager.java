package pl.corekit.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.corekit.CoreKitPlugin;
import pl.corekit.config.Settings;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the connection pool and the executor on which all database work runs.
 *
 * <p>Every query is dispatched to a dedicated executor so the main server thread
 * is never blocked on I/O — the single most common cause of TPS drops in
 * amateur plugins. Callers receive a {@link CompletableFuture}; results that
 * must touch the Bukkit API are hopped back onto the main thread with
 * {@link #sync(Runnable)}.
 *
 * <p>SQLite specifics handled here: WAL journalling (enabled once, persisted in
 * the file header) for concurrent readers alongside a single writer, and
 * per-connection {@code foreign_keys=ON} enforcement. The pool is intentionally
 * small — SQLite serialises writes regardless of pool size.
 */
public final class DatabaseManager {

    private final CoreKitPlugin plugin;
    private final Settings settings;

    private HikariDataSource dataSource;
    private ExecutorService executor;

    public DatabaseManager(CoreKitPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void initialize() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), settings.storageFile());
        //noinspection ResultOfMethodCallIgnored
        dbFile.getParentFile().mkdirs();

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("CoreKit-SQLite");
        hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikari.setMaximumPoolSize(settings.poolSize());
        hikari.setConnectionTimeout(TimeUnit.SECONDS.toMillis(10));
        // Enforced per physical connection; WAL is set once in the schema step.
        hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");

        this.dataSource = new HikariDataSource(hikari);

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "CoreKit-DB-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
        this.executor = Executors.newFixedThreadPool(Math.max(2, settings.poolSize()), factory);

        applySchema();
    }

    private void applySchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // Persistent DB-level setting; survives across connections and restarts.
            statement.execute("PRAGMA journal_mode = WAL");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        uuid             TEXT    PRIMARY KEY,
                        name             TEXT    NOT NULL,
                        first_seen       INTEGER NOT NULL,
                        last_seen        INTEGER NOT NULL,
                        playtime_seconds INTEGER NOT NULL DEFAULT 0
                    )""");
        }
    }

    /** Runs a query that returns a value, off the main thread. */
    public <T> CompletableFuture<T> query(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return function.apply(connection);
            } catch (SQLException ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    /** Runs a statement with no return value, off the main thread. */
    public CompletableFuture<Void> execute(SqlConsumer consumer) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                consumer.accept(connection);
            } catch (SQLException ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    /** Hops a callback back onto the main server thread (for Bukkit API access). */
    public void sync(Runnable runnable) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, runnable);
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                // Give in-flight writes a chance to finish before closing the pool.
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
