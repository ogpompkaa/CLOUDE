package pl.ultrahc.paper.game;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Druzyna w grze. SOLO = druzyna 1-osobowa (ten sam model), zeby dodanie
 * DUO/TRIO/SQUAD nie wymagalo przepisywania logiki killi/wygranej/kompasu.
 */
public class Team {

    private final int id;
    private final String name;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private int kills;

    public Team(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getAlive() { return alive; }
    public int getKills() { return kills; }

    public void addMember(UUID uuid) {
        members.add(uuid);
        alive.add(uuid);
    }

    public void addKill() { kills++; }

    /** Oznacza gracza jako martwego (eliminacja/rozlaczenie). */
    public void markDead(UUID uuid) {
        alive.remove(uuid);
    }

    public boolean isAlive(UUID uuid) { return alive.contains(uuid); }

    /** Druzyna wyeliminowana, gdy nikt z niej nie zyje. */
    public boolean isEliminated() { return alive.isEmpty(); }

    public int aliveCount() { return alive.size(); }
}
