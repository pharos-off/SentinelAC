package fr.pharos.sentinelac.ml;

import fr.pharos.sentinelac.SentinelAC;
import fr.pharos.sentinelac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Système de Machine Learning pour détecter les patterns de comportement suspects
 * Utilise un algorithme de clustering K-means simplifié
 */
public class MLAnalyzer {

    private final SentinelAC plugin;
    private final Map<UUID, BehaviorProfile> behaviorProfiles;
    private final Map<String, BehaviorModel> models;
    private boolean enabled;
    private int minSampleSize;
    private double confidenceThreshold;

    public MLAnalyzer(SentinelAC plugin) {
        this.plugin = plugin;
        this.behaviorProfiles = new ConcurrentHashMap<>();
        this.models = new HashMap<>();
        this.enabled = plugin.getConfig().getBoolean("machine-learning.enabled", false);
        this.minSampleSize = plugin.getConfig().getInt("machine-learning.min-sample-size", 100);
        this.confidenceThreshold = plugin.getConfig().getDouble("machine-learning.confidence-threshold", 0.85);

        if (enabled) {
            initializeModels();
            startTrainingTask();
            plugin.getLogger().info("Machine Learning activé - Modèles initialisés");
        }
    }

    /**
     * Initialise les modèles de comportement
     */
    private void initializeModels() {
        // Modèle pour le mouvement
        models.put("MOVEMENT", new BehaviorModel("MOVEMENT"));

        // Modèle pour le combat
        models.put("COMBAT", new BehaviorModel("COMBAT"));

        // Modèle pour l'interaction
        models.put("INTERACTION", new BehaviorModel("INTERACTION"));
    }

    /**
     * Démarre la tâche d'entraînement périodique
     */
    private void startTrainingTask() {
        int interval = plugin.getConfig().getInt("machine-learning.training-interval", 30);

        new BukkitRunnable() {
            @Override
            public void run() {
                trainModels();
            }
        }.runTaskTimerAsynchronously(plugin, interval * 60 * 20L, interval * 60 * 20L);
    }

    /**
     * Entraîne les modèles avec les données collectées
     */
    private void trainModels() {
        plugin.debug("Entraînement des modèles ML...");

        for (BehaviorModel model : models.values()) {
            List<double[]> samples = collectSamplesForModel(model.getModelName());

            if (samples.size() >= minSampleSize) {
                model.train(samples);
                plugin.debug("Modèle " + model.getModelName() + " entraîné avec " + samples.size() + " échantillons");
            }
        }
    }

    /**
     * Collecte les échantillons pour un modèle
     */
    private List<double[]> collectSamplesForModel(String modelName) {
        List<double[]> samples = new ArrayList<>();

        for (BehaviorProfile profile : behaviorProfiles.values()) {
            switch (modelName) {
                case "MOVEMENT":
                    samples.addAll(profile.getMovementSamples());
                    break;
                case "COMBAT":
                    samples.addAll(profile.getCombatSamples());
                    break;
                case "INTERACTION":
                    samples.addAll(profile.getInteractionSamples());
                    break;
            }
        }

        return samples;
    }

    /**
     * Analyse le comportement d'un joueur
     */
    public void analyzePlayer(Player player, PlayerData data) {
        if (!enabled) return;

        BehaviorProfile profile = getProfile(player.getUniqueId());

        // Extraire les features du comportement
        double[] movementFeatures = extractMovementFeatures(data);
        double[] combatFeatures = extractCombatFeatures(data);
        double[] interactionFeatures = extractInteractionFeatures(data);

        // Ajouter aux échantillons
        profile.addMovementSample(movementFeatures);
        profile.addCombatSample(combatFeatures);
        profile.addInteractionSample(interactionFeatures);

        // Analyser avec les modèles
        analyzeWithModel(player, "MOVEMENT", movementFeatures);
        analyzeWithModel(player, "COMBAT", combatFeatures);
        analyzeWithModel(player, "INTERACTION", interactionFeatures);
    }

