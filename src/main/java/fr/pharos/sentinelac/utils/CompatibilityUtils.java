package fr.pharos.sentinelac.utils;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

/**
 * Utilitaire pour gérer la compatibilité entre différentes versions de Minecraft
 */
public class CompatibilityUtils {

    // ==================== ENCHANTEMENTS ====================

    /**
     * Efficiency (Efficacité)
     */
    public static Enchantment getEfficiencyEnchantment() {
        try {
            // 1.13+
            return Enchantment.DIG_SPEED;
        } catch (NoSuchFieldError e) {
            // Versions plus anciennes
            return Enchantment.getByName("DIG_SPEED");
        }
    }

    /**
     * Feather Falling (Chute Amortie)
     */
    public static Enchantment getFeatherFallingEnchantment() {
        try {
            // 1.13+
            return Enchantment.PROTECTION_FALL;
        } catch (NoSuchFieldError e) {
            // Versions plus anciennes
            return Enchantment.getByName("PROTECTION_FALL");
        }
    }

    /**
     * Fortune
     */
    public static Enchantment getFortuneEnchantment() {
        try {
            return Enchantment.LOOT_BONUS_BLOCKS;
        } catch (NoSuchFieldError e) {
            return Enchantment.getByName("LOOT_BONUS_BLOCKS");
        }
    }

    /**
     * Silk Touch (Toucher de Soie)
     */
    public static Enchantment getSilkTouchEnchantment() {
        try {
            return Enchantment.SILK_TOUCH;
        } catch (NoSuchFieldError e) {
            return Enchantment.getByName("SILK_TOUCH");
        }
    }

    // ==================== EFFETS DE POTION ====================

    /**
     * Speed (Vitesse)
     */
    public static PotionEffectType getSpeedEffect() {
        try {
            return PotionEffectType.SPEED;
        } catch (NoSuchFieldError e) {
            return PotionEffectType.getByName("SPEED");
        }
    }

    /**
     * Slowness (Lenteur)
     */
    public static PotionEffectType getSlownessEffect() {
        try {
            // 1.13+
            return PotionEffectType.SLOW;
        } catch (NoSuchFieldError e) {
            // Versions plus anciennes
            return PotionEffectType.getByName("SLOW");
        }
    }

    /**
     * Jump Boost (Boost de Saut)
     */
    public static PotionEffectType getJumpBoostEffect() {
        try {
            // 1.13+
            return PotionEffectType.JUMP;
        } catch (NoSuchFieldError e) {
            // Versions plus anciennes
            return PotionEffectType.getByName("JUMP");
        }
    }

    /**
     * Haste (Célérité)
     */
    public static PotionEffectType getHasteEffect() {
        try {
            // 1.13+
            return PotionEffectType.FAST_DIGGING;
        } catch (NoSuchFieldError e) {
            // Versions plus anciennes
            return PotionEffectType.getByName("FAST_DIGGING");
        }
    }

    /**
     * Slow Falling (Chute Lente)
     */
    public static PotionEffectType getSlowFallingEffect() {
        try {
            return PotionEffectType.SLOW_FALLING;
        } catch (NoSuchFieldError e) {
            // Peut ne pas exister dans les anciennes versions
            return PotionEffectType.getByName("SLOW_FALLING");
        }
    }

    /**
     * Regeneration (Régénération)
     */
    public static PotionEffectType getRegenerationEffect() {
        try {
            return PotionEffectType.REGENERATION;
        } catch (NoSuchFieldError e) {
            return PotionEffectType.getByName("REGENERATION");
        }
    }

    /**
     * Absorption
     */
    public static PotionEffectType getAbsorptionEffect() {
        try {
            return PotionEffectType.ABSORPTION;
        } catch (NoSuchFieldError e) {
            return PotionEffectType.getByName("ABSORPTION");
        }
    }

    /**
     * Vérifie si un effet de potion existe (pour éviter les erreurs)
     */
    public static boolean hasPotionEffect(org.bukkit.entity.Player player, PotionEffectType type) {
        if (type == null) return false;
        return player.hasPotionEffect(type);
    }
}