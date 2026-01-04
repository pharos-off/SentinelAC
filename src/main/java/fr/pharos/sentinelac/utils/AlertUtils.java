package fr.pharos.sentinelac.utils;

import fr.pharos.sentinelac.SentinelAC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utilitaires pour envoyer des alertes au staff
 */
public class AlertUtils {

    private static final Map<UUID, Long> lastAlertTime = new HashMap<>();

    /**
     * Envoie une alerte à tous les joueurs ayant la permission
     */
    public static void sendAlert(SentinelAC plugin, Player violator, String checkName,
                                 int violations, int maxViolations, String details) {

        // Vérifier le cooldown
        long currentTime = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("alerts.cooldown", 3) * 1000;

        UUID violatorId = violator.getUniqueId();
        if (lastAlertTime.containsKey(violatorId)) {
            if (currentTime - lastAlertTime.get(violatorId) < cooldown) {
                return; // Cooldown actif
            }
        }
        lastAlertTime.put(violatorId, currentTime);

        // Formater le message
        String format = plugin.getConfig().getString("alerts.format",
                "&c[AC] &e%player% &7a déclenché &f%check% &7(VL: %violations%/%max%)");

        String message = formatMessage(format, violator.getName(), checkName, violations, maxViolations);

        // Ajouter les détails si en mode debug
        if (plugin.getConfig().getBoolean("general.debug") && details != null) {
            message += ChatColor.DARK_GRAY + " [" + details + "]";
        }

        // Envoyer aux joueurs avec permission
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("anticheat.alerts")) {
                staff.sendMessage(message);

                // Jouer un son si activé
                if (plugin.getConfig().getBoolean("alerts.sound-enabled", true)) {
                    playAlertSound(plugin, staff);
                }
            }
        }

        // Log dans la console
        plugin.getLogger().warning("[ALERT] " + ChatColor.stripColor(message));
    }

    /**
     * Formate le message d'alerte
     */
    private static String formatMessage(String format, String playerName, String checkName,
                                        int violations, int maxViolations) {
        return ChatColor.translateAlternateColorCodes('&', format)
                .replace("%player%", playerName)
                .replace("%check%", checkName)
                .replace("%violations%", String.valueOf(violations))
                .replace("%max%", String.valueOf(maxViolations));
    }

    /**
     * Joue un son d'alerte
     */
    private static void playAlertSound(SentinelAC plugin, Player player) {
        try {
            String soundName = plugin.getConfig().getString("alerts.sound-type", "BLOCK_NOTE_BLOCK_PLING");
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Son d'alerte invalide dans la config: " + e.getMessage());
        }
    }

    /**
     * Envoie un message de broadcast aux admins
     */
    public static void broadcastToAdmins(String message) {
        String formatted = ChatColor.translateAlternateColorCodes('&', message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("anticheat.admin")) {
                player.sendMessage(formatted);
            }
        }
    }

    /**
     * Crée une barre de progression pour les violations
     */
    public static String createProgressBar(int current, int max, int barLength) {
        double percentage = (double) current / max;
        int filled = (int) (percentage * barLength);

        StringBuilder bar = new StringBuilder(ChatColor.GRAY + "[");

        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                if (percentage < 0.5) {
                    bar.append(ChatColor.GREEN + "█");
                } else if (percentage < 0.75) {
                    bar.append(ChatColor.YELLOW + "█");
                } else {
                    bar.append(ChatColor.RED + "█");
                }
            } else {
                bar.append(ChatColor.DARK_GRAY + "█");
            }
        }

        bar.append(ChatColor.GRAY + "]");
        return bar.toString();
    }

    /**
     * Nettoie les anciens cooldowns
     */
    public static void cleanupOldCooldowns() {
        long currentTime = System.currentTimeMillis();
        long maxAge = 300000; // 5 minutes

        lastAlertTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > maxAge
        );
    }
}