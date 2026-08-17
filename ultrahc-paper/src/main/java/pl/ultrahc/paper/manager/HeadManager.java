package pl.ultrahc.paper.manager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.Locale;

/**
 * Glowki graczy: konsumowalne (zjadane) — po kliknieciu daja efekty z config.
 * Typ "normal" (drop po zabiciu) i "golden" (Pozlacana Glowka z receptury).
 */
public class HeadManager {

    private final UltraHcPlugin plugin;
    private final NamespacedKey headKey;

    public HeadManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.headKey = new NamespacedKey(plugin, "uhc_head");
    }

    /** Tworzy glowke gracza danego typu z tagiem PDC (do rozpoznania przy konsumpcji). */
    public ItemStack createHead(OfflinePlayer owner, String type) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (owner != null) meta.setOwningPlayer(owner);
        String label = type.equals("golden") ? "&6Pozlacana Glowka" : "&fGlowka " + (owner != null ? owner.getName() : "gracza");
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(label));
        meta.getPersistentDataContainer().set(headKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    /** Zwraca typ glowki UHC (normal/golden) albo null gdy to nie nasza glowka. */
    public String headType(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(headKey, PersistentDataType.STRING);
    }

    /** Aplikuje efekty danego typu glowki wg config (heads.<type>). */
    public void applyEffects(Player player, String type) {
        ConfigurationSection sec = plugin.configManager().raw().getConfigurationSection("heads." + type);
        if (sec == null) return;
        int duration = sec.getInt("duration-seconds", 10) * 20; // ticki
        for (java.util.Map<?, ?> effect : sec.getMapList("effects")) {
            PotionEffectType t = resolveEffect(String.valueOf(effect.get("type")));
            if (t == null) continue;
            int amplifier = effect.get("amplifier") == null ? 0 : ((Number) effect.get("amplifier")).intValue();
            player.addPotionEffect(new PotionEffect(t, duration, amplifier, true, true));
        }
    }

    private PotionEffectType resolveEffect(String name) {
        if (name == null) return null;
        PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return t != null ? t : PotionEffectType.getByName(name.toUpperCase(Locale.ROOT));
    }
}
