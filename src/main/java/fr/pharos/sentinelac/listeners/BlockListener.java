package fr.pharos.sentinelac.listeners;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.checks.BlockCheck;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.managers.ViolationManager;
import fr.pharos.sentinelac.utils.CompatibilityUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Écoute tous les événements liés aux blocs
 */
public class BlockListener implements Listener {

    private final SentinelAC plugin;
    private final ViolationManager violationManager;
    private final BlockCheck.FastBreakCheck fastBreakCheck;
    private final BlockCheck.XRayCheck xRayCheck;

    public BlockListener(SentinelAC plugin) {
        this.plugin = plugin;
        this.violationManager = plugin.getViolationManager();
        this.fastBreakCheck = new BlockCheck.FastBreakCheck(plugin);
        this.xRayCheck = new BlockCheck.XRayCheck(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Bypass si permission
        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        Block block = event.getBlock();
        PlayerData data = violationManager.getPlayerData(player);

        // === REACH CHECK (distance de casse) ===
        if (checkBlockReach(player, block)) {
            violationManager.flagPlayer(player, "reach",
                    "Block: " + block.getType().name() +
                            " | Distance: " + String.format("%.2f",
                            player.getLocation().distance(block.getLocation())));
            event.setCancelled(true);
            return;
        }

        // === FASTBREAK CHECK ===
        if (fastBreakCheck.isEnabled()) {
            if (fastBreakCheck.check(player, block, data)) {
                violationManager.flagPlayer(player, "fastbreak",
                        "Block: " + block.getType().name() +
                                " | Time: " + (System.currentTimeMillis() - data.getLastBlockBreakTime()) + "ms");
                event.setCancelled(true);
                return;
            }
        }

        // === XRAY CHECK ===
        if (xRayCheck.isEnabled()) {
            if (xRayCheck.check(player, block, data)) {
                String blockName = block.getType().name();
                violationManager.flagPlayer(player, "xray",
                        "Block: " + blockName +
                                " | Ratio: " + String.format("%.2f%%", data.getOreRatio(blockName) * 100) +
                                " | Total: " + data.getTotalBlocksMined());
                // Ne pas annuler pour XRay, juste alerter le staff
            }
        }

        // === NUKER CHECK (casse trop de blocs trop vite) ===
        if (checkNuker(player, data)) {
            violationManager.flagPlayer(player, "nuker",
                    "Blocks/sec: " + calculateBreakRate(data));
            event.setCancelled(true);
            return;
        }

        // Mettre à jour le temps de dernière casse
        data.setLastBlockBreakTime(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        Block block = event.getBlock();
        PlayerData data = violationManager.getPlayerData(player);

        // === REACH CHECK (distance de placement) ===
        if (checkBlockReach(player, block)) {
            violationManager.flagPlayer(player, "reach",
                    "Place: " + block.getType().name() +
                            " | Distance: " + String.format("%.2f",
                            player.getLocation().distance(block.getLocation())));
            event.setCancelled(true);
            return;
        }

        // === SCAFFOLD CHECK (placement en l'air trop rapide) ===
        if (checkScaffold(player, block, data)) {
            violationManager.flagPlayer(player, "scaffold",
                    "Block: " + block.getType().name() +
                            " | Air: " + !player.isOnGround());
            event.setCancelled(true);
            return;
        }

        // === TOWER CHECK (montée verticale trop rapide) ===
        if (checkTower(player, block, data)) {
            violationManager.flagPlayer(player, "tower",
                    "Height: " + String.format("%.2f",
                            block.getY() - player.getLocation().getY()));
            event.setCancelled(true);
        }
    }

    /**
     * Vérifie si le joueur casse/place un bloc trop loin
     */
    private boolean checkBlockReach(Player player, Block block) {
        double maxReach = player.getGameMode() == GameMode.CREATIVE ? 6.0 : 4.5;

        Location playerLoc = player.getEyeLocation();
        Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);

        double distance = playerLoc.distance(blockLoc);

        return distance > maxReach;
    }

    /**
     * Vérifie le Nuker (casse massive de blocs)
     */
    private boolean checkNuker(Player player, PlayerData data) {
        if (!plugin.getConfig().getBoolean("blocks.nuker.enabled", true)) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long timeSinceLastBreak = currentTime - data.getLastBlockBreakTime();

        // Si moins de 100ms entre deux casses = suspect
        if (timeSinceLastBreak < 100) {
            return true;
        }

        return false;
    }

    /**
     * Calcule le taux de casse de blocs
     */
    private double calculateBreakRate(PlayerData data) {
        // Simplification : basé sur le dernier intervalle
        long interval = System.currentTimeMillis() - data.getLastBlockBreakTime();
        if (interval == 0) return 0;

        return 1000.0 / interval; // Blocs par seconde
    }

    /**
     * Vérifie le Scaffold (placement en l'air)
     */
    private boolean checkScaffold(Player player, Block block, PlayerData data) {
        if (!plugin.getConfig().getBoolean("blocks.scaffold.enabled", true)) {
            return false;
        }

        // Vérifier si le joueur est en l'air
        if (!player.isOnGround()) {
            // Vérifier la rotation (scaffold = souvent regarder vers le bas)
            float pitch = player.getLocation().getPitch();

            // Si pitch > 70° (regarde vers le bas) + en l'air = suspect
            if (pitch > 70) {
                // Vérifier la vitesse de placement
                long timeSinceLastBreak = System.currentTimeMillis() - data.getLastBlockBreakTime();

                if (timeSinceLastBreak < 150) { // Placement très rapide
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Vérifie le Tower (montée verticale rapide)
     */
    private boolean checkTower(Player player, Block block, PlayerData data) {
        if (!plugin.getConfig().getBoolean("blocks.tower.enabled", true)) {
            return false;
        }

        // Vérifier si c'est un placement vertical sous le joueur
        Location playerLoc = player.getLocation();
        Location blockLoc = block.getLocation();

        // Bloc directement sous le joueur
        if (Math.abs(playerLoc.getX() - blockLoc.getX()) < 1 &&
                Math.abs(playerLoc.getZ() - blockLoc.getZ()) < 1 &&
                blockLoc.getY() < playerLoc.getY()) {

            // Vérifier la vitesse de montée
            double yVelocity = data.getLastYVelocity();

            // Si monte trop vite = tower
            org.bukkit.potion.PotionEffectType jumpBoost = CompatibilityUtils.getJumpBoostEffect();
            if (yVelocity > 0.42 && !CompatibilityUtils.hasPotionEffect(player, jumpBoost)) {
                return true;
            }
        }

        return false;
    }
}