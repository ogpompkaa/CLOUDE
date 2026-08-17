package pl.ultrahc.paper.hologram;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hologramy przez DecentHolograms — integracja REFLECTION (bez zaleznosci Maven,
 * softdepend). Gdy plugin nieobecny lub API sie zmieni, degradujemy sie gracefully.
 * Uzywamy statycznego API {@code eu.decentsoftware.holograms.api.DHAPI}.
 */
public class DecentHologramsManager implements HologramManager {

    private final JavaPlugin plugin;
    private final Set<String> created = new HashSet<>();

    private Method createHologram;
    private Method removeHologram;
    private boolean ok;

    public DecentHologramsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> dhapi = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            this.createHologram = dhapi.getMethod("createHologram", String.class, Location.class, List.class);
            this.removeHologram = dhapi.getMethod("removeHologram", String.class);
            this.ok = true;
            plugin.getLogger().info("[UltraHC] Integracja DecentHolograms aktywna.");
        } catch (Throwable t) {
            this.ok = false;
            plugin.getLogger().warning("[UltraHC] DecentHolograms niedostepne — hologramy topek wylaczone (" + t.getMessage() + ").");
        }
    }

    @Override
    public boolean available() {
        return ok && plugin.getServer().getPluginManager().getPlugin("DecentHolograms") != null;
    }

    @Override
    public void createOrUpdate(String id, Location loc, List<String> lines) {
        if (!available()) return;
        try {
            // Najprostsza aktualizacja: usun i utworz ponownie. DecentHolograms
            // obsluguje kody '&' natywnie, wiec przekazujemy linie bez konwersji.
            removeHologram.invoke(null, id);
            createHologram.invoke(null, id, loc, new ArrayList<>(lines));
            created.add(id);
        } catch (Throwable t) {
            plugin.getLogger().warning("[UltraHC] Blad hologramu " + id + ": " + t.getMessage());
        }
    }

    @Override
    public void remove(String id) {
        if (!ok) return;
        try {
            removeHologram.invoke(null, id);
            created.remove(id);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void removeAll() {
        for (String id : new ArrayList<>(created)) remove(id);
    }
}
