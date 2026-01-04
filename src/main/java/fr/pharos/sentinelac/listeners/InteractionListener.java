package fr.pharos.sentinelac.listeners;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.checks.InteractionCheck;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.managers.ViolationManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Écoute tous les événements d'interaction
 */
public class InteractionListener implements Listener {

    private final SentinelAC plugin;
    private final ViolationManager violationManager;
    private final InteractionCheck.InteractCheck interactCheck;
    private final InteractionCheck.FastUseCheck fastUseCheck;
    private final InteractionCheck.InventoryCheck inventoryCheck;
    private final InteractionCheck.ChestStealerCheck chestStealerCheck;
    private final InteractionCheck.ImpossibleActionsCheck impossibleActionsCheck;

    public InteractionListener(SentinelAC plugin) {
        this.plugin = plugin;
        this.violationManager = plugin.getViolationManager();
        this.interactCheck = new InteractionCheck.InteractCheck(plugin);
        this.fastUseCheck = new InteractionCheck.FastUseCheck(plugin);
        this.inventoryCheck = new InteractionCheck.InventoryCheck(plugin);
        this.chestStealerCheck = new InteractionCheck.ChestStealerCheck(plugin);
        this.impossibleActionsCheck = new InteractionCheck.ImpossibleActionsCheck(plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Initialiser les données du joueur
        violationManager.getPlayerData(player);

        // Enregistrer le joueur dans la BDD si activée
        if (plugin.getDatabaseManager().isEnabled()) {
            plugin.getDatabaseManager().savePlayer(player.getUniqueId(), player.getName());
        }

        plugin.debug("Données initialisées pour " + player.getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        PlayerData data = violationManager.getPlayerData(player);
        Action action = event.getAction();

        // Enregistrer les clics gauches pour AutoClicker
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            data.addClick();
        }

        // === INTERACT CHECK (distance et ligne de vue) ===
        if (event.getClickedBlock() != null && interactCheck.isEnabled()) {
            Block block = event.getClickedBlock();

            if (interactCheck.check(player, block, data)) {
                violationManager.flagPlayer(player, "interact",
                        "Block: " + block.getType().name() +
                                " | Distance: " + String.format("%.2f",
                                player.getLocation().distance(block.getLocation())));
                event.setCancelled(true);
                return;
            }
        }

        // === IMPOSSIBLE ACTIONS CHECK ===
        if (impossibleActionsCheck.isEnabled()) {
            String[] actions = determineCurrentActions(player, data);

            if (impossibleActionsCheck.check(player, data, actions)) {
                violationManager.flagPlayer(player, "impossibleactions",
                        "Actions: " + String.join(", ", actions));
                event.setCancelled(true);
                return;
            }
        }

        // === INVENTORY CHECK (action avec inventaire ouvert) ===
        if (inventoryCheck.isEnabled()) {
            String actionType = determineActionType(action);

            if (inventoryCheck.check(player, data, actionType)) {
                violationManager.flagPlayer(player, "inventory",
                        "Action: " + actionType + " with inventory open");
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

        ItemStack item = event.getItem();
        PlayerData data = violationManager.getPlayerData(player);

        // === FASTUSE CHECK (manger/boire trop vite) ===
        if (fastUseCheck.isEnabled()) {
            if (fastUseCheck.check(player, item, data)) {
                violationManager.flagPlayer(player, "fastuse",
                        "Item: " + item.getType().name() +
                                " | Time: " + (System.currentTimeMillis() - data.getLastBlockBreakTime()) + "ms");
                event.setCancelled(true);
                return;
            }
        }

        // Mettre à jour le temps d'utilisation
        data.setLastBlockBreakTime(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.hasPermission("anticheat.bypass")) {
            return;
        }

        PlayerData data = violationManager.getPlayerData(player);

        // Enregistrer le clic
        data.addClick();

        // === CHESTSTEALER CHECK ===
        if (chestStealerCheck.isEnabled()) {
            // Vérifier seulement si c'est un coffre/inventaire externe
            if (event.getClickedInventory() != player.getInventory()) {
                if (chestStealerCheck.check(player, data)) {
                    violationManager.flagPlayer(player, "cheststealer",
                            "CPS: " + String.format("%.1f", data.getCPS(1000)) +
                                    " in inventory");
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Détermine les actions actuelles du joueur
     */
    private String[] determineCurrentActions(Player player, PlayerData data) {
        java.util.List<String> actions = new java.util.ArrayList<>();

        if (!player.isOnGround()) {
            actions.add("JUMPING");
        }
        if (player.isSprinting()) {
            actions.add("SPRINTING");
        }
        if (player.isBlocking()) {
            actions.add("BLOCKING");
        }
        if (System.currentTimeMillis() - data.getLastAttackTime() < 100) {
            actions.add("ATTACKING");
        }
        if (System.currentTimeMillis() - data.getLastBlockBreakTime() < 100) {
            actions.add("BREAKING");
        }

        return actions.toArray(new String[0]);
    }

    /**
     * Détermine le type d'action
     */
    private String determineActionType(Action action) {
        return switch (action) {
            case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> "ATTACK";
            case RIGHT_CLICK_BLOCK -> "INTERACT_BLOCK";
            case RIGHT_CLICK_AIR -> "USE_ITEM";
            default -> "UNKNOWN";
        };
    }
}