package fr.pharos.sentinelac.data;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Stocke toutes les données nécessaires pour analyser le comportement d'un joueur
 */
public class PlayerData {

    private final UUID uuid;
    private final Map<String, Integer> violations;
    private final Map<String, Long> lastViolationTime;

    // Données de mouvement
    private Location lastLocation;
    private Location currentLocation;
    private long lastMoveTime;
    private double lastYVelocity;
    private int airTicks;
    private boolean wasOnGround;

    // Données de combat
    private long lastAttackTime;
    private final List<Long> clickTimestamps;
    private static final int MAX_CLICKS_STORED = 50;
    private Location lastAttackLocation;

    // Données de blocs
    private long lastBlockBreakTime;
    private final Map<String, Integer> oresMined;
    private int totalBlocksMined;

    // Nouvelles données pour checks avancés
    private int keepSprintCount;
    private long lastArmorEquipTime;
    private long lastPotionTime;
    private final Map<java.util.UUID, Location> lastTargetLocations;
    private final List<@NotNull Vector> strafeDirections;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.violations = new HashMap<>();
        this.lastViolationTime = new HashMap<>();
        this.clickTimestamps = new ArrayList<>();
        this.oresMined = new HashMap<>();
        this.airTicks = 0;
        this.totalBlocksMined = 0;
        this.wasOnGround = true;
        this.keepSprintCount = 0;
        this.lastArmorEquipTime = 0;
        this.lastPotionTime = 0;
        this.lastTargetLocations = new HashMap<>();
        this.strafeDirections = new ArrayList<org.bukkit.util.@NotNull Vector>();
    }

    // Méthodes de violation
    public int getViolations(String checkName) {
        return violations.getOrDefault(checkName, 0);
    }

    public void addViolation(String checkName) {
        violations.put(checkName, getViolations(checkName) + 1);
        lastViolationTime.put(checkName, System.currentTimeMillis());
    }

    public void resetViolations(String checkName) {
        violations.put(checkName, 0);
    }

    public void decreaseViolation(String checkName, int amount) {
        int current = getViolations(checkName);
        violations.put(checkName, Math.max(0, current - amount));
    }

    public int getTotalViolations() {
        return violations.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> getAllViolations() {
        return new HashMap<>(violations);
    }

    public long getLastViolationTime(String checkName) {
        return lastViolationTime.getOrDefault(checkName, 0L);
    }

    // Méthodes de mouvement
    public void updateLocation(Location newLocation) {
        this.lastLocation = this.currentLocation;
        this.currentLocation = newLocation;
        this.lastMoveTime = System.currentTimeMillis();
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public long getLastMoveTime() {
        return lastMoveTime;
    }

    public double getHorizontalDistance() {
        if (lastLocation == null || currentLocation == null) return 0;
        double dx = currentLocation.getX() - lastLocation.getX();
        double dz = currentLocation.getZ() - lastLocation.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double getVerticalDistance() {
        if (lastLocation == null || currentLocation == null) return 0;
        return currentLocation.getY() - lastLocation.getY();
    }

    public void setLastYVelocity(double velocity) {
        this.lastYVelocity = velocity;
    }

    public double getLastYVelocity() {
        return lastYVelocity;
    }

    public void incrementAirTicks() {
        this.airTicks++;
    }

    public void resetAirTicks() {
        this.airTicks = 0;
    }

    public int getAirTicks() {
        return airTicks;
    }

    public void setWasOnGround(boolean wasOnGround) {
        this.wasOnGround = wasOnGround;
    }

    public boolean wasOnGround() {
        return wasOnGround;
    }

    // Méthodes de combat
    public void addClick() {
        clickTimestamps.add(System.currentTimeMillis());
        if (clickTimestamps.size() > MAX_CLICKS_STORED) {
            clickTimestamps.remove(0);
        }
        lastAttackTime = System.currentTimeMillis();
    }

    public double getCPS(long timeWindow) {
        long currentTime = System.currentTimeMillis();
        long threshold = currentTime - timeWindow;

        long validClicks = clickTimestamps.stream()
                .filter(time -> time > threshold)
                .count();

        return (validClicks * 1000.0) / timeWindow;
    }

    public long getLastAttackTime() {
        return lastAttackTime;
    }

    public void setLastAttackTime(long time) {
        this.lastAttackTime = time;
    }

    public void setLastAttackLocation(Location location) {
        this.lastAttackLocation = location;
    }

    public Location getLastAttackLocation() {
        return lastAttackLocation;
    }

    public List<Long> getClickTimestamps() {
        return new ArrayList<>(clickTimestamps);
    }

    // Méthodes de blocs
    public void setLastBlockBreakTime(long time) {
        this.lastBlockBreakTime = time;
    }

    public long getLastBlockBreakTime() {
        return lastBlockBreakTime;
    }

    public void addOreMined(String oreType) {
        oresMined.put(oreType, oresMined.getOrDefault(oreType, 0) + 1);
        totalBlocksMined++;
    }

    public double getOreRatio(String oreType) {
        if (totalBlocksMined == 0) return 0;
        return (double) oresMined.getOrDefault(oreType, 0) / totalBlocksMined;
    }

    public void resetMiningStats() {
        oresMined.clear();
        totalBlocksMined = 0;
    }

    public int getTotalBlocksMined() {
        return totalBlocksMined;
    }

    // Nouvelles méthodes pour checks avancés
    public void incrementKeepSprintCount() {
        this.keepSprintCount++;
    }

    public void resetKeepSprintCount() {
        this.keepSprintCount = 0;
    }

    public int getKeepSprintCount() {
        return keepSprintCount;
    }

    public void setLastArmorEquipTime(long time) {
        this.lastArmorEquipTime = time;
    }

    public long getLastArmorEquipTime() {
        return lastArmorEquipTime;
    }

    public void setLastPotionTime(long time) {
        this.lastPotionTime = time;
    }

    public long getLastPotionTime() {
        return lastPotionTime;
    }

    public void setLastTargetLocation(java.util.UUID targetId, Location location) {
        this.lastTargetLocations.put(targetId, location);
    }

    public Location getLastTargetLocation(java.util.UUID targetId) {
        return this.lastTargetLocations.get(targetId);
    }

    public void addStrafeDirection(org.bukkit.util.@NotNull Vector direction) {
        this.strafeDirections.add(direction);
        if (this.strafeDirections.size() > 30) {
            this.strafeDirections.remove(0);
        }
    }

    public ArrayList<@NotNull Vector> getStrafeDirections() {
        return new ArrayList<>(strafeDirections);
    }

    public void clearStrafeDirections() {
        this.strafeDirections.clear();
    }

    // Getters
    public UUID getUuid() {
        return uuid;
    }
}