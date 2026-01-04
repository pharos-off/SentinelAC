package fr.pharos.sentinelac.listeners;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.checks.AdvancedChecks;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.managers.ViolationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Écoute les événements pour les checks avancés
 */
public class AdvancedListener implements Listener {

    private final SentinelAC plugin;
    private final ViolationManager violationManager;

    // Checks avancés
    private final AdvancedChecks.StepCheck stepCheck;
    private final AdvancedChecks.JesusCheck jesusCheck;
    private final AdvancedChecks.SpiderCheck spiderCheck;
    private final AdvancedChecks.PhaseCheck phaseCheck;
    private final AdvancedChecks.BlinkCheck blinkCheck;
    private final AdvancedChecks.AntiKnockbackCheck antiKnockbackCheck;
    private final AdvancedChecks.KeepSprintCheck keepSprintCheck;
    private final AdvancedChecks.AutoArmorCheck autoArmorCheck;
    private final AdvancedChecks.AutoPotionCheck autoPotionCheck;
    private final AdvancedChecks.BackTrackCheck backTrackCheck;
    private final AdvancedChecks.StrafeCheck strafeCheck;

    public AdvancedListener(SentinelAC plugin) {
        this.plugin = plugin;
        this.violationManager = plugin.getViolationManager();

        // Initialiser les checks
        this.stepCheck = new AdvancedChecks.StepCheck(plugin);
        this.jesusCheck = new AdvancedChecks.JesusCheck(plugin);
        this.spiderCheck = new AdvancedChecks.SpiderCheck(plugin);
        this.phaseCheck = new AdvancedChecks.PhaseCheck(plugin);
        this.blinkCheck = new AdvancedChecks.BlinkCheck(plugin);
        this.antiKnockbackCheck = new AdvancedChecks.AntiKnockbackCheck(plugin);
        this.keepSprintCheck = new AdvancedChecks.KeepSprintCheck(plugin);
        this.autoArmorCheck = new AdvancedChecks.AutoArmorCheck(plugin);
        this.autoPotionCheck = new AdvancedChecks.AutoPotionCheck(plugin);
        this.backTrackCheck = new AdvancedChecks.BackTrackCheck(plugin);
        this.strafeCheck = new AdvancedChecks.StrafeCheck(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        PlayerData data = violationManager.getPlayerData(player);

        // Check Step
        if (stepCheck.isEnabled() && stepCheck.check(player, data)) {
            violationManager.flagPlayer(player, "step",
                    "Y-diff: " + String.format("%.2f",
                            player.getLocation().getY() - data.getLastLocation().getY()));
        }

        // Check Jesus
        if (jesusCheck.isEnabled() && jesusCheck.check(player, data)) {
            violationManager.flagPlayer(player, "jesus",
                    "Walking on water");
            event.setCancelled(true);
        }

        // Check Spider
        if (spiderCheck.isEnabled() && spiderCheck.check(player, data)) {
            violationManager.flagPlayer(player, "spider",
                    "Climbing wall without ladder");
            event.setCancelled(true);
        }

        // Check Phase
        if (phaseCheck.isEnabled() && phaseCheck.check(player, data)) {
            violationManager.flagPlayer(player, "phase",
                    "Inside solid block");
            event.setCancelled(true);
        }

        // Check Blink
        if (blinkCheck.isEnabled() && blinkCheck.check(player, data)) {
            violationManager.flagPlayer(player, "blink",
                    "Position desync");
        }

        // Check Strafe
        if (strafeCheck.isEnabled() && strafeCheck.check(player, data)) {
            violationManager.flagPlayer(player, "strafe",
                    "Perfect circular movement");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (attacker.hasPermission("anticheat.bypass")) {
            return;
        }

        PlayerData data = violationManager.getPlayerData(attacker);

        // Check KeepSprint
        if (keepSprintCheck.isEnabled() && keepSprintCheck.check(attacker, data)) {
            violationManager.flagPlayer(attacker, "keepsprint",
                    "Sprint after attack");
        }

        // Check BackTrack
        if (backTrackCheck.isEnabled() && backTrackCheck.check(attacker, event.getEntity(), data)) {
            violationManager.flagPlayer(attacker, "backtrack",
                    "Hit on old position");
        }

        // Check AntiKnockback (pour la victime si c'est un joueur)
        if (event.getEntity() instanceof Player victim) {
            PlayerData victimData = violationManager.getPlayerData(victim);
            double expectedKB = 0.4; // Knockback de base

            // Vérifier après un court délai pour voir le mouvement réel
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (antiKnockbackCheck.isEnabled() &&
                        antiKnockbackCheck.check(victim, victimData, expectedKB)) {
                    violationManager.flagPlayer(victim, "antiknockback",
                            "Reduced knockback");
                }
            }, 2L); // 2 ticks = 100ms
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        // Check si c'est une pièce d'armure
        if (event.getCurrentItem() != null &&
                isArmorPiece(event.getCurrentItem().getType().name())) {

            PlayerData data = violationManager.getPlayerData(player);

            // Check AutoArmor
            if (autoArmorCheck.isEnabled() && autoArmorCheck.check(player, data)) {
                violationManager.flagPlayer(player, "autoarmor",
                        "Armor equipped too fast");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        PlayerData data = violationManager.getPlayerData(player);

        // Check AutoPotion
        if (autoPotionCheck.isEnabled() &&
                autoPotionCheck.check(player, data, event.getItem())) {
            violationManager.flagPlayer(player, "autopotion",
                    "Potion used too fast");
            event.setCancelled(true);
        }
    }

    private boolean isArmorPiece(String materialName) {
        return materialName.contains("HELMET") ||
                materialName.contains("CHESTPLATE") ||
                materialName.contains("LEGGINGS") ||
                materialName.contains("BOOTS");
    }
}