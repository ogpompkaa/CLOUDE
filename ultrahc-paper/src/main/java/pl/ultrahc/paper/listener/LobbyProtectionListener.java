package pl.ultrahc.paper.listener;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import pl.ultrahc.paper.ServerRole;
import pl.ultrahc.paper.UltraHcPlugin;

/**
 * Ochrona swiata lobby/poczekalni (anti-grief): brak niszczenia/stawiania blokow,
 * obrazen, glodu i wyrzucania itemow. Chroni hub (rola LOBBY) oraz poczekalnie
 * areny (rola ARENA) — ale NIE swiat meczu. Budowa mozliwa dla ekipy
 * (ultrahc.build / tryb kreatywny). Sterowane world.lobby-protection.
 */
public class LobbyProtectionListener implements Listener {

    private final UltraHcPlugin plugin;

    public LobbyProtectionListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    /** Czy dany swiat jest chronionym lobby. */
    private boolean isLobby(World w) {
        if (!plugin.configManager().raw().getBoolean("world.lobby-protection", true)) return false;
        if (plugin.role() == ServerRole.LOBBY) return true;             // caly hub
        return plugin.arenaLobby() != null && w.equals(plugin.arenaLobby().world()); // poczekalnia, nie mecz
    }

    /** Gracz podlega ochronie (poza ekipa budujaca). */
    private boolean protectedFor(Player p) {
        if (!isLobby(p.getWorld())) return false;
        if (p.getGameMode() == GameMode.CREATIVE) return false;
        return !p.hasPermission("ultrahc.build");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protectedFor(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (protectedFor(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (protectedFor(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && isLobby(p.getWorld())) {
            e.setFoodLevel(20);
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isLobby(p.getWorld())) return;
        // Pustka nie moze uwiezic gracza — zamiast obrazen odeslij na spawn.
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            p.teleport(p.getWorld().getSpawnLocation());
            return;
        }
        e.setCancelled(true);
    }
}
