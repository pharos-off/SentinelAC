# 🛡️ SentinelAC - Guide d'Installation Complet

## 📋 Table des Matières

1. [Prérequis](#prérequis)
2. [Installation Rapide](#installation-rapide)
3. [Configuration MySQL](#configuration-mysql-optionnel)
4. [Configuration du Plugin](#configuration-du-plugin)
5. [Auto-Updater](#configuration-auto-updater)
6. [Vérification](#vérification)
7. [Commandes](#commandes)
8. [Permissions](#permissions)
9. [Dépannage](#dépannage)
10. [Optimisation](#optimisation)

---

## 📋 Prérequis

### Serveur Minecraft
- **Spigot/Paper** 1.17+ (Paper fortement recommandé)
- **Java** 17 ou 21
- **RAM** : Minimum 2GB allouée au serveur
- **Système** : Windows, Linux, ou macOS

### Plugins Requis
- **ProtocolLib** 5.1.0+ (pour l'analyse de packets NMS)
   - 📥 [Télécharger ici](https://www.spigotmc.org/resources/protocollib.1997/)

### Plugins Optionnels
- **MySQL/MariaDB** 8.0+ (pour l'historique des violations)
- **LuckPerms** (pour la gestion des permissions)

---

## 🚀 Installation Rapide

### Étape 1 : Téléchargement

```bash
# Télécharger ProtocolLib
wget -P plugins/ https://ci.dmulloy2.net/job/ProtocolLib/lastSuccessfulBuild/artifact/target/ProtocolLib.jar

# Télécharger SentinelAC
# Placez SentinelAC-1.0.0.jar dans plugins/
```

### Étape 2 : Premier Démarrage

1. Démarrez votre serveur
2. Le plugin créera automatiquement `plugins/SentinelAC/config.yml`
3. Arrêtez le serveur

### Étape 3 : Configuration de Base

Éditez `plugins/SentinelAC/config.yml` :

```yaml
# Configuration minimale pour démarrer
database:
  enabled: false  # Désactivé par défaut

auto-updater:
  enabled: true
  auto-download: false

general:
  debug: false
  auto-ban-threshold: 50

packet-analysis:
  enabled: true

machine-learning:
  enabled: true
```

### Étape 4 : Redémarrage

Redémarrez le serveur. Vous devriez voir :

```
[SentinelAC] ========================================
[SentinelAC]    SentinelAC - Anti-Cheat Professionnel
[SentinelAC]    Version: 1.0.0
[SentinelAC] ========================================
[SentinelAC] Checks actifs: 37/37
[SentinelAC] Analyse de packets NMS: ACTIVE
[SentinelAC] Machine Learning: ACTIVE
[SentinelAC] SentinelAC activé avec succès!
```

✅ **Installation terminée !**

---

## 🗄️ Configuration MySQL (Optionnel)

### Pourquoi MySQL ?

- ✅ Historique complet des violations
- ✅ Statistiques long-terme
- ✅ Partage des données entre serveurs
- ✅ Analyse comportementale avancée

### Installation MySQL

#### Sur Ubuntu/Debian
```bash
sudo apt update
sudo apt install mysql-server
sudo systemctl start mysql
sudo mysql_secure_installation
```

#### Sur Windows
1. Téléchargez [MySQL Community Server](https://dev.mysql.com/downloads/mysql/)
2. Installez avec l'assistant
3. Démarrez le service MySQL

### Configuration Base de Données

```bash
# Connexion à MySQL
mysql -u root -p

# Créer la base de données
CREATE DATABASE sentinelac CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Créer l'utilisateur
CREATE USER 'sentinelac'@'localhost' IDENTIFIED BY 'VotreMotDePasse_Secure123';

# Donner les permissions
GRANT ALL PRIVILEGES ON sentinelac.* TO 'sentinelac'@'localhost';
FLUSH PRIVILEGES;

# Vérifier
SHOW DATABASES;
EXIT;
```

### Configuration dans SentinelAC

Éditez `config.yml` :

```yaml
database:
  enabled: true
  host: "localhost"
  port: 3306
  database: "sentinelac"
  username: "sentinelac"
  password: "VotreMotDePasse_Secure123"
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
```

Redémarrez le serveur. Les tables seront créées automatiquement :
- `sentinel_players` - Données joueurs
- `sentinel_violations` - Historique violations
- `sentinel_behavior_patterns` - Patterns ML
- `sentinel_packet_logs` - Logs packets

---

## ⚙️ Configuration du Plugin

### Configuration par Type de Serveur

#### 🏰 Serveur Survie/Semi-RP

```yaml
# Plus tolérant pour éviter faux positifs
movement:
  fly:
    max-violations: 8
  speed:
    max-violations: 10
    max-speed-multiplier: 1.35
    lag-tolerance: 0.25  # Plus tolérant pour le lag

combat:
  killaura:
    max-violations: 6
    max-reach: 3.4
  autoclicker:
    max-violations: 10
    max-cps: 20

general:
  auto-ban-threshold: 60  # Plus tolérant
```

#### ⚔️ Serveur PvP/Faction

```yaml
# Plus strict pour le combat
movement:
  fly:
    max-violations: 5
  speed:
    max-violations: 6
    max-speed-multiplier: 1.25

combat:
  killaura:
    max-violations: 4
    max-reach: 3.2
  autoclicker:
    max-violations: 6
    max-cps: 16
  keepsprint:
    enabled: true
    max-violations: 3

general:
  auto-ban-threshold: 40  # Plus strict
```

#### 🎮 Serveur Mini-Jeux

```yaml
# Focus sur les cheats de mouvement
movement:
  fly:
    enabled: true
    max-violations: 3
  speed:
    enabled: true
    max-violations: 4
  
# Moins strict sur le combat
combat:
  killaura:
    max-violations: 10
  autoclicker:
    enabled: false  # Désactivé en mini-jeux

packet-analysis:
  enabled: true
  checks:
    timer:
      enabled: true
      max-violations: 3
```

### Checks Disponibles

<details>
<summary><b>Mouvement (11 checks)</b></summary>

```yaml
movement:
  fly:
    enabled: true
    max-violations: 8
  speed:
    enabled: true
    max-violations: 10
  nofall:
    enabled: true
    max-violations: 5
  step:
    enabled: true
    max-violations: 8
  jesus:
    enabled: true
    max-violations: 6
  spider:
    enabled: true
    max-violations: 7
  phase:
    enabled: true
    max-violations: 3
  blink:
    enabled: true
    max-violations: 5
  strafe:
    enabled: true
    max-violations: 8
```
</details>

<details>
<summary><b>Combat (8 checks)</b></summary>

```yaml
combat:
  killaura:
    enabled: true
    max-violations: 8
  autoclicker:
    enabled: true
    max-violations: 10
  antiknockback:
    enabled: true
    max-violations: 6
  keepsprint:
    enabled: true
    max-violations: 5
  backtrack:
    enabled: true
    max-violations: 7
```
</details>

<details>
<summary><b>Blocs (5 checks)</b></summary>

```yaml
blocks:
  fastbreak:
    enabled: true
    max-violations: 8
  xray:
    enabled: true
    max-violations: 15
  nuker:
    enabled: true
  scaffold:
    enabled: true
  tower:
    enabled: true
```
</details>

<details>
<summary><b>Interaction (5 checks)</b></summary>

```yaml
interaction:
  reach:
    enabled: true
  interact:
    enabled: true
  fastuse:
    enabled: true
  autoarmor:
    enabled: true
  autopotion:
    enabled: true
```
</details>

<details>
<summary><b>Packets NMS (8 checks)</b></summary>

```yaml
packet-analysis:
  enabled: true
  checks:
    flying-packets:
      enabled: true
      max-packets-per-second: 25
    position-packets:
      enabled: true
      max-position-diff: 10.0
    rotation-packets:
      enabled: true
      max-rotation-speed: 180.0
    badpackets:
      enabled: true
      max-violations: 10
    abilities:
      enabled: true
    noslow:
      enabled: true
    exploit:
      enabled: true
```
</details>

---

### Utilisation

**Notification en jeu :**
```
[SentinelAC] Nouvelle version disponible!
Actuelle: 1.0.0 → Dernière: 1.0.1
Utilisez /ac update pour télécharger
```

**Mise à jour manuelle :**
```
/ac update
```

Les mises à jour sont téléchargées dans `plugins/updates/` et s'appliquent au prochain redémarrage.

---

## ✅ Vérification

### Test de Fonctionnement

1. **Connectez-vous sur le serveur**

2. **Vérifiez les checks actifs**
```
/ac info
```
Devrait afficher :
```
Checks actifs: 37/37
Base de données MySQL: ACTIVE (si configurée)
Analyse de packets NMS: ACTIVE
Machine Learning: ACTIVE
```

3. **Testez une alerte**
   En tant qu'admin avec `anticheat.alerts`, sprintez rapidement ou sautez plusieurs fois.
   Vous devriez recevoir une alerte si les seuils sont dépassés.

4. **Vérifiez les logs**
```bash
tail -f logs/latest.log | grep SentinelAC
```

### Tests Recommandés

- ✅ Mouvement normal → Pas d'alerte
- ✅ Sprint/Saut → Pas d'alerte
- ✅ Interaction coffre → Pas d'alerte
- ✅ Combat normal → Pas d'alerte
- ✅ `/ac violations VotreNom` → Affiche 0 violations

---

## 🎮 Commandes

| Commande | Description | Permission |
|----------|-------------|------------|
| `/ac info` | Infos sur le plugin | `anticheat.admin` |
| `/ac violations <joueur>` | Voir les violations | `anticheat.admin` |
| `/ac reset <joueur> [check]` | Reset violations | `anticheat.admin` |
| `/ac top` | Top violateurs | `anticheat.admin` |
| `/ac alerts` | Toggle alertes | `anticheat.alerts` |
| `/ac update` | Vérifier mises à jour | `anticheat.admin` |
| `/ac reload` | Recharger config | `anticheat.admin` |

### Exemples

```bash
# Voir violations d'un joueur
/ac violations Notch

# Reset toutes les violations
/ac reset Notch

# Reset un check spécifique
/ac reset Notch fly

# Top 10 des tricheurs
/ac top
```

---

## 🔐 Permissions

### Permissions Principales

```yaml
anticheat.admin:      # Accès complet (commandes + alertes)
  default: op
  
anticheat.bypass:     # Bypass TOUS les checks
  default: false
  
anticheat.alerts:     # Recevoir les alertes de violations
  default: op
```

### Configuration LuckPerms

```bash
# Admin complet
/lp group admin permission set anticheat.admin true

# Modérateurs (alertes seulement)
/lp group mod permission set anticheat.alerts true

# Bypass pour staff en test
/lp user PlayerName permission set anticheat.bypass true
```

⚠️ **ATTENTION** : N'accordez `anticheat.bypass` qu'à des personnes de confiance !

---

## 🔧 Dépannage

### Le plugin ne démarre pas

**Erreur : ProtocolLib non trouvé**
```
[SentinelAC] ProtocolLib non trouvé! Analyse de packets désactivée
```
➜ Téléchargez ProtocolLib dans `/plugins/`

**Erreur : MySQL Connection Failed**
```
[SentinelAC] Erreur lors de la connexion MySQL
```
➜ Vérifiez les identifiants dans `config.yml`
➜ Vérifiez que MySQL est démarré : `sudo systemctl status mysql`
➜ Ou désactivez MySQL : `database.enabled: false`

### Trop de Faux Positifs

**Speed/Fly détecté constamment**
```yaml
movement:
  speed:
    lag-tolerance: 0.3  # Augmenter
    max-violations: 15  # Augmenter
  fly:
    max-violations: 12
```

**AutoClicker pour clics normaux**
```yaml
combat:
  autoclicker:
    max-cps: 22        # Augmenter
    max-violations: 15
```

**Interact lors d'interactions normales**
```yaml
# Dans config.yml
interaction:
  interact:
    enabled: false  # Désactiver si trop de problèmes
```

### Logs pour Debug

Activez le mode debug :
```yaml
general:
  debug: true
```

Puis regardez les logs :
```bash
tail -f logs/latest.log | grep "\[DEBUG\]"
```

---

## 📊 Optimisation

### Performance Serveur

**Pour petit serveur (<20 joueurs)**
```yaml
machine-learning:
  enabled: false  # Désactiver ML
  
violations:
  decay-time: 120  # Augmenter
```

**Pour gros serveur (50+ joueurs)**
```yaml
database:
  pool:
    maximum-pool-size: 20  # Augmenter
    
machine-learning:
  training-interval: 60  # Moins fréquent
```

### Réduire les Alertes

```yaml
alerts:
  cooldown: 5  # 5 secondes entre alertes du même joueur
```

### MySQL Optimisation

```sql
-- Nettoyer les vieilles violations (tous les mois)
DELETE FROM sentinel_violations WHERE timestamp < DATE_SUB(NOW(), INTERVAL 3 MONTH);

-- Optimiser les tables
OPTIMIZE TABLE sentinel_violations;
OPTIMIZE TABLE sentinel_packet_logs;
```

---

## 📈 Statistiques MySQL

### Requêtes Utiles

```sql
-- Top 10 tricheurs
SELECT p.username, p.total_violations 
FROM sentinel_players p 
ORDER BY p.total_violations DESC 
LIMIT 10;

-- Violations par check
SELECT check_name, COUNT(*) as count 
FROM sentinel_violations 
GROUP BY check_name 
ORDER BY count DESC;

-- Violations dernières 24h
SELECT COUNT(*) 
FROM sentinel_violations 
WHERE timestamp > DATE_SUB(NOW(), INTERVAL 24 HOUR);

-- Patterns ML suspects
SELECT p.username, bp.pattern_type, bp.confidence 
FROM sentinel_behavior_patterns bp
JOIN sentinel_players p ON bp.player_uuid = p.uuid
WHERE bp.confidence > 0.85
ORDER BY bp.timestamp DESC 
LIMIT 20;
```

---

## 🆘 Support

### Logs à Fournir

En cas de problème, fournissez :

1. **Version serveur**
```
/version
```

2. **Logs SentinelAC**
```bash
grep "SentinelAC" logs/latest.log > sentinelac.log
```

3. **Configuration**
```
plugins/SentinelAC/config.yml
```

4. **Plugins installés**
```
/plugins
```

### Liens Utiles

- 📖 Documentation : [GitHub Wiki](https://github.com/pharos-off/SentinelAC/wiki)
- 🐛 Bug Report : [GitHub Issues](https://github.com/pharos-off/SentinelAC/issues)
- 💬 Discord : [Lien Discord](https://discord.gg/bfpSWxceRV)

---

## 📝 Notes Finales

### Bonnes Pratiques

✅ **Faites** :
- Testez en mode `debug: true` d'abord
- Commencez avec des seuils tolérants
- Sauvegardez régulièrement la BDD
- Donnez `anticheat.bypass` seulement au staff testé

❌ **Ne faites pas** :
- Activer `auto-ban` sans avoir testé
- Donner `anticheat.bypass` à tous les VIP
- Ignorer les faux positifs rapportés
- Utiliser sur versions < 1.17

### Mises à Jour

Vérifiez régulièrement :
```
/ac update
```

Ou activez le téléchargement automatique :
```yaml
auto-updater:
  auto-download: true
```

---

**Votre SentinelAC est maintenant opérationnel ! 🛡️**

Pour toute question : [GitHub Discussions](https://github.com/pharos-off/SentinelAC/discussions)