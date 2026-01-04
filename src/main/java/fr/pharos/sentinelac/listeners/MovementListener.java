package fr.pharos.sentinelac.listeners;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.checks.MovementCheck;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.managers.ViolationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Écoute tous les événements de mouvement
 */
public class MovementListener implements Listener {

    private final SentinelAC plugin;
    private final ViolationManager violationManager;
    private final MovementCheck.FlyCheck flyCheck;
    private final MovementCheck.SpeedCheck speedCheck;
    private final MovementCheck.NoFallCheck noFallCheck;

    public MovementListener(SentinelAC plugin) {
        this.plugin = plugin;
        this.violationManager = plugin.getViolationManager();
        this.flyCheck = new MovementCheck.FlyCheck(plugin);
        this.speedCheck = new MovementCheck.SpeedCheck(plugin);
        this.noFallCheck = new MovementCheck.NoFallCheck(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Bypass si permission
        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        // Ignorer les micro-mouvements (rotation seulement)
        if (event.getFrom().getX() == event.getTo().getX() &&
                event.getFrom().getY() == event.getTo().getY() &&
                event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }

        PlayerData data = violationManager.getPlayerData(player);

        // Sauvegarder l'ancienne position avant mise à jour
        if (data.getCurrentLocation() != null) {
            data.updateLocation(event.getTo());
        } else {
            // Première position
            data.updateLocation(event.getTo());
            return; // Pas assez de données pour checker
        }

        // === FLY CHECK ===
        if (flyCheck.isEnabled()) {
            if (flyCheck.check(player, data)) {
                violationManager.flagPlayer(player, "fly",
                        "ΔY: " + String.format("%.2f", data.getVerticalDistance()) +
                                " | AirTicks: " + data.getAirTicks() +
                                " | Ground: " + player.isOnGround());

                // Annuler le mouvement et téléporter à la dernière position valide
                event.setCancelled(true);
                if (data.getLastLocation() != null) {
                    player.teleport(data.getLastLocation());
                }
            }
        }

        // === SPEED CHECK ===
        if (speedCheck.isEnabled()) {
            if (speedCheck.check(player, data)) {
                violationManager.flagPlayer(player, "speed",
                        "Distance: " + String.format("%.3f", data.getHorizontalDistance()) +
                                " | Sprint: " + player.isSprinting());

                // Annuler et setback
                event.setCancelled(true);
                if (data.getLastLocation() != null) {
                    player.teleport(data.getLastLocation());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Vérifier seulement les dégâts de chute
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        PlayerData data = violationManager.getPlayerData(player);
        double fallDistance = player.getFallDistance();

        // === NOFALL CHECK ===
        if (noFallCheck.isEnabled()) {
            // Si l'événement est annulé mais devrait causer des dégâts = NoFall
            if (event.isCancelled() && noFallCheck.check(player, fallDistance, data)) {
                violationManager.flagPlayer(player, "nofall",
                        "Fall: " + String.format("%.2f", fallDistance) +
                                " blocks | Damage: 0");
            }

            // Vérifier aussi si les dégâts sont anormalement faibles
            if (!event.isCancelled()) {
                double expectedDamage = Math.max(0, fallDistance - 3.0);
                double actualDamage = event.getFinalDamage();

                if (fallDistance > 5.0 && actualDamage < expectedDamage * 0.3) {
                    violationManager.flagPlayer(player, "nofall",
                            "Fall: " + String.format("%.2f", fallDistance) +
                                    " | Damage: " + String.format("%.1f", actualDamage) +
                                    " < expected " + String.format("%.1f", expectedDamage));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Réinitialiser certaines données après téléportation
        Player player = event.getPlayer();
        PlayerData data = violationManager.getPlayerData(player);

        // Reset air ticks et position
        data.resetAirTicks();
        data.updateLocation(event.getTo());

        plugin.debug(player.getName() + " teleported, data reset");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Nettoyer les données après un délai pour permettre les reconnexions rapides
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            violationManager.removePlayerData(player.getUniqueId());

            // Nettoyer les données packet et ML
            if (plugin.getPacketAnalyzer() != null) {
                plugin.getPacketAnalyzer().removePlayerData(player.getUniqueId());
            }
            if (plugin.getMLAnalyzer() != null) {
                plugin.getMLAnalyzer().removePlayerProfile(player.getUniqueId());
            }

            plugin.debug(player.getName() + " data cleaned up");
        }, 6000L); // 5 minutes (6000 ticks)
    }
}