package pl.ultrahc.common.storage;

import pl.ultrahc.common.model.PlayerProfile;

import java.util.List;
import java.util.UUID;

/**
 * Warstwa DAO — jedyny punkt styku logiki z bazą.
 *
 * <p>Implementacje: {@link SqliteStorage} (dev) oraz w przyszłości MysqlStorage
 * (produkcja/sieć). Podmiana = zmiana implementacji za tym interfejsem, BEZ zmian
 * w logice managerów (patrz DECYZJE, sekcja 1).
 */
public interface Storage {

    /** Inicjalizacja połączenia i schematu (tworzy tabele jeśli brak). */
    void init() throws Exception;

    /** Wczytuje profil; jeśli gracza nie ma — tworzy domyślny i zapisuje. */
    PlayerProfile loadProfile(UUID uuid, String name) throws Exception;

    /** Zapisuje pełny profil gracza. */
    void saveProfile(PlayerProfile profile) throws Exception;

    /** Topka wg kategorii (kille/wygrane/poziom) — do hologramów i komend. */
    List<LeaderboardEntry> topBy(LeaderboardType type, int limit) throws Exception;

    /** Reset sezonu: zeruje poziomy, PD i odblokowane receptury WSZYSTKIM. Klasy zostają. */
    void resetSeason() throws Exception;

    /** Wczytuje postep questow gracza. */
    List<QuestRecord> loadQuests(UUID uuid) throws Exception;

    /** Zapisuje postep pojedynczego questa gracza. */
    void saveQuest(UUID uuid, QuestRecord record) throws Exception;

    /** Zamknięcie zasobów. */
    void close();

    /** Kategoria topki. */
    enum LeaderboardType { KILLS, WINS, LEVEL }

    /** Pojedynczy wiersz topki. */
    record LeaderboardEntry(UUID uuid, String name, long value) {}

    /** Postep questa: okres (klucz dzienny/tygodniowy), ile zrobione, czy odebrane. */
    record QuestRecord(String questId, String period, long progress, boolean completed) {}
}
