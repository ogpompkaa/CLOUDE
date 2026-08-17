package pl.ultrahc.common.instances;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Rejestr instancji gier wspoldzielony miedzy serwerami (patrz DECYZJE, sekcja 1).
 * Arena-serwery zapisuja/odswiezaja swoj wiersz (heartbeat), lobby i proxy czytaja
 * go do matchmakingu. Ta sama warstwa dziala na SQLite (dev) i MySQL (produkcja) —
 * w sieci wieloserwerowej WYMAGANY jest MySQL (SQLite to jeden plik).
 */
public class InstanceRegistry {

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final Logger logger;
    private Connection connection;

    public InstanceRegistry(String jdbcUrl, String user, String password, Logger logger) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.logger = logger;
    }

    public synchronized void init() throws Exception {
        connection = (user == null || user.isBlank())
                ? DriverManager.getConnection(jdbcUrl)
                : DriverManager.getConnection(jdbcUrl, user, password);
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS instances (" +
                        "id VARCHAR(64) PRIMARY KEY," +          // VARCHAR: zgodne z SQLite i MySQL
                        "state VARCHAR(16) NOT NULL," +
                        "players INTEGER NOT NULL DEFAULT 0," +
                        "max_players INTEGER NOT NULL DEFAULT 100," +
                        "team_size INTEGER NOT NULL DEFAULT 1," +
                        "mode VARCHAR(16) NOT NULL DEFAULT 'SOLO'," +
                        "heartbeat BIGINT NOT NULL DEFAULT 0," +
                        "close_requested INTEGER NOT NULL DEFAULT 0)")) {
            ps.executeUpdate();
        }
        // Migracja: dodaj kolumne close_requested do istniejacej (starszej) tabeli. Idempotentne.
        try (PreparedStatement ps = connection.prepareStatement(
                "ALTER TABLE instances ADD COLUMN close_requested INTEGER NOT NULL DEFAULT 0")) {
            ps.executeUpdate();
        } catch (Exception ignored) {
            // kolumna juz istnieje — ok
        }
        logger.info("[UltraHC] Rejestr instancji gotowy.");
    }

    /** Zadanie zamkniecia instancji (ustawiane przez lobby/admina, odbierane przez arene). */
    public synchronized void requestClose(String id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE instances SET close_requested = 1 WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    /** Arena: sprawdza i czysci flage zamkniecia swojej instancji. */
    public synchronized boolean consumeCloseRequest(String id) throws Exception {
        boolean requested = false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT close_requested FROM instances WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) requested = rs.getInt("close_requested") != 0;
            }
        }
        if (requested) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE instances SET close_requested = 0 WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        }
        return requested;
    }

    /** Zapis/aktualizacja wiersza instancji wraz z heartbeatem. */
    public synchronized void upsert(InstanceInfo info) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO instances (id,state,players,max_players,team_size,mode,heartbeat) " +
                        "VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET " +
                        "state=excluded.state, players=excluded.players, max_players=excluded.max_players," +
                        "team_size=excluded.team_size, mode=excluded.mode, heartbeat=excluded.heartbeat")) {
            ps.setString(1, info.id());
            ps.setString(2, info.state());
            ps.setInt(3, info.players());
            ps.setInt(4, info.maxPlayers());
            ps.setInt(5, info.teamSize());
            ps.setString(6, info.mode());
            ps.setLong(7, info.heartbeat());
            ps.executeUpdate();
        }
    }

    public synchronized void remove(String id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM instances WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    /** Instancje, do ktorych mozna dolaczyc: stan WAITING/COUNTDOWN, jest miejsce, swiezy heartbeat. */
    public synchronized List<InstanceInfo> listJoinable(int teamSize, long maxAgeMillis) throws Exception {
        long minHeartbeat = System.currentTimeMillis() - maxAgeMillis;
        List<InstanceInfo> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM instances WHERE team_size = ? AND players < max_players " +
                        "AND state IN ('WAITING','COUNTDOWN') AND heartbeat >= ? ORDER BY players DESC")) {
            ps.setInt(1, teamSize);
            ps.setLong(2, minHeartbeat);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        }
        return out;
    }

    /** Dolaczalne instancje dowolnego trybu (matchmaking wg rozmiaru party). */
    public synchronized List<InstanceInfo> listJoinableAny(long maxAgeMillis) throws Exception {
        long minHeartbeat = System.currentTimeMillis() - maxAgeMillis;
        List<InstanceInfo> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM instances WHERE players < max_players " +
                        "AND state IN ('WAITING','COUNTDOWN') AND heartbeat >= ? ORDER BY team_size, players DESC")) {
            ps.setLong(1, minHeartbeat);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        }
        return out;
    }

    public synchronized List<InstanceInfo> listAll() throws Exception {
        List<InstanceInfo> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM instances ORDER BY id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        }
        return out;
    }

    private InstanceInfo read(ResultSet rs) throws Exception {
        return new InstanceInfo(rs.getString("id"), rs.getString("state"), rs.getInt("players"),
                rs.getInt("max_players"), rs.getInt("team_size"), rs.getString("mode"), rs.getLong("heartbeat"));
    }

    public synchronized void close() {
        try {
            if (connection != null) connection.close();
        } catch (Exception e) {
            logger.warning("[UltraHC] Blad zamykania rejestru instancji: " + e.getMessage());
        }
    }
}
