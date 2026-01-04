package fr.pharos.sentinelac.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analyse les packets réseau pour détecter les cheats côté client
 */
public class PacketAnalyzer {

    private final SentinelAC plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, PacketData> packetDataMap;
    private boolean enabled;

    public PacketAnalyzer(SentinelAC plugin) {
        this.plugin = plugin;
        this.packetDataMap = new ConcurrentHashMap<>();
        this.enabled = plugin.getConfig().getBoolean("packet-analysis.enabled", false);

        if (enabled && plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
            registerPacketListeners();
            plugin.getLogger().info("Analyse de packets NMS activée");
        } else {
            this.protocolManager = null;
            if (enabled) {
                plugin.getLogger().warning("ProtocolLib non trouvé! Analyse de packets désactivée");
                this.enabled = false;
            }
        }
    }

    /**
     * Enregistre les listeners de packets
     */
    private void registerPacketListeners() {
        // Listener pour les packets de mouvement (Flying)
        if (plugin.getConfig().getBoolean("packet-analysis.checks.flying-packets.enabled", true)) {
            registerFlyingPacketListener();
        }

        // Listener pour les packets de position
        if (plugin.getConfig().getBoolean("packet-analysis.checks.position-packets.enabled", true)) {
            registerPositionPacketListener();
        }

        // Listener pour les packets de rotation
        if (plugin.getConfig().getBoolean("packet-analysis.checks.rotation-packets.enabled", true)) {
            registerRotationPacketListener();
        }

        // Listener pour les packets de transaction (Timer check)
        if (plugin.getConfig().getBoolean("packet-analysis.checks.transaction-packets.enabled", true)) {
            registerTransactionPacketListener();
        }

        // NOUVEAUX CHECKS
        registerActionPacketListener();        // BadPackets
        registerAbilitiesPacketListener();     // Flight/Abilities manipulation
        registerEntityActionPacketListener();  // Sprint/Sneak cheats
        registerClientCommandPacketListener(); // Respawn exploits
    }

    /**
     * Écoute les packets Flying (détection de modification de tick rate)
     */
    private void registerFlyingPacketListener() {
        final SentinelAC sentinelPlugin = this.plugin; // Référence finale pour l'inner class

        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.LOOK
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player.hasPermission("anticheat.bypass")) return;

                PacketData data = getPacketData(player.getUniqueId());
                data.incrementFlyingPackets();

                // Vérifier le taux de packets
                long currentTime = System.currentTimeMillis();
                long timeDiff = currentTime - data.getLastFlyingCheck();

                if (timeDiff >= 1000) { // Vérifier chaque seconde
                    int packetsPerSecond = data.getFlyingPacketsCount();
                    int maxPackets = sentinelPlugin.getConfig().getInt("packet-analysis.checks.flying-packets.max-packets-per-second", 25);

                    if (packetsPerSecond > maxPackets) {
                        sentinelPlugin.getViolationManager().flagPlayer(player, "timer",
                                "Packets/sec: " + packetsPerSecond + " > " + maxPackets);

                        // Sauvegarder dans la BDD si activée
                        if (sentinelPlugin.getDatabaseManager() != null && sentinelPlugin.getDatabaseManager().isEnabled()) {
                            sentinelPlugin.getDatabaseManager().savePacketLog(
                                    player.getUniqueId(),
                                    "FLYING",
                                    packetsPerSecond,
                                    true
                            );
                        }
                    }

                    data.resetFlyingPackets();
                    data.setLastFlyingCheck(currentTime);
                }
            }
        });
    }

    /**
     * Écoute les packets de position (téléportation/position impossible)
     */
    private void registerPositionPacketListener() {
        final SentinelAC sentinelPlugin = this.plugin;

        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player.hasPermission("anticheat.bypass")) return;

                PacketData data = getPacketData(player.getUniqueId());

                // Lire la position du packet
                double x = event.getPacket().getDoubles().read(0);
                double y = event.getPacket().getDoubles().read(1);
                double z = event.getPacket().getDoubles().read(2);

                if (data.hasLastPosition()) {
                    double[] lastPos = data.getLastPosition();
                    double distance = Math.sqrt(
                            Math.pow(x - lastPos[0], 2) +
                                    Math.pow(y - lastPos[1], 2) +
                                    Math.pow(z - lastPos[2], 2)
                    );

                    double maxDiff = sentinelPlugin.getConfig().getDouble("packet-analysis.checks.position-packets.max-position-diff", 10.0);

                    // Détection de téléportation non autorisée
                    if (distance > maxDiff && !player.isFlying()) {
                        sentinelPlugin.getViolationManager().flagPlayer(player, "invalid-position",
                                "Distance: " + String.format("%.2f", distance) + " > " + maxDiff);
                    }
                }

                data.setLastPosition(x, y, z);
            }
        });
    }

    /**
     * Écoute les packets de rotation (aim assist/aimbot)
     */
    private void registerRotationPacketListener() {
        final SentinelAC sentinelPlugin = this.plugin;

        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.LOOK,
                PacketType.Play.Client.POSITION_LOOK
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player.hasPermission("anticheat.bypass")) return;

                PacketData data = getPacketData(player.getUniqueId());

                // Lire la rotation du packet
                float yaw = event.getPacket().getFloat().read(0);
                float pitch = event.getPacket().getFloat().read(1);

                if (data.hasLastRotation()) {
                    float[] lastRot = data.getLastRotation();

                    float yawDiff = Math.abs(yaw - lastRot[0]);
                    float pitchDiff = Math.abs(pitch - lastRot[1]);

                    // Normaliser le yaw
                    if (yawDiff > 180) yawDiff = 360 - yawDiff;

                    double rotationSpeed = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                    double maxRotation = sentinelPlugin.getConfig().getDouble("packet-analysis.checks.rotation-packets.max-rotation-speed", 180.0);

                    // Détection de rotation impossible
                    if (rotationSpeed > maxRotation) {
                        sentinelPlugin.getViolationManager().flagPlayer(player, "impossible-rotation",
                                "Speed: " + String.format("%.1f", rotationSpeed) + "°/tick");
                    }

                    // Détection d'aim assist (rotations trop parfaites)
                    data.addRotationSample(rotationSpeed);
                    if (data.getRotationSamples().size() >= 20) {
                        double variance = calculateVariance(data.getRotationSamples());

                        // Variance trop faible = rotations trop régulières (aimbot)
                        if (variance < 2.0 && rotationSpeed > 20) {
                            sentinelPlugin.getViolationManager().flagPlayer(player, "aim-assist",
                                    "Variance: " + String.format("%.2f", variance));
                        }

                        data.clearRotationSamples();
                    }
                }

                data.setLastRotation(yaw, pitch);
            }
        });
    }

    /**
     * Écoute les packets de transaction (timer detection avancée)
     */
    private void registerTransactionPacketListener() {
        final SentinelAC sentinelPlugin = this.plugin;

        // TRANSACTION n'existe plus en 1.17+, on utilise PONG à la place
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.PONG  // Remplace TRANSACTION
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player.hasPermission("anticheat.bypass")) return;

                PacketData data = getPacketData(player.getUniqueId());
                long currentTime = System.currentTimeMillis();

                data.addTransactionTime(currentTime);

                // Analyser les dernières transactions
                if (data.getTransactionTimes().size() >= 20) {
                    double avgInterval = calculateAverageInterval(data.getTransactionTimes());

                    // Intervalle normal: ~50ms (20 TPS)
                    // Timer = intervalle plus court
                    if (avgInterval < 40) { // Moins de 40ms = accéléré
                        sentinelPlugin.getViolationManager().flagPlayer(player, "timer",
                                "Avg interval: " + String.format("%.1f", avgInterval) + "ms");
                    }

                    data.clearTransactionTimes();
                }
            }
        });
    }

    /**
     * Récupère ou crée les données de packets d'un joueur
     */
    private PacketData getPacketData(UUID uuid) {
        return packetDataMap.computeIfAbsent(uuid, k -> new PacketData());
    }

    /**
     * Nettoie les données d'un joueur
     */
    public void removePlayerData(UUID uuid) {
        packetDataMap.remove(uuid);
    }

    /**
     * Calcule la variance d'un ensemble de valeurs
     */
    private double calculateVariance(java.util.List<Double> values) {
        if (values.isEmpty()) return 0;

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = 0;

        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }

        return variance / values.size();
    }

    /**
     * Calcule l'intervalle moyen entre les timestamps
     */
    private double calculateAverageInterval(java.util.List<Long> timestamps) {
        if (timestamps.size() < 2) return 50.0;

        long totalInterval = 0;
        for (int i = 1; i < timestamps.size(); i++) {
            totalInterval += timestamps.get(i) - timestamps.get(i - 1);
        }

        return (double) totalInterval / (timestamps.size() - 1);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * NOUVEAU: Détecte les BadPackets (ordre invalide, valeurs impossibles)
     */
    private void registerActionPacketListener() {
        final SentinelAC sentinelPlugin = this.plugin;

        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.USE_ENTITY,
                PacketType.Play.Client.BLOCK_DIG,
                PacketType.Play.Client.BLOCK_PLACE
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player.hasPermission("anticheat.bypass")) return;

                PacketData data = getPacketData(player.getUniqueId());

                // SUPPRIMÉ: Check inventaire ouvert (trop de faux positifs)

                // Détecter les actions trop rapides (spam packets)
                long currentTime = System.currentTimeMillis();
                long lastActionTime = data.getLastActionTime();

                if (lastActionTime > 0 && currentTime - lastActionTime < 3) { // Moins de 3ms = spam
                    sentinelPlugin.getViolationManager().flagPlayer(player, "badpackets",
                            "Action spam: " + (currentTime - lastActionTime) + "ms");
                    event.setCancelled(true);
                }

                data.setLastActionTime(currentTime);
            }
        });
    }

    /**
     * NOUVEAU: Détecte les manipulations de capacités (flight, invulnérabilité)
     */
    private void registerAbilitiesPacketListener() {
        final SentinelAC sentinelPlugin = this.plugin;

        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.ABILITIES
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player.hasPermission("anticheat.bypass")) return;

                // Le client ne devrait JAMAIS envoyer ce packet en survival/adventure
                if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL ||
                        player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {

                    sentinelPlugin.getViolationManager().flagPlayer(player, "abilities",
                            "Client sent abilities packet in " + player.getGameMode());
                    event.setCancelled(true);
                }
            }
        });
    }

    /**
     * NOUVEAU: Détecte les cheats de sprint/sneak (NoSlow, etc.)
     */
    private void registerEntityActionPacketListener() {
        final SentinelAC sentinelPlugin = this.plugin;

        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.ENTITY_ACTION
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player.hasPermission("anticheat.bypass")) return;

                PacketData data = getPacketData(player.getUniqueId());

                try {
                    // Lire l'action du packet
                    int actionId = event.getPacket().getIntegers().read(0);

                    // 3 = START_SPRINTING, 4 = STOP_SPRINTING
                    if (actionId == 3) { // Start Sprint
                        long currentTime = System.currentTimeMillis();
                        long lastSprint = data.getLastSprintTime();

                        // Si spam de sprint (toggle rapide = NoSlow)
                        if (currentTime - lastSprint < 50) {
                            sentinelPlugin.getViolationManager().flagPlayer(player, "noslow",
                                    "Sprint toggle: " + (currentTime - lastSprint) + "ms");
                        }

                        data.setLastSprintTime(currentTime);
                    }
                } catch (Exception e) {
                    // Ignore si structure du packet change
                }
            }
        });
    }

    /**
     * NOUVEAU: Détecte les exploits de respawn
     */
    private void registerClientCommandPacketListener() {
        final SentinelAC sentinelPlugin = this.plugin;

        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.CLIENT_COMMAND
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player.hasPermission("anticheat.bypass")) return;

                try {
                    // Lire la commande
                    Object command = event.getPacket().getEnumModifier(
                            com.comphenix.protocol.wrappers.EnumWrappers.ClientCommand.class, 0
                    ).read(0);

                    // PERFORM_RESPAWN = respawn request
                    if (command.toString().equals("PERFORM_RESPAWN")) {
                        // Si le joueur n'est pas mort = exploit
                        if (player.getHealth() > 0) {
                            sentinelPlugin.getViolationManager().flagPlayer(player, "exploit",
                                    "Respawn packet while alive");
                            event.setCancelled(true);
                        }
                    }
                } catch (Exception e) {
                    // Ignore si structure change
                }
            }
        });
    }

    /**
     * Classe interne pour stocker les données de packets d'un joueur
     */
    private static class PacketData {
        private int flyingPacketsCount = 0;
        private long lastFlyingCheck = System.currentTimeMillis();
        private double[] lastPosition = null;
        private float[] lastRotation = null;
        private final java.util.List<Double> rotationSamples = new java.util.ArrayList<>();
        private final java.util.List<Long> transactionTimes = new java.util.ArrayList<>();
        private long lastActionTime = 0;
        private long lastSprintTime = 0;

        public void incrementFlyingPackets() {
            flyingPacketsCount++;
        }

        public int getFlyingPacketsCount() {
            return flyingPacketsCount;
        }

        public void resetFlyingPackets() {
            flyingPacketsCount = 0;
        }

        public long getLastFlyingCheck() {
            return lastFlyingCheck;
        }

        public void setLastFlyingCheck(long time) {
            lastFlyingCheck = time;
        }

        public boolean hasLastPosition() {
            return lastPosition != null;
        }

        public double[] getLastPosition() {
            return lastPosition;
        }

        public void setLastPosition(double x, double y, double z) {
            lastPosition = new double[]{x, y, z};
        }

        public boolean hasLastRotation() {
            return lastRotation != null;
        }

        public float[] getLastRotation() {
            return lastRotation;
        }

        public void setLastRotation(float yaw, float pitch) {
            lastRotation = new float[]{yaw, pitch};
        }

        public void addRotationSample(double sample) {
            rotationSamples.add(sample);
        }

        public java.util.List<Double> getRotationSamples() {
            return rotationSamples;
        }

        public void clearRotationSamples() {
            rotationSamples.clear();
        }

        public void addTransactionTime(long time) {
            transactionTimes.add(time);
            if (transactionTimes.size() > 50) {
                transactionTimes.remove(0);
            }
        }

        public java.util.List<Long> getTransactionTimes() {
            return transactionTimes;
        }

        public void clearTransactionTimes() {
            transactionTimes.clear();
        }

        public long getLastActionTime() {
            return lastActionTime;
        }

        public void setLastActionTime(long time) {
            lastActionTime = time;
        }

        public long getLastSprintTime() {
            return lastSprintTime;
        }

        public void setLastSprintTime(long time) {
            lastSprintTime = time;
        }
    }
}