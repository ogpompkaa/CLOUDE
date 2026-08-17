package pl.ultrahc.paper.manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.GameState;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cykliczne pasywki klas: Yeti (Speed na sniegu/lodzie — dziala na dowolnym
 * bloku sniegu/lodu, wiec jest odporne na brak zimnego biomu = fallback ze spec),
 * Wedkarz (Regen I + Speed I co N sekund w wodzie).
 */
public class AbilityScheduler {

    private final UltraHcPlugin plugin;
    private BukkitTask task;
    private final Map<UUID, Long> fisherLastBuff = new ConcurrentHashMap<>();

    private static final Set<Material> SNOW_ICE = EnumSet.of(
            Material.SNOW, Material.SNOW_BLOCK, Material.POWDER_SNOW,
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);

    public AbilityScheduler(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        GameInstance game = plugin.games() == null ? null : plugin.games().current();
        if (game == null || game.state() != GameState.RUNNING || game.teams() == null) return;
        var cfg = plugin.configManager().raw();

        for (UUID id : game.participants()) {
            var team = game.teams().getTeam(id);
            if (team == null || !team.isAlive(id)) continue;
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) continue;
            String clazz = classOf(id);

            if ("yeti".equals(clazz)) {
                Material below = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
                Material feet = player.getLocation().getBlock().getType();
                if (SNOW_ICE.contains(below) || SNOW_ICE.contains(feet)) {
                    int sec = cfg.getInt("classes.yeti.ability.speed-seconds", 2);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, sec * 20, 0, true, false));
                }
            } else if ("fisher".equals(clazz)) {
                if (player.isInWater()) {
                    long now = System.currentTimeMillis() / 1000L;
                    long interval = cfg.getInt("classes.fisher.ability.water-interval-seconds", 12);
                    long last = fisherLastBuff.getOrDefault(id, 0L);
                    if (now - last >= interval) {
                        fisherLastBuff.put(id, now);
                        int sec = cfg.getInt("classes.fisher.ability.buff-seconds", 2);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, sec * 20, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, sec * 20, 0));
                    }
                }
            }
        }
    }

    private String classOf(UUID id) {
        PlayerProfile p = plugin.profiles().get(id);
        return p == null ? "civil" : p.getSelectedClass();
    }
}
