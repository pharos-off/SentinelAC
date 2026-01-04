package fr.pharos.sentinelac.checks;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Checks avancés supplémentaires
 */
public class AdvancedChecks {

    /**
     * Détecte le Step (montée de blocs anormale)
     */
    public static class StepCheck extends Check {
        private final double maxStepHeight;

        public StepCheck(SentinelAC plugin) {
            super(plugin, "step", "movement");
            this.maxStepHeight = getConfigDouble("max-step-height", 0.6);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled() || player.isFlying() || !player.isOnGround()) {
                return false;
            }

            Location current = player.getLocation();
            Location last = data.getLastLocation();

            if (last == null || !data.wasOnGround()) return false;

            double yDiff = current.getY() - last.getY();

            // Si montée verticale > 0.6 blocs sans saut
            if (yDiff > maxStepHeight && yDiff < 1.5) {
                // Vérifier qu'il n'y a pas d'escalier ou de dalle
                Block blockBelow = current.getBlock().getRelative(0, -1, 0);
                Material type = blockBelow.getType();

                if (!type.name().contains("STAIRS") &&
                        !type.name().contains("SLAB") &&
                        !type.name().contains("CARPET")) {

                    plugin.debug(player.getName() + " STEP: Y-diff=" +
                            String.format("%.2f", yDiff));
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Détecte le Jesus (marche sur l'eau)
     */
    public static class JesusCheck extends Check {

        public JesusCheck(SentinelAC plugin) {
            super(plugin, "jesus", "movement");
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled() || player.isFlying() || player.isInsideVehicle()) {
                return false;
            }

            Location loc = player.getLocation();
            Block blockAt = loc.getBlock();
            Block blockBelow = loc.getBlock().getRelative(0, -1, 0);

            // Si le joueur est sur l'eau
            if (blockAt.getType() == Material.WATER || blockBelow.getType() == Material.WATER) {
                // IMPORTANT: Vérifier qu'il ne nage PAS et qu'il est vraiment sur l'eau
                if (!player.isSwimming() && !player.isInWater() && player.isOnGround()) {
                    // Vérifier qu'il n'y a pas de bloc adjacent solide (bord de bloc)
                    if (!hasAdjacentSolidBlock(loc)) {
                        plugin.debug(player.getName() + " JESUS: Walking on water");
                        return true;
                    }
                }
            }

            return false;
        }

        private boolean hasAdjacentSolidBlock(Location loc) {
            // Vérifier les blocs adjacents pour éviter faux positifs sur les bords
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = loc.getBlock().getRelative(x, -1, z);
                    if (block.getType().isSolid() && block.getType() != Material.WATER) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Détecte le Spider (escalade de murs sans vigne/échelle)
     */
    public static class SpiderCheck extends Check {
        private final double minClimbSpeed;

        public SpiderCheck(SentinelAC plugin) {
            super(plugin, "spider", "movement");
            this.minClimbSpeed = getConfigDouble("min-climb-speed", 0.1);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled() || player.isFlying() || player.isInsideVehicle()) {
                return false;
            }

            Location loc = player.getLocation();
            double yVelocity = data.getLastYVelocity();

            // Si le joueur monte contre un mur
            if (yVelocity > minClimbSpeed && !player.isOnGround()) {
                Block blockAt = loc.getBlock();

                // Vérifier qu'il n'y a pas d'échelle/vigne
                if (blockAt.getType() != Material.LADDER &&
                        blockAt.getType() != Material.VINE &&
                        !blockAt.getType().name().contains("SCAFFOLDING")) {

                    // Vérifier qu'il y a un mur solide à côté
                    if (hasWallNearby(loc)) {
                        plugin.debug(player.getName() + " SPIDER: Climbing wall");
                        return true;
                    }
                }
            }

            return false;
        }

        private boolean hasWallNearby(Location loc) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;

                    Block block = loc.getBlock().getRelative(x, 0, z);
                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Détecte le Phase (traverser les blocs solides)
     */
    public static class PhaseCheck extends Check {

        public PhaseCheck(SentinelAC plugin) {
            super(plugin, "phase", "movement");
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled() || player.isFlying()) {
                return false;
            }

            Location loc = player.getLocation();
            Block blockAt = loc.getBlock();

            // Si le joueur est dans un bloc solide
            if (blockAt.getType().isSolid() &&
                    blockAt.getType() != Material.WATER &&
                    blockAt.getType() != Material.LAVA) {

                // Vérifier que ce n'est pas un faux positif (pistons, etc.)
                if (!isAllowedBlock(blockAt.getType())) {
                    plugin.debug(player.getName() + " PHASE: Inside " + blockAt.getType());
                    return true;
                }
            }

            return false;
        }

        private boolean isAllowedBlock(Material type) {
            String name = type.name();
            return name.contains("DOOR") ||
                    name.contains("GATE") ||
                    name.contains("SIGN") ||
                    name.contains("BANNER") ||
                    name.contains("CARPET");
        }
    }

    /**
     * Détecte le Blink (désynchronisation position)
     */
    public static class BlinkCheck extends Check {
        private final long maxDesyncTime;

        public BlinkCheck(SentinelAC plugin) {
            super(plugin, "blink", "movement");
            this.maxDesyncTime = getConfigInt("max-desync-time", 2000);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled()) {
                return false;
            }

            long currentTime = System.currentTimeMillis();
            long timeSinceMove = currentTime - data.getLastMoveTime();

            // Si aucun mouvement depuis longtemps mais actions
            if (timeSinceMove > maxDesyncTime) {
                long timeSinceAction = currentTime - data.getLastAttackTime();

                // Si le joueur agit sans bouger = désynchronisation
                if (timeSinceAction < 500) {
                    plugin.debug(player.getName() + " BLINK: No movement for " + timeSinceMove + "ms");
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Détecte l'AntiKnockback avancé (vélocité)
     */
    public static class AntiKnockbackCheck extends Check {
        private final double minKnockbackRatio;

        public AntiKnockbackCheck(SentinelAC plugin) {
            super(plugin, "antiknockback", "combat");
            this.minKnockbackRatio = getConfigDouble("min-knockback-ratio", 0.6);
        }

        public boolean check(Player player, PlayerData data, double expectedKB) {
            if (!isEnabled()) {
                return false;
            }

            // Vérifier le temps depuis le dernier hit
            long timeSinceHit = System.currentTimeMillis() - data.getLastAttackTime();
            if (timeSinceHit > 1000 || timeSinceHit < 0) return false;

            // Comparer mouvement réel vs attendu
            double actualMovement = data.getHorizontalDistance();
            double ratio = actualMovement / Math.max(expectedKB, 0.1);

            if (ratio < minKnockbackRatio) {
                plugin.debug(player.getName() + " ANTI-KB: " +
                        String.format("%.2f", ratio * 100) + "% KB applied");
                return true;
            }

            return false;
        }
    }

    /**
     * Détecte le KeepSprint (sprint conservé après attaque)
     */
    public static class KeepSprintCheck extends Check {

        public KeepSprintCheck(SentinelAC plugin) {
            super(plugin, "keepsprint", "combat");
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled()) {
                return false;
            }

            // Si le joueur attaque en sprintant
            long timeSinceAttack = System.currentTimeMillis() - data.getLastAttackTime();

            if (timeSinceAttack < 100 && player.isSprinting()) {
                // Le sprint devrait s'arrêter après une attaque en vanilla
                // On vérifie si le sprint continue anormalement
                data.incrementKeepSprintCount();

                if (data.getKeepSprintCount() > 3) {
                    plugin.debug(player.getName() + " KEEPSPRINT: Sprint after attack");
                    data.resetKeepSprintCount();
                    return true;
                }
            } else {
                data.resetKeepSprintCount();
            }

            return false;
        }
    }

    /**
     * Détecte l'AutoArmor (équipement automatique d'armure)
     */
    public static class AutoArmorCheck extends Check {
        private final long minEquipTime;

        public AutoArmorCheck(SentinelAC plugin) {
            super(plugin, "autoarmor", "interaction");
            this.minEquipTime = getConfigInt("min-equip-time", 150);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled()) {
                return false;
            }

            long currentTime = System.currentTimeMillis();
            long timeSinceLastEquip = currentTime - data.getLastArmorEquipTime();

            // Si équipement trop rapide
            if (timeSinceLastEquip > 0 && timeSinceLastEquip < minEquipTime) {
                plugin.debug(player.getName() + " AUTO-ARMOR: " + timeSinceLastEquip + "ms");
                return true;
            }

            data.setLastArmorEquipTime(currentTime);
            return false;
        }
    }

    /**
     * Détecte l'AutoPotion (utilisation automatique de potions)
     */
    public static class AutoPotionCheck extends Check {
        private final long minPotionTime;

        public AutoPotionCheck(SentinelAC plugin) {
            super(plugin, "autopotion", "interaction");
            this.minPotionTime = getConfigInt("min-potion-time", 1200);
        }

        public boolean check(Player player, PlayerData data, ItemStack item) {
            if (!isEnabled() || item == null) {
                return false;
            }

            // Si c'est une potion
            if (item.getType().name().contains("POTION")) {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastPotion = currentTime - data.getLastPotionTime();

                // Si utilisation trop rapide (< 1.2s)
                if (timeSinceLastPotion > 0 && timeSinceLastPotion < minPotionTime) {
                    plugin.debug(player.getName() + " AUTO-POTION: " + timeSinceLastPotion + "ms");
                    return true;
                }

                data.setLastPotionTime(currentTime);
            }

            return false;
        }
    }

    /**
     * Détecte le BackTrack (retour en arrière artificiel)
     */
    public static class BackTrackCheck extends Check {
        private final double maxBacktrack;

        public BackTrackCheck(SentinelAC plugin) {
            super(plugin, "backtrack", "combat");
            this.maxBacktrack = getConfigDouble("max-backtrack", 3.0);
        }

        public boolean check(Player attacker, Entity target, PlayerData data) {
            if (!isEnabled()) {
                return false;
            }

            // Comparer la position réelle de la cible avec où l'attaque a eu lieu
            Location targetLoc = target.getLocation();
            Location lastTargetLoc = data.getLastTargetLocation(target.getUniqueId());

            if (lastTargetLoc != null) {
                double distance = targetLoc.distance(lastTargetLoc);

                // Si la cible a bougé significativement mais l'attaque touche = backtrack
                if (distance > maxBacktrack) {
                    plugin.debug(attacker.getName() + " BACKTRACK: Target moved " +
                            String.format("%.2f", distance) + " blocks");
                    return true;
                }
            }

            data.setLastTargetLocation(target.getUniqueId(), targetLoc.clone());
            return false;
        }
    }

    /**
     * Détecte le Strafe (mouvement circulaire parfait)
     */
    public static class StrafeCheck extends Check {
        private final double maxStrafeConsistency;

        public StrafeCheck(SentinelAC plugin) {
            super(plugin, "strafe", "movement");
            this.maxStrafeConsistency = getConfigDouble("max-strafe-consistency", 0.95);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled() || !player.isSprinting()) {
                return false;
            }

            Location current = player.getLocation();
            Location last = data.getLastLocation();

            if (last == null) return false;

            // Ajouter la direction de mouvement
            Vector direction = current.toVector().subtract(last.toVector()).normalize();
            data.addStrafeDirection(direction);

            // Analyser après 20 mouvements
            if (data.getStrafeDirections().size() >= 20) {
                double consistency = calculateStrafeConsistency(data.getStrafeDirections());

                // Si trop constant = strafe cheat
                if (consistency > maxStrafeConsistency) {
                    plugin.debug(player.getName() + " STRAFE: Consistency=" +
                            String.format("%.2f", consistency));
                    data.clearStrafeDirections();
                    return true;
                }

                data.clearStrafeDirections();
            }

            return false;
        }

        private double calculateStrafeConsistency(java.util.List<Vector> directions) {
            if (directions.size() < 2) return 0;

            double totalAngleChange = 0;
            for (int i = 1; i < directions.size(); i++) {
                double angle = directions.get(i).angle(directions.get(i - 1));
                totalAngleChange += angle;
            }

            double avgAngleChange = totalAngleChange / (directions.size() - 1);

            // Plus l'angle moyen est constant, plus c'est suspect
            return 1.0 - (avgAngleChange / Math.PI);
        }
    }
}