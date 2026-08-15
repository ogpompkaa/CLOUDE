package pl.corekit.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.corekit.CoreKitPlugin;
import pl.corekit.config.ConfigManager;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Localisation service built on Adventure + MiniMessage.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Language files are copied to the data folder on first run so operators
 *       can edit them, while the bundled copies act as an always-present
 *       fallback for missing keys.</li>
 *   <li>Messages are parsed with MiniMessage rather than legacy {@code §} codes,
 *       giving hex colours, gradients, click/hover events and clean placeholder
 *       substitution.</li>
 *   <li>The message map is flattened to dotted keys once at load time, so
 *       {@link #render} is a cheap map lookup on the hot path.</li>
 * </ul>
 */
public final class MessageService {

    private final CoreKitPlugin plugin;
    private final ConfigManager config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final Map<String, String> entries = new HashMap<>();
    private Component prefix = Component.empty();

    public MessageService(CoreKitPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        // Ship every bundled language to disk once, without clobbering edits.
        saveBundledLanguage("en");
        saveBundledLanguage("pl");

        String language = config.settings().language();
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // Fall back to the bundled file of the same language, then to English, so
        // a key an operator deleted still resolves instead of showing raw text.
        applyBundledDefaults(yaml, language);

        entries.clear();
        flatten("", yaml.getConfigurationSection("messages"));

        this.prefix = miniMessage.deserialize(raw("prefix", ""));
    }

    /** Renders a message to a Component, applying the given placeholders. */
    public Component render(String key, TagResolver... resolvers) {
        return miniMessage.deserialize(raw(key, key), resolvers);
    }

    /** Renders a message prefixed with the configured plugin prefix. */
    public Component renderPrefixed(String key, TagResolver... resolvers) {
        return prefix.append(render(key, resolvers));
    }

    /** Sends a prefixed message to a recipient. */
    public void send(CommandSender recipient, String key, TagResolver... resolvers) {
        recipient.sendMessage(renderPrefixed(key, resolvers));
    }

    /** Convenience factory mirroring MiniMessage's placeholder tags. */
    public static TagResolver placeholder(String name, String value) {
        return Placeholder.unparsed(name, value);
    }

    private String raw(String key, String fallback) {
        return entries.getOrDefault(key, fallback);
    }

    private void applyBundledDefaults(YamlConfiguration yaml, String language) {
        InputStream stream = plugin.getResource("lang/" + language + ".yml");
        if (stream == null) {
            stream = plugin.getResource("lang/en.yml");
        }
        if (stream != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
            yaml.options().copyDefaults(true);
        }
    }

    private void saveBundledLanguage(String language) {
        String path = "lang/" + language + ".yml";
        if (!new File(plugin.getDataFolder(), path).exists()
                && plugin.getResource(path) != null) {
            plugin.saveResource(path, false);
        }
    }

    /** Recursively collapses a nested section into dotted keys. */
    private void flatten(String prefix, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                flatten(path, section.getConfigurationSection(key));
            } else {
                entries.put(path, section.getString(key, ""));
            }
        }
    }
}
