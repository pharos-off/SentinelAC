package fr.pharos.sentinelac.commands;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.utils.AlertUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Commande principale de l'anti-cheat
 */
public class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private final SentinelAC plugin;

    public AntiCheatCommand(SentinelAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("anticheat.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(sender);
                break;

            case "info":
                sendInfo(sender);
                break;

            case "violations":
            case "vl":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /ac violations <joueur>");
                    return true;
                }
                showViolations(sender, args[1]);
                break;

            case "reset":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /ac reset <joueur> [check]");
                    return true;
                }
                String checkName = args.length >= 3 ? args[2] : null;
                resetViolations(sender, args[1], checkName);
                break;

            case "alerts":
                toggleAlerts(sender);
                break;

            case "top":
                showTopViolators(sender);
                break;

            case "reload":
                reloadConfig(sender);
                break;

            case "update":
                checkUpdate(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Sous-commande inconnue. Utilisez /ac help");
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.WHITE + "AntiCheat" +
                ChatColor.GOLD + " ==========");
        sender.sendMessage(ChatColor.YELLOW + "/ac info" + ChatColor.GRAY +
                " - Informations sur le plugin");
        sender.sendMessage(ChatColor.YELLOW + "/ac violations <joueur>" + ChatColor.GRAY +
                " - Voir les violations d'un joueur");
        sender.sendMessage(ChatColor.YELLOW + "/ac reset <joueur> [check]" + ChatColor.GRAY +
                " - Réinitialiser les violations");
        sender.sendMessage(ChatColor.YELLOW + "/ac alerts" + ChatColor.GRAY +
                " - Activer/désactiver les alertes");
        sender.sendMessage(ChatColor.YELLOW + "/ac top" + ChatColor.GRAY +
                " - Top des joueurs avec le plus de violations");
        sender.sendMessage(ChatColor.YELLOW + "/ac update" + ChatColor.GRAY +
                " - Vérifier et télécharger les mises à jour");
        sender.sendMessage(ChatColor.YELLOW + "/ac reload" + ChatColor.GRAY +
                " - Recharger la configuration");
    }

    private void sendInfo(CommandSender sender) {
        int enabledChecks = plugin.getCheckManager().getEnabledChecksCount();
        int totalChecks = plugin.getCheckManager().getAllChecks().size();
        int playersTracked = plugin.getViolationManager().getAllPlayerData().size();

        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.WHITE + "AntiCheat Info" +
                ChatColor.GOLD + " ==========");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE +
                plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Checks actifs: " + ChatColor.WHITE +
                enabledChecks + "/" + totalChecks);
        sender.sendMessage(ChatColor.YELLOW + "Joueurs surveillés: " + ChatColor.WHITE +
                playersTracked);
        sender.sendMessage(ChatColor.YELLOW + "Checks disponibles: " + ChatColor.WHITE);

        for (String checkName : plugin.getCheckManager().getEnabledCheckNames()) {
            sender.sendMessage(ChatColor.GRAY + "  - " + checkName);
        }
    }

    private void showViolations(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable ou hors ligne.");
            return;
        }

        PlayerData data = plugin.getViolationManager().getPlayerData(target);
        Map<String, Integer> violations = data.getAllViolations();

        if (violations.isEmpty() || data.getTotalViolations() == 0) {
            sender.sendMessage(ChatColor.GREEN + playerName + " n'a aucune violation.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.WHITE + "Violations de " +
                playerName + ChatColor.GOLD + " ==========");
        sender.sendMessage(ChatColor.YELLOW + "Total: " + ChatColor.WHITE + data.getTotalViolations());
        sender.sendMessage("");

        for (Map.Entry<String, Integer> entry : violations.entrySet()) {
            if (entry.getValue() > 0) {
                int maxVL = 10; // Valeur par défaut
                String bar = AlertUtils.createProgressBar(entry.getValue(), maxVL, 10);
                sender.sendMessage(ChatColor.YELLOW + entry.getKey() + ": " +
                        ChatColor.WHITE + entry.getValue() + " " + bar);
            }
        }
    }

    private void resetViolations(CommandSender sender, String playerName, String checkName) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable ou hors ligne.");
            return;
        }

        plugin.getViolationManager().resetViolations(target, checkName);

        if (checkName == null) {
            sender.sendMessage(ChatColor.GREEN + "Toutes les violations de " + playerName +
                    " ont été réinitialisées.");
            AlertUtils.broadcastToAdmins("&c[AC] &7" + sender.getName() +
                    " a réinitialisé les violations de " + playerName);
        } else {
            sender.sendMessage(ChatColor.GREEN + "Violations " + checkName + " de " +
                    playerName + " réinitialisées.");
        }
    }

    private void toggleAlerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est réservée aux joueurs.");
            return;
        }

        // Cette fonctionnalité nécessiterait un système de préférences
        // Pour simplifier, on suppose que les alertes sont basées sur la permission
        sender.sendMessage(ChatColor.YELLOW + "Les alertes sont contrôlées par la permission " +
                ChatColor.WHITE + "anticheat.alerts");
        sender.sendMessage(ChatColor.GRAY + "Contactez un administrateur pour modifier vos permissions.");
    }

    private void showTopViolators(CommandSender sender) {
        Map<UUID, PlayerData> allData = plugin.getViolationManager().getAllPlayerData();

        // Trier par nombre total de violations
        List<Map.Entry<UUID, PlayerData>> sorted = allData.entrySet().stream()
                .filter(entry -> entry.getValue().getTotalViolations() > 0)
                .sorted((a, b) -> Integer.compare(
                        b.getValue().getTotalViolations(),
                        a.getValue().getTotalViolations()
                ))
                .limit(10)
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Aucune violation enregistrée.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.WHITE + "Top Violations" +
                ChatColor.GOLD + " ==========");

        int rank = 1;
        for (Map.Entry<UUID, PlayerData> entry : sorted) {
            Player player = Bukkit.getPlayer(entry.getKey());
            String playerName = player != null ? player.getName() : "Inconnu";
            int violations = entry.getValue().getTotalViolations();

            sender.sendMessage(ChatColor.YELLOW + "#" + rank + " " + ChatColor.WHITE +
                    playerName + ChatColor.GRAY + " - " + ChatColor.RED + violations + " VL");
            rank++;
        }
    }

    private void reloadConfig(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Configuration rechargée avec succès!");

        // Recharger les checks
        int enabledChecks = plugin.getCheckManager().getEnabledChecksCount();
        sender.sendMessage(ChatColor.YELLOW + "Checks actifs: " + ChatColor.WHITE + enabledChecks);
    }

    private void checkUpdate(CommandSender sender) {
        if (plugin.getAutoUpdater() == null || !plugin.getAutoUpdater().isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Auto-updater désactivé dans la configuration");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Vérification des mises à jour...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getAutoUpdater().checkForUpdates();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.getAutoUpdater().isUpdateAvailable()) {
                    sender.sendMessage(ChatColor.GREEN + "Nouvelle version disponible: " +
                            plugin.getAutoUpdater().getLatestVersion());
                    sender.sendMessage(ChatColor.YELLOW + "Téléchargement en cours...");
                    plugin.getAutoUpdater().manualDownload();
                } else {
                    sender.sendMessage(ChatColor.GREEN + "SentinelAC est à jour!");
                }
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("help", "info", "violations", "reset", "alerts", "top", "reload", "update"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("violations") ||
                args[0].equalsIgnoreCase("vl") ||
                args[0].equalsIgnoreCase("reset"))) {
            // Suggérer les joueurs en ligne
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            // Suggérer les checks disponibles
            completions.addAll(plugin.getCheckManager().getEnabledCheckNames());
        }

        // Filtrer selon ce que l'utilisateur a déjà tapé
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}