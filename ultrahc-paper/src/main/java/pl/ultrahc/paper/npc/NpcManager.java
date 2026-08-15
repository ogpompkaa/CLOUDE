package pl.ultrahc.paper.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Abstrakcja NPC lobby (Mietek/Krzysiu/Sklepikarz). Implementacja domyslna:
 * Citizens (przez reflection, softdepend). Interfejs pozwala podmienic backend.
 */
public interface NpcManager {

    /** Czy backend NPC jest dostepny (plugin obecny). */
    boolean available();

    /** Tworzy NPC-gracza o danej nazwie i podpina akcje na PPM. */
    void spawn(String id, String displayName, Location loc, Consumer<Player> onRightClick);

    /** Usuwa wszystkie NPC utworzone przez plugin. */
    void removeAll();
}
