package pl.ultrahc.paper.manager;

import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.List;

/**
 * Sklep Sklepikarza: sprzedaz receptur za XP (waluta sklepowa). Ceny w config
 * (shop.recipes.<id>.price). Odblokowana receptura -> gracz moze ja skraftic.
 */
public class ShopManager {

    private final UltraHcPlugin plugin;
    private final ShopCurrencyManager currency;

    public ShopManager(UltraHcPlugin plugin, ShopCurrencyManager currency) {
        this.plugin = plugin;
        this.currency = currency;
    }

    public List<String> recipeIds() {
        return List.copyOf(RecipeManager.NAMES.keySet());
    }

    public String displayName(String id) {
        return RecipeManager.NAMES.getOrDefault(id, id);
    }

    public long price(String id) {
        return plugin.configManager().raw().getLong("shop.recipes." + id + ".price", 0);
    }

    public boolean owns(PlayerProfile p, String id) {
        return p.getUnlockedRecipes().contains(id);
    }

    /** Kupno receptury. Zwraca true przy sukcesie (odjeto XP i odblokowano). */
    public boolean buy(PlayerProfile p, String id) {
        if (!RecipeManager.NAMES.containsKey(id) || owns(p, id)) return false;
        if (!currency.tryDeduct(p, price(id))) return false;
        p.getUnlockedRecipes().add(id);
        plugin.profiles().saveNow(p);
        return true;
    }
}
