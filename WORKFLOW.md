# Workflow de Piquetage FTTH (Field Engineering Workflow)

Ce document décrit le cycle de vie opérationnel complet du piquetage sur le terrain avec l'application **OptiFTTH**, depuis la reconnaissance préliminaire jusqu'à la livraison du dossier d'ouvrage exécuté (DOE) et l'intégration SIG.

---

## 1. Vue d'Ensemble du Processus Métier

```text
[1. Préparation Bureau d'Études]
           │
           ▼
[2. Reconnaissance Terrain & Calage GPS]
           │
           ▼
[3. Piquetage & Audit des Nœuds d'Infrastructure]
    ├── Poteaux (Charges, Matériau, Éligibilité)
    ├── Chambres (Génie civil, Masques, Saturation)
    ├── Boîtiers BPE (Épissures, Capacité FO)
    ├── PBO (Positionnement, Ports, Photométrie)
    └── Bâtiments (Immeubles / Villas raccordables)
           │
           ▼
[4. Traçage & Qualification des Liaisons Câbles]
    ├── Transport (144 FO)
    ├── Distribution (24/48 FO)
    └── Branchement Client (1/4 FO)
           │
           ▼
[5. Synchronisation Temps Réel & Contrôle Qualité]
           │
           ▼
[6. Export SIG & Clôture du Dossier de Piquetage]
```

---

## 2. Étapes Détaillées du Workflow

### Étape 1 : Préparation & Import du Périmètre (SRO / PM)
- Chargement du secteur de desserte (ex: SRO-04 / PM-12).
- Consultation des calques parcellaires cadastraux et du réseau d'adduction prévisionnel.
- Initialisation du mode de synchronisation temps réel.

### Étape 2 : Relevé Terrain & Positionnement GPS
- Activation de la géolocalisation WGS84 de haute précision.
- Positionnement du technicien sur la carte interactive.
- Piquetage de nouveaux points par appui long ou bouton rapide `Piqueter ici`.

### Étape 3 : Audit Technique par Type d'Équipement
- **Poteau / Appui aérien** :
  - Identification du matériau (Béton, Bois, Métal, Composite).
  - Hauteur sous câble et charge résiduelle admissible (daN).
  - Contrôle d'éligibilité (poteau sain vs poteau à remplacer).
- **Chambre de tirage** :
  - Type de chambre normalisée (L0T, L1T, L2T, K1C, K2C, sous-sol).
  - État des tampons et des parois de masques.
  - Taux d'encombrement (%) et alerte hydrocurage en cas de boue ou d'eau.
- **Boîtier de Protection d'Épissure (BPE)** :
  - Capacité nominale en cassettes et fibres (12 à 144 FO).
  - Nombre d'épissures soudées et lovées.
- **Point de Branchement Optique (PBO)** :
  - Type d'implantation (Aérien, Façade, Intérieur, Chambre).
  - Capacité en ports (4, 8, 12 FO) et raccordements actifs.
  - Mesure de puissance optique au photomètre (-15 à -24 dBm).
- **Immeubles & Pavillons** :
  - Nombre de logements / équivalents logements (EL).
  - Accès aux gaines techniques et présence du PMI.
  - Typologie d'adduction (aérien ou fourreau direct).

### Étape 4 : Raccordement & Métré des Câbles Fibres
- Activation du `Mode Câblage` sur la carte interactive.
- Sélection séquentielle des nœuds amont et aval.
- Calcul automatique de la distance géodésique (formule de Haversine).
- Spécification de la typologie de pose (Aérien, Souterrain, Façade) et du nombre de brins.

### Étape 5 : Synchronisation Temps Réel & Collaboration
- Envoi continu des modifications locales vers le serveur central SIG via WebSocket.
- Mode de persistance locale Room assurant une continuité de travail 100% hors-ligne.
- Réception en temps réel des actions des autres techniciens déployés sur le secteur.

### Étape 6 : Clôture & Export des Livrables
- Revue du tableau de bord de conformité (nœuds validés, anomalies critiques).
- Export des données au format GeoJSON géoréférencé pour injection dans QGIS / ArcGIS.
- Génération du bordereau de piquetage au format CSV pour validation bureau d'études.

---

## 3. Workflow d'Intégration Continue (CI/CD)

Le fichier `.github/workflows/android.yml` assure l'automatisation suivante sur chaque push et pull request :
1. **Compilation du projet** avec Gradle et Java 17.
2. **Exécution des tests unitaires et Robolectric** pour valider la logique métier FTTH et la persistance Room.
3. **Génération de l'APK Debug** et archivage en tant qu'artefact de build téléchargeable.
