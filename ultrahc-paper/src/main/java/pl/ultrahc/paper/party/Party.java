package pl.ultrahc.paper.party;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Party (grupa graczy). Lider + czlonkowie (lider tez jest w members). */
public class Party {

    private final UUID id = UUID.randomUUID();
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getId() { return id; }
    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }
    public Set<UUID> getMembers() { return members; }
    public boolean isLeader(UUID uuid) { return uuid.equals(leader); }
    public int size() { return members.size(); }
}
