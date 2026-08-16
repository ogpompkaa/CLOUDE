package pl.ultrahc.paper.integration;

import pl.ultrahc.paper.UltraHcPlugin;

/**
 * Miekka integracja z PlaceholderAPI. Klasa {@link UltraHcExpansion} (i jej
 * nadklasa z PAPI) jest ladowana dopiero przy wywolaniu {@link #register}, i tylko
 * gdy plugin PlaceholderAPI jest obecny — dzieki temu brak PAPI nie blokuje startu.
 */
public final class PlaceholderIntegration {

    private PlaceholderIntegration() {}

    public static void register(UltraHcPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("[UltraHC] PlaceholderAPI nieobecne — placeholdery %ultrahc_...% pominiete.");
            return;
        }
        try {
            new UltraHcExpansion(plugin).register();
            plugin.getLogger().info("[UltraHC] Zarejestrowano placeholdery %ultrahc_...%.");
        } catch (Throwable t) {
            plugin.getLogger().warning("[UltraHC] Nie udalo sie zarejestrowac placeholderow: " + t.getMessage());
        }
    }
}
