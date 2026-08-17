package pl.ultrahc.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import pl.ultrahc.common.instances.InstanceRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.LogManager;

/**
 * Plugin Velocity: rozdziela graczy miedzy arena-serwery na podstawie wspolnego
 * rejestru instancji (patrz DECYZJE, sekcja 1). Komenda /play znajduje dolaczalna
 * arene i przenosi gracza. Wlasciwy transfer z GUI lobby idzie kanalem
 * BungeeCord 'Connect' (obslugiwanym natywnie przez Velocity).
 */
@Plugin(id = "ultrahc", name = "UltraHC-Velocity", version = "0.1.0",
        description = "Routing instancji UHC", authors = {"UltraHC"})
public class UltraHcVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private InstanceRegistry registry;
    private int teamSize = 1;
    private long staleMillis = 10_000L;

    @Inject
    public UltraHcVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        Properties props = loadConfig();
        String url = props.getProperty("jdbc-url", "jdbc:sqlite:ultrahc.db");
        String user = props.getProperty("user", "");
        String pass = props.getProperty("password", "");
        this.teamSize = Integer.parseInt(props.getProperty("team-size", "1"));
        this.staleMillis = Long.parseLong(props.getProperty("instance-stale-seconds", "10")) * 1000L;

        this.registry = new InstanceRegistry(url, user, pass, jul());
        try {
            registry.init();
        } catch (Exception ex) {
            logger.error("[UltraHC] Nie udalo sie polaczyc z rejestrem instancji: {}", ex.getMessage());
            this.registry = null;
        }

        CommandManager cm = proxy.getCommandManager();
        CommandMeta meta = cm.metaBuilder("play").aliases("uhc").plugin(this).build();
        cm.register(meta, new PlayCommand(proxy, () -> registry, () -> teamSize, () -> staleMillis, logger));
        logger.info("[UltraHC] Velocity gotowe. Komenda /play routuje do dolaczalnej areny.");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent e) {
        if (registry != null) registry.close();
    }

    /** Wczytuje registry.properties z katalogu danych; tworzy domyslny, gdy brak. */
    private Properties loadConfig() {
        Properties props = new Properties();
        Path file = dataDir.resolve("registry.properties");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dataDir);
                try (InputStream in = getClass().getResourceAsStream("/registry.properties")) {
                    if (in != null) Files.copy(in, file);
                }
                if (!Files.exists(file)) {
                    // Zapis domyslnych wartosci.
                    props.setProperty("jdbc-url", "jdbc:mysql://127.0.0.1:3306/ultrahc");
                    props.setProperty("user", "ultrahc");
                    props.setProperty("password", "zmien_mnie");
                    props.setProperty("team-size", "1");
                    props.setProperty("instance-stale-seconds", "10");
                    try (OutputStream out = Files.newOutputStream(file)) {
                        props.store(out, "Konfiguracja rejestru instancji UltraHC (Velocity)");
                    }
                    logger.warn("[UltraHC] Utworzono domyslny {} — ustaw dane bazy (MySQL w produkcji).", file);
                    return props;
                }
            }
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
        } catch (IOException ex) {
            logger.error("[UltraHC] Blad wczytywania registry.properties: {}", ex.getMessage());
        }
        return props;
    }

    /** InstanceRegistry loguje przez java.util.logging — most na cichy logger JUL. */
    private java.util.logging.Logger jul() {
        LogManager.getLogManager();
        return java.util.logging.Logger.getLogger("UltraHC-Velocity");
    }
}
