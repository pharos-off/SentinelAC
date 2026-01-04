package fr.pharos.sentinelac.checks;

import fr.pharos.sentinelac.SentinelAC;

/**
 * Classe abstraite de base pour tous les checks
 */
public abstract class Check {

    protected final SentinelAC plugin;
    protected final String name;
    protected final String category;
    protected boolean enabled;

    public Check(SentinelAC plugin, String name, String category) {
        this.plugin = plugin;
        this.name = name;
        this.category = category;
        this.enabled = loadEnabledState();
    }

    /**
     * Charge l'état activé/désactivé depuis la config
     */
    private boolean loadEnabledState() {
        String configPath = category + "." + name.toLowerCase() + ".enabled";
        return plugin.getConfig().getBoolean(configPath, true);
    }

    /**
     * Récupère une valeur de configuration pour ce check
     */
    protected double getConfigDouble(String key, double defaultValue) {
        String configPath = category + "." + name.toLowerCase() + "." + key;
        return plugin.getConfig().getDouble(configPath, defaultValue);
    }

    protected int getConfigInt(String key, int defaultValue) {
        String configPath = category + "." + name.toLowerCase() + "." + key;
        return plugin.getConfig().getInt(configPath, defaultValue);
    }

    protected boolean getConfigBoolean(String key, boolean defaultValue) {
        String configPath = category + "." + name.toLowerCase() + "." + key;
        return plugin.getConfig().getBoolean(configPath, defaultValue);
    }

    // Getters et Setters
    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFullName() {
        return category + "." + name;
    }
}