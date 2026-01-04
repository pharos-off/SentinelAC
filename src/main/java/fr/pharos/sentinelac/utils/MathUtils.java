package fr.pharos.sentinelac.utils;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Utilitaires mathématiques pour les calculs de l'anti-cheat
 */
public class MathUtils {

    /**
     * Calcule l'angle entre deux vecteurs en degrés
     */
    public static double getAngleBetweenVectors(Vector v1, Vector v2) {
        // Normaliser les vecteurs
        Vector vec1 = v1.clone().normalize();
        Vector vec2 = v2.clone().normalize();

        // Calculer le produit scalaire
        double dot = vec1.dot(vec2);

        // Limiter le dot product entre -1 et 1 pour éviter NaN
        dot = Math.max(-1.0, Math.min(1.0, dot));

        // Calculer l'angle en radians puis convertir en degrés
        double angleRadians = Math.acos(dot);
        return Math.toDegrees(angleRadians);
    }

    /**
     * Calcule la distance horizontale entre deux locations
     */
    public static double getHorizontalDistance(Location loc1, Location loc2) {
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Calcule la distance verticale entre deux locations
     */
    public static double getVerticalDistance(Location loc1, Location loc2) {
        return Math.abs(loc1.getY() - loc2.getY());
    }

    /**
     * Vérifie si un nombre est dans une plage avec tolérance
     */
    public static boolean isInRange(double value, double expected, double tolerance) {
        return Math.abs(value - expected) <= tolerance;
    }

    /**
     * Calcule l'écart-type d'une série de valeurs
     */
    public static double calculateStandardDeviation(double[] values) {
        if (values.length == 0) return 0;

        // Calculer la moyenne
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        double mean = sum / values.length;

        // Calculer la variance
        double variance = 0;
        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }
        variance /= values.length;

        // Retourner l'écart-type
        return Math.sqrt(variance);
    }

    /**
     * Arrondit un nombre à N décimales
     */
    public static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    /**
     * Calcule le pourcentage d'une valeur par rapport à une autre
     */
    public static double getPercentage(double value, double total) {
        if (total == 0) return 0;
        return (value / total) * 100;
    }

    /**
     * Vérifie si une valeur est anormalement haute comparée à une baseline
     */
    public static boolean isAnomalouslyHigh(double value, double baseline, double threshold) {
        return value > baseline * (1 + threshold);
    }

    /**
     * Interpole linéairement entre deux valeurs
     */
    public static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
}