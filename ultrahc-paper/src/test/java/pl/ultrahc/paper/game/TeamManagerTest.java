package pl.ultrahc.paper.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Testy grupowania druzyn: party razem, solo pakowani osobno. */
class TeamManagerTest {

    @Test
    void partiesStayTogetherSolosArePacked() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();   // party p1
        UUID c = UUID.randomUUID(), d = UUID.randomUUID();   // party p2
        UUID e = UUID.randomUUID(), f = UUID.randomUUID();   // solo

        Map<UUID, String> groups = Map.of(a, "p1", b, "p1", c, "p2", d, "p2");
        TeamManager tm = new TeamManager(2);
        tm.buildTeams(List.of(a, b, c, d, e, f), null, id -> groups.get(id)); // solo -> null

        assertSame(tm.getTeam(a), tm.getTeam(b), "party p1 razem");
        assertSame(tm.getTeam(c), tm.getTeam(d), "party p2 razem");
        assertNotSame(tm.getTeam(a), tm.getTeam(c), "rozne party osobno");
        assertSame(tm.getTeam(e), tm.getTeam(f), "solo spakowani w druzyne po 2");
        assertNotSame(tm.getTeam(a), tm.getTeam(e), "party i solo nie mieszani");
    }

    @Test
    void soloModePutsEveryoneAlone() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        TeamManager tm = new TeamManager(1);
        tm.buildTeams(List.of(a, b), null, id -> null);
        assertEquals(2, tm.getTeams().size());
        assertNotSame(tm.getTeam(a), tm.getTeam(b));
    }
}
