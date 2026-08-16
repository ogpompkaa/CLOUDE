package pl.ultrahc.paper.party;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testy regul party (bez serwera) — PartyCore. */
class PartyCoreTest {

    private static final long NOW = 1_000_000L;
    private static final long TTL = 60_000L;

    private PartyCore core() { return new PartyCore(4, TTL); }

    @Test
    void inviteCreatesPartyForLeader() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartyCore.InviteResult r = c.invite(a, b, NOW);
        assertEquals(PartyCore.Status.OK, r.status());
        assertTrue(r.createdParty());
        assertNotNull(c.get(a));
        assertTrue(c.isLeader(a));
        assertEquals(1, c.get(a).size());        // zaproszony jeszcze nie dolaczyl
    }

    @Test
    void cannotInviteSelf() {
        PartyCore c = core();
        UUID a = UUID.randomUUID();
        assertEquals(PartyCore.Status.SELF, c.invite(a, a, NOW).status());
        assertFalse(c.hasInvite(a));
    }

    @Test
    void cannotInvitePlayerAlreadyInParty() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        // d probuje zaprosic b, ktory jest juz w party a.
        assertEquals(PartyCore.Status.ALREADY_IN_PARTY, c.invite(d, b, NOW).status());
    }

    @Test
    void nonLeaderCannotInvite() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        // b jest czlonkiem, nie liderem — nie moze zapraszac.
        assertEquals(PartyCore.Status.NOT_LEADER, c.invite(b, d, NOW).status());
    }

    @Test
    void acceptJoinsParty() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        PartyCore.AcceptResult r = c.accept(b, NOW);
        assertEquals(PartyCore.Status.OK, r.status());
        assertEquals(2, c.get(a).size());
        assertEquals(c.get(a), c.get(b));        // ta sama party
        assertFalse(c.hasInvite(b));             // zaproszenie zuzyte
    }

    @Test
    void acceptWithoutInvite() {
        PartyCore c = core();
        assertEquals(PartyCore.Status.NO_INVITE, c.accept(UUID.randomUUID(), NOW).status());
    }

    @Test
    void expiredInviteRejected() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        assertEquals(PartyCore.Status.INVITE_EXPIRED, c.accept(b, NOW + TTL + 1).status());
        assertFalse(c.hasInvite(b));
    }

    @Test
    void partyRespectsMaxSize() {
        PartyCore c = new PartyCore(2, TTL);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);                        // party pelna (2/2)
        assertEquals(PartyCore.Status.FULL, c.invite(a, d, NOW).status());
    }

    @Test
    void leaderLeaveDisbandsParty() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        PartyCore.LeaveResult r = c.leave(a);
        assertEquals(PartyCore.Status.OK, r.status());
        assertTrue(r.disbanded());
        assertTrue(r.affected().contains(a));
        assertTrue(r.affected().contains(b));
        assertNull2(c, a);
        assertNull2(c, b);
    }

    @Test
    void memberLeaveKeepsParty() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        PartyCore.LeaveResult r = c.leave(b);
        assertEquals(PartyCore.Status.OK, r.status());
        assertFalse(r.disbanded());
        assertNotNull(c.get(a));                 // lider dalej w party
        assertEquals(1, c.get(a).size());
        assertNull2(c, b);
    }

    @Test
    void leaderKicksMember() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        PartyCore.KickResult r = c.kick(a, b);
        assertEquals(PartyCore.Status.OK, r.status());
        assertEquals(b, r.target());
        assertNull2(c, b);
        assertEquals(1, c.get(a).size());
    }

    @Test
    void memberCannotKick() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        assertEquals(PartyCore.Status.NOT_LEADER, c.kick(b, a).status());
    }

    @Test
    void kickNonMemberRejected() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        assertEquals(PartyCore.Status.NOT_MEMBER, c.kick(a, UUID.randomUUID()).status());
    }

    @Test
    void disbandClearsEveryone() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        PartyCore.DisbandResult r = c.disband(a);
        assertEquals(PartyCore.Status.OK, r.status());
        assertEquals(2, r.affected().size());
        assertNull2(c, a);
        assertNull2(c, b);
    }

    @Test
    void quitRemovesInvitesAndMembership() {
        PartyCore c = core();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.invite(a, b, NOW);
        c.accept(b, NOW);
        c.handleQuit(b);
        assertNull2(c, b);
        assertEquals(1, c.get(a).size());
        // wychodzacy z oczekujacym zaproszeniem — zaproszenie znika
        UUID d = UUID.randomUUID();
        c.invite(a, d, NOW);
        c.handleQuit(d);
        assertFalse(c.hasInvite(d));
    }

    private static void assertNull2(PartyCore c, UUID id) {
        org.junit.jupiter.api.Assertions.assertNull(c.get(id));
    }
}
