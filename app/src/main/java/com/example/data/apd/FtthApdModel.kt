package com.example.data.apd

/**
 * Modèles de données pour le moteur d'extraction et d'analyse FTTH APD
 * Porté directement du script Python Processing QGIS APD v1.
 */

enum class ApdObjectType(val label: String) {
    SRO("SRO"),
    CHAMBRE("CHAMBRE"),
    POTEAU("POTEAU"),
    BPE("BPE"),
    BPE_PBO("BPE-PBO"),
    PBO("PBO"),
    AUTRE("AUTRE")
}

data class ApdRawPlacemark(
    val name: String,
    val folder: String,
    val geometryType: String, // "Point" ou "LineString"
    val coordinatesStr: String,
    val attributes: Map<String, String>
)

data class ApdPoint(
    val id: Int,
    val name: String,
    val folder: String,
    val latitude: Double,
    val longitude: Double,
    val objectType: ApdObjectType,
    val typeFonc: String = "",
    val typeStruc: String = "",
    val attributes: Map<String, String> = emptyMap(),
    var cableEntre: String = "",
    var cablesSortie: String = "",
    var nbCablesSorties: Int = 0,
    var supportName: String = "",
    var supportType: String = ""
) {
    val coordinatesStr: String
        get() = "$longitude,$latitude,0"
}

data class ApdCable(
    val nom: String,
    var typeStruc: String = "",
    var originName: String = "",
    var supportsTraverses: String = "",
    var nbChTraversees: Int = 0,
    var nbPotTraversees: Int = 0,
    var longueurMeters: Double = 0.0,
    var logTotalMeters: Double = 0.0,
    val coordinatesStr: String = "",
    val segments: List<List<Pair<Double, Double>>> = emptyList()
)

data class ApdAnomaly(
    val type: String,
    val targetName: String,
    val distanceMeters: Double? = null,
    val detail: String
)

data class ApdCableChain(
    val joints: List<Int>,
    val segmentSupports: List<Set<String>>,
    val segmentLengths: List<Double>
)

data class ApdAnalysisResult(
    val allPoints: List<ApdPoint>,
    val bpePboList: List<ApdPoint>,
    val supportsList: List<ApdPoint>,
    val cablesList: List<ApdCable>,
    val anomalies: List<ApdAnomaly>,
    val roots: List<String>,
    val childrenOf: Map<String, List<String>>,
    val attachOf: Map<String, Int?>,
    val chains: Map<String, ApdCableChain>,
    val resolvedGroups: Map<String, Map<Int, List<String>>>
)
