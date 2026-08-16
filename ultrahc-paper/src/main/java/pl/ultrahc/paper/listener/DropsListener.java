package pl.ultrahc.paper.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dropy przy kopaniu: kamien -> surowce, liscie -> jablka (szanse z config).
 * Uwzglednia modyfikatory klas: Gornik (dodatkowy drop + exp), Kowal (surowce
 * od razu przepalone), Szef Kuchni (wieksza szansa na jablko).
 */
public class DropsListener implements Listener {

    private final UltraHcPlugin plugin;

    private static final Map<String, Material> STONE_DROPS = new LinkedHashMap<>();
    static {
        STONE_DROPS.put("diamond", Material.DIAMOND);
        STONE_DROPS.put("iron", Material.RAW_IRON);
        STONE_DROPS.put("coal", Material.COAL);
        STONE_DROPS.put("gold", Material.RAW_GOLD);
        STONE_DROPS.put("redstone", Material.REDSTONE);
        STONE_DROPS.put("quartz", Material.QUARTZ);
        STONE_DROPS.put("lapis", Material.LAPIS_LAZULI);
        STONE_DROPS.put("emerald", Material.EMERALD);
    }

    public DropsListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Material type = e.getBlock().getType();
        Location loc = e.getBlock().getLocation();
        Player player = e.getPlayer();
        String clazz = selectedClass(player);

        // UHC: rudy NIE dropia normalnie — surowce zdobywa sie z kamienia (nizej).
        if (isOre(type) && plugin.configManager().raw().getBoolean("drops.disable-ore-drops", true)) {
            e.setDropItems(false);
            e.setExpToDrop(0);
            return;
        }

        if (isStone(type)) {
            ConfigurationSection sec = plugin.configManager().raw().getConfigurationSection("drops.stone");
            if (sec == null) return;
            for (Map.Entry<String, Material> entry : STONE_DROPS.entrySet()) {
                if (roll(sec.getDouble(entry.getKey(), 0))) {
                    dropItem(loc, entry.getValue(), clazz);
                }
            }
            // Gornik: dodatkowy drop + natywny exp ze stone.
            if ("gornik".equals(clazz)) {
                var ab = plugin.configManager().raw();
                if (roll(ab.getDouble("classes.gornik.ability.extra-drop-chance", 0.125))) {
                    dropItem(loc, Material.RAW_IRON, clazz);
                }
                int xp = ab.getInt("classes.gornik.ability.extra-xp-orbs", 2);
                if (xp > 0) player.giveExp(xp);
            }
        } else if (isLeaves(type)) {
            double chance = plugin.configManager().raw().getDouble("drops.leaves.apple", 0);
            if ("chef".equals(clazz)) {
                chance += plugin.configManager().raw().getDouble("classes.chef.ability.apple-bonus-chance", 0);
            }
            if (roll(chance)) {
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.APPLE));
            }
        }
    }

    /** Drop z uwzglednieniem Kowala (surowce od razu przepalone). */
    private void dropItem(Location loc, Material material, String clazz) {
        Material out = material;
        if ("smith".equals(clazz)) {
            if (material == Material.RAW_IRON) out = Material.IRON_INGOT;
            else if (material == Material.RAW_GOLD) out = Material.GOLD_INGOT;
        }
        loc.getWorld().dropItemNaturally(loc, new ItemStack(out));
    }

    private String selectedClass(Player player) {
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        return p == null ? "civil" : p.getSelectedClass();
    }

    private boolean isStone(Material m) {
        return m == Material.STONE || m == Material.DEEPSLATE;
    }

    /** Bloki rud (kazdy wariant, tez deepslate/nether) — rozpoznanie po nazwie. */
    private boolean isOre(Material m) {
        return m.name().endsWith("_ORE");
    }

    private boolean isLeaves(Material m) {
        return m.name().endsWith("_LEAVES");
    }

    private boolean roll(double chancePercent) {
        return chancePercent > 0 && ThreadLocalRandom.current().nextDouble() * 100.0 < chancePercent;
    }
}
