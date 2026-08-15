package pl.ultrahc.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.ultrahc.common.model.PlayerProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testy integracyjne warstwy DAO na tymczasowej bazie SQLite (bez serwera). */
class SqliteStorageTest {

    private SqliteStorage storage;

    @BeforeEach
    void setup(@TempDir Path dir) throws Exception {
        storage = new SqliteStorage(dir.resolve("test.db").toString(), Logger.getLogger("test"));
        storage.init();
    }

    @Test
    void newProfileHasDefaults() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerProfile p = storage.loadProfile(id, "Gracz");
        assertEquals("civil", p.getSelectedClass());
        assertTrue(p.getUnlockedClasses().contains("civil"));
        assertEquals(0, p.getCredits());
        assertEquals(0, p.getLevel());
    }

    @Test
    void saveAndLoadRoundtrip() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerProfile p = storage.loadProfile(id, "Gracz");
        p.setCredits(12345);
        p.setProgressPoints(678);
        p.setLevel(5);
        p.setKills(9);
        p.setWins(2);
        p.getUnlockedClasses().add("yeti");
        p.getUnlockedRecipes().add("hell-sword");
        p.setSelectedClass("yeti");
        storage.saveProfile(p);

        PlayerProfile r = storage.loadProfile(id, "Gracz");
        assertEquals(12345, r.getCredits());
        assertEquals(678, r.getProgressPoints());
        assertEquals(5, r.getLevel());
        assertEquals(9, r.getKills());
        assertEquals(2, r.getWins());
        assertTrue(r.getUnlockedClasses().contains("yeti"));
        assertTrue(r.getUnlockedRecipes().contains("hell-sword"));
        assertEquals("yeti", r.getSelectedClass());
    }

    @Test
    void leaderboardOrdersByValue() throws Exception {
        savePlayer("Aaa", 10, 1, 3);
        savePlayer("Bbb", 30, 5, 1);   // najwiecej killi
        savePlayer("Ccc", 20, 9, 7);   // najwiecej wygranych i najwyzszy poziom
        assertEquals("Bbb", storage.topBy(Storage.LeaderboardType.KILLS, 10).get(0).name());
        assertEquals("Ccc", storage.topBy(Storage.LeaderboardType.WINS, 10).get(0).name());
        assertEquals("Ccc", storage.topBy(Storage.LeaderboardType.LEVEL, 10).get(0).name());
    }

    @Test
    void seasonResetKeepsClassesAndXpButClearsLevels() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerProfile p = storage.loadProfile(id, "Gracz");
        p.setCredits(5000);
        p.setLevel(10);
        p.setProgressPoints(400);
        p.getUnlockedClasses().add("smith");
        p.getUnlockedRecipes().add("detector");
        storage.saveProfile(p);

        storage.resetSeason();

        PlayerProfile r = storage.loadProfile(id, "Gracz");
        assertEquals(0, r.getLevel(), "poziom wyzerowany");
        assertEquals(0, r.getProgressPoints(), "PD wyzerowane");
        assertTrue(r.getUnlockedRecipes().isEmpty(), "receptury wyzerowane");
        assertEquals(5000, r.getCredits(), "XP zachowane");
        assertTrue(r.getUnlockedClasses().contains("smith"), "klasy zachowane");
    }

    @Test
    void questPersistence() throws Exception {
        UUID id = UUID.randomUUID();
        storage.saveQuest(id, new Storage.QuestRecord("daily_kill", "2026-08-15", 3, false));
        List<Storage.QuestRecord> q = storage.loadQuests(id);
        assertEquals(1, q.size());
        assertEquals(3, q.get(0).progress());
        assertFalse(q.get(0).completed());
    }

    @Test
    void partyMembers() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        storage.setPartyMember(a, "p1");
        storage.setPartyMember(b, "p1");
        var ids = storage.loadPartyIds(List.of(a, b, c));
        assertEquals("p1", ids.get(a));
        assertEquals("p1", ids.get(b));
        assertFalse(ids.containsKey(c));
        storage.clearPartyMember(a);
        assertFalse(storage.loadPartyIds(List.of(a)).containsKey(a));
    }

    private void savePlayer(String name, int kills, int wins, int level) throws Exception {
        PlayerProfile p = storage.loadProfile(UUID.randomUUID(), name);
        p.setKills(kills);
        p.setWins(wins);
        p.setLevel(level);
        storage.saveProfile(p);
    }
}