    /**
     * Extrait les features de mouvement
     */
    private double[] extractMovementFeatures(PlayerData data) {
        return new double[] {
                data.getHorizontalDistance(),           // Distance horizontale
                data.getVerticalDistance(),             // Distance verticale
                data.getAirTicks(),                     // Ticks en l'air
                data.getLastYVelocity(),                // Vélocité Y
                data.wasOnGround() ? 1.0 : 0.0,        // État au sol
        };
    }

    /**
     * Extrait les features de combat
     */
    private double[] extractCombatFeatures(PlayerData data) {
        return new double[] {
                data.getCPS(1000),                      // CPS sur 1 seconde
                data.getCPS(500),                       // CPS sur 500ms
                calculateClickConsistency(data),        // Régularité des clics
                data.getClickTimestamps().size(),       // Nombre de clics récents
                timeSinceLastAttack(data)               // Temps depuis dernière attaque
        };
    }

    /**
     * Extrait les features d'interaction
     */
    private double[] extractInteractionFeatures(PlayerData data) {
        return new double[] {
                timeSinceLastBreak(data),               // Temps depuis dernier break
                data.getTotalBlocksMined(),             // Blocs minés
                calculateBreakPattern(data),            // Pattern de casse
                data.getOreRatio("DIAMOND_ORE"),        // Ratio de diamants
                data.getOreRatio("ANCIENT_DEBRIS")      // Ratio de debris
        };
    }

    /**
     * Analyse avec un modèle spécifique
     */
    private void analyzeWithModel(Player player, String modelName, double[] features) {
        BehaviorModel model = models.get(modelName);
        if (model == null || !model.isTrained()) return;

        double anomalyScore = model.detectAnomaly(features);

        if (anomalyScore > confidenceThreshold) {
            plugin.getViolationManager().flagPlayer(player, "ml-" + modelName.toLowerCase(),
                    "Anomaly score: " + String.format("%.2f", anomalyScore * 100) + "%");

            // Sauvegarder le pattern suspect dans la BDD
            if (plugin.getConfig().getBoolean("machine-learning.save-suspicious-patterns", true)) {
                String patternData = Arrays.toString(features);
                plugin.getDatabaseManager().saveBehaviorPattern(
                        player.getUniqueId(),
                        modelName,
                        patternData,
                        anomalyScore
                );
            }
        }
    }

    /**
     * Calcule la consistance des clics
     */
    private double calculateClickConsistency(PlayerData data) {
        List<Long> clicks = data.getClickTimestamps();
        if (clicks.size() < 2) return 0;

        double[] intervals = new double[clicks.size() - 1];
        for (int i = 1; i < clicks.size(); i++) {
            intervals[i - 1] = clicks.get(i) - clicks.get(i - 1);
        }

        // Calculer l'écart-type
        double mean = Arrays.stream(intervals).average().orElse(0);
        double variance = 0;
        for (double interval : intervals) {
            variance += Math.pow(interval - mean, 2);
        }
        return Math.sqrt(variance / intervals.length);
    }

    /**
     * Calcule le pattern de casse de blocs
     */
    private double calculateBreakPattern(PlayerData data) {
        long timeSinceBreak = System.currentTimeMillis() - data.getLastBlockBreakTime();
        if (timeSinceBreak > 5000) return 0; // Pas de pattern récent

        return 1000.0 / Math.max(timeSinceBreak, 1); // Rate de casse
    }

    private double timeSinceLastAttack(PlayerData data) {
        return System.currentTimeMillis() - data.getLastAttackTime();
    }

    private double timeSinceLastBreak(PlayerData data) {
        return System.currentTimeMillis() - data.getLastBlockBreakTime();
    }

    /**
     * Récupère le profil comportemental d'un joueur
     */
    private BehaviorProfile getProfile(UUID uuid) {
        return behaviorProfiles.computeIfAbsent(uuid, k -> new BehaviorProfile());
    }

    /**
     * Nettoie les données d'un joueur
     */
    public void removePlayerProfile(UUID uuid) {
        behaviorProfiles.remove(uuid);
    }

    /**
     * Classe représentant un profil comportemental
     */
    private static class BehaviorProfile {
        private final List<double[]> movementSamples = new ArrayList<>();
        private final List<double[]> combatSamples = new ArrayList<>();
        private final List<double[]> interactionSamples = new ArrayList<>();
        private static final int MAX_SAMPLES = 1000;

        public void addMovementSample(double[] sample) {
            addSample(movementSamples, sample);
        }

        public void addCombatSample(double[] sample) {
            addSample(combatSamples, sample);
        }

        public void addInteractionSample(double[] sample) {
            addSample(interactionSamples, sample);
        }

        private void addSample(List<double[]> list, double[] sample) {
            list.add(sample);
            if (list.size() > MAX_SAMPLES) {
                list.remove(0);
            }
        }

        public List<double[]> getMovementSamples() {
            return new ArrayList<>(movementSamples);
        }

        public List<double[]> getCombatSamples() {
            return new ArrayList<>(combatSamples);
        }

        public List<double[]> getInteractionSamples() {
            return new ArrayList<>(interactionSamples);
        }
    }

    /**
     * Modèle de comportement utilisant K-means pour détecter les anomalies
     */
    private static class BehaviorModel {
        private final String modelName;
        private double[][] centroids;
        private boolean trained = false;
        private static final int NUM_CLUSTERS = 3;

        public BehaviorModel(String modelName) {
            this.modelName = modelName;
        }

        /**
         * Entraîne le modèle avec K-means
         */
        public void train(List<double[]> samples) {
            if (samples.isEmpty()) return;

            int dimensions = samples.get(0).length;
            centroids = new double[NUM_CLUSTERS][dimensions];

            // Initialiser les centroïdes aléatoirement
            Random rand = new Random();
            for (int i = 0; i < NUM_CLUSTERS; i++) {
                centroids[i] = samples.get(rand.nextInt(samples.size())).clone();
            }

            // K-means iterations
            for (int iter = 0; iter < 10; iter++) {
                List<List<double[]>> clusters = new ArrayList<>();
                for (int i = 0; i < NUM_CLUSTERS; i++) {
                    clusters.add(new ArrayList<>());
                }

                // Assigner chaque échantillon au centroïde le plus proche
                for (double[] sample : samples) {
                    int nearestCluster = findNearestCluster(sample);
                    clusters.get(nearestCluster).add(sample);
                }

                // Recalculer les centroïdes
                for (int i = 0; i < NUM_CLUSTERS; i++) {
                    if (!clusters.get(i).isEmpty()) {
                        centroids[i] = calculateMean(clusters.get(i));
                    }
                }
            }

            trained = true;
        }

        /**
         * Détecte une anomalie (distance aux centroïdes)
         */
        public double detectAnomaly(double[] features) {
            if (!trained || centroids == null) return 0;

            double minDistance = Double.MAX_VALUE;
            for (double[] centroid : centroids) {
                double distance = euclideanDistance(features, centroid);
                minDistance = Math.min(minDistance, distance);
            }

            // Normaliser le score (0-1)
            return Math.min(1.0, minDistance / 10.0);
        }

        private int findNearestCluster(double[] sample) {
            int nearest = 0;
            double minDistance = euclideanDistance(sample, centroids[0]);

            for (int i = 1; i < NUM_CLUSTERS; i++) {
                double distance = euclideanDistance(sample, centroids[i]);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = i;
                }
            }

            return nearest;
        }

        private double[] calculateMean(List<double[]> samples) {
            if (samples.isEmpty()) return new double[0];

            int dimensions = samples.get(0).length;
            double[] mean = new double[dimensions];

            for (double[] sample : samples) {
                for (int i = 0; i < dimensions; i++) {
                    mean[i] += sample[i];
                }
            }

            for (int i = 0; i < dimensions; i++) {
                mean[i] /= samples.size();
            }

            return mean;
        }

        private double euclideanDistance(double[] a, double[] b) {
            double sum = 0;
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                sum += Math.pow(a[i] - b[i], 2);
            }
            return Math.sqrt(sum);
        }

        public String getModelName() {
            return modelName;
        }

        public boolean isTrained() {
            return trained;
        }
    }
}