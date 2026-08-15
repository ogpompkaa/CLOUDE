package pl.ultrahc.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pl.ultrahc.paper.UltraHcPlugin;

/**
 * Formatowanie czatu z ranga: prefiks poziomu/gwiazdki (+ podium #1/#2/#3 UHC),
 * nick i tresc. Uzywa renderera Paper, wiec dziala per-widz i nie psuje innych pluginow.
 */
public class ChatListener implements Listener {

    private final UltraHcPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public ChatListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        e.renderer((source, sourceDisplayName, message, viewer) -> {
            String prefix = plugin.rankFormat().prefix(source.getUniqueId());
            Component name = LEGACY.deserialize(prefix + "&f" + source.getName());
            return name.append(LEGACY.deserialize(plugin.messages().raw("chat.separator"))).append(message);
        });
    }
}
