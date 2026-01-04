package fr.pharos.sentinelac.listeners;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.checks.CombatCheck;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.managers.ViolationManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

/**
 * Écoute tous les événements de combat
 */
public class CombatListener implements Listener {

    private final SentinelAC plugin;
    private final ViolationManager violationManager;
    private final CombatCheck.KillAuraCheck killAuraCheck;
    private final CombatCheck.AutoClickerCheck autoClickerCheck;
    private final CombatCheck.VelocityCheck velocityCheck;

    public CombatListener(SentinelAC plugin) {
        this.plugin = plugin;
        this.violationManager = plugin.getViolationManager();
        this.killAuraCheck = new CombatCheck.KillAuraCheck(plugin);
        this.autoClickerCheck = new CombatCheck.AutoClickerCheck(plugin);
        this.velocityCheck = new CombatCheck.VelocityCheck(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Vérifier que l'attaquant est un joueur
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // Bypass si permission
        if (attacker.hasPermission("anticheat.bypass")) {
            return;
        }

        Entity target = event.getEntity();
        PlayerData data = violationManager.getPlayerData(attacker);

        // Enregistrer le clic pour analyse
        data.addClick();

        // === KILLAURA CHECK ===
        if (killAuraCheck.isEnabled()) {
            if (killAuraCheck.check(attacker, target, data)) {
                violationManager.flagPlayer(attacker, "killaura",
                        "Target: " + target.getType().name() +
                                " | Distance: " + String.format("%.2f",
                                attacker.getLocation().distance(target.getLocation())));

                // Annuler l'attaque
                event.setCancelled(true);
                return; // Ne pas continuer si KillAura détecté
            }
        }

        // === AUTOCLICKER CHECK ===
        if (autoClickerCheck.isEnabled()) {
            if (autoClickerCheck.check(attacker, data)) {
                violationManager.flagPlayer(attacker, "autoclicker",
                        "CPS: " + String.format("%.1f", data.getCPS(1000)) +
                                " | Clicks: " + data.getClickTimestamps().size());

                // Ne pas annuler pour autoclicker, juste alerter
                // L'accumulation de violations mènera à des actions
            }
        }

        // Enregistrer les données de cette attaque pour le prochain check
        data.setLastAttackLocation(attacker.getLocation());

        // Si la cible est un joueur, enregistrer pour velocity check
        if (target instanceof Player targetPlayer) {
            PlayerData targetData = violationManager.getPlayerData(targetPlayer);

            // Calculer le knockback attendu
            Vector knockback = targetPlayer.getLocation().toVector()
                    .subtract(attacker.getLocation().toVector())
                    .normalize()
                    .multiply(0.4); // Knockback de base

            // Stocker pour vérification ultérieure
            targetData.setLastYVelocity(knockback.getY());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Vérifier les dégâts pour Velocity check
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {

            PlayerData data = violationManager.getPlayerData(player);

            // Enregistrer le moment où le joueur a été touché
            long currentTime = System.currentTimeMillis();
            data.setLastAttackTime(currentTime);

            // Le velocity check sera fait dans MovementListener
            // en comparant le mouvement réel avec le knockback attendu
        }
    }

    /**
     * Détection de critiques impossibles
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCriticalHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (attacker.hasPermission("anticheat.bypass")) {
            return;
        }

        // Vérifier si c'est un coup critique
        if (event.getDamage() > event.getDamage(EntityDamageEvent.DamageModifier.BASE)) {
            PlayerData data = violationManager.getPlayerData(attacker);

            // Un coup critique nécessite d'être en chute (vélocité Y négative)
            if (!attacker.isOnGround()) {
                double yVelocity = data.getLastYVelocity();

                // Si le joueur monte ou est immobile, pas de critique possible
                if (yVelocity >= 0) {
                    violationManager.flagPlayer(attacker, "criticals",
                            "Crit without falling | Y velocity: " +
                                    String.format("%.3f", yVelocity));

                    // Réduire les dégâts au niveau normal
                    event.setDamage(event.getDamage(EntityDamageEvent.DamageModifier.BASE));
                }
            }
        }
    }
}