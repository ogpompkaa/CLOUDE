package pl.ultrahc.common.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Trwały profil gracza (dane międzysesyjne, per-konto — NIE per-gra).
 *
 * <p>Trzy rozdzielne systemy liczbowe (nigdy nie mieszane):
 * <ul>
 *   <li>{@link #credits} — waluta sklepowa (wyświetlana jako "XP"), za klasy i receptury,</li>
 *   <li>{@link #progressPoints} + {@link #level} — PD i poziom "gwiazdki",</li>
 *   <li>natywny exp Minecrafta — tu NIEobecny, żyje wyłącznie po stronie vanilla.</li>
 * </ul>
 */
public class PlayerProfile {

    private final UUID uuid;
    private String name;

    /** Waluta sklepowa (spec: "XP"). Kolumna DB: credits. */
    private long credits;
    /** PD zdobyte w bieżącym poziomie (odwzorowuje "0/750"). */
    private long progressPoints;
    /** Poziom "gwiazdki". */
    private int level;

    /** Statystyki dożywotnie (per-konto, nie per-gra). */
    private int kills;
    private int wins;

    private final Set<String> unlockedClasses = new LinkedHashSet<>();
    private final Set<String> unlockedRecipes = new LinkedHashSet<>();
    private String selectedClass;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCredits() { return credits; }
    public void setCredits(long credits) { this.credits = credits; }

    public long getProgressPoints() { return progressPoints; }
    public void setProgressPoints(long progressPoints) { this.progressPoints = progressPoints; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public Set<String> getUnlockedClasses() { return unlockedClasses; }
    public Set<String> getUnlockedRecipes() { return unlockedRecipes; }

    public String getSelectedClass() { return selectedClass; }
    public void setSelectedClass(String selectedClass) { this.selectedClass = selectedClass; }
}
