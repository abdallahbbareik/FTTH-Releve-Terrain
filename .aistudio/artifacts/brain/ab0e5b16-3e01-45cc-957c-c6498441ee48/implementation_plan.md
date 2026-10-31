# Synoptique Graphique de l'Architecture FTTH

Proposition pour représenter l'architecture du réseau FTTH sous forme de synoptique graphique interactif (Arborescence et Graph de câblage optique SRO ➔ Câbles Principaux ➔ Boîtiers / PBO / BPE ➔ Câbles Dérivés).

---

## Synthèse & Choix de Conception

> [!IMPORTANT]
> **Proposition retenue :** Un composant interactif en canvas Compose avec gestion du Zoom & Pan (pincement et glissement), affichant l'arborescence optique de la zone KMZ.
>
> - **Accès UI :** Intégré directement sous la forme d'un nouvel onglet **"Synoptique Graphique"** dans le tableau de bord de classification KMZ (aux côtés de "Nœuds" et "Câbles FO").
> - **Navigation Visuelle :** SRO en position d'origine (racine à gauche/haut), avec les câbles structurés sous forme de lignes colorées étiquetées par leur capacité FO, reliant les différents boîtiers.
> - **Interactivité :** Clic sur n'importe quel boîtier ou câble pour illuminer le parcours d'entrée et de sortie et afficher sa fiche de connectivité détaillée.

---

## 1. Présentation du Synoptique Graphique

### Fonctionnalités Clés :
1. **Arborescence Réseau Automatique :**
   - **Racine :** Le SRO (ou nœud source déterminé).
   - **Niveau 1 - Transport/Poches :** Câbles principaux issus du SRO (ex: CDI_01 288FO).
   - **Niveau 2 - Boîtiers de Distribution / BPE :** Boîtiers d'épissurage et de dérivation.
   - **Niveau 3 - Raccordement / PBO :** Points de branchement optiques terminaux.

2. **Représentation des Nœuds & Câbles :**
   - **Nœuds (Cartes/Pastilles) :**
     - **SRO :** Badge violet distinctif avec icône d'armoire réseau.
     - **Boîtiers / BPE / PBO :** Badges sarcelles/cyan avec le nom et la catégorie calculée.
     - *(Les Chambres et Poteaux traversés peuvent être affichés de manière discrète ou sous forme de puces le long des câbles).*
   - **Câbles (Lignes de liaison) :**
     - Trait plein coloré selon la capacité FO (ex: Bleu pour 144/288 FO, Orange pour 48 FO, Vert pour 24/12 FO).
     - Flèche indiquant le sens d'orientation optique (depuis le SRO).
     - Étiquette intégrée au milieu de la ligne indiquant le **Nom du câble** et sa **Capacité FO**.

3. **Interactions Avancées :**
   - **Zoom & Pan :** Pincement pour zoomer (0.5x à 3.0x) et glissement du doigt pour explorer les grands réseaux.
   - **Mode Focus / Highlight :** Clic sur un boîtier ➔ Surbrillance du câble d'entrée (rouge/jaune) et des câbles de sortie (bleu turquoise), avec estompage du reste du réseau.
   - **Fiche Détails Intégrée :** Panneau inférieur dépliable (Bottom Sheet / Drawer) affichant :
     - Nom et type du boîtier.
     - **Câble Entrée :** Nom, capacité, longueur, supports traversés (Chambres/Poteaux).
     - **Câbles Sortie :** Liste des câbles dérivés et leurs destinations.

---

## 2. Architecture Technique & Flux de Données

```
 ┌─────────────────────────────────────────────────────────┐
 │               Sources de Données KMZ                    │
 │    (ClassifiedKmzNode + ClassifiedCable + Connectivity) │
 └────────────────────────────┬────────────────────────────┘
                              │
                              ▼
 ┌─────────────────────────────────────────────────────────┐
 │               FtthSynopticGraphBuilder                  │
 │ - Calcule la profondeur hiérarchique de chaque boîtier   │
 │ - Positionne les nœuds (coordonnées X, Y sur le Canvas)│
 │ - Relie les liaisons (Cable_Entree & Cables_Sortie)     │
 └────────────────────────────┬────────────────────────────┘
                              │
                              ▼
 ┌─────────────────────────────────────────────────────────┐
 │               FtthSynopticCanvas (View)                 │
 │ - Render Canvas (Line, Path, Text, Badges)              │
 │ - TransformGestures (Scale, Offset)                     │
 │ - Detection de Tap sur Nœuds et Câbles                 │
 └────────────────────────────┬────────────────────────────┘
                              │
                              ▼
 ┌─────────────────────────────────────────────────────────┐
 │            SynopticNodeDetailsSheet (Drawer)            │
 │ - Affichage détaillé du nœud / câble sélectionné       │
 └─────────────────────────────────────────────────────────┘
```

---

## 3. Plan d'Implémentation

### Étape 1 : Modèle de Graph & Algorithme de Layout Hiérarchique
- Créer `FtthSynopticGraphBuilder.kt` dans `com.example.data.util` :
  - Calcule l'arborescence à partir des `BoitierConnectivity` et `ClassifiedCable`.
  - Assigne des niveaux hiérarchiques (Niveau 0 = SRO, Niveau 1 = Boîtiers directement connectés au SRO, Niveau 2+ = Boîtiers enfants).
  - Distribue les coordonnées `(x, y)` sur une grille virtuelle pour éviter les chevauchements de lignes.

### Étape 2 : Composant Canvas Interactif (`FtthSynopticCanvas.kt`)
- Composable Jetpack Compose utilisant `Canvas` et `Modifier.pointerInput` :
  - Support de `detectTransformGestures` pour modifier `scale` et `offset`.
  - Dessin des liaisons par courbes de Bézier (`drawPath`) ou lignes brisées avec flèches de direction optique.
  - Dessin des cartes de nœuds avec bordures, ombres et labels M3.
  - Calcul de collision lors d'un tap (`onTap`) pour détecter le nœud ou le câble touché par l'utilisateur.

### Étape 3 : Intégration dans `KmzNodeClassificationDialog.kt`
- Ajout de l'onglet **"Synoptique (Graph)"** dans la barre d'onglets du composant principal.
- Barre d'outils du synoptique :
  - Boutons de zoom (`+`, `-`, `Réinitialiser la vue`).
  - Champ de recherche rapide d'un boîtier avec centrage automatique du graph.
- Intégration de la BottomSheet/Drawer pour la fiche de détails.

---

## 4. Prochaines Étapes

Une fois ce plan validé, le développement sera exécuté directement dans le projet, suivi de la vérification de compilation via `compile_applet`.
