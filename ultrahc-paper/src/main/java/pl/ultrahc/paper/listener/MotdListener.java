package pl.ultrahc.paper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import pl.ultrahc.paper.UltraHcPlugin;

/** MOTD na liscie serwerow (branding UltraHC). Tekst w messages.yml (motd.*). */
public class MotdListener implements Listener {

    private final UltraHcPlugin plugin;

    public MotdListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        String l1 = plugin.messages().raw("motd.line1");
        String l2 = plugin.messages().raw("motd.line2");
        e.motd(plugin.messages().legacy(l1 + "\n" + l2));
    }
}
