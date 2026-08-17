package pl.ultrahc.paper.hologram;

import org.bukkit.Location;

import java.util.List;

/**
 * Abstrakcja hologramow topek. Implementacja domyslna: DecentHolograms (przez
 * reflection, softdepend). Interfejs pozwala podmienic backend bez zmian w logice.
 */
public interface HologramManager {

    /** Czy backend hologramow jest dostepny (plugin obecny). */
    boolean available();

    /** Tworzy lub aktualizuje hologram o danym id w podanej lokalizacji. */
    void createOrUpdate(String id, Location loc, List<String> lines);

    /** Usuwa hologram o danym id. */
    void remove(String id);

    /** Usuwa wszystkie hologramy utworzone przez plugin. */
    void removeAll();
}
