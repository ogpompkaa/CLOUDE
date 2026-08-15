package pl.ultrahc.paper.manager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receptury UHC: buduje specjalne przedmioty i rejestruje ich przepisy craftingu.
 * Crafting jest bramkowany per-gracz — kto nie wykupil receptury, nie skrafti
 * (PrepareItemCraftEvent zeruje wynik). Przedmioty oznaczone PDC (uhc_recipe=id).
 *
 * <p>Uwagi interpretacyjne (spec bywa ogolny):
 *  - "Zaklinacz" realizujemy jako craftowalny stol (utrudniony dostep do enchantu);
 *  - "Puszka Pandory"/"Wykrywacz" — logika uzycia w osobnych listenerach.
 */
public class RecipeManager implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey recipeKey;

    /** id -> nazwa PL (wspoldzielone ze sklepem, dostepne bez instancji ARENA). */
    public static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("gornik-pickaxe", "Kilof Gornika");
        NAMES.put("sharp-sword", "Miecz Ostrosci");
        NAMES.put("fire-sword", "Ognisty Miecz");
        NAMES.put("hell-sword", "Piekielny Miecz");
        NAMES.put("enchanter", "Zaklinacz");
        NAMES.put("pandora-box", "Puszka Pandory");
        NAMES.put("golden-head", "Pozlacana Glowka");
        NAMES.put("detector", "Wykrywacz");
    }

    public RecipeManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "uhc_recipe");
    }

    /** Rejestruje wszystkie przepisy w serwerze (ARENA). */
    public void registerAll() {
        // Zaklinacz: utrudniony enchant -> wylaczamy zwykly przepis na stol do zaklec.
        if (plugin.configManager().raw().getBoolean("enchanter.disable-vanilla-table", true)) {
            plugin.getServer().removeRecipe(org.bukkit.NamespacedKey.minecraft("enchanting_table"));
        }
        register("gornik-pickaxe", buildResult("gornik-pickaxe"), Material.IRON_PICKAXE, Material.DIAMOND);
        register("sharp-sword", buildResult("sharp-sword"), Material.IRON_SWORD, Material.IRON_INGOT, Material.IRON_INGOT);
        register("fire-sword", buildResult("fire-sword"), Material.IRON_SWORD, Material.BLAZE_POWDER);
        register("hell-sword", buildResult("hell-sword"), Material.DIAMOND_SWORD, Material.BLAZE_ROD, Material.FIRE_CHARGE);
        register("enchanter", buildResult("enchanter"), Material.BOOK, Material.LAPIS_LAZULI, Material.DIAMOND);
        register("pandora-box", buildResult("pandora-box"), Material.PLAYER_HEAD, Material.CHEST, Material.GUNPOWDER);
        register("golden-head", buildResult("golden-head"), Material.PLAYER_HEAD,
                Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_INGOT,
                Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_INGOT);
        register("detector", buildResult("detector"), Material.COMPASS, Material.REDSTONE, Material.ENDER_PEARL);
    }

    private void register(String id, ItemStack result, Material... ingredients) {
        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "uhc_" + id), result);
        for (Material m : ingredients) recipe.addIngredient(m);
        plugin.getServer().addRecipe(recipe);
    }

    /** Buduje przedmiot-wynik danej receptury (z tagiem PDC i efektami/enchantami). */
    public ItemStack buildResult(String id) {
        return switch (id) {
            case "gornik-pickaxe" -> tagged(enchant(Material.IRON_PICKAXE, Map.of(Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 3)), id);
            case "sharp-sword" -> tagged(enchant(Material.IRON_SWORD, Map.of(Enchantment.SHARPNESS, 2, Enchantment.UNBREAKING, 1)), id);
            case "fire-sword" -> tagged(enchant(Material.IRON_SWORD, Map.of(Enchantment.FIRE_ASPECT, 1)), id);
            case "hell-sword" -> tagged(enchant(Material.DIAMOND_SWORD, Map.of(Enchantment.FIRE_ASPECT, 2)), id);
            case "enchanter" -> tagged(named(new ItemStack(Material.ENCHANTING_TABLE), "Zaklinacz"), id);
            case "pandora-box" -> tagged(named(new ItemStack(Material.CHEST), "Puszka Pandory"), id);
            case "golden-head" -> tagged(plugin.heads().createHead(null, "golden"), id);
            case "detector" -> tagged(named(new ItemStack(Material.CLOCK), "Wykrywacz"), id);
            default -> new ItemStack(Material.STONE);
        };
    }

    /** Zwraca id receptury zapisane w przedmiocie (albo null). */
    public String recipeIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(recipeKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------ bramkowanie craftu
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        String id = recipeIdOf(result);
        if (id == null) return;
        if (!(e.getView().getPlayer() instanceof Player player)) return;
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null || !p.getUnlockedRecipes().contains(id)) {
            e.getInventory().setResult(null); // brak wykupionej receptury -> brak wyniku
        }
    }

    // --------------------------------------------------------------- helpers
    private ItemStack enchant(Material material, Map<Enchantment, Integer> ench) {
        ItemStack item = new ItemStack(material);
        ench.forEach((en, lvl) -> item.addUnsafeEnchantment(en, lvl));
        return item; // nazwa nadawana w tagged()
    }

    private ItemStack named(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&6" + name));
        item.setItemMeta(meta);
        return item;
    }

    /** Nadaje przedmiotowi tag PDC receptury i czytelna nazwe wg id. */
    private ItemStack tagged(ItemStack item, String id) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(recipeKey, PersistentDataType.STRING, id);
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&6" + NAMES.getOrDefault(id, id)));
        item.setItemMeta(meta);
        return item;
    }
}
