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
    public void buildTeams(List<UUID> participants, Map<UUID, String> names) {
        List<UUID> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        int teamId = 1;
        for (int i = 0; i < shuffled.size(); i += teamSize) {
            List<UUID> slice = shuffled.subList(i, Math.min(i + teamSize, shuffled.size()));
            // Nazwa druzyny: nick lidera w solo, inaczej "Druzyna N".
            String display = teamSize == 1 && names != null && !slice.isEmpty()
                    ? names.getOrDefault(slice.get(0), "Druzyna " + teamId)
                    : "Druzyna " + teamId;
            Team team = new Team(teamId, display);
            for (UUID uuid : slice) {
                team.addMember(uuid);
                byPlayer.put(uuid, team);
            }
            teams.add(team);
            teamId++;
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
