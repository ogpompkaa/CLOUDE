package pl.ultrahc.paper.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.GameState;
import pl.ultrahc.paper.game.Team;

import java.util.UUID;

/**
 * Zasady walki UHC: brak PvP przez 10 min, brak podpalania/lawy w tym czasie,
 * brak "friendly fire", oraz obsluga smierci (eliminacja + drop glowki) i
 * respawnu (tryb spectator zamiast wyjscia z gry).
 */
public class CombatListener implements Listener {

    private final UltraHcPlugin plugin;

    public CombatListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private GameInstance game() {
        return plugin.games() == null ? null : plugin.games().current();
    }

    // ---------------------------------------------------------- PvP i druzyny
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e);
        if (attacker == null) return;

        GameInstance game = game();
        if (game == null || game.state() != GameState.RUNNING || game.teams() == null) return;

        Team vt = game.teams().getTeam(victim.getUniqueId());
        Team at = game.teams().getTeam(attacker.getUniqueId());
        if (vt == null || at == null) return;

        // Friendly fire wylaczony (wazne dla DUO/TRIO/SQUAD).
        if (vt == at) {
            e.setCancelled(true);
            return;
        }
        // Brak PvP przez pierwsze N minut.
        if (!game.pvpEnabled()) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.messages().prefixed("game.pvp-not-active", null));
            return;
        }
        // Cios doszedl -> combat-tag (do kary za combat-log).
        game.recordHit(victim.getUniqueId(), attacker.getUniqueId());
    }

    // ---------------------------------------------- reconnect (okno powrotu)
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        GameInstance game = game();
        if (game != null) game.onDisconnect(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        GameInstance game = game();
        if (game == null) return;
        Player player = e.getPlayer();
        game.onReconnect(player.getUniqueId());
        // Jesli gracz wrocil do nieistniejacego/starego swiata (gra sie skonczyla,
        // swiat areny skasowany) — przenies go do biezacej instancji.
        if (player.getWorld() != game.world()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.teleport(game.world().getSpawnLocation());
                if (game.state() == pl.ultrahc.paper.game.GameState.WAITING
                        || game.state() == pl.ultrahc.paper.game.GameState.COUNTDOWN) {
                    player.setGameMode(GameMode.ADVENTURE);
                }
            });
        }
    }

    // ----------------------------------------- ochrona przed lawa/ogniem w no-PvP
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        GameInstance game = game();
        if (game == null || game.state() != GameState.RUNNING || game.pvpEnabled()) return;
        if (game.teams() == null || game.teams().getTeam(victim.getUniqueId()) == null) return;

        switch (e.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> e.setCancelled(true);
            default -> {}
        }
    }

    // -------------------------------------------------------------- smierc
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        GameInstance game = game();
        if (game == null || game.state() != GameState.RUNNING || game.teams() == null) return;
        if (game.teams().getTeam(victim.getUniqueId()) == null) return;

        Player killer = victim.getKiller();
        UUID killerId = killer != null ? killer.getUniqueId() : null;

        // Drop konsumowalnej glowki ofiary.
        if (plugin.heads() != null) {
            e.getDrops().add(plugin.heads().createHead(victim, "normal"));
        }
        // Wybuch czastek w miejscu smierci.
        pl.ultrahc.paper.util.Feedback.killParticles(victim.getLocation().add(0, 1, 0));

        // Eliminacja + nagroda za zabojstwo (w GameInstance).
        game.handleElimination(victim.getUniqueId(), killerId);
        if (killer != null) {
            plugin.getServer().broadcast(plugin.messages().prefixed("game.kill-weapon",
                    java.util.Map.of("victim", victim.getName(), "killer", killer.getName(),
                            "weapon", weaponName(killer))));
        } else {
            plugin.getServer().broadcast(plugin.messages().prefixed("game.death-generic",
                    java.util.Map.of("victim", victim.getName())));
        }

        // Auto-respawn w nastepnym ticku (bez ekranu smierci) -> obsluze onRespawn.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (victim.isOnline() && victim.isDead()) victim.spigot().respawn();
        }, 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        GameInstance game = game();
        if (game == null || game.teams() == null) return;
        Team team = game.teams().getTeam(player.getUniqueId());
        if (team == null) return;
        // Wyeliminowany -> spectator na arenie (nie wychodzi z gry).
        if (!team.isAlive(player.getUniqueId())) {
            e.setRespawnLocation(game.world().getSpawnLocation());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(plugin.messages().prefixed("spectate.hint", null));
            });
        }
    }

    /** Czytelna nazwa broni w rece zabojcy (nazwa wlasna albo material). */
    private String weaponName(Player killer) {
        org.bukkit.inventory.ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return plugin.messages().raw("game.weapon-fist");
        }
        if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(hand.getItemMeta().displayName());
        }
        return hand.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private Player resolveAttacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
