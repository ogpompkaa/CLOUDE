package pl.ultrahc.paper.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zarzadza druzynami w obrebie JEDNEJ gry. Operuje na pojeciu "druzyna" od
 * poczatku (solo = druzyna 1-osobowa), rozmiar sterowany parametrem gry.
 */
public class TeamManager {

    private final int teamSize;
    private final List<Team> teams = new ArrayList<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();

    public TeamManager(int teamSize) {
        this.teamSize = Math.max(1, teamSize);
    }

    /**
     * Buduje druzyny z listy uczestnikow (na razie kolejno; matchmaking DUO/SQUAD
     * dojdzie z warstwa sieciowa). Nazwy: nick lidera (solo) lub "Druzyna N".
     */
    /**
     * Buduje druzyny grupujac graczy wg party (ten sam groupKey = razem), a graczy
     * bez party (groupKey == null) pakujac wspolnie w druzyny po teamSize.
     * @param groupKey funkcja uuid -> id party (null = solo/bez party)
     */
    public void buildTeams(List<UUID> participants, Map<UUID, String> names,
                           java.util.function.Function<UUID, String> groupKey) {
        List<UUID> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);

        java.util.Map<String, List<UUID>> parties = new java.util.LinkedHashMap<>();
        List<UUID> solos = new ArrayList<>();
        for (UUID uuid : shuffled) {
            String key = groupKey == null ? null : groupKey.apply(uuid);
            if (key == null) solos.add(uuid);
            else parties.computeIfAbsent(key, k -> new ArrayList<>()).add(uuid);
        }

        int[] teamId = {1};
        for (List<UUID> group : parties.values()) chunkIntoTeams(group, names, teamId); // party razem
        chunkIntoTeams(solos, names, teamId);                                           // solo wspolnie
    }

    private void chunkIntoTeams(List<UUID> group, Map<UUID, String> names, int[] teamId) {
        for (int i = 0; i < group.size(); i += teamSize) {
            List<UUID> slice = group.subList(i, Math.min(i + teamSize, group.size()));
            String display = teamSize == 1 && names != null && !slice.isEmpty()
                    ? names.getOrDefault(slice.get(0), "Druzyna " + teamId[0])
                    : "Druzyna " + teamId[0];
            Team team = new Team(teamId[0], display);
            for (UUID uuid : slice) {
                team.addMember(uuid);
                byPlayer.put(uuid, team);
            }
            teams.add(team);
            teamId[0]++;
        }
    }

    public Team getTeam(UUID uuid) { return byPlayer.get(uuid); }

    public List<Team> getTeams() { return teams; }

    /** Druzyny wciaz zywe. */
    public List<Team> aliveTeams() {
        List<Team> out = new ArrayList<>();
        for (Team t : teams) if (!t.isEliminated()) out.add(t);
        return out;
    }

    /** Liczba zywych graczy (do scoreboardu "Zywi"). */
    public int alivePlayers() {
        int n = 0;
        for (Team t : teams) n += t.aliveCount();
        return n;
    }

    /** Top druzyny wg killi (do scoreboardu Top 3). */
    public List<Team> topByKills(int limit) {
        List<Team> sorted = new ArrayList<>(teams);
        sorted.sort(Comparator.comparingInt(Team::getKills).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
}
