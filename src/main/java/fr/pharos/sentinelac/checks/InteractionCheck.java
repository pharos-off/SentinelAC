package fr.pharos.sentinelac.checks;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Checks liés aux interactions du joueur
 */
public class InteractionCheck {

    /**
     * Détecte les interactions impossibles (trop loin, à travers murs, etc.)
     */
    public static class InteractCheck extends Check {
        private final double maxInteractDistance;

        public InteractCheck(SentinelAC plugin) {
            super(plugin, "interact", "interaction");
            this.maxInteractDistance = getConfigDouble("max-interact-distance", 6.5);
        }

        public boolean check(Player player, Block block, PlayerData data) {
            if (!isEnabled()) {
                return false;
            }

            Location playerLoc = player.getEyeLocation();
            Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);

            // Check 1: Distance d'interaction (augmentée pour éviter faux positifs)
            double distance = playerLoc.distance(blockLoc);
            if (distance > maxInteractDistance) {
                plugin.debug(player.getName() + " INTERACT DISTANCE: " +
                        String.format("%.2f", distance) + " > " + maxInteractDistance);
                return true;
            }

            // Check 2: Ligne de vue (DÉSACTIVÉ temporairement - trop de faux positifs)
            // if (!hasLineOfSight(playerLoc, blockLoc)) {
            //     plugin.debug(player.getName() + " INTERACT NO LINE OF SIGHT");
            //     return true;
            // }

            return false;
        }

        private boolean hasLineOfSight(Location from, Location to) {
            org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector()).normalize();
            double distance = from.distance(to);

            for (double d = 0.5; d < distance; d += 0.5) {
                Location check = from.clone().add(direction.clone().multiply(d));
                Block block = check.getBlock();

                // Vérifier si c'est un bloc solide (pas air, pas plante, etc.)
                if (block.getType().isSolid() && block.getType() != Material.GLASS &&
                        !block.getType().name().contains("GLASS_PANE")) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Détecte l'utilisation rapide d'items (FastEat, FastBow, etc.)
     */
    public static class FastUseCheck extends Check {
        private final long minUseTime;

        public FastUseCheck(SentinelAC plugin) {
            super(plugin, "fastuse", "interaction");
            this.minUseTime = getConfigInt("min-use-time", 800); // 800ms pour manger
        }

        public boolean check(Player player, ItemStack item, PlayerData data) {
            if (!isEnabled() || item == null) {
                return false;
            }

            Material type = item.getType();
            long currentTime = System.currentTimeMillis();
            long timeSinceLastUse = currentTime - data.getLastBlockBreakTime();

            // Déterminer le temps minimum selon l'item
            long requiredTime = getRequiredUseTime(type);

            if (requiredTime == 0) return false; // Item non concerné

            // Check si utilisé trop rapidement
            if (timeSinceLastUse > 0 && timeSinceLastUse < requiredTime) {
                plugin.debug(player.getName() + " FAST USE: " + type.name() +
                        " in " + timeSinceLastUse + "ms < " + requiredTime + "ms");
                return true;
            }

            return false;
        }

        private long getRequiredUseTime(Material type) {
            String name = type.name();

            // Nourriture (1.6s = 32 ticks)
            if (type.isEdible()) {
                return 1600;
            }

            // Arc (1s minimum pour tirer)
            if (name.equals("BOW")) {
                return 200; // Temps minimum de charge
            }

            // Arbalète
            if (name.equals("CROSSBOW")) {
                return 1200;
            }

            // Potions
            if (name.contains("POTION") && !name.contains("SPLASH")) {
                return 1600;
            }

            // Trident
            if (name.equals("TRIDENT")) {
                return 200;
            }

            return 0; // Pas de restriction
        }
    }

    /**
     * Détecte l'inventaire ouvert pendant des actions impossibles
     */
    public static class InventoryCheck extends Check {

        public InventoryCheck(SentinelAC plugin) {
            super(plugin, "inventory", "interaction");
        }

        public boolean check(Player player, PlayerData data, String action) {
            if (!isEnabled()) {
                return false;
            }

            // Vérifier si le joueur a un inventaire ouvert
            if (player.getOpenInventory().getTopInventory().getSize() > 0) {

                // Actions impossibles avec inventaire ouvert
                if (action.equals("ATTACK") || action.equals("BREAK_BLOCK") ||
                        action.equals("PLACE_BLOCK")) {

                    plugin.debug(player.getName() + " ACTION WITH INVENTORY OPEN: " + action);
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Détecte les clics trop rapides sur les inventaires (ChestStealer)
     */
    public static class ChestStealerCheck extends Check {
        private final int maxClicksPerSecond;

        public ChestStealerCheck(SentinelAC plugin) {
            super(plugin, "cheststealer", "interaction");
            this.maxClicksPerSecond = getConfigInt("max-clicks-per-second", 10);
        }

        public boolean check(Player player, PlayerData data) {
            if (!isEnabled()) {
                return false;
            }

            // Utiliser le système de clicks existant
            double cps = data.getCPS(1000);

            // Dans un inventaire, CPS élevé = suspect
            if (cps > maxClicksPerSecond) {
                plugin.debug(player.getName() + " CHEST STEALER: " +
                        String.format("%.1f", cps) + " clicks/s");
                return true;
            }

            return false;
        }
    }

    /**
     * Détecte les actions impossibles (sauter + sprint + attaque simultanés)
     */
    public static class ImpossibleActionsCheck extends Check {

        public ImpossibleActionsCheck(SentinelAC plugin) {
            super(plugin, "impossibleactions", "interaction");
        }

        public boolean check(Player player, PlayerData data, String[] actions) {
            if (!isEnabled()) {
                return false;
            }

            // Vérifier les combinaisons impossibles
            boolean isJumping = !player.isOnGround();
            boolean isSprinting = player.isSprinting();
            boolean isAttacking = System.currentTimeMillis() - data.getLastAttackTime() < 100;
            boolean isBlocking = player.isBlocking();

            // Combinaison 1: Sprinter + Bloquer (impossible en vanilla)
            if (isSprinting && isBlocking) {
                plugin.debug(player.getName() + " SPRINT + BLOCK");
                return true;
            }

            // Combinaison 2: Attaquer pendant un saut très haut
            if (isJumping && isAttacking) {
                double yVelocity = data.getLastYVelocity();
                if (yVelocity > 0.42) { // Montée anormale
                    plugin.debug(player.getName() + " ATTACK DURING HIGH JUMP");
                    return true;
                }
            }

            // Combinaison 3: Actions multiples trop rapides
            long currentTime = System.currentTimeMillis();
            long attackTime = currentTime - data.getLastAttackTime();
            long breakTime = currentTime - data.getLastBlockBreakTime();

            if (attackTime < 50 && breakTime < 50) {
                plugin.debug(player.getName() + " ATTACK + BREAK TOO FAST");
                return true;
            }

            return false;
        }
    }
}