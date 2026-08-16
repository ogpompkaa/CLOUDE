package pl.ultrahc.paper.listener;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import pl.ultrahc.paper.UltraHcPlugin;

/**
 * Opcjonalny tryb PvP 1.8: usuwa cooldown ataku (attack-speed = duza wartosc,
 * klikanie bez paska) i wylacza atak obszarowy miecza (sweep). Sterowane
 * combat.old-pvp / combat.attack-speed. Atrybut ustawiany przy wejsciu i po
 * respawnie (respawn resetuje atrybuty do domyslnych).
 */
public class OldPvpListener implements Listener {

    private final UltraHcPlugin plugin;

    public OldPvpListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.configManager().raw().getBoolean("combat.old-pvp", true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        // Respawn resetuje atrybuty — ustaw w kolejnym ticku.
        plugin.getServer().getScheduler().runTask(plugin, () -> { if (p.isOnline()) apply(p); });
    }

    private void apply(Player p) {
        if (!enabled()) return;
        AttributeInstance inst = p.getAttribute(Attribute.ATTACK_SPEED);
        if (inst != null) {
            inst.setBaseValue(plugin.configManager().raw().getDouble("combat.attack-speed", 1024));
        }
    }

    /** Bez ataku obszarowego (sweep) — jak w 1.8. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSweep(EntityDamageByEntityEvent e) {
        if (enabled() && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            e.setCancelled(true);
        }
    }
}
