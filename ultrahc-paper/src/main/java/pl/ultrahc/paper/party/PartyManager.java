package pl.ultrahc.paper.party;

import org.bukkit.entity.Player;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zarzadza party (grupami graczy) przed gra — w pamieci serwera. Party trzymaja
 * sie razem w jednej druzynie meczu (grupowanie w TeamManager). Lider zaprasza,
 * czlonkowie akceptuja; lider ruszajac do gry zabiera online czlonkow.
 */
public class PartyManager {

    private final UltraHcPlugin plugin;
    private final Map<UUID, Party> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    private record Invite(UUID leader, long expiresAt) {}

    public PartyManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private MessagesManager msg() { return plugin.messages(); }
    public boolean enabled() { return plugin.configManager().raw().getBoolean("party.enabled", true); }
    public int maxSize() { return plugin.configManager().raw().getInt("party.max-size", 4); }

    public Party get(UUID uuid) { return byMember.get(uuid); }

    /** Klucz grupujacy do druzyny: id party (gdy >1 czlonek), inaczej null (solo). */
    public String groupKey(UUID uuid) {
        Party p = byMember.get(uuid);
        return (p != null && p.size() > 1) ? p.getId().toString() : null;
    }

    // ----------------------------------------------------------- komendy
    public void invite(Player leader, String targetName) {
        if (!checkEnabled(leader)) return;
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) { leader.sendMessage(msg().prefixed("party.target-offline", null)); return; }
        if (target.equals(leader)) { leader.sendMessage(msg().prefixed("party.self-invite", null)); return; }
        if (byMember.containsKey(target.getUniqueId())) { leader.sendMessage(msg().prefixed("party.target-in-party", null)); return; }

        Party party = byMember.get(leader.getUniqueId());
        if (party == null) { // auto-zaloz party
            party = new Party(leader.getUniqueId());
            byMember.put(leader.getUniqueId(), party);
            dbSet(leader.getUniqueId(), party.getId().toString());
            leader.sendMessage(msg().prefixed("party.created", null));
        } else if (!party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(msg().prefixed("party.not-leader", null));
            return;
        }
        if (party.size() >= maxSize()) {
            leader.sendMessage(msg().prefixed("party.full", Map.of("max", String.valueOf(maxSize()))));
            return;
        }
        long ttl = plugin.configManager().raw().getInt("party.invite-expiry-seconds", 60) * 1000L;
        invites.put(target.getUniqueId(), new Invite(leader.getUniqueId(), System.currentTimeMillis() + ttl));
        leader.sendMessage(msg().prefixed("party.invited", Map.of("player", target.getName())));
        target.sendMessage(msg().prefixed("party.invite-received", Map.of("player", leader.getName())));
    }

    public void accept(Player target) {
        if (!checkEnabled(target)) return;
        Invite inv = invites.remove(target.getUniqueId());
        if (inv == null) { target.sendMessage(msg().prefixed("party.invite-none", null)); return; }
        if (System.currentTimeMillis() > inv.expiresAt()) { target.sendMessage(msg().prefixed("party.invite-expired", null)); return; }
        Party party = byMember.get(inv.leader());
        if (party == null) { target.sendMessage(msg().prefixed("party.invite-expired", null)); return; }
        if (party.size() >= maxSize()) { target.sendMessage(msg().prefixed("party.full", Map.of("max", String.valueOf(maxSize())))); return; }

        party.getMembers().add(target.getUniqueId());
        byMember.put(target.getUniqueId(), party);
        dbSet(target.getUniqueId(), party.getId().toString());
        broadcast(party, "party.joined", Map.of("player", target.getName()));
    }

    public void deny(Player target) {
        invites.remove(target.getUniqueId());
        target.sendMessage(msg().prefixed("party.invite-none", null));
    }

    public void leave(Player player) {
        Party party = byMember.remove(player.getUniqueId());
        if (party == null) { player.sendMessage(msg().prefixed("party.not-in", null)); return; }
        party.getMembers().remove(player.getUniqueId());
        dbClear(player.getUniqueId());
        if (party.isLeader(player.getUniqueId())) {
            disbandInternal(party, "party.leader-left");
        } else {
            broadcast(party, "party.left", Map.of("player", player.getName()));
            player.sendMessage(msg().prefixed("party.left", Map.of("player", player.getName())));
        }
    }

    public void kick(Player leader, String targetName) {
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) { leader.sendMessage(msg().prefixed("party.not-in", null)); return; }
        if (!party.isLeader(leader.getUniqueId())) { leader.sendMessage(msg().prefixed("party.not-leader", null)); return; }
        Player target = plugin.getServer().getPlayerExact(targetName);
        UUID targetId = target != null ? target.getUniqueId() : null;
        if (targetId == null || !party.getMembers().contains(targetId)) { leader.sendMessage(msg().prefixed("party.target-in-party", null)); return; }
        party.getMembers().remove(targetId);
        byMember.remove(targetId);
        dbClear(targetId);
        broadcast(party, "party.kicked", Map.of("player", target.getName()));
        target.sendMessage(msg().prefixed("party.kicked", Map.of("player", target.getName())));
    }

    public void disband(Player leader) {
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) { leader.sendMessage(msg().prefixed("party.not-in", null)); return; }
        if (!party.isLeader(leader.getUniqueId())) { leader.sendMessage(msg().prefixed("party.not-leader", null)); return; }
        disbandInternal(party, "party.disbanded");
    }

    public void list(Player player) {
        Party party = byMember.get(player.getUniqueId());
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
        Party party = byMember.get(player.getUniqueId());
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
        Party party = byMember.get(leader);
        List<Player> out = new java.util.ArrayList<>();
        if (party == null) return out;
        for (UUID id : party.getMembers()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public boolean isLeader(UUID uuid) {
        Party p = byMember.get(uuid);
        return p != null && p.isLeader(uuid);
    }

    /** Sprzatanie przy wyjsciu gracza (usuwa z party, zaproszenia). */
    public void handleQuit(Player player) {
        invites.remove(player.getUniqueId());
        if (byMember.containsKey(player.getUniqueId())) leave(player);
    }

    // --------------------------------------------------------- helpers
    private void disbandInternal(Party party, String reasonKey) {
        for (UUID id : new java.util.ArrayList<>(party.getMembers())) {
            byMember.remove(id);
            dbClear(id);
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(msg().prefixed(reasonKey, null));
        }
        party.getMembers().clear();
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
