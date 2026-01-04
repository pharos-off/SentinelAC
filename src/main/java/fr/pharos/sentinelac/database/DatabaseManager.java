package fr.pharos.sentinelac.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.pharos.sentinelac.SentinelAC;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Gère la connexion et les opérations de base de données MySQL
 */
public class DatabaseManager {

    private final SentinelAC plugin;
    private HikariDataSource dataSource;
    private boolean enabled;

    public DatabaseManager(SentinelAC plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("database.enabled", false);
    }

    /**
     * Initialise la connexion à la base de données
     */
    public void initialize() {
        if (!enabled) {
            plugin.getLogger().info("Base de données MySQL désactivée");
            return;
        }

        try {
            HikariConfig config = new HikariConfig();

            String host = plugin.getConfig().getString("database.host");
            int port = plugin.getConfig().getInt("database.port");
            String database = plugin.getConfig().getString("database.database");
            String username = plugin.getConfig().getString("database.username");
            String password = plugin.getConfig().getString("database.password");

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&allowPublicKeyRetrieval=true");
            config.setUsername(username);
            config.setPassword(password);

            // Paramètres de la pool
            config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool.maximum-pool-size", 10));
            config.setMinimumIdle(plugin.getConfig().getInt("database.pool.minimum-idle", 2));
            config.setConnectionTimeout(plugin.getConfig().getLong("database.pool.connection-timeout", 30000));
            config.setIdleTimeout(plugin.getConfig().getLong("database.pool.idle-timeout", 600000));
            config.setMaxLifetime(plugin.getConfig().getLong("database.pool.max-lifetime", 1800000));

            // Options MySQL
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);

            // Créer les tables
            createTables();

            plugin.getLogger().info("Connexion MySQL établie avec succès!");

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la connexion MySQL: " + e.getMessage());
            e.printStackTrace();
            enabled = false;
        }
    }

    /**
     * Crée les tables nécessaires
     */
    private void createTables() {
        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS sentinel_players (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) UNIQUE NOT NULL,
                username VARCHAR(16) NOT NULL,
                first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                total_violations INT DEFAULT 0,
                banned BOOLEAN DEFAULT FALSE,
                INDEX idx_uuid (uuid),
                INDEX idx_username (username)
            )
        """;

        String createViolationsTable = """
            CREATE TABLE IF NOT EXISTS sentinel_violations (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                check_name VARCHAR(50) NOT NULL,
                violation_level INT NOT NULL,
                details TEXT,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                server_name VARCHAR(50),
                INDEX idx_player (player_uuid),
                INDEX idx_check (check_name),
                INDEX idx_timestamp (timestamp),
                FOREIGN KEY (player_uuid) REFERENCES sentinel_players(uuid) ON DELETE CASCADE
            )
        """;

        String createBehaviorPatternsTable = """
            CREATE TABLE IF NOT EXISTS sentinel_behavior_patterns (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                pattern_type VARCHAR(50) NOT NULL,
                pattern_data TEXT NOT NULL,
                confidence DOUBLE NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_player (player_uuid),
                INDEX idx_confidence (confidence),
                FOREIGN KEY (player_uuid) REFERENCES sentinel_players(uuid) ON DELETE CASCADE
            )
        """;

        String createPacketLogsTable = """
            CREATE TABLE IF NOT EXISTS sentinel_packet_logs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                packet_type VARCHAR(100) NOT NULL,
                packet_count INT NOT NULL,
                anomaly_detected BOOLEAN DEFAULT FALSE,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_player (player_uuid),
                INDEX idx_anomaly (anomaly_detected),
                FOREIGN KEY (player_uuid) REFERENCES sentinel_players(uuid) ON DELETE CASCADE
            )
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(createPlayersTable);
            stmt.executeUpdate(createViolationsTable);
            stmt.executeUpdate(createBehaviorPatternsTable);
            stmt.executeUpdate(createPacketLogsTable);

            plugin.getLogger().info("Tables de base de données créées/vérifiées");

        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors de la création des tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obtient une connexion à la base de données
     */
    public Connection getConnection() throws SQLException {
        if (!enabled || dataSource == null) {
            throw new SQLException("Base de données non disponible");
        }
        return dataSource.getConnection();
    }

    /**
     * Enregistre ou met à jour un joueur
     */
    public CompletableFuture<Void> savePlayer(UUID uuid, String username) {
        return CompletableFuture.runAsync(() -> {
            String query = """
                INSERT INTO sentinel_players (uuid, username, first_seen, last_seen)
                VALUES (?, ?, NOW(), NOW())
                ON DUPLICATE KEY UPDATE username = ?, last_seen = NOW()
            """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setString(3, username);
                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Erreur lors de la sauvegarde du joueur: " + e.getMessage());
            }
        });
    }

    /**
     * Enregistre une violation
     */
    public CompletableFuture<Void> saveViolation(UUID uuid, String checkName, int violationLevel, String details) {
        return CompletableFuture.runAsync(() -> {
            String query = """
                INSERT INTO sentinel_violations (player_uuid, check_name, violation_level, details, server_name)
                VALUES (?, ?, ?, ?, ?)
            """;

            String updatePlayer = """
                UPDATE sentinel_players SET total_violations = total_violations + 1 WHERE uuid = ?
            """;

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, checkName);
                    stmt.setInt(3, violationLevel);
                    stmt.setString(4, details);
                    stmt.setString(5, plugin.getServer().getName());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = conn.prepareStatement(updatePlayer)) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                conn.commit();

            } catch (SQLException e) {
                plugin.getLogger().warning("Erreur lors de la sauvegarde de la violation: " + e.getMessage());
            }
        });
    }

    /**
     * Enregistre un pattern comportemental
     */
    public CompletableFuture<Void> saveBehaviorPattern(UUID uuid, String patternType, String patternData, double confidence) {
        return CompletableFuture.runAsync(() -> {
            String query = """
                INSERT INTO sentinel_behavior_patterns (player_uuid, pattern_type, pattern_data, confidence)
                VALUES (?, ?, ?, ?)
            """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, patternType);
                stmt.setString(3, patternData);
                stmt.setDouble(4, confidence);
                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Erreur lors de la sauvegarde du pattern: " + e.getMessage());
            }
        });
    }

    /**
     * Enregistre les logs de packets
     */
    public CompletableFuture<Void> savePacketLog(UUID uuid, String packetType, int packetCount, boolean anomaly) {
        return CompletableFuture.runAsync(() -> {
            String query = """
                INSERT INTO sentinel_packet_logs (player_uuid, packet_type, packet_count, anomaly_detected)
                VALUES (?, ?, ?, ?)
            """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, packetType);
                stmt.setInt(3, packetCount);
                stmt.setBoolean(4, anomaly);
                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Erreur lors de la sauvegarde du log packet: " + e.getMessage());
            }
        });
    }

    /**
     * Récupère le total de violations d'un joueur
     */
    public CompletableFuture<Integer> getTotalViolations(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT total_violations FROM sentinel_players WHERE uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getInt("total_violations");
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("Erreur lors de la récupération des violations: " + e.getMessage());
            }

            return 0;
        });
    }

    /**
     * Vérifie si un joueur est banni
     */
    public CompletableFuture<Boolean> isBanned(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT banned FROM sentinel_players WHERE uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getBoolean("banned");
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("Erreur lors de la vérification du ban: " + e.getMessage());
            }

            return false;
        });
    }

    /**
     * Ferme la connexion à la base de données
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Connexion MySQL fermée");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}