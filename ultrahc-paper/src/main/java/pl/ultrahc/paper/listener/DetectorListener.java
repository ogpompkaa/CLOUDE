package pl.ultrahc.paper.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.ultrahc.paper.UltraHcPlugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wykrywacz: po uzyciu, po X sekundach pokazuje na srodku ekranu najblizszego
 * gracza w formacie "Nick - X Kratek". Cooldown z config (detector.*).
 */
public class DetectorListener implements Listener {

    private final UltraHcPlugin plugin;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public DetectorListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.recipes() == null) return;
        ItemStack item = e.getItem();
        if (!"detector".equals(plugin.recipes().recipeIdOf(item))) return;

        e.setCancelled(true);
        Player player = e.getPlayer();
        var cfg = plugin.configManager().raw();
        int cooldown = cfg.getInt("detector.cooldown-seconds", 60);
        long now = System.currentTimeMillis() / 1000L;
        long last = lastUse.getOrDefault(player.getUniqueId(), 0L);
        long remaining = cooldown - (now - last);
        if (remaining > 0) {
            player.sendMessage(plugin.messages().prefixed("detector.cooldown", Map.of("seconds", String.valueOf(remaining))));
            return;
        }
        lastUse.put(player.getUniqueId(), now);

        int delay = cfg.getInt("detector.reveal-delay-seconds", 5);
        player.sendMessage(plugin.messages().prefixed("detector.used", Map.of("seconds", String.valueOf(delay))));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> reveal(player), delay * 20L);
    }

    private void reveal(Player player) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || other.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double d = other.getLocation().distance(player.getLocation());
            if (d < best) { best = d; nearest = other; }
        }
        if (nearest == null) {
            player.sendMessage(plugin.messages().prefixed("detector.none", null));
            return;
        }
        Component main = plugin.messages().component("detector.result", Map.of(
                "player", nearest.getName(), "distance", String.valueOf((int) best)));
        player.showTitle(Title.title(main, Component.empty(),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));

        // Podswietlenie wykrytego gracza (Glowing).
        var cfg = plugin.configManager().raw();
        if (cfg.getBoolean("detector.glow-target", true)) {
            int sec = cfg.getInt("detector.glow-seconds", 5);
            nearest.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.GLOWING, sec * 20, 0, false, false));
        }
    }
}
