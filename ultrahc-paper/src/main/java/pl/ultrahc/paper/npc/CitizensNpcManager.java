package pl.ultrahc.paper.npc;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * NPC przez Citizens — integracja REFLECTION (bez zaleznosci Maven, softdepend).
 * Tworzy NPC-graczy i podpina akcje na PPM przez event NPCRightClickEvent.
 * Gdy Citizens nieobecny lub API sie zmieni — degradujemy sie gracefully.
 */
public class CitizensNpcManager implements NpcManager {

    private final JavaPlugin plugin;
    private final Map<Integer, Consumer<Player>> actions = new ConcurrentHashMap<>();
    private final List<Object> created = new ArrayList<>();

    private Object registry;
    private Method createNpc;   // NPCRegistry#createNPC(EntityType, String)
    private Method spawnMethod;  // NPC#spawn(Location)
    private Method getIdMethod;  // NPC#getId()
    private Method destroyMethod;// NPC#destroy()
    private boolean ok;

    public CitizensNpcManager(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            this.registry = api.getMethod("getNPCRegistry").invoke(null);
            Class<?> registryClass = Class.forName("net.citizensnpcs.api.npc.NPCRegistry");
            this.createNpc = registryClass.getMethod("createNPC", EntityType.class, String.class);
            Class<?> npcClass = Class.forName("net.citizensnpcs.api.npc.NPC");
            this.spawnMethod = npcClass.getMethod("spawn", Location.class);
            this.getIdMethod = npcClass.getMethod("getId");
            this.destroyMethod = npcClass.getMethod("destroy");
            registerClickListener();
            this.ok = true;
            plugin.getLogger().info("[UltraHC] Integracja Citizens aktywna.");
        } catch (Throwable t) {
            this.ok = false;
            plugin.getLogger().warning("[UltraHC] Citizens niedostepne — NPC wylaczone (" + t.getMessage() + ").");
        }
    }

    @Override
    public boolean available() {
        return ok && plugin.getServer().getPluginManager().getPlugin("Citizens") != null;
    }

    @Override
    public void spawn(String id, String displayName, Location loc, Consumer<Player> onRightClick) {
        if (!available()) return;
        try {
            Object npc = createNpc.invoke(registry, EntityType.PLAYER, displayName);
            spawnMethod.invoke(npc, loc);
            int npcId = (int) getIdMethod.invoke(npc);
            actions.put(npcId, onRightClick);
            created.add(npc);
        } catch (Throwable t) {
            plugin.getLogger().warning("[UltraHC] Blad tworzenia NPC " + id + ": " + t.getMessage());
        }
    }

    @Override
    public void removeAll() {
        for (Object npc : created) {
            try {
                destroyMethod.invoke(npc);
            } catch (Throwable ignored) {
            }
        }
        created.clear();
        actions.clear();
    }

    /** Rejestruje nasluch NPCRightClickEvent przez reflection (bez importu klasy Citizens). */
    private void registerClickListener() throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends org.bukkit.event.Event> eventClass =
                (Class<? extends org.bukkit.event.Event>) Class.forName("net.citizensnpcs.api.event.NPCRightClickEvent");
        Method getNpc = eventClass.getMethod("getNPC");
        Method getClicker = eventClass.getMethod("getClicker");

        Listener dummy = new Listener() {};
        plugin.getServer().getPluginManager().registerEvent(eventClass, dummy, EventPriority.NORMAL,
                (listener, event) -> {
                    try {
                        Object npc = getNpc.invoke(event);
                        int npcId = (int) getIdMethod.invoke(npc);
                        Consumer<Player> action = actions.get(npcId);
                        if (action == null) return;
                        Object clicker = getClicker.invoke(event);
                        if (clicker instanceof Player player) action.accept(player);
                    } catch (Throwable ignored) {
                    }
                }, plugin);
    }
}
