package pl.ultrahc.paper.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bonusowe dropy przy kopaniu: kamien -> surowce (niezalezne rzuty), liscie -> jablka.
 * Szanse (%) z config (drops.*). Dropy klasowe (Gornik/Szef Kuchni...) dojda jako
 * modyfikatory w etapie klas.
 */
public class DropsListener implements Listener {

    private final UltraHcPlugin plugin;

    /** Mapowanie kluczy config -> Material (identyfikacja przedmiotu, nie balans). */
    private static final Map<String, Material> STONE_DROPS = new LinkedHashMap<>();
    static {
        STONE_DROPS.put("diamond", Material.DIAMOND);
        STONE_DROPS.put("iron", Material.RAW_IRON);      // Kowal zamieni na sztabke
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

        if (isStone(type)) {
            ConfigurationSection sec = plugin.configManager().raw().getConfigurationSection("drops.stone");
            if (sec == null) return;
            for (Map.Entry<String, Material> entry : STONE_DROPS.entrySet()) {
                double chance = sec.getDouble(entry.getKey(), 0);
                if (roll(chance)) {
                    loc.getWorld().dropItemNaturally(loc, new ItemStack(entry.getValue()));
                }
            }
        } else if (isLeaves(type)) {
            double chance = plugin.configManager().raw().getDouble("drops.leaves.apple", 0);
            if (roll(chance)) {
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.APPLE));
            }
        }
    }

    private boolean isStone(Material m) {
        return m == Material.STONE || m == Material.DEEPSLATE;
    }

    private boolean isLeaves(Material m) {
        return m.name().endsWith("_LEAVES");
    }

    /** Rzut na szanse podana w procentach. */
    private boolean roll(double chancePercent) {
        return chancePercent > 0 && ThreadLocalRandom.current().nextDouble() * 100.0 < chancePercent;
    }
}
