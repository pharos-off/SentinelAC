package fr.pharos.sentinelac.updater;

import fr.pharos.sentinelac.SentinelAC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Système de mise à jour automatique du plugin
 */
public class AutoUpdater {

    private final SentinelAC plugin;
    private final String updateUrl;
    private final String versionUrl;
    private final boolean enabled;
    private final boolean autoDownload;
    private String latestVersion;
    private boolean updateAvailable;

    public AutoUpdater(SentinelAC plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("auto-updater.enabled", true);
        this.autoDownload = plugin.getConfig().getBoolean("auto-updater.auto-download", false);
        this.updateUrl = plugin.getConfig().getString("auto-updater.download-url",
                "https://github.com/pharos-off/SentinelAC/releases/download/Paper%2FSpigot/SentinelAC-1.0.0.jar");
        this.versionUrl = plugin.getConfig().getString("auto-updater.version-url",
                "https://raw.githubusercontent.com/pharos-off/SentinelAC/main/version.txt");
        this.updateAvailable = false;
    }

    /**
     * Vérifie les mises à jour disponibles
     */
    public void checkForUpdates() {
        if (!enabled) {
            plugin.getLogger().info("Auto-updater désactivé");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String currentVersion = plugin.getDescription().getVersion();
                    latestVersion = fetchLatestVersion();

                    if (latestVersion == null) {
                        plugin.getLogger().warning("Impossible de vérifier les mises à jour");
                        return;
                    }

                    if (isNewerVersion(latestVersion, currentVersion)) {
                        updateAvailable = true;
                        plugin.getLogger().info("========================================");
                        plugin.getLogger().info("Nouvelle version disponible!");
                        plugin.getLogger().info("Version actuelle: " + currentVersion);
                        plugin.getLogger().info("Dernière version: " + latestVersion);
                        plugin.getLogger().info("========================================");

                        // Notifier les admins en ligne
                        notifyAdmins(currentVersion, latestVersion);

                        // Téléchargement automatique si activé
                        if (autoDownload) {
                            downloadUpdate();
                        }
                    } else {
                        plugin.getLogger().info("SentinelAC est à jour (v" + currentVersion + ")");
                    }

                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors de la vérification des mises à jour: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Récupère la dernière version depuis l'URL
     */
    private String fetchLatestVersion() {
        try {
            URL url = new URL(versionUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "SentinelAC-Updater");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );
                String version = reader.readLine();
                reader.close();

                if (version != null) {
                    // Nettoyer la version (supprimer espaces, retours à la ligne, etc.)
                    version = version.trim().replaceAll("[^0-9.]", "");
                    plugin.debug("Version récupérée: '" + version + "'");
                    return version.isEmpty() ? null : version;
                }
            } else {
                plugin.getLogger().warning("Erreur HTTP " + responseCode + " lors de la récupération de la version");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Erreur lors de la récupération de la version: " + e.getMessage());
        }
        return null;
    }

    /**
     * Compare deux versions (format: x.y.z)
     */
    private boolean isNewerVersion(String latest, String current) {
        try {
            // Nettoyer les versions
            latest = latest.trim().replaceAll("[^0-9.]", "");
            current = current.trim().replaceAll("[^0-9.]", "");

            plugin.debug("Comparaison versions: latest='" + latest + "' current='" + current + "'");

            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");

            for (int i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
                int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;

                if (latestPart > currentPart) {
                    return true;
                } else if (latestPart < currentPart) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Format de version invalide - Latest: '" + latest + "', Current: '" + current + "'");
            plugin.getLogger().warning("Détails: " + e.getMessage());
        }
        return false;
    }

    /**
     * Télécharge la mise à jour
     */
    private void downloadUpdate() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    plugin.getLogger().info("Téléchargement de la mise à jour...");

                    URL url = new URL(updateUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("User-Agent", "SentinelAC-Updater");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        // Télécharger dans un fichier temporaire
                        File updateFolder = new File(plugin.getDataFolder().getParentFile(), "updates");
                        if (!updateFolder.exists()) {
                            updateFolder.mkdirs();
                        }

                        File updateFile = new File(updateFolder, "SentinelAC-" + latestVersion + ".jar");

                        try (InputStream in = connection.getInputStream();
                             FileOutputStream out = new FileOutputStream(updateFile)) {

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            long totalBytes = 0;

                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                                totalBytes += bytesRead;
                            }

                            plugin.getLogger().info("Mise à jour téléchargée avec succès! (" +
                                    (totalBytes / 1024) + " KB)");
                            plugin.getLogger().info("Fichier: " + updateFile.getName());
                            plugin.getLogger().info("Redémarrez le serveur pour appliquer la mise à jour");

                            // Notifier les admins
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    if (p.hasPermission("anticheat.admin")) {
                                        p.sendMessage("§a[SentinelAC] Mise à jour téléchargée! Redémarrez le serveur.");
                                    }
                                }
                            });

                        }
                    } else {
                        plugin.getLogger().warning("Erreur lors du téléchargement: HTTP " + responseCode);
                    }

                } catch (IOException e) {
                    plugin.getLogger().severe("Erreur lors du téléchargement de la mise à jour: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Notifie les administrateurs en ligne
     */
    private void notifyAdmins(String current, String latest) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("anticheat.admin")) {
                    player.sendMessage("§6========================================");
                    player.sendMessage("§e[SentinelAC] Nouvelle version disponible!");
                    player.sendMessage("§7Actuelle: §f" + current + " §7→ Dernière: §a" + latest);

                    if (autoDownload) {
                        player.sendMessage("§aTéléchargement automatique en cours...");
                    } else {
                        player.sendMessage("§7Utilisez §f/ac update §7pour télécharger");
                    }
                    player.sendMessage("§6========================================");
                }
            }
        });
    }

    /**
     * Téléchargement manuel via commande
     */
    public void manualDownload() {
        if (!updateAvailable) {
            plugin.getLogger().info("Aucune mise à jour disponible");
            return;
        }
        downloadUpdate();
    }

    /**
     * Démarre la vérification périodique
     */
    public void startPeriodicCheck() {
        if (!enabled) return;

        int checkInterval = plugin.getConfig().getInt("auto-updater.check-interval", 12);

        // Première vérification au démarrage
        checkForUpdates();

        // Vérifications périodiques
        new BukkitRunnable() {
            @Override
            public void run() {
                checkForUpdates();
            }
        }.runTaskTimerAsynchronously(plugin, checkInterval * 3600 * 20L, checkInterval * 3600 * 20L);
    }

    // Getters
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }
}