package pl.ultrahc.paper.party;

import org.bukkit.entity.Player;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Warstwa serwerowa party: komunikaty, dzwieki, persystencja do DB. Cala logika
 * regul (zaproszenia, akceptacja, leave/kick/disband, limity) siedzi w
 * {@link PartyCore} — testowalnej bez serwera. Party trzymaja sie razem w jednej
 * druzynie meczu (grupowanie w TeamManager); lider ruszajac do gry zabiera online
 * czlonkow. Sklad persystowany do DB (arena czyta przy starcie gry — cross-server).
 */
public class PartyManager {

    private final UltraHcPlugin plugin;
    private final PartyCore core;

    public PartyManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
        int max = plugin.configManager().raw().getInt("party.max-size", 4);
        long ttl = plugin.configManager().raw().getInt("party.invite-expiry-seconds", 60) * 1000L;
        this.core = new PartyCore(max, ttl);
    }

    private MessagesManager msg() { return plugin.messages(); }
    public boolean enabled() { return plugin.configManager().raw().getBoolean("party.enabled", true); }
    public int maxSize() { return core.maxSize(); }

    public Party get(UUID uuid) { return core.get(uuid); }
    public boolean isLeader(UUID uuid) { return core.isLeader(uuid); }

    // ----------------------------------------------------------- komendy
    public void invite(Player leader, String targetName) {
        if (!checkEnabled(leader)) return;
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) { leader.sendMessage(msg().prefixed("party.target-offline", null)); return; }

        PartyCore.InviteResult r = core.invite(leader.getUniqueId(), target.getUniqueId(), System.currentTimeMillis());
        switch (r.status()) {
            case SELF -> leader.sendMessage(msg().prefixed("party.self-invite", null));
            case ALREADY_IN_PARTY -> leader.sendMessage(msg().prefixed("party.target-in-party", null));
            case NOT_LEADER -> leader.sendMessage(msg().prefixed("party.not-leader", null));
            case FULL -> leader.sendMessage(msg().prefixed("party.full", Map.of("max", String.valueOf(maxSize()))));
            case OK -> {
                if (r.createdParty()) {
                    dbSet(leader.getUniqueId(), r.party().getId().toString());
                    leader.sendMessage(msg().prefixed("party.created", null));
                }
                leader.sendMessage(msg().prefixed("party.invited", Map.of("player", target.getName())));
                target.sendMessage(msg().prefixed("party.invite-received", Map.of("player", leader.getName())));
            }
            default -> { }
        }
    }

    public void accept(Player target) {
        if (!checkEnabled(target)) return;
        PartyCore.AcceptResult r = core.accept(target.getUniqueId(), System.currentTimeMillis());
        switch (r.status()) {
            case NO_INVITE -> target.sendMessage(msg().prefixed("party.invite-none", null));
            case INVITE_EXPIRED -> target.sendMessage(msg().prefixed("party.invite-expired", null));
            case ALREADY_IN_PARTY -> target.sendMessage(msg().prefixed("party.target-in-party", null));
            case FULL -> target.sendMessage(msg().prefixed("party.full", Map.of("max", String.valueOf(maxSize()))));
            case OK -> {
                dbSet(target.getUniqueId(), r.party().getId().toString());
                broadcast(r.party(), "party.joined", Map.of("player", target.getName()));
            }
            default -> { }
        }
    }

    public void deny(Player target) {
        core.deny(target.getUniqueId());
        target.sendMessage(msg().prefixed("party.invite-none", null));
    }

    public void leave(Player player) {
        PartyCore.LeaveResult r = core.leave(player.getUniqueId());
        if (r.status() == PartyCore.Status.NOT_IN_PARTY) {
            player.sendMessage(msg().prefixed("party.not-in", null));
            return;
        }
        applyDeparture(r, player.getName());
        if (!r.disbanded()) player.sendMessage(msg().prefixed("party.left", Map.of("player", player.getName())));
    }

    public void kick(Player leader, String targetName) {
        Player target = plugin.getServer().getPlayerExact(targetName);
        UUID targetId = target != null ? target.getUniqueId() : null;
        PartyCore.KickResult r = core.kick(leader.getUniqueId(), targetId);
        switch (r.status()) {
            case NOT_IN_PARTY -> leader.sendMessage(msg().prefixed("party.not-in", null));
            case NOT_LEADER -> leader.sendMessage(msg().prefixed("party.not-leader", null));
            case NOT_MEMBER -> leader.sendMessage(msg().prefixed("party.target-in-party", null));
            case OK -> {
                dbClear(r.target());
                String name = target != null ? target.getName() : nameOf(r.target());
                broadcast(r.party(), "party.kicked", Map.of("player", name));
                if (target != null) target.sendMessage(msg().prefixed("party.kicked", Map.of("player", name)));
            }
            default -> { }
        }
    }

    public void disband(Player leader) {
        PartyCore.DisbandResult r = core.disband(leader.getUniqueId());
        switch (r.status()) {
            case NOT_IN_PARTY -> leader.sendMessage(msg().prefixed("party.not-in", null));
            case NOT_LEADER -> leader.sendMessage(msg().prefixed("party.not-leader", null));
            case OK -> notifyAffected(r.affected(), "party.disbanded");
            default -> { }
        }
    }

    public void list(Player player) {
        Party party = core.get(player.getUniqueId());
        if (party == null) { player.sendMessage(msg().prefixed("party.not-in", null)); return; }
        player.sendMessage(msg().prefixed("party.list-header",
                Map.of("count", String.valueOf(party.size()), "max", String.valueOf(maxSize()))));
        for (UUID id : party.getMembers()) {
            String name = nameOf(id);
            String key = party.isLeader(id) ? "party.list-leader" : "party.list-member";
            player.sendMessage(msg().legacy(msg().raw(key, Map.of("player", name))));
        }
    }

    /** Czat party — wiadomosc tylko do czlonkow party. */
    public void chat(Player player, String message) {
        Party party = core.get(player.getUniqueId());
        if (party == null) { player.sendMessage(msg().prefixed("party.not-in", null)); return; }
        var comp = msg().legacy(msg().raw("party.chat-format",
                Map.of("player", player.getName(), "message", message)));
        for (UUID id : party.getMembers()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(comp);
        }
    }

    /** Online czlonkowie party gracza (do zabrania do gry przez lidera). */
    public List<Player> onlineMembers(UUID leader) {
        List<Player> out = new java.util.ArrayList<>();
        for (UUID id : core.members(leader)) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Sprzatanie przy wyjsciu gracza (usuwa z party, zaproszenia). */
    public void handleQuit(Player player) {
        PartyCore.LeaveResult r = core.handleQuit(player.getUniqueId());
        if (r.status() == PartyCore.Status.OK) applyDeparture(r, player.getName());
    }

    // --------------------------------------------------------- helpers

    /** Wspolna obsluga wyjscia/rozwiazania: DB + komunikaty do pozostalych/rozwiazanych. */
    private void applyDeparture(PartyCore.LeaveResult r, String who) {
        if (r.disbanded()) {
            notifyAffected(r.affected(), "party.leader-left");
        } else {
            for (UUID id : r.affected()) dbClear(id);
            if (r.party() != null) broadcast(r.party(), "party.left", Map.of("player", who));
        }
    }

    /** DB-clear + komunikat dla listy dotknietych graczy (rozwiazanie party). */
    private void notifyAffected(List<UUID> affected, String reasonKey) {
        for (UUID id : affected) {
            dbClear(id);
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(msg().prefixed(reasonKey, null));
        }
    }

    // Persystencja skladu party do DB (cross-server: arena czyta przy starcie gry).
    private void dbSet(UUID uuid, String partyId) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { plugin.profiles().storage().setPartyMember(uuid, partyId); }
            catch (Exception e) { plugin.getLogger().warning("[UltraHC] Blad zapisu party: " + e.getMessage()); }
        });
    }

    private void dbClear(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { plugin.profiles().storage().clearPartyMember(uuid); }
            catch (Exception e) { plugin.getLogger().warning("[UltraHC] Blad usuwania party: " + e.getMessage()); }
        });
    }

    private boolean checkEnabled(Player p) {
        if (enabled()) return true;
        p.sendMessage(msg().prefixed("party.disabled", null));
        return false;
    }

    private void broadcast(Party party, String key, Map<String, String> ph) {
        var comp = msg().prefixed(key, ph);
        for (UUID id : party.getMembers()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(comp);
        }
    }

    private String nameOf(UUID id) {
        Player p = plugin.getServer().getPlayer(id);
        if (p != null) return p.getName();
        var prof = plugin.profiles().get(id);
        return prof != null ? prof.getName() : id.toString().substring(0, 8);
    }
}
