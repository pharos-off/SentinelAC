package fr.pharos.sentinelac;

import fr.pharos.sentinelac.commands.AntiCheatCommand;
import fr.pharos.sentinelac.database.DatabaseManager;
import fr.pharos.sentinelac.listeners.*;
import fr.pharos.sentinelac.managers.CheckManager;
import fr.pharos.sentinelac.managers.ViolationManager;
import fr.pharos.sentinelac.ml.MLAnalyzer;
import fr.pharos.sentinelac.packets.PacketAnalyzer;
import fr.pharos.sentinelac.updater.AutoUpdater;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentinelAC extends JavaPlugin {

    private static SentinelAC instance;
    private ViolationManager violationManager;
    private CheckManager checkManager;
    private DatabaseManager databaseManager;
    private PacketAnalyzer packetAnalyzer;
    private MLAnalyzer mlAnalyzer;
    private AutoUpdater autoUpdater;

    @Override
    public void onEnable() {
        instance = this;

        // Bannière de démarrage
        getLogger().info("========================================");
        getLogger().info("   SentinelAC - Anti-Cheat Professionnel");
        getLogger().info("   Version: " + getDescription().getVersion());
        getLogger().info("========================================");

        // Sauvegarder la config par défaut
        saveDefaultConfig();

        // Initialiser la base de données
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        // Initialiser les managers
        violationManager = new ViolationManager(this);
        checkManager = new CheckManager(this);

        // Initialiser l'analyseur de packets
        packetAnalyzer = new PacketAnalyzer(this);

        // Initialiser le Machine Learning
        mlAnalyzer = new MLAnalyzer(this);

        // Initialiser l'auto-updater
        autoUpdater = new AutoUpdater(this);
        autoUpdater.startPeriodicCheck();

        // Enregistrer les listeners
        registerListeners();

        // Enregistrer les commandes
        registerCommands();

        // Démarrer le task de décrémentation des violations
        violationManager.startDecayTask();

        // Statistiques de démarrage
        getLogger().info("Checks actifs: " + checkManager.getEnabledChecksCount() + "/" +
                checkManager.getAllChecks().size());
        if (databaseManager.isEnabled()) {
            getLogger().info("Base de données MySQL: ACTIVE");
        }
        if (packetAnalyzer.isEnabled()) {
            getLogger().info("Analyse de packets NMS: ACTIVE");
        }
        if (mlAnalyzer != null) {
            getLogger().info("Machine Learning: ACTIVE");
        }
        getLogger().info("SentinelAC activé avec succès!");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Arrêt de SentinelAC...");

        // Arrêter les tasks
        if (violationManager != null) {
            violationManager.stopDecayTask();
        }

        // Sauvegarder les données
        if (violationManager != null) {
            violationManager.saveAllData();
        }

        // Fermer la base de données
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("SentinelAC désactivé!");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new AdvancedListener(this), this);

        if (getConfig().getBoolean("general.debug")) {
            getLogger().info("Tous les listeners ont été enregistrés");
        }
    }

    private void registerCommands() {
        getCommand("anticheat").setExecutor(new AntiCheatCommand(this));
        getCommand("anticheat").setTabCompleter(new AntiCheatCommand(this));
    }

    // Getters
    public static SentinelAC getInstance() {
        return instance;
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PacketAnalyzer getPacketAnalyzer() {
        return packetAnalyzer;
    }

    public MLAnalyzer getMLAnalyzer() {
        return mlAnalyzer;
    }

    public AutoUpdater getAutoUpdater() {
        return autoUpdater;
    }

    // Méthode utilitaire pour le debug
    public void debug(String message) {
        if (getConfig().getBoolean("general.debug")) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}