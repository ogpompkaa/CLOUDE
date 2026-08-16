package pl.ultrahc.paper.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

/**
 * Ladowanie i formatowanie komunikatow z messages.yml (PL).
 * Zaden tekst do gracza nie jest hardkodowany — wszystko przez klucze.
 */
public class MessagesManager {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;
    private String prefix;

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    public MessagesManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResource("messages.yml", false);
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
        this.prefix = cfg.getString("prefix", "");
    }

    /** Surowy tekst (z kodami &), bez prefiksu, z podstawieniem %klucz% -> wartosc. */
    public String raw(String path, Map<String, String> placeholders) {
        String value = cfg.getString(path, path);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                value = value.replace("%" + e.getKey() + "%", e.getValue());
            }
        }
        return value;
    }

    public String raw(String path) {
        return raw(path, null);
    }

    /** Lista surowych linii (np. dialog NPC). */
    public java.util.List<String> rawList(String path) {
        return cfg.getStringList(path);
    }

    /** Komponent z prefiksem (do wiadomosci czatu). */
    public Component prefixed(String path, Map<String, String> placeholders) {
        return LEGACY.deserialize(prefix + raw(path, placeholders));
    }

    /** Komponent bez prefiksu (scoreboard, tytuly). */
    public Component component(String path, Map<String, String> placeholders) {
        return LEGACY.deserialize(raw(path, placeholders));
    }

    public Component legacy(String withAmpersand) {
        return LEGACY.deserialize(withAmpersand);
    }

    /** Usuwa kody koloru/formatu (&x) — czysty tekst (np. dla placeholderow). */
    public String legacyStrip(String withAmpersand) {
        if (withAmpersand == null) return "";
        return withAmpersand.replaceAll("(?i)&[0-9A-FK-OR]", "").trim();
    }
}
