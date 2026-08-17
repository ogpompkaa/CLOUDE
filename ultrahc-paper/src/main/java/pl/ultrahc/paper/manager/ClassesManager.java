package pl.ultrahc.paper.manager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Klasy: definicje, kity startowe, kupno za XP i wybor. Ceny i parametry
 * umiejetnosci w config (classes.*). Umiejetnosci pasywne realizuje
 * {@code ClassAbilityListener}. Klasa startowa: Cywil (0 XP, odblokowana).
 */
public class ClassesManager {

    private final UltraHcPlugin plugin;
    private final ShopCurrencyManager currency;

    /** Kolejnosc = kolejnosc w GUI. Nazwy PL do wyswietlania. */
    public static final Map<String, String> CLASSES = new LinkedHashMap<>();
    static {
        CLASSES.put("civil", "Cywil");
        CLASSES.put("gornik", "Gornik");
        CLASSES.put("sniper", "Snajper");
        CLASSES.put("chef", "Szef Kuchni");
        CLASSES.put("fisher", "Wedkarz");
        CLASSES.put("yeti", "Yeti");
        CLASSES.put("smith", "Kowal");
        CLASSES.put("guard", "Straznik");
    }

    public ClassesManager(UltraHcPlugin plugin, ShopCurrencyManager currency) {
        this.plugin = plugin;
        this.currency = currency;
    }

    public boolean exists(String id) { return CLASSES.containsKey(id); }
    public String displayName(String id) { return CLASSES.getOrDefault(id, id); }
    public long price(String id) { return plugin.configManager().raw().getLong("classes." + id + ".price", 0); }
    public List<String> ids() { return List.copyOf(CLASSES.keySet()); }

    public boolean isUnlocked(PlayerProfile p, String id) {
        return "civil".equals(id) || p.getUnlockedClasses().contains(id);
    }

    /** Kupno klasy za XP. Zwraca true przy sukcesie. */
    public boolean buy(PlayerProfile p, String id) {
        if (!exists(id) || isUnlocked(p, id)) return false;
        long price = price(id);
        if (!currency.tryDeduct(p, price)) return false;
        p.getUnlockedClasses().add(id);
        plugin.profiles().saveNow(p);
        return true;
    }

    public boolean select(PlayerProfile p, String id) {
        if (!exists(id) || !isUnlocked(p, id)) return false;
        p.setSelectedClass(id);
        plugin.profiles().saveNow(p);
        return true;
    }

    /** Zaklada kit startowy wybranej klasy (wolane na starcie gry). */
    public void applyKit(Player player, String classId) {
        var inv = player.getInventory();
        inv.clear();
        String id = classId == null ? "civil" : classId;
        switch (id) {
            case "gornik" -> {
                inv.addItem(enchanted(Material.STONE_PICKAXE, 1, "Kilof Gornika", Map.of(Enchantment.EFFICIENCY, 1)));
                inv.addItem(new ItemStack(Material.COOKED_BEEF, 3));
            }
            case "sniper" -> {
                inv.addItem(enchanted(Material.BOW, 1, "AWP", Map.of(Enchantment.UNBREAKING, 2)));
                inv.addItem(new ItemStack(Material.ARROW, 8));
            }
            case "chef" -> {
                inv.addItem(new ItemStack(Material.COOKED_BEEF, 8));
                inv.addItem(new ItemStack(Material.BREAD, 2));
            }
            case "fisher" -> {
                inv.addItem(enchanted(Material.FISHING_ROD, 1, "Wedka Wedkarza", Map.of(Enchantment.UNBREAKING, 3)));
                inv.addItem(new ItemStack(Material.COD, 3));
            }
            case "yeti" -> {
                inv.addItem(enchanted(Material.IRON_SHOVEL, 1, "Lopata Yeti",
                        Map.of(Enchantment.EFFICIENCY, 2, Enchantment.UNBREAKING, 2)));
                inv.addItem(new ItemStack(Material.SNOWBALL, 32));
            }
            case "smith" -> {
                inv.addItem(new ItemStack(Material.IRON_INGOT, 1));
                inv.addItem(new ItemStack(Material.RAW_IRON, 1));
            }
            case "guard" -> {
                applyGuardHearts(player);
            }
            default -> { // civil (startowa)
                inv.addItem(new ItemStack(Material.STICK, 1));
                inv.addItem(new ItemStack(Material.BREAD, 8));
            }
        }
    }

    /** Straznik: dodatkowe serduszka (kit). */
    private void applyGuardHearts(Player player) {
        int extra = plugin.configManager().raw().getInt("classes.guard.ability.extra-hearts", 2);
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0 + extra * 2.0);
            player.setHealth(attr.getBaseValue());
        }
    }

    private ItemStack enchanted(Material material, int amount, String name, Map<Enchantment, Integer> ench) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (name != null) meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&e" + name));
        item.setItemMeta(meta);
        ench.forEach((e, lvl) -> item.addUnsafeEnchantment(e, lvl));
        return item;
    }
}
