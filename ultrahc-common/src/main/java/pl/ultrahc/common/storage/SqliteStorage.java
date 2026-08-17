package pl.ultrahc.common.storage;

import pl.ultrahc.common.model.PlayerProfile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Magazyn SQLite (dev / pojedynczy serwer). Zbiory odblokowań trzymane jako
 * lista rozdzielona przecinkami w jednej kolumnie (prostota nad normalizacją).
 */
public class SqliteStorage implements Storage {

    /** Domyślna klasa startowa nowego gracza (Cywil). */
    public static final String DEFAULT_CLASS = "civil";

    private final String jdbcUrl;
    private final Logger logger;
    private Connection connection;

    public SqliteStorage(String filePath, Logger logger) {
        this.jdbcUrl = "jdbc:sqlite:" + filePath;
        this.logger = logger;
    }

    @Override
    public void init() throws Exception {
        // Sterownik SQLite bywa spakowany w shade — wymuszamy załadowanie.
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection(jdbcUrl);
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS players (" +
                        "uuid TEXT PRIMARY KEY," +
                        "name TEXT NOT NULL," +
                        "credits INTEGER NOT NULL DEFAULT 0," +
                        "progress_points INTEGER NOT NULL DEFAULT 0," +
                        "level INTEGER NOT NULL DEFAULT 0," +
                        "kills INTEGER NOT NULL DEFAULT 0," +
                        "wins INTEGER NOT NULL DEFAULT 0," +
                        "unlocked_classes TEXT NOT NULL DEFAULT ''," +
                        "unlocked_recipes TEXT NOT NULL DEFAULT ''," +
                        "selected_class TEXT NOT NULL DEFAULT '" + DEFAULT_CLASS + "')")) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS quest_progress (" +
                        "uuid TEXT NOT NULL," +
                        "quest_id TEXT NOT NULL," +
                        "period TEXT NOT NULL DEFAULT ''," +
                        "progress INTEGER NOT NULL DEFAULT 0," +
                        "completed INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY(uuid, quest_id))")) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS party_members (uuid TEXT PRIMARY KEY, party_id TEXT NOT NULL)")) {
            ps.executeUpdate();
        }
        logger.info("[UltraHC] Magazyn SQLite zainicjalizowany: " + jdbcUrl);
    }

    @Override
    public PlayerProfile loadProfile(UUID uuid, String name) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readProfile(rs);
                }
            }
        }
        // Nowy gracz — profil domyślny (klasa Cywil odblokowana i wybrana).
        PlayerProfile fresh = new PlayerProfile(uuid, name);
        fresh.setSelectedClass(DEFAULT_CLASS);
        fresh.getUnlockedClasses().add(DEFAULT_CLASS);
        saveProfile(fresh);
        return fresh;
    }

    @Override
    public void saveProfile(PlayerProfile p) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO players (uuid,name,credits,progress_points,level,kills,wins," +
                        "unlocked_classes,unlocked_recipes,selected_class) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET " +
                        "name=excluded.name,credits=excluded.credits," +
                        "progress_points=excluded.progress_points,level=excluded.level," +
                        "kills=excluded.kills,wins=excluded.wins," +
                        "unlocked_classes=excluded.unlocked_classes," +
                        "unlocked_recipes=excluded.unlocked_recipes," +
                        "selected_class=excluded.selected_class")) {
            ps.setString(1, p.getUuid().toString());
            ps.setString(2, p.getName());
            ps.setLong(3, p.getCredits());
            ps.setLong(4, p.getProgressPoints());
            ps.setInt(5, p.getLevel());
            ps.setInt(6, p.getKills());
            ps.setInt(7, p.getWins());
            ps.setString(8, String.join(",", p.getUnlockedClasses()));
            ps.setString(9, String.join(",", p.getUnlockedRecipes()));
            ps.setString(10, p.getSelectedClass());
            ps.executeUpdate();
        }
    }

    @Override
    public List<LeaderboardEntry> topBy(LeaderboardType type, int limit) throws Exception {
        String column = switch (type) {
            case KILLS -> "kills";
            case WINS -> "wins";
            case LEVEL -> "level";
        };
        // Dla poziomu dogrywka po PD w bieżącym poziomie.
        String order = type == LeaderboardType.LEVEL ? "level DESC, progress_points DESC" : column + " DESC";
        List<LeaderboardEntry> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, name, " + column + " AS v FROM players ORDER BY " + order + " LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LeaderboardEntry(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("v")));
                }
            }
        }
        return out;
    }

    @Override
    public List<QuestRecord> loadQuests(UUID uuid) throws Exception {
        List<QuestRecord> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT quest_id, period, progress, completed FROM quest_progress WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new QuestRecord(rs.getString("quest_id"), rs.getString("period"),
                            rs.getLong("progress"), rs.getInt("completed") != 0));
                }
            }
        }
        return out;
    }

    @Override
    public void saveQuest(UUID uuid, QuestRecord r) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO quest_progress (uuid, quest_id, period, progress, completed) VALUES (?,?,?,?,?) " +
                        "ON CONFLICT(uuid, quest_id) DO UPDATE SET " +
                        "period=excluded.period, progress=excluded.progress, completed=excluded.completed")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, r.questId());
            ps.setString(3, r.period());
            ps.setLong(4, r.progress());
            ps.setInt(5, r.completed() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    @Override
    public void resetSeason() throws Exception {
        // Reset sezonu: poziomy, PD i receptury do zera. Klasy i XP-sklepowe zostają.
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE players SET level=0, progress_points=0, unlocked_recipes=''")) {
            ps.executeUpdate();
        }
        logger.info("[UltraHC] Reset sezonu wykonany (poziomy, PD i receptury wyzerowane).");
    }

    @Override
    public void setPartyMember(UUID uuid, String partyId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO party_members (uuid, party_id) VALUES (?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET party_id=excluded.party_id")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, partyId);
            ps.executeUpdate();
        }
    }

    @Override
    public void clearPartyMember(UUID uuid) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM party_members WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    @Override
    public java.util.Map<UUID, String> loadPartyIds(java.util.Collection<UUID> uuids) throws Exception {
        java.util.Map<UUID, String> out = new java.util.HashMap<>();
        if (uuids.isEmpty()) return out;
        String placeholders = String.join(",", java.util.Collections.nCopies(uuids.size(), "?"));
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, party_id FROM party_members WHERE uuid IN (" + placeholders + ")")) {
            int i = 1;
            for (UUID u : uuids) ps.setString(i++, u.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(UUID.fromString(rs.getString("uuid")), rs.getString("party_id"));
            }
        }
        return out;
    }

    @Override
    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (Exception e) {
            logger.warning("[UltraHC] Błąd zamykania SQLite: " + e.getMessage());
        }
    }

    private PlayerProfile readProfile(ResultSet rs) throws Exception {
        PlayerProfile p = new PlayerProfile(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
        p.setCredits(rs.getLong("credits"));
        p.setProgressPoints(rs.getLong("progress_points"));
        p.setLevel(rs.getInt("level"));
        p.setKills(rs.getInt("kills"));
        p.setWins(rs.getInt("wins"));
        p.setSelectedClass(rs.getString("selected_class"));
        addCsv(p.getUnlockedClasses(), rs.getString("unlocked_classes"));
        addCsv(p.getUnlockedRecipes(), rs.getString("unlocked_recipes"));
        return p;
    }

    private void addCsv(java.util.Set<String> target, String csv) {
        if (csv == null || csv.isBlank()) return;
        for (String s : csv.split(",")) {
            if (!s.isBlank()) target.add(s.trim());
        }
    }
}
