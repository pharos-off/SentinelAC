package fr.pharos.sentinelac.managers;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.checks.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gère l'initialisation et la configuration de tous les checks
 */
public class CheckManager {

    private final SentinelAC plugin;
    private final Map<String, Check> checks;

    public CheckManager(SentinelAC plugin) {
        this.plugin = plugin;
        this.checks = new HashMap<>();
        initializeChecks();
    }

    /**
     * Initialise tous les checks disponibles
     */
    private void initializeChecks() {
        // Checks de mouvement
        registerCheck(new MovementCheck.FlyCheck(plugin));
        registerCheck(new MovementCheck.SpeedCheck(plugin));
        registerCheck(new MovementCheck.NoFallCheck(plugin));

        // Checks de combat
        registerCheck(new CombatCheck.KillAuraCheck(plugin));
        registerCheck(new CombatCheck.AutoClickerCheck(plugin));

        // Checks de blocs
        registerCheck(new BlockCheck.FastBreakCheck(plugin));
        registerCheck(new BlockCheck.XRayCheck(plugin));

        // NOUVEAUX CHECKS AVANCÉS
        registerCheck(new AdvancedChecks.StepCheck(plugin));
        registerCheck(new AdvancedChecks.JesusCheck(plugin));
        registerCheck(new AdvancedChecks.SpiderCheck(plugin));
        registerCheck(new AdvancedChecks.PhaseCheck(plugin));
        registerCheck(new AdvancedChecks.BlinkCheck(plugin));
        registerCheck(new AdvancedChecks.AntiKnockbackCheck(plugin));
        registerCheck(new AdvancedChecks.KeepSprintCheck(plugin));
        registerCheck(new AdvancedChecks.AutoArmorCheck(plugin));
        registerCheck(new AdvancedChecks.AutoPotionCheck(plugin));
        registerCheck(new AdvancedChecks.BackTrackCheck(plugin));
        registerCheck(new AdvancedChecks.StrafeCheck(plugin));

        plugin.getLogger().info("Chargé " + checks.size() + " checks");
    }

    /**
     * Enregistre un check
     */
    private void registerCheck(Check check) {
        checks.put(check.getName().toLowerCase(), check);
        plugin.debug("Check enregistré: " + check.getName());
    }

    /**
     * Récupère un check par son nom
     */
    public Check getCheck(String name) {
        return checks.get(name.toLowerCase());
    }

    /**
     * Vérifie si un check est activé
     */
    public boolean isCheckEnabled(String checkName) {
        Check check = getCheck(checkName);
        return check != null && check.isEnabled();
    }

    /**
     * Récupère tous les checks
     */
    public Map<String, Check> getAllChecks() {
        return new HashMap<>(checks);
    }

    /**
     * Récupère le nombre de checks activés
     */
    public int getEnabledChecksCount() {
        return (int) checks.values().stream()
                .filter(Check::isEnabled)
                .count();
    }

    /**
     * Récupère la liste des noms de checks activés
     */
    public List<String> getEnabledCheckNames() {
        List<String> names = new ArrayList<>();
        for (Check check : checks.values()) {
            if (check.isEnabled()) {
                names.add(check.getName());
            }
        }
        return names;
    }

    /**
     * Active ou désactive un check
     */
    public void toggleCheck(String checkName, boolean enabled) {
        Check check = getCheck(checkName);
        if (check != null) {
            check.setEnabled(enabled);
            plugin.getLogger().info("Check " + checkName + " " +
                    (enabled ? "activé" : "désactivé"));
        }
    }
}