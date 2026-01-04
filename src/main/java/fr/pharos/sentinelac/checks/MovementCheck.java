package fr.pharos.sentinelac.checks;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Checks liés au mouvement du joueur - Version améliorée
 */
public class MovementCheck {

    /**
     * Détecte le vol non autorisé avec analyse avancée
     */
    public static class FlyCheck extends Check {
        private final double maxJumpHeight;
        private final double maxAirDistance;

        public FlyCheck(SentinelAC plugin) {
            super(plugin, "fly", "movement");
            this.maxJumpHeight = getConfigDouble("max-jump-height", 1.5);
            this.maxAirDistance = getConfigDouble("max-air-distance", 5.0);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled() || shouldBypass(player)) {
                return false;
            }

            Location current = player.getLocation();
            Location last = data.getLastLocation();

            if (last == null) return false;

            // Calculer les effets de jump boost
            double jumpBoost = getJumpBoostEffect(player);
            double adjustedMaxHeight = maxJumpHeight + jumpBoost;

            boolean onGround = player.isOnGround();
            double verticalDist = current.getY() - last.getY();

            // Réinitialiser les ticks en l'air si le joueur touche le sol
            if (!data.wasOnGround() && onGround) {
                data.resetAirTicks();
            }

            // Si le joueur est en l'air
            if (!onGround) {
                data.incrementAirTicks();

                // Check 1: Montée anormale en l'air
                if (verticalDist > adjustedMaxHeight && data.getAirTicks() > 5) {
                    // Vérifier qu'il n'y a pas de bloc sous le joueur qui pourrait le propulser
                    if (!hasBoostBlockBelow(current)) {
                        return true;
                    }
                }

                // Check 2: Sustain en l'air (hover)
                if (data.getAirTicks() > 30) {
                    // Vérifier la vitesse verticale moyenne
                    double avgYVelocity = Math.abs(verticalDist);
                    if (avgYVelocity < 0.05) { // Presque immobile en l'air
                        return true;
                    }
                }

                // Check 3: Distance horizontale excessive en l'air
                double horizontalDist = Math.sqrt(
                        Math.pow(current.getX() - last.getX(), 2) +
                                Math.pow(current.getZ() - last.getZ(), 2)
                );

                if (data.getAirTicks() > 15 && horizontalDist > 0.5) {
                    // Calculer la distance totale en l'air
                    double totalAirMovement = horizontalDist * data.getAirTicks();
                    if (totalAirMovement > maxAirDistance * 15 && !player.isGliding()) {
                        return true;
                    }
                }

                // Check 4: Pattern de vol (montée puis sustain)
                if (data.getAirTicks() > 20 && verticalDist > -0.08 && verticalDist < 0.08) {
                    // Le joueur ne tombe pas (gravité ignorée)
                    if (!player.isGliding() && !hasWaterNearby(current)) {
                        return true;
                    }
                }
            }

            data.setWasOnGround(onGround);
            data.setLastYVelocity(verticalDist);

            return false;
        }

        private double getJumpBoostEffect(Player player) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.JUMP);
            if (effect != null) {
                return (effect.getAmplifier() + 1) * 0.5;
            }
            return 0;
        }

        private boolean hasBoostBlockBelow(Location loc) {
            Block below = loc.getBlock().getRelative(0, -1, 0);
            Material type = below.getType();
            return type == Material.SLIME_BLOCK ||
                    type == Material.HONEY_BLOCK ||
                    type.name().contains("BED");
        }

        private boolean hasWaterNearby(Location loc) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Material type = loc.getBlock().getRelative(x, y, z).getType();
                        if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private boolean shouldBypass(Player player) {
            return player.isFlying() ||
                    player.getGameMode() == GameMode.CREATIVE ||
                    player.getGameMode() == GameMode.SPECTATOR ||
                    player.isInsideVehicle();
        }
    }

    /**
     * Détecte la vitesse anormale avec analyse contextuelle
     */
    public static class SpeedCheck extends Check {
        private final double maxSpeedMultiplier;
        private final double lagTolerance;

        public SpeedCheck(SentinelAC plugin) {
            super(plugin, "speed", "movement");
            this.maxSpeedMultiplier = getConfigDouble("max-speed-multiplier", 1.35);
            this.lagTolerance = getConfigDouble("lag-tolerance", 0.2);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled() || shouldBypass(player)) {
                return false;
            }

            Location current = player.getLocation();
            Location last = data.getLastLocation();

            if (last == null) return false;

            // Calculer la vitesse de base avec tous les modificateurs
            double baseSpeed = calculateBaseSpeed(player);
            double maxSpeed = baseSpeed * maxSpeedMultiplier + lagTolerance;

            // Calculer la distance horizontale parcourue
            double dx = current.getX() - last.getX();
            double dz = current.getZ() - last.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            // Calculer le temps écoulé
            long timeDiff = System.currentTimeMillis() - data.getLastMoveTime();
            if (timeDiff < 50) return false; // Éviter division par zéro

            // Calculer la vitesse réelle (blocs par tick)
            double speed = horizontalDist / (timeDiff / 50.0);

            // IMPORTANT: Seuil minimum pour éviter faux positifs
            if (horizontalDist < 0.2) return false; // Micro-mouvement ignoré

            // Check principal de vitesse
            if (speed > maxSpeed) {
                // Vérifications supplémentaires pour réduire les faux positifs

                // Ignorer si le joueur vient d'atterrir (boost de chute)
                if (player.isOnGround() && data.getAirTicks() > 10) {
                    return false;
                }

                // Ignorer si il y a un bloc qui pousse (piston, slime)
                if (hasBoostingBlock(current)) {
                    return false;
                }

                // Ignorer si knockback récent (500ms)
                if (System.currentTimeMillis() - data.getLastAttackTime() < 500) {
                    return false;
                }

                return true;
            }

            return false;
        }

        private double calculateBaseSpeed(Player player) {
            double baseSpeed = 0.28; // Vitesse de marche normale

            // Ajuster pour Sprint
            if (player.isSprinting()) {
                baseSpeed *= 1.3;
            }

            // Ajuster pour Speed Effect
            PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
            if (speedEffect != null) {
                baseSpeed *= 1 + (speedEffect.getAmplifier() + 1) * 0.2;
            }

            // Ajuster pour Slowness Effect
            PotionEffect slownessEffect = player.getPotionEffect(PotionEffectType.SLOW);
            if (slownessEffect != null) {
                baseSpeed *= 1 - (slownessEffect.getAmplifier() + 1) * 0.15;
            }

            // Ajuster pour surfaces (glace, âme, etc.)
            Block blockOn = player.getLocation().getBlock().getRelative(0, -1, 0);
            Material type = blockOn.getType();

            if (type.name().contains("ICE")) {
                baseSpeed *= 1.1;
            } else if (type == Material.SOUL_SAND || type == Material.SOUL_SOIL) {
                baseSpeed *= 0.4;
            } else if (type == Material.HONEY_BLOCK) {
                baseSpeed *= 0.4;
            }

            return baseSpeed;
        }

        private boolean hasBoostingBlock(Location loc) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Material type = loc.getBlock().getRelative(x, 0, z).getType();
                    if (type == Material.PISTON || type == Material.STICKY_PISTON ||
                            type == Material.SLIME_BLOCK) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean shouldBypass(Player player) {
            return player.isFlying() ||
                    player.getGameMode() == GameMode.CREATIVE ||
                    player.getGameMode() == GameMode.SPECTATOR ||
                    player.isInsideVehicle() ||
                    player.isGliding();
        }
    }

    /**
     * Détecte l'absence de dégâts de chute
     */
    public static class NoFallCheck extends Check {
        private final double minFallDistance;

        public NoFallCheck(SentinelAC plugin) {
            super(plugin, "nofall", "movement");
            this.minFallDistance = getConfigDouble("min-fall-distance", 3.0);
        }

        public boolean check(Player player, double fallDistance, PlayerData data) {
            if (!isEnabled() || shouldBypass(player)) {
                return false;
            }

            // Vérifier si le joueur devrait prendre des dégâts
            if (fallDistance < minFallDistance) {
                return false;
            }

            // Vérifier qu'il n'y a pas d'eau ou de bloc qui annule les dégâts
            Location loc = player.getLocation();
            Block blockAt = loc.getBlock();
            Material type = blockAt.getType();

            if (type == Material.WATER || type == Material.COBWEB ||
                    type.name().contains("VINE") || type.name().contains("LADDER")) {
                return false;
            }

            // Check si le joueur a des bottes avec Feather Falling
            if (player.getInventory().getBoots() != null) {
                if (player.getInventory().getBoots().containsEnchantment(Enchantment.PROTECTION_FALL)) {
                    // Ajuster la distance minimale selon le niveau
                    int level = player.getInventory().getBoots().getEnchantmentLevel(Enchantment.PROTECTION_FALL);
                    double adjustedMin = minFallDistance + (level * 1.5);
                    if (fallDistance < adjustedMin) {
                        return false;
                    }
                }
            }

            // Si on arrive ici et que le joueur est au sol sans dégâts = suspect
            return player.isOnGround();
        }

        private boolean shouldBypass(Player player) {
            return player.getGameMode() == GameMode.CREATIVE ||
                    player.getGameMode() == GameMode.SPECTATOR ||
                    player.isFlying() ||
                    player.getPotionEffect(PotionEffectType.SLOW_FALLING) != null;
        }
    }
}