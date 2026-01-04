package fr.pharos.sentinelac.checks;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.utils.MathUtils;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Checks liés au combat - Version améliorée
 */
public class CombatCheck {

    /**
     * Détecte KillAura avec analyse multi-critères avancée
     */
    public static class KillAuraCheck extends Check {
        private final double maxAttackAngle;
        private final double maxReach;

        public KillAuraCheck(SentinelAC plugin) {
            super(plugin, "killaura", "combat");
            this.maxAttackAngle = getConfigDouble("max-attack-angle", 120);
            this.maxReach = getConfigDouble("max-reach", 3.5);
        }

        public boolean check(Player attacker, Entity target, PlayerData data) {
            if (!isEnabled() || !(target instanceof LivingEntity)) {
                return false;
            }

            Location attackerLoc = attacker.getEyeLocation();
            Location targetLoc = target.getLocation().add(0, target.getHeight() / 2, 0);

            // Check 1: Vérification de la portée (Reach)
            double distance = attackerLoc.distance(targetLoc);
            if (distance > maxReach) {
                plugin.debug(attacker.getName() + " REACH: " +
                        String.format("%.2f", distance) + " > " + maxReach);
                return true;
            }

            // Check 2: Vérification de l'angle d'attaque
            Vector toTarget = targetLoc.toVector().subtract(attackerLoc.toVector());
            Vector direction = attackerLoc.getDirection();
            double angle = MathUtils.getAngleBetweenVectors(direction, toTarget);

            if (angle > maxAttackAngle) {
                plugin.debug(attacker.getName() + " ANGLE: " +
                        String.format("%.1f", angle) + "° > " + maxAttackAngle + "°");
                return true;
            }

            // Check 3: Détection de rotation impossible
            if (data.getLastAttackLocation() != null) {
                double rotationSpeed = calculateRotationSpeed(
                        data.getLastAttackLocation(),
                        attackerLoc
                );

                long timeDiff = System.currentTimeMillis() - data.getLastAttackTime();

                // Si rotation trop rapide (> 180° en moins de 100ms)
                if (rotationSpeed > 180 && timeDiff < 100) {
                    plugin.debug(attacker.getName() + " ROTATION: " +
                            String.format("%.0f", rotationSpeed) + "°/100ms");
                    return true;
                }
            }

            // Check 4: Multi-aura (attaque plusieurs entités trop rapidement)
            long currentTime = System.currentTimeMillis();
            if (currentTime - data.getLastAttackTime() < 50) {
                // Attaque trop rapide après une autre attaque
                if (data.getLastAttackLocation() != null) {
                    double distanceMoved = attackerLoc.distance(data.getLastAttackLocation());

                    // Si le joueur a bougé significativement entre deux attaques rapides
                    if (distanceMoved > 1.5) {
                        plugin.debug(attacker.getName() + " MULTI-AURA: " +
                                "Attack interval: " + (currentTime - data.getLastAttackTime()) + "ms");
                        return true;
                    }
                }
            }

            // Check 5: Attaque à travers les murs
            if (!hasLineOfSight(attackerLoc, targetLoc)) {
                plugin.debug(attacker.getName() + " NO LINE OF SIGHT");
                return true;
            }

            // Check 6: Pattern d'attaque parfait (trop régulier = bot)
            List<Long> recentAttacks = data.getClickTimestamps();
            if (recentAttacks.size() >= 10) {
                double consistency = calculateAttackConsistency(recentAttacks);
                // Trop régulier = suspect (variation < 10ms)
                if (consistency < 10.0) {
                    plugin.debug(attacker.getName() + " PERFECT PATTERN: " +
                            String.format("%.1f", consistency) + "ms variance");
                    return true;
                }
            }

            // Enregistrer cette attaque
            data.setLastAttackLocation(attackerLoc.clone());

            return false;
        }

        private double calculateRotationSpeed(Location from, Location to) {
            float yawDiff = Math.abs(to.getYaw() - from.getYaw());
            float pitchDiff = Math.abs(to.getPitch() - from.getPitch());

            // Normaliser les différences d'angle
            if (yawDiff > 180) yawDiff = 360 - yawDiff;

            return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        }

        private boolean hasLineOfSight(Location from, Location to) {
            Vector direction = to.toVector().subtract(from.toVector()).normalize();
            double distance = from.distance(to);

            // Vérifier les blocs entre l'attaquant et la cible
            for (double d = 0; d < distance; d += 0.5) {
                Location check = from.clone().add(direction.clone().multiply(d));
                if (check.getBlock().getType().isSolid()) {
                    return false;
                }
            }

            return true;
        }

        private double calculateAttackConsistency(List<Long> timestamps) {
            if (timestamps.size() < 2) return 100.0;

            double[] intervals = new double[timestamps.size() - 1];
            for (int i = 1; i < timestamps.size(); i++) {
                intervals[i - 1] = timestamps.get(i) - timestamps.get(i - 1);
            }

            return MathUtils.calculateStandardDeviation(intervals);
        }
    }

    /**
     * Détecte AutoClicker avec analyse statistique avancée
     */
    public static class AutoClickerCheck extends Check {
        private final int maxCPS;
        private final boolean checkConsistency;

        public AutoClickerCheck(SentinelAC plugin) {
            super(plugin, "autoclicker", "combat");
            this.maxCPS = getConfigInt("max-cps", 20);
            this.checkConsistency = getConfigBoolean("check-consistency", true);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled()) {
                return false;
            }

            List<Long> clicks = data.getClickTimestamps();
            if (clicks.size() < 20) return false; // Pas assez de données

            // Check 1: CPS maximum
            double cps1s = data.getCPS(1000);
            if (cps1s > maxCPS) {
                plugin.debug(player.getName() + " HIGH CPS: " +
                        String.format("%.1f", cps1s) + " > " + maxCPS);
                return true;
            }

            // Check 2: CPS sur différentes fenêtres temporelles
            double cps500ms = data.getCPS(500);

            // Si CPS très élevé sur courte période
            if (cps500ms > maxCPS * 1.3) {
                plugin.debug(player.getName() + " BURST CPS: " +
                        String.format("%.1f", cps500ms));
                return true;
            }

            if (!checkConsistency) return false;

            // Check 3: Régularité des clics (signature d'autoclicker)
            // IMPORTANT: Seulement si CPS > 12 (en combat)
            if (clicks.size() >= 30 && cps1s > 12) {
                double consistency = calculateClickVariance(clicks);

                // Autoclicker = très faible variance + CPS élevé
                if (consistency < 5.0) {
                    plugin.debug(player.getName() + " CONSISTENT CLICKS: " +
                            String.format("%.1f", consistency) + "ms variance @ " +
                            String.format("%.1f", cps1s) + " CPS");
                    return true;
                }

                // Check 4: Pattern répétitif (double-clic automatique)
                if (hasRepetitivePattern(clicks)) {
                    plugin.debug(player.getName() + " REPETITIVE PATTERN detected");
                    return true;
                }
            }

            // Check 5: Distribution des intervalles
            if (clicks.size() >= 40 && cps1s > 15) {
                double entropy = calculateClickEntropy(clicks);

                // Faible entropie = clics trop réguliers
                if (entropy < 0.3) {
                    plugin.debug(player.getName() + " LOW ENTROPY: " +
                            String.format("%.2f", entropy));
                    return true;
                }
            }

            return false;
        }

        private double calculateClickVariance(List<Long> timestamps) {
            if (timestamps.size() < 2) return 100.0;

            // Calculer les intervalles entre clics
            double[] intervals = new double[timestamps.size() - 1];
            for (int i = 1; i < timestamps.size(); i++) {
                intervals[i - 1] = timestamps.get(i) - timestamps.get(i - 1);
            }

            return MathUtils.calculateStandardDeviation(intervals);
        }

        private boolean hasRepetitivePattern(List<Long> timestamps) {
            if (timestamps.size() < 10) return false;

            // Chercher des patterns de double-clic (2 clics rapides, pause, répétition)
            int patternCount = 0;

            for (int i = 2; i < timestamps.size() - 1; i++) {
                long interval1 = timestamps.get(i) - timestamps.get(i - 1);
                long interval2 = timestamps.get(i - 1) - timestamps.get(i - 2);

                // Si deux intervalles consécutifs sont très similaires
                if (Math.abs(interval1 - interval2) < 10 && interval1 < 100) {
                    patternCount++;
                }
            }

            // Si plus de 50% des clics suivent un pattern
            return patternCount > timestamps.size() * 0.5;
        }

        private double calculateClickEntropy(List<Long> timestamps) {
            // Calculer l'entropie de Shannon des intervalles
            // Plus l'entropie est basse, plus les clics sont prévisibles

            double[] intervals = new double[timestamps.size() - 1];
            for (int i = 1; i < timestamps.size(); i++) {
                intervals[i - 1] = timestamps.get(i) - timestamps.get(i - 1);
            }

            // Grouper en buckets de 10ms
            int[] buckets = new int[50]; // 0-500ms en buckets de 10ms
            for (double interval : intervals) {
                int bucket = (int) (interval / 10);
                if (bucket < buckets.length) {
                    buckets[bucket]++;
                }
            }

            // Calculer l'entropie
            double entropy = 0;
            int total = intervals.length;

            for (int count : buckets) {
                if (count > 0) {
                    double probability = (double) count / total;
                    entropy -= probability * (Math.log(probability) / Math.log(2));
                }
            }

            // Normaliser entre 0 et 1
            return entropy / Math.log(buckets.length) / Math.log(2);
        }
    }

    /**
     * Détecte Velocity (anti-knockback)
     */
    public static class VelocityCheck extends Check {

        public VelocityCheck(SentinelAC plugin) {
            super(plugin, "velocity", "combat");
        }

        public boolean check(Player player, PlayerData data, double expectedKnockback) {
            if (!isEnabled()) {
                return false;
            }

            // Vérifier si le joueur a reçu du knockback récemment
            long timeSinceHit = System.currentTimeMillis() - data.getLastAttackTime();

            if (timeSinceHit > 1000) return false; // Trop vieux

            // Comparer le mouvement réel avec le knockback attendu
            double actualMovement = data.getHorizontalDistance();

            // Si le mouvement est significativement inférieur au knockback attendu
            if (actualMovement < expectedKnockback * 0.5) {
                plugin.debug(player.getName() + " LOW VELOCITY: " +
                        String.format("%.2f", actualMovement) + " < expected " +
                        String.format("%.2f", expectedKnockback));
                return true;
            }

            return false;
        }
    }
}