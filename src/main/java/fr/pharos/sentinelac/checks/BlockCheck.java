package fr.pharos.sentinelac.checks;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import fr.pharos.sentinelac.utils.CompatibilityUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Checks liés aux interactions avec les blocs
 */
public class BlockCheck {

    /**
     * Détecte la casse de blocs trop rapide
     */
    public static class FastBreakCheck extends Check {
        private final long breakTimeTolerance;

        public FastBreakCheck(SentinelAC plugin) {
            super(plugin, "fastbreak", "blocks");
            this.breakTimeTolerance = getConfigInt("break-time-tolerance", 50);
        }

        public boolean check(Player player, Block block, PlayerData data) {
            if (!isEnabled() || player.getGameMode() == GameMode.CREATIVE) {
                return false;
            }

            long currentTime = System.currentTimeMillis();
            long timeSinceLastBreak = currentTime - data.getLastBlockBreakTime();

            // Calculer le temps théorique de casse
            long expectedBreakTime = calculateExpectedBreakTime(player, block);

            data.setLastBlockBreakTime(currentTime);

            // Si le joueur casse trop vite
            if (timeSinceLastBreak > 0 && timeSinceLastBreak < (expectedBreakTime - breakTimeTolerance)) {
                plugin.debug(player.getName() + " break time: " + timeSinceLastBreak +
                        " < expected: " + expectedBreakTime);
                return true;
            }

            return false;
        }

        /**
         * Calcule le temps théorique de casse d'un bloc
         */
        private long calculateExpectedBreakTime(Player player, Block block) {
            Material blockType = block.getType();
            ItemStack tool = player.getInventory().getItemInMainHand();

            // Temps de base selon la dureté du bloc (en ms)
            long baseTime = getBlockHardness(blockType);

            // Ajuster selon l'outil
            double toolMultiplier = getToolMultiplier(blockType, tool);

            // Ajuster selon l'efficacité
            org.bukkit.enchantments.Enchantment efficiency = CompatibilityUtils.getEfficiencyEnchantment();
            if (tool.getEnchantments().containsKey(efficiency)) {
                int efficiencyLevel = tool.getEnchantmentLevel(efficiency);
                toolMultiplier *= (1 - efficiencyLevel * 0.15);
            }

            // Ajuster selon la hâte
            org.bukkit.potion.PotionEffectType haste = CompatibilityUtils.getHasteEffect();
            if (CompatibilityUtils.hasPotionEffect(player, haste)) {
                org.bukkit.potion.PotionEffect effect = player.getPotionEffect(haste);
                if (effect != null) {
                    int hasteLevel = effect.getAmplifier();
                    toolMultiplier *= (1 - (hasteLevel + 1) * 0.2);
                }
            }

            return (long) (baseTime * toolMultiplier);
        }

        private long getBlockHardness(Material material) {
            // Valeurs approximatives en millisecondes
            String name = material.name();

            if (name.equals("STONE") || name.equals("COBBLESTONE") ||
                    name.equals("ANDESITE") || name.equals("DIORITE") || name.equals("GRANITE")) {
                return 750;
            } else if (name.equals("DEEPSLATE") || name.contains("DEEPSLATE") && name.contains("ORE")) {
                return 900;
            } else if (name.contains("IRON_ORE") || name.contains("GOLD_ORE") || name.contains("DIAMOND_ORE")) {
                return 1500;
            } else if (name.equals("OBSIDIAN")) {
                return 5000;
            } else if (name.equals("DIRT") || name.equals("GRASS_BLOCK") ||
                    name.equals("SAND") || name.equals("GRAVEL")) {
                return 250;
            } else if (name.contains("LOG") || name.contains("WOOD") || name.contains("PLANKS")) {
                return 1000;
            }

            return 500; // Par défaut
        }

        private double getToolMultiplier(Material blockType, ItemStack tool) {
            if (tool == null || tool.getType() == Material.AIR) {
                return 1.0;
            }

            Material toolType = tool.getType();
            String toolName = toolType.name();
            String blockName = blockType.name();

            // Pickaxe pour pierre
            if (toolName.contains("PICKAXE") && (blockName.contains("STONE") ||
                    blockName.contains("ORE") || blockName.contains("OBSIDIAN"))) {
                if (toolName.contains("DIAMOND") || toolName.contains("NETHERITE")) return 0.15;
                if (toolName.contains("IRON")) return 0.25;
                if (toolName.contains("GOLDEN")) return 0.20;
                return 0.4;
            }

            // Axe pour bois
            if (toolName.contains("AXE") && (blockName.contains("LOG") || blockName.contains("WOOD"))) {
                if (toolName.contains("DIAMOND") || toolName.contains("NETHERITE")) return 0.20;
                if (toolName.contains("IRON")) return 0.30;
                return 0.5;
            }

            // Pelle pour terre/sable
            if (toolName.contains("SHOVEL") && (blockName.contains("DIRT") ||
                    blockName.contains("SAND") || blockName.contains("GRAVEL"))) {
                return 0.25;
            }

            return 1.0;
        }
    }

    /**
     * Détecte les patterns de minage suspects (XRay)
     */
    public static class XRayCheck extends Check {
        private final double suspiciousOreRatio;
        private final List<String> watchedBlocks;

        public XRayCheck(SentinelAC plugin) {
            super(plugin, "xray", "blocks");
            this.suspiciousOreRatio = getConfigDouble("suspicious-ore-ratio", 0.8);
            this.watchedBlocks = plugin.getConfig().getStringList("blocks.xray.watched-blocks");
        }

        public boolean check(Player player, Block block, PlayerData data) {
            if (!isEnabled()) {
                return false;
            }

            Material blockType = block.getType();
            String blockName = blockType.name();

            // Vérifier si c'est un minerai surveillé
            if (!watchedBlocks.contains(blockName)) {
                return false;
            }

            // Enregistrer le minerai
            data.addOreMined(blockName);

            // Vérifier le ratio seulement après un certain nombre de blocs
            if (data.getTotalBlocksMined() < 50) {
                return false;
            }

            // Calculer le ratio de minerais précieux
            double diamondRatio = data.getOreRatio("DIAMOND_ORE") +
                    data.getOreRatio("DEEPSLATE_DIAMOND_ORE");
            double ancientDebrisRatio = data.getOreRatio("ANCIENT_DEBRIS");

            // Ratio suspect si trop élevé
            if (diamondRatio > suspiciousOreRatio || ancientDebrisRatio > suspiciousOreRatio * 0.5) {
                plugin.debug(player.getName() + " suspicious mining ratio - Diamond: " +
                        diamondRatio + ", Debris: " + ancientDebrisRatio);
                return true;
            }

            // Réinitialiser les stats après 100 blocs pour éviter les faux positifs
            if (data.getTotalBlocksMined() > 100) {
                data.resetMiningStats();
            }

            return false;
        }
    }
}