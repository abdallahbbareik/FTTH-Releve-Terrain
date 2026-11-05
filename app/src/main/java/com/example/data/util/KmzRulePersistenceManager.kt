package com.example.data.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Propriété ciblée par une règle de qualification.
 */
enum class RuleProperty(val label: String) {
    NAME("Nom KML"),
    TYPE_FONC("Champ TYPE_FONC"),
    REFERENCE("Champ REFERENCE"),
    FOLDER("Dossier KML"),
    DESCRIPTION("Notes & Description"),
    ANY_ATTRIBUTE("Tous les attributs SIG"),
    CONNECTED_CABLES("Nbr Câbles Connectés")
}

/**
 * Opérateur de comparaison pour la règle de qualification.
 */
enum class RuleOperator(val label: String) {
    CONTAINS("Contient"),
    EQUALS("Est égal à"),
    STARTS_WITH("Commence par"),
    REGEX("Expression Régulière (Regex)"),
    MIN_CONNECTED_CABLES("Au moins (nbr câbles)"),
    MAX_CONNECTED_CABLES("Au plus (nbr câbles)")
}

/**
 * Modèle de règle de qualification d'un nœud.
 */
data class QualificationRule(
    val id: String,
    val name: String,
    val category: KmzNodeCategory,
    val targetProperty: RuleProperty,
    val operator: RuleOperator,
    val pattern: String,
    val weight: Int = 50,          // Poids / Priorité de la règle (10 à 100)
    val isEnabled: Boolean = true,
    val isCustomLearned: Boolean = false, // True si créée par l'utilisateur ou apprise
    val description: String = ""
)

/**
 * Suggestion d'apprentissage générée automatiquement par l'analyseur topologique.
 */
data class LearnedRuleSuggestion(
    val rule: QualificationRule,
    val matchingNodeCount: Int,
    val sampleNodeNames: List<String>,
    val explanation: String
)

/**
 * Gestionnaire de persistance des règles de qualification apprises et personnalisées.
 */
object KmzRulePersistenceManager {

    private const val FILE_NAME = "user_qualification_rules.json"

    val DEFAULT_RULES = listOf(
        QualificationRule(
            id = "R_SRO_NAME_START",
            name = "SRO - Préfixe Nom",
            category = KmzNodeCategory.SRO,
            targetProperty = RuleProperty.NAME,
            operator = RuleOperator.STARTS_WITH,
            pattern = "SRO",
            weight = 100,
            isEnabled = true,
            isCustomLearned = false,
            description = "Commence par SRO"
        ),
        QualificationRule(
            id = "R_CHAMBRE_TYPE_FONC",
            name = "Chambre - TYPE_FONC",
            category = KmzNodeCategory.CHAMBRE,
            targetProperty = RuleProperty.TYPE_FONC,
            operator = RuleOperator.CONTAINS,
            pattern = "CHAMBRE",
            weight = 90,
            isEnabled = true,
            isCustomLearned = false,
            description = "Attribut TYPE_FONC contient CHAMBRE"
        ),
        QualificationRule(
            id = "R_APPUI_TYPE_FONC",
            name = "Poteau - APPUI / POTEAU",
            category = KmzNodeCategory.POTEAU,
            targetProperty = RuleProperty.TYPE_FONC,
            operator = RuleOperator.CONTAINS,
            pattern = "APPUI",
            weight = 90,
            isEnabled = true,
            isCustomLearned = false,
            description = "Attribut TYPE_FONC contient APPUI ou POTEAU"
        ),
        QualificationRule(
            id = "R_BOITIER_TYPE_FONC",
            name = "Boîtier - POINT BRANCHEMENT OPTIQUE",
            category = KmzNodeCategory.BOITIER,
            targetProperty = RuleProperty.TYPE_FONC,
            operator = RuleOperator.CONTAINS,
            pattern = "POINT DE BRANCHEMENT OPTIQUE",
            weight = 85,
            isEnabled = true,
            isCustomLearned = false,
            description = "Attribut TYPE_FONC contient POINT DE BRANCHEMENT OPTIQUE"
        ),
        QualificationRule(
            id = "R_BOITIER_PBO_BPE_NAME",
            name = "Boîtier - Mots Clés PBO/BPE/PEC/SPLIT/JOINT",
            category = KmzNodeCategory.BOITIER,
            targetProperty = RuleProperty.NAME,
            operator = RuleOperator.REGEX,
            pattern = "(?i).*(PBO|BPE|PEC|SPLIT|JOINT|BOITIER|BTI).*",
            weight = 80,
            isEnabled = true,
            isCustomLearned = false,
            description = "Nom contient PBO, BPE, PEC, SPLITTER, BTI ou JOINT"
        ),
        QualificationRule(
            id = "R_BOITIER_REF_KEYWORD",
            name = "Boîtier - Référence PBO/BPE",
            category = KmzNodeCategory.BOITIER,
            targetProperty = RuleProperty.REFERENCE,
            operator = RuleOperator.REGEX,
            pattern = "(?i).*(PBO|BPE|BOITIER|EPISSURE).*",
            weight = 75,
            isEnabled = true,
            isCustomLearned = false,
            description = "Référence contient PBO ou BPE"
        )
    )

    /**
     * Charger la liste complète des règles (défaut + personnalisées persistées).
     */
    fun loadRules(context: Context): List<QualificationRule> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return DEFAULT_RULES
        }

        return try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            val customRules = mutableListOf<QualificationRule>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rule = QualificationRule(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    category = KmzNodeCategory.valueOf(obj.getString("category")),
                    targetProperty = RuleProperty.valueOf(obj.getString("targetProperty")),
                    operator = RuleOperator.valueOf(obj.getString("operator")),
                    pattern = obj.getString("pattern"),
                    weight = obj.optInt("weight", 50),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    isCustomLearned = obj.optBoolean("isCustomLearned", true),
                    description = obj.optString("description", "")
                )
                customRules.add(rule)
            }

            if (customRules.isEmpty()) DEFAULT_RULES else customRules
        } catch (_: Exception) {
            DEFAULT_RULES
        }
    }

    /**
     * Sauvegarder la liste des règles dans le stockage local.
     */
    fun saveRules(context: Context, rules: List<QualificationRule>): Boolean {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            val jsonArray = JSONArray()

            for (rule in rules) {
                val obj = JSONObject()
                obj.put("id", rule.id)
                obj.put("name", rule.name)
                obj.put("category", rule.category.name)
                obj.put("targetProperty", rule.targetProperty.name)
                obj.put("operator", rule.operator.name)
                obj.put("pattern", rule.pattern)
                obj.put("weight", rule.weight)
                obj.put("isEnabled", rule.isEnabled)
                obj.put("isCustomLearned", rule.isCustomLearned)
                obj.put("description", rule.description)
                jsonArray.put(obj)
            }

            file.writeText(jsonArray.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Réinitialiser les règles aux règles d'usine par défaut.
     */
    fun resetToDefaults(context: Context): List<QualificationRule> {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
        return DEFAULT_RULES
    }
}
