package pl.ultrahc.common.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.ultrahc.common.model.PlayerProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Magazyn MySQL/MariaDB (produkcja / sieć wieloserwerowa). Ta sama logika DAO co
 * {@link SqliteStorage}, ale z pulą połączeń HikariCP (bezpieczne wielowątkowo) i
 * dialektem MySQL (VARCHAR w kluczach, ON DUPLICATE KEY UPDATE).
 */
public class MysqlStorage implements Storage {

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final int poolSize;
    private final Logger logger;
    private HikariDataSource dataSource;

    public MysqlStorage(String host, int port, String database, String user, String password, int poolSize, Logger logger) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
        this.poolSize = poolSize;
        this.logger = logger;
    }

    @Override
    public void init() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true");
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(Math.max(2, poolSize));
        cfg.setPoolName("UltraHC-MySQL");
        this.dataSource = new HikariDataSource(cfg);

        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS players (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "name VARCHAR(32) NOT NULL," +
                            "credits BIGINT NOT NULL DEFAULT 0," +
                            "progress_points BIGINT NOT NULL DEFAULT 0," +
                            "level INT NOT NULL DEFAULT 0," +
                            "kills INT NOT NULL DEFAULT 0," +
                            "wins INT NOT NULL DEFAULT 0," +
                            "unlocked_classes TEXT," +
                            "unlocked_recipes TEXT," +
                            "selected_class VARCHAR(32) NOT NULL DEFAULT '" + SqliteStorage.DEFAULT_CLASS + "')")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS quest_progress (" +
                            "uuid VARCHAR(36) NOT NULL," +
                            "quest_id VARCHAR(64) NOT NULL," +
                            "period VARCHAR(32) NOT NULL DEFAULT ''," +
                            "progress BIGINT NOT NULL DEFAULT 0," +
                            "completed TINYINT NOT NULL DEFAULT 0," +
                            "PRIMARY KEY (uuid, quest_id))")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS party_members (uuid VARCHAR(36) PRIMARY KEY, party_id VARCHAR(36) NOT NULL)")) {
                ps.executeUpdate();
            }
        }
        logger.info("[UltraHC] Magazyn MySQL zainicjalizowany: " + host + ":" + port + "/" + database);
    }

    @Override
    public PlayerProfile loadProfile(UUID uuid, String name) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return readProfile(rs);
            }
        }
        PlayerProfile fresh = new PlayerProfile(uuid, name);
        fresh.setSelectedClass(SqliteStorage.DEFAULT_CLASS);
        fresh.getUnlockedClasses().add(SqliteStorage.DEFAULT_CLASS);
        saveProfile(fresh);
        return fresh;
    }

    @Override
    public void saveProfile(PlayerProfile p) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO players (uuid,name,credits,progress_points,level,kills,wins," +
                             "unlocked_classes,unlocked_recipes,selected_class) VALUES (?,?,?,?,?,?,?,?,?,?) " +
                             "ON DUPLICATE KEY UPDATE name=VALUES(name),credits=VALUES(credits)," +
                             "progress_points=VALUES(progress_points),level=VALUES(level),kills=VALUES(kills)," +
                             "wins=VALUES(wins),unlocked_classes=VALUES(unlocked_classes)," +
                             "unlocked_recipes=VALUES(unlocked_recipes),selected_class=VALUES(selected_class)")) {
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
        String order = type == LeaderboardType.LEVEL ? "level DESC, progress_points DESC" : column + " DESC";
        List<LeaderboardEntry> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
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
    public void resetSeason() throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE players SET level=0, progress_points=0, unlocked_recipes=''")) {
            ps.executeUpdate();
        }
        logger.info("[UltraHC] Reset sezonu wykonany (MySQL).");
    }

    @Override
    public List<QuestRecord> loadQuests(UUID uuid) throws Exception {
        List<QuestRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
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
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO quest_progress (uuid, quest_id, period, progress, completed) VALUES (?,?,?,?,?) " +
                             "ON DUPLICATE KEY UPDATE period=VALUES(period), progress=VALUES(progress), completed=VALUES(completed)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, r.questId());
            ps.setString(3, r.period());
            ps.setLong(4, r.progress());
            ps.setInt(5, r.completed() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    @Override
    public void setPartyMember(UUID uuid, String partyId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO party_members (uuid, party_id) VALUES (?,?) " +
                             "ON DUPLICATE KEY UPDATE party_id=VALUES(party_id)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, partyId);
            ps.executeUpdate();
        }
    }

    @Override
    public void clearPartyMember(UUID uuid) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM party_members WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    @Override
    public java.util.Map<UUID, String> loadPartyIds(java.util.Collection<UUID> uuids) throws Exception {
        java.util.Map<UUID, String> out = new java.util.HashMap<>();
        if (uuids.isEmpty()) return out;
        String placeholders = String.join(",", java.util.Collections.nCopies(uuids.size(), "?"));
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
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
        if (dataSource != null) dataSource.close();
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

    private void addCsv(Set<String> target, String csv) {
        if (csv == null || csv.isBlank()) return;
        for (String s : csv.split(",")) {
            if (!s.isBlank()) target.add(s.trim());
        }
    }
}
