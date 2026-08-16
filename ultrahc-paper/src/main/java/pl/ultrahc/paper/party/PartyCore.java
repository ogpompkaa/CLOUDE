package pl.ultrahc.paper.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Czysta logika party (reguly bez zaleznosci od Bukkit/messages/DB) — dzieki temu
 * jest w pelni testowalna jednostkowo. Trzyma sklad party ({@code byMember}) oraz
 * aktywne zaproszenia z czasem wygasniecia. {@link PartyManager} deleguje tu reguly,
 * a sam zajmuje sie warstwa serwera: komunikaty, dzwieki, persystencja do DB.
 */
public class PartyCore {

    private final int maxSize;
    private final long inviteTtlMillis;
    private final Map<UUID, Party> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public record Invite(UUID leader, long expiresAt) {}

    /** Wynik operacji — {@link PartyManager} mapuje na klucze komunikatow. */
    public enum Status {
        OK, SELF, ALREADY_IN_PARTY, NOT_LEADER, FULL,
        NO_INVITE, INVITE_EXPIRED, NOT_IN_PARTY, NOT_MEMBER
    }

    public PartyCore(int maxSize, long inviteTtlMillis) {
        this.maxSize = maxSize;
        this.inviteTtlMillis = inviteTtlMillis;
    }

    public int maxSize() { return maxSize; }

    public Party get(UUID uuid) { return byMember.get(uuid); }

    public boolean isLeader(UUID uuid) {
        Party p = byMember.get(uuid);
        return p != null && p.isLeader(uuid);
    }

    /** Kopia listy czlonkow party gracza (pusta gdy poza party). */
    public List<UUID> members(UUID anyMember) {
        Party p = byMember.get(anyMember);
        return p == null ? List.of() : new ArrayList<>(p.getMembers());
    }

    public boolean hasInvite(UUID target) {
        Invite inv = invites.get(target);
        return inv != null;
    }

    // ----------------------------------------------------------- operacje

    public record InviteResult(Status status, Party party, boolean createdParty) {}

    /** Zaproszenie gracza do party lidera (party zakladane automatycznie). */
    public InviteResult invite(UUID leader, UUID target, long now) {
        if (leader.equals(target)) return new InviteResult(Status.SELF, null, false);
        if (byMember.containsKey(target)) return new InviteResult(Status.ALREADY_IN_PARTY, null, false);

        Party party = byMember.get(leader);
        boolean created = false;
        if (party == null) {
            party = new Party(leader);
            byMember.put(leader, party);
            created = true;
        } else if (!party.isLeader(leader)) {
            return new InviteResult(Status.NOT_LEADER, null, false);
        }
        if (party.size() >= maxSize) {
            // Cofnij automatyczne zalozenie, jesli party bylo puste-jednoosobowe i pelne (maxSize<=1).
            if (created) byMember.remove(leader);
            return new InviteResult(Status.FULL, party, false);
        }
        invites.put(target, new Invite(leader, now + inviteTtlMillis));
        return new InviteResult(Status.OK, party, created);
    }

    public record AcceptResult(Status status, Party party) {}

    /** Akceptacja aktywnego zaproszenia. */
    public AcceptResult accept(UUID target, long now) {
        Invite inv = invites.remove(target);
        if (inv == null) return new AcceptResult(Status.NO_INVITE, null);
        if (now > inv.expiresAt()) return new AcceptResult(Status.INVITE_EXPIRED, null);
        Party party = byMember.get(inv.leader());
        if (party == null) return new AcceptResult(Status.INVITE_EXPIRED, null);
        if (byMember.containsKey(target)) return new AcceptResult(Status.ALREADY_IN_PARTY, party);
        if (party.size() >= maxSize) return new AcceptResult(Status.FULL, party);
        party.getMembers().add(target);
        byMember.put(target, party);
        return new AcceptResult(Status.OK, party);
    }

    public void deny(UUID target) { invites.remove(target); }

    public record LeaveResult(Status status, Party party, boolean disbanded, List<UUID> affected) {}

    /** Wyjscie z party. Gdy wychodzi lider — party rozwiazane (affected = wszyscy). */
    public LeaveResult leave(UUID player) {
        Party party = byMember.get(player);
        if (party == null) return new LeaveResult(Status.NOT_IN_PARTY, null, false, List.of());
        if (party.isLeader(player)) {
            List<UUID> affected = new ArrayList<>(party.getMembers());
            dropAll(party);
            return new LeaveResult(Status.OK, party, true, affected);
        }
        byMember.remove(player);
        party.getMembers().remove(player);
        return new LeaveResult(Status.OK, party, false, List.of(player));
    }

    public record KickResult(Status status, Party party, UUID target) {}

    /** Lider wyrzuca czlonka. */
    public KickResult kick(UUID leader, UUID target) {
        Party party = byMember.get(leader);
        if (party == null) return new KickResult(Status.NOT_IN_PARTY, null, null);
        if (!party.isLeader(leader)) return new KickResult(Status.NOT_LEADER, null, null);
        if (target == null || target.equals(leader) || !party.getMembers().contains(target)) {
            return new KickResult(Status.NOT_MEMBER, party, null);
        }
        party.getMembers().remove(target);
        byMember.remove(target);
        return new KickResult(Status.OK, party, target);
    }

    public record DisbandResult(Status status, Party party, List<UUID> affected) {}

    /** Lider rozwiazuje party. */
    public DisbandResult disband(UUID leader) {
        Party party = byMember.get(leader);
        if (party == null) return new DisbandResult(Status.NOT_IN_PARTY, null, List.of());
        if (!party.isLeader(leader)) return new DisbandResult(Status.NOT_LEADER, null, List.of());
        List<UUID> affected = new ArrayList<>(party.getMembers());
        dropAll(party);
        return new DisbandResult(Status.OK, party, affected);
    }

    /** Sprzatanie przy wyjsciu gracza z serwera (zaproszenia + ewentualny leave). */
    public LeaveResult handleQuit(UUID player) {
        invites.remove(player);
        if (byMember.containsKey(player)) return leave(player);
        return new LeaveResult(Status.NOT_IN_PARTY, null, false, List.of());
    }

    private void dropAll(Party party) {
        for (UUID id : new ArrayList<>(party.getMembers())) byMember.remove(id);
        party.getMembers().clear();
    }
}
