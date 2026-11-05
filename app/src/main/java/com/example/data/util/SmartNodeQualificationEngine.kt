package com.example.data.util

import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import java.util.Locale

/**
 * Résultat de qualification intelligente d'un nœud avec score de confiance et explication.
 */
data class IntelligentQualificationResult(
    val classifiedNode: ClassifiedKmzNode,
    val confidenceScore: Int,                 // 0 à 100%
    val primaryMatchingRule: QualificationRule?,
    val allMatchingRules: List<QualificationRule>,
    val confidenceBadgeLabel: String,
    val explanation: String
)

/**
 * Moteur d'Apprentissage et Qualification Intelligente des Nœuds FTTH.
 */
object SmartNodeQualificationEngine {

    /**
     * Évaluer et classifier un nœud selon l'ensemble de règles configurées.
     */
    fun classifyNodeIntelligently(
        node: FtthNodeEntity,
        activeRules: List<QualificationRule>,
        connectedCableCount: Int = 0
    ): IntelligentQualificationResult {
        val baseClassified = KmzNodeClassifier.classifyNode(node)
        val enabledRules = activeRules.filter { it.isEnabled }.sortedByDescending { it.weight }

        val categoryScores = mutableMapOf<KmzNodeCategory, Int>()
        val matchingRulesByCategory = mutableMapOf<KmzNodeCategory, MutableList<QualificationRule>>()

        for (rule in enabledRules) {
            if (matchesRule(node, baseClassified, rule, connectedCableCount)) {
                val currentScore = categoryScores.getOrDefault(rule.category, 0)
                categoryScores[rule.category] = currentScore + rule.weight
                
                val ruleList = matchingRulesByCategory.getOrPut(rule.category) { mutableListOf() }
                ruleList.add(rule)
            }
        }

        if (categoryScores.isEmpty()) {
            return IntelligentQualificationResult(
                classifiedNode = baseClassified.copy(
                    category = KmzNodeCategory.AUTRE,
                    ruleApplied = "Aucune règle correspondante (Poids 0)"
                ),
                confidenceScore = 30,
                primaryMatchingRule = null,
                allMatchingRules = emptyList(),
                confidenceBadgeLabel = "Non classifié (30%)",
                explanation = "Nœud non reconnu par les règles actuelles. Soumis aux suggestions d'apprentissage."
            )
        }

        // Trouver la catégorie gagnante avec le meilleur score cumulé
        val bestCategoryEntry = categoryScores.maxByOrNull { it.value }!!
        val winningCategory = bestCategoryEntry.key
        val totalWinningScore = bestCategoryEntry.value
        val winningRules = matchingRulesByCategory[winningCategory] ?: emptyList()
        val primaryRule = winningRules.firstOrNull()

        // Calcul du pourcentage de confiance (plafonné à 99% si < 100)
        val calculatedConfidence = when {
            totalWinningScore >= 120 -> 99
            totalWinningScore >= 90 -> 95
            totalWinningScore >= 70 -> 88
            totalWinningScore >= 50 -> 78
            else -> 60
        }

        val badgeLabel = when {
            calculatedConfidence >= 90 -> "Élevée ($calculatedConfidence%)"
            calculatedConfidence >= 70 -> "Moyenne ($calculatedConfidence%)"
            else -> "Faible ($calculatedConfidence%)"
        }

        val explanation = if (primaryRule != null) {
            "Catégorie ${winningCategory.label} appliquée via '${primaryRule.name}' (${primaryRule.targetProperty.label} ${primaryRule.operator.label} '${primaryRule.pattern}'). Poids: ${primaryRule.weight}."
        } else {
            "Catégorie ${winningCategory.label} par défaut."
        }

        // Mettre à jour la référence et le sous-type si nécessaire
        val updatedClassified = baseClassified.copy(
            category = winningCategory,
            subType = when (winningCategory) {
                KmzNodeCategory.SRO -> "SRO"
                KmzNodeCategory.CHAMBRE -> "Chambre"
                KmzNodeCategory.POTEAU -> "Poteau"
                KmzNodeCategory.BOITIER -> "Boîtier"
                KmzNodeCategory.AUTRE -> baseClassified.subType
            },
            ruleApplied = primaryRule?.name ?: baseClassified.ruleApplied
        )

        return IntelligentQualificationResult(
            classifiedNode = updatedClassified,
            confidenceScore = calculatedConfidence,
            primaryMatchingRule = primaryRule,
            allMatchingRules = winningRules,
            confidenceBadgeLabel = badgeLabel,
            explanation = explanation
        )
    }

    /**
     * Vérifier si un nœud correspond à une règle spécifique.
     */
    private fun matchesRule(
        node: FtthNodeEntity,
        classified: ClassifiedKmzNode,
        rule: QualificationRule,
        connectedCableCount: Int
    ): Boolean {
        if (rule.targetProperty == RuleProperty.CONNECTED_CABLES) {
            val countVal = rule.pattern.trim().toIntOrNull() ?: 0
            return when (rule.operator) {
                RuleOperator.MIN_CONNECTED_CABLES -> connectedCableCount >= countVal
                RuleOperator.MAX_CONNECTED_CABLES -> connectedCableCount <= countVal
                else -> false
            }
        }

        val textToTest = when (rule.targetProperty) {
            RuleProperty.NAME -> node.name
            RuleProperty.TYPE_FONC -> classified.typeFonc
            RuleProperty.REFERENCE -> classified.reference
            RuleProperty.FOLDER -> node.folderName
            RuleProperty.DESCRIPTION -> node.notes
            RuleProperty.ANY_ATTRIBUTE -> "${node.name} ${classified.typeFonc} ${classified.reference} ${node.folderName} ${node.notes} ${node.kmlProperties}"
            RuleProperty.CONNECTED_CABLES -> ""
        }.uppercase(Locale.ROOT)

        val patternUpper = rule.pattern.uppercase(Locale.ROOT)

        return when (rule.operator) {
            RuleOperator.CONTAINS -> textToTest.contains(patternUpper)
            RuleOperator.EQUALS -> textToTest.trim() == patternUpper.trim()
            RuleOperator.STARTS_WITH -> textToTest.trim().startsWith(patternUpper.trim())
            RuleOperator.REGEX -> {
                try {
                    Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(textToTest)
                } catch (_: Exception) {
                    false
                }
            }
            RuleOperator.MIN_CONNECTED_CABLES, RuleOperator.MAX_CONNECTED_CABLES -> false
        }
    }

    /**
     * Module d'apprentissage automatique ("Apprentissage Intelligent") :
     * Analyse l'ensemble du dataset et génère des suggestions de règles de qualification
     * pour les nœuds actuellement non classifiés ou à faible confiance.
     */
    fun analyzeDatasetAndSuggestRules(
        nodes: List<FtthNodeEntity>,
        activeRules: List<QualificationRule>
    ): List<LearnedRuleSuggestion> {
        val suggestions = mutableListOf<LearnedRuleSuggestion>()
        if (nodes.isEmpty()) return suggestions

        // 1. Identifier les nœuds "Autre" ou non classifiés
        val unclassifiedNodes = nodes.filter { node ->
            val res = classifyNodeIntelligently(node, activeRules)
            res.classifiedNode.category == KmzNodeCategory.AUTRE || res.confidenceScore < 70
        }

        if (unclassifiedNodes.isEmpty()) return suggestions

        // 2. Extraire les motifs récurrents dans TYPE_FONC
        val typeFoncGroups = unclassifiedNodes.groupBy { node ->
            KmzNodeClassifier.extractProperty(node, listOf("TYPE_FONC", "type_fonc", "TYPE", "FONCTION")).trim().uppercase(Locale.ROOT)
        }.filter { it.key.isNotBlank() && it.value.size >= 2 }

        for ((typeFonc, group) in typeFoncGroups) {
            val inferredCategory = inferCategoryFromToken(typeFonc)
            val sampleNames = group.take(3).map { it.name }
            val ruleId = "LEARNED_TF_${typeFonc.replace(" ", "_")}"

            // Éviter de suggérer si une règle identique existe déjà
            if (activeRules.none { it.pattern.equals(typeFonc, ignoreCase = true) }) {
                val newRule = QualificationRule(
                    id = ruleId,
                    name = "Appris : TYPE_FONC = $typeFonc",
                    category = inferredCategory,
                    targetProperty = RuleProperty.TYPE_FONC,
                    operator = RuleOperator.CONTAINS,
                    pattern = typeFonc,
                    weight = 80,
                    isEnabled = true,
                    isCustomLearned = true,
                    description = "Règle apprise automatiquement pour $typeFonc"
                )

                suggestions.add(
                    LearnedRuleSuggestion(
                        rule = newRule,
                        matchingNodeCount = group.size,
                        sampleNodeNames = sampleNames,
                        explanation = "Motif récurrent détecté dans TYPE_FONC ('$typeFonc') sur ${group.size} nœuds."
                    )
                )
            }
        }

        // 3. Extraire les motifs récurrents dans le dossier KML
        val folderGroups = unclassifiedNodes.groupBy { node ->
            node.folderName.trim().uppercase(Locale.ROOT)
        }.filter { it.key.isNotBlank() && it.value.size >= 3 }

        for ((folderName, group) in folderGroups) {
            val inferredCategory = inferCategoryFromToken(folderName)
            val sampleNames = group.take(3).map { it.name }
            val ruleId = "LEARNED_FLD_${folderName.replace(" ", "_")}"

            if (activeRules.none { it.targetProperty == RuleProperty.FOLDER && it.pattern.equals(folderName, ignoreCase = true) }) {
                val newRule = QualificationRule(
                    id = ruleId,
                    name = "Appris : Dossier KML = $folderName",
                    category = inferredCategory,
                    targetProperty = RuleProperty.FOLDER,
                    operator = RuleOperator.CONTAINS,
                    pattern = folderName,
                    weight = 75,
                    isEnabled = true,
                    isCustomLearned = true,
                    description = "Règle apprise par analyse de l'arborescence KML"
                )

                suggestions.add(
                    LearnedRuleSuggestion(
                        rule = newRule,
                        matchingNodeCount = group.size,
                        sampleNodeNames = sampleNames,
                        explanation = "Dossier KML récurrent '$folderName' contenant ${group.size} nœuds."
                    )
                )
            }
        }

        return suggestions
    }

    /**
     * Déduire la catégorie la plus probable d'un mot-clé brut.
     */
    private fun inferCategoryFromToken(token: String): KmzNodeCategory {
        val upper = token.uppercase(Locale.ROOT)
        return when {
            upper.contains("SRO") || upper.contains("ARMOIRE") || upper.contains("PM") -> KmzNodeCategory.SRO
            upper.contains("CHAMBRE") || upper.contains("CH_") || upper.contains("MANHOLE") -> KmzNodeCategory.CHAMBRE
            upper.contains("APPUI") || upper.contains("POTEAU") || upper.contains("POT") || upper.contains("POLE") -> KmzNodeCategory.POTEAU
            upper.contains("BRANCHEMENT") || upper.contains("PBO") || upper.contains("BPE") || upper.contains("BOITIER") || upper.contains("JOINT") -> KmzNodeCategory.BOITIER
            else -> KmzNodeCategory.BOITIER
        }
    }

    /**
     * Générer une règle d'apprentissage personnalisée lorsqu'un utilisateur corrige
     * manuellement la catégorie d'un nœud dans l'interface ("Apprendre de la correction").
     */
    fun createLearnedRuleFromCorrection(
        node: FtthNodeEntity,
        correctedCategory: KmzNodeCategory
    ): QualificationRule {
        val typeFonc = KmzNodeClassifier.extractProperty(node, listOf("TYPE_FONC", "type_fonc", "TYPE"))
        val reference = KmzNodeClassifier.extractProperty(node, listOf("REFERENCE", "REF"))

        val (targetProp, operator, pattern, ruleName) = when {
            typeFonc.isNotBlank() -> Quadruple(
                RuleProperty.TYPE_FONC,
                RuleOperator.CONTAINS,
                typeFonc,
                "Règle Apprise : TYPE_FONC = $typeFonc"
            )
            reference.isNotBlank() && reference != "-" -> Quadruple(
                RuleProperty.REFERENCE,
                RuleOperator.CONTAINS,
                reference,
                "Règle Apprise : Ref = $reference"
            )
            node.folderName.isNotBlank() -> Quadruple(
                RuleProperty.FOLDER,
                RuleOperator.CONTAINS,
                node.folderName,
                "Règle Apprise : Dossier = ${node.folderName}"
            )
            else -> Quadruple(
                RuleProperty.NAME,
                RuleOperator.STARTS_WITH,
                node.name.take(4),
                "Règle Apprise : Nom = ${node.name.take(4)}"
            )
        }

        return QualificationRule(
            id = "USER_LEARNED_${System.currentTimeMillis()}",
            name = ruleName,
            category = correctedCategory,
            targetProperty = targetProp,
            operator = operator,
            pattern = pattern,
            weight = 95,
            isEnabled = true,
            isCustomLearned = true,
            description = "Créée par correction manuelle utilisateur sur le nœud '${node.name}'"
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
