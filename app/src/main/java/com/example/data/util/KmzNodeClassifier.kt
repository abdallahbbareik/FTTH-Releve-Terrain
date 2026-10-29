package com.example.data.util

import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import org.json.JSONObject
import java.util.Locale

/**
 * Catégorie de classification selon l'étude de qualification des nœuds KMZ/KML.
 */
enum class KmzNodeCategory(
    val label: String,
    val badgeColorHex: String
) {
    SRO("SRO", "#9333EA"),               // Violet
    CHAMBRE("CHAMBRE", "#2563EB"),       // Bleu
    POTEAU("POTEAU", "#D97706"),         // Ambre/Orange (TYPE_FONC = APPUI)
    BOITIER("Boîtier", "#0D9488"),       // Sarcelle/Teal (TYPE_FONC = POINT DE BRANCHEMENT OPTIQU)
    AUTRE("Autre / Non classifié", "#6B7280") // Gris
}

/**
 * Entité représentant un nœud classifié avec ses attributs extraits et la règle appliquée.
 */
data class ClassifiedKmzNode(
    val id: String,
    val name: String,
    val typeFonc: String,
    val reference: String,
    val category: KmzNodeCategory,
    val subType: String,
    val ruleApplied: String,
    val latitude: Double,
    val longitude: Double,
    val sourceFile: String,
    val rawNode: FtthNodeEntity
)

/**
 * Statistiques récapitulatives du tableau de classification.
 */
data class ClassificationStats(
    val totalNodes: Int,
    val sroCount: Int,
    val chambreCount: Int,
    val poteauCount: Int,
    val boitierCount: Int,
    val autreCount: Int
) {
    fun getPercentage(count: Int): Float {
        if (totalNodes == 0) return 0f
        return (count.toFloat() / totalNodes) * 100f
    }
}

object KmzNodeClassifier {

    /**
     * Extraire la valeur d'une propriété dans les kmlProperties (JSON) ou les notes.
     */
    fun extractProperty(node: FtthNodeEntity, keyNames: List<String>): String {
        // 1. Chercher dans kmlProperties JSON
        if (node.kmlProperties.isNotBlank()) {
            try {
                val json = JSONObject(node.kmlProperties)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (keyNames.any { it.equals(k, ignoreCase = true) || k.lowercase(Locale.ROOT).contains(it.lowercase(Locale.ROOT)) }) {
                        val v = json.optString(k, "").trim()
                        if (v.isNotBlank()) return v
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Chercher dans les notes (format "• KEY : VAL" ou "KEY = VAL")
        if (node.notes.isNotBlank()) {
            for (line in node.notes.lines()) {
                val cleanLine = line.removePrefix("•").trim()
                for (key in keyNames) {
                    if (cleanLine.contains(key, ignoreCase = true)) {
                        val parts = if (cleanLine.contains(":")) cleanLine.split(":") else cleanLine.split("=")
                        if (parts.size >= 2) {
                            val v = parts[1].trim()
                            if (v.isNotBlank()) return v
                        }
                    }
                }
            }
        }

        return ""
    }

    /**
     * Classifie un nœud selon les règles strictes mises à jour :
     * 1. Nom commençant par "SRO" → SRO
     * 2. TYPE_FONC = CHAMBRE → CHAMBRE
     * 3. TYPE_FONC = APPUI → POTEAU
     * 4. TYPE_FONC = POINT DE BRANCHEMENT OPTIQU → Boîtier (sans sous-classe)
     * 5. Autre → Autre / Non classifié
     */
    fun classifyNode(node: FtthNodeEntity): ClassifiedKmzNode {
        val nameUpper = node.name.trim().uppercase(Locale.ROOT)
        
        // Extraire TYPE_FONC
        val rawTypeFonc = extractProperty(node, listOf("TYPE_FONC", "type_fonc", "TYPEFONC", "TYPE", "FONCTION")).ifBlank {
            when (node.type) {
                FtthNodeType.POTEAU -> "APPUI"
                FtthNodeType.CHAMBRE -> "CHAMBRE"
                FtthNodeType.SRO -> "SRO"
                FtthNodeType.BOITIER -> "POINT DE BRANCHEMENT OPTIQUE"
                else -> ""
            }
        }
        val typeFoncUpper = rawTypeFonc.trim().uppercase(Locale.ROOT)

        // Extraire REFERENCE
        val rawReference = extractProperty(node, listOf("REFERENCE", "reference", "REF", "Ref", "REF_MAT", "CODE_REF")).ifBlank {
            if (node.type == FtthNodeType.BOITIER && node.boitierType.isNotBlank()) node.boitierType
            else node.name
        }

        var category: KmzNodeCategory
        var subType: String
        var ruleApplied: String

        // RÈGLE 1 : Nom commençant par "SRO"
        val nomProperty = extractProperty(node, listOf("Nom", "NOM", "name", "NAME")).uppercase(Locale.ROOT)
        if (nameUpper.startsWith("SRO") || nomProperty.startsWith("SRO")) {
            category = KmzNodeCategory.SRO
            subType = "SRO"
            ruleApplied = "Nom commence par 'SRO'"
        }
        // RÈGLE 2 : TYPE_FONC = CHAMBRE
        else if (typeFoncUpper == "CHAMBRE" || typeFoncUpper.contains("CHAMBRE")) {
            category = KmzNodeCategory.CHAMBRE
            subType = "Chambre"
            ruleApplied = "TYPE_FONC = CHAMBRE"
        }
        // RÈGLE 3 : TYPE_FONC = APPUI
        else if (typeFoncUpper == "APPUI" || typeFoncUpper.contains("APPUI") || typeFoncUpper.contains("POTEAU")) {
            category = KmzNodeCategory.POTEAU
            subType = "Poteau"
            ruleApplied = "TYPE_FONC = APPUI"
        }
        // RÈGLE 4 : TYPE_FONC = POINT DE BRANCHEMENT OPTIQU (uniquement sans sous classe)
        else if (typeFoncUpper.contains("POINT DE BRANCHEMENT OPTIQU") ||
            typeFoncUpper.contains("POINT DE BRANCHEMENT OPTIQUE") ||
            typeFoncUpper.contains("POINT_DE_BRANCHEMENT_OPTIQUE") ||
            typeFoncUpper.contains("BOITIER")
        ) {
            category = KmzNodeCategory.BOITIER
            subType = "Boîtier"
            ruleApplied = "TYPE_FONC = POINT DE BRANCHEMENT OPTIQU"
        }
        // RÈGLE 5 : Non classifié
        else {
            category = KmzNodeCategory.AUTRE
            subType = node.type.label
            ruleApplied = "Non classifié selon critères spécifiques"
        }

        return ClassifiedKmzNode(
            id = node.id,
            name = node.name.ifBlank { "Nœud sans nom" },
            typeFonc = if (rawTypeFonc.isNotBlank()) rawTypeFonc else "-",
            reference = if (rawReference.isNotBlank()) rawReference else "-",
            category = category,
            subType = subType,
            ruleApplied = ruleApplied,
            latitude = node.latitude,
            longitude = node.longitude,
            sourceFile = node.sourceFile.ifBlank { "Natif" },
            rawNode = node
        )
    }

    /**
     * Calculer les statistiques globales pour une liste de nœuds classifiés.
     */
    fun computeStats(classifiedList: List<ClassifiedKmzNode>): ClassificationStats {
        val total = classifiedList.size
        var sro = 0
        var chambre = 0
        var poteau = 0
        var boitier = 0
        var autre = 0

        for (item in classifiedList) {
            when (item.category) {
                KmzNodeCategory.SRO -> sro++
                KmzNodeCategory.CHAMBRE -> chambre++
                KmzNodeCategory.POTEAU -> poteau++
                KmzNodeCategory.BOITIER -> boitier++
                KmzNodeCategory.AUTRE -> autre++
            }
        }

        return ClassificationStats(
            totalNodes = total,
            sroCount = sro,
            chambreCount = chambre,
            poteauCount = poteau,
            boitierCount = boitier,
            autreCount = autre
        )
    }

    /**
     * Supprimer les géométries dupliquées selon les critères par catégorie :
     * 1. SRO, CHAMBRE et POTEAU : Déduplication par même position (coordonnées GPS).
     * 2. BOÎTIER : Déduplication par même position ET même nom.
     * 3. AUTRE : Aucune déduplication (conservés).
     */
    fun deduplicateByLocation(nodes: List<FtthNodeEntity>): Pair<List<FtthNodeEntity>, Int> {
        if (nodes.isEmpty()) return Pair(emptyList(), 0)

        val uniqueNodes = mutableListOf<FtthNodeEntity>()
        val seenSroLocations = mutableSetOf<String>()
        val seenChambreLocations = mutableSetOf<String>()
        val seenPoteauLocations = mutableSetOf<String>()
        val seenBoitierPosAndName = mutableSetOf<String>()
        var duplicatesCount = 0

        for (node in nodes) {
            val classified = classifyNode(node)
            val locKey = String.format(Locale.US, "%.6f,%.6f", node.latitude, node.longitude)

            when (classified.category) {
                KmzNodeCategory.SRO -> {
                    if (seenSroLocations.contains(locKey)) {
                        duplicatesCount++
                    } else {
                        seenSroLocations.add(locKey)
                        uniqueNodes.add(node)
                    }
                }
                KmzNodeCategory.CHAMBRE -> {
                    if (seenChambreLocations.contains(locKey)) {
                        duplicatesCount++
                    } else {
                        seenChambreLocations.add(locKey)
                        uniqueNodes.add(node)
                    }
                }
                KmzNodeCategory.POTEAU -> {
                    if (seenPoteauLocations.contains(locKey)) {
                        duplicatesCount++
                    } else {
                        seenPoteauLocations.add(locKey)
                        uniqueNodes.add(node)
                    }
                }
                KmzNodeCategory.BOITIER -> {
                    val cleanName = node.name.trim().uppercase(Locale.ROOT)
                    val boitierKey = "$locKey::$cleanName"
                    if (seenBoitierPosAndName.contains(boitierKey)) {
                        duplicatesCount++
                    } else {
                        seenBoitierPosAndName.add(boitierKey)
                        uniqueNodes.add(node)
                    }
                }
                KmzNodeCategory.AUTRE -> {
                    // Les autres entités non classifiées sont toujours conservées
                    uniqueNodes.add(node)
                }
            }
        }

        return Pair(uniqueNodes, duplicatesCount)
    }
}
