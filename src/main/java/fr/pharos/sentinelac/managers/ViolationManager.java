package fr.pharos.sentinelac.managers;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.utils.AlertUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gère les violations des joueurs et les actions associées
 */
public class ViolationManager {

    private final SentinelAC plugin;
    private final Map<UUID, PlayerData> playerDataMap;
    private BukkitTask decayTask;

    public ViolationManager(SentinelAC plugin) {
        this.plugin = plugin;
        this.playerDataMap = new HashMap<>();
    }

    /**
     * Récupère ou crée les données d'un joueur
     */
    public PlayerData getPlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerData(uuid));
    }

    /**
     * Récupère les données d'un joueur par UUID
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    /**
     * Supprime les données d'un joueur
     */
    public void removePlayerData(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    /**
     * Enregistre une violation pour un joueur
     */
    public void flagPlayer(Player player, String checkName, String details) {
        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        PlayerData data = getPlayerData(player);
        data.addViolation(checkName);

        int violations = data.getViolations(checkName);
        int maxViolations = plugin.getConfig().getInt("movement." + checkName + ".max-violations", 10);

        plugin.debug(player.getName() + " flagged for " + checkName +
                " (VL: " + violations + "/" + maxViolations + ") - " + details);

        // Envoyer l'alerte au staff
        AlertUtils.sendAlert(plugin, player, checkName, violations, maxViolations, details);

        // Sauvegarder dans la base de données si activée
        if (plugin.getDatabaseManager().isEnabled()) {
            plugin.getDatabaseManager().saveViolation(
                    player.getUniqueId(),
                    checkName,
                    violations,
                    details
            );
        }

        // Analyser avec Machine Learning si activé
        if (plugin.getMLAnalyzer() != null) {
            plugin.getMLAnalyzer().analyzePlayer(player, data);
        }

        // Vérifier si le seuil de violations est atteint
        if (violations >= maxViolations) {
            handleMaxViolations(player, checkName, violations);
        }

        // Vérifier le seuil de ban automatique
        int totalViolations = data.getTotalViolations();
        int autoBanThreshold = plugin.getConfig().getInt("general.auto-ban-threshold", 50);

        if (autoBanThreshold > 0 && totalViolations >= autoBanThreshold) {
            executeBan(player, checkName);
        }
    }

    /**
     * Gère les actions quand le max de violations est atteint
     */
    private void handleMaxViolations(Player player, String checkName, int violations) {
        // Téléporter le joueur à sa dernière position valide
        PlayerData data = getPlayerData(player);
        if (data.getLastLocation() != null) {
            player.teleport(data.getLastLocation());
        }

        // Message au joueur
        player.sendMessage("§c[AntiCheat] §fMouvement suspect détecté. Ne trichez pas!");

        plugin.debug(player.getName() + " setback applied for " + checkName);
    }

    /**
     * Exécute le ban d'un joueur
     */
    private void executeBan(Player player, String checkName) {
        String banCommand = plugin.getConfig().getString("general.ban-command", "");

        if (banCommand.isEmpty()) {
            return;
        }

        banCommand = banCommand
                .replace("%player%", player.getName())
                .replace("%check%", checkName);

        final String finalCommand = banCommand;
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
        );

        plugin.getLogger().warning("Auto-ban exécuté sur " + player.getName() +
                " pour violations répétées (" + checkName + ")");
    }

    /**
     * Démarre la tâche de décrémentation des violations
     */
    public void startDecayTask() {
        int decayTime = plugin.getConfig().getInt("violations.decay-time", 60);
        int decayAmount = plugin.getConfig().getInt("violations.decay-amount", 1);

        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (PlayerData data : playerDataMap.values()) {
                for (String checkName : data.getAllViolations().keySet()) {
                    if (data.getViolations(checkName) > 0) {
                        data.decreaseViolation(checkName, decayAmount);
                    }
                }
            }
        }, decayTime * 20L, decayTime * 20L); // Convertir secondes en ticks
    }

    /**
     * Arrête la tâche de décrémentation
     */
    public void stopDecayTask() {
        if (decayTask != null) {
            decayTask.cancel();
        }
    }

    /**
     * Réinitialise les violations d'un joueur
     */
    public void resetViolations(Player player, String checkName) {
        PlayerData data = getPlayerData(player);
        if (checkName == null) {
            // Réinitialiser toutes les violations
            for (String check : data.getAllViolations().keySet()) {
                data.resetViolations(check);
            }
        } else {
            // Réinitialiser une violation spécifique
            data.resetViolations(checkName);
        }
    }

    /**
     * Sauvegarde toutes les données (pour onDisable)
     */
    public void saveAllData() {
        // Cette méthode peut être étendue pour sauvegarder dans une base de données
        plugin.getLogger().info("Sauvegarde des données de " + playerDataMap.size() + " joueurs");
    }

    /**
     * Récupère toutes les données des joueurs
     */
    public Map<UUID, PlayerData> getAllPlayerData() {
        return new HashMap<>(playerDataMap);
    }
}