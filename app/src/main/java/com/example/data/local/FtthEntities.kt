package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FtthNodeType(val label: String, val category: String) {
    POTEAU("Poteau", "Appui aérien"),
    CHAMBRE("Chambre", "Génie civil souterrain"),
    BOITIER_BPE("Boîtier BPE / PA", "Protection d'épissure"),
    PBO("PBO", "Point de Branchement Optique"),
    IMMEUBLE("Immeuble", "Bâtiment collectif"),
    VILLA("Villa / Pavillon", "Habitation individuelle")
}

enum class SurveyStatus(val label: String) {
    PENDING("À auditer"),
    VALIDATED("Conforme / Validé"),
    NON_CONFORMANT("Non conforme / Anomalie"),
    NEEDS_REPLACEMENT("À remplacer / Travaux")
}

enum class SyncState {
    SYNCED,
    PENDING_UPLOAD,
    SYNCING
}

@Entity(tableName = "ftth_nodes")
data class FtthNodeEntity(
    @PrimaryKey
    val id: String,
    val type: FtthNodeType,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val status: SurveyStatus = SurveyStatus.PENDING,
    val address: String = "",
    val referenceCadastre: String = "",
    val notes: String = "",
    val technicianName: String = "Tech-01 (Moi)",
    val photoCount: Int = 0,
    val syncState: SyncState = SyncState.SYNCED,
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Type specific technical attributes
    // For Poteau:
    val poleMaterial: String = "Béton", // Bois, Béton, Métal, Mixte
    val poleHeightMeters: Double = 8.0,
    val poleResidualLoadDan: Int = 300,
    val poleOwner: String = "Orange", // Orange, Enedis, Privé
    
    // For Chambre:
    val chamberType: String = "L2T", // L0T, L1T, L2T, K1C, K2C, Sous-sol
    val chamberCoverState: String = "Bon", // Bon, Fissuré, Bloqué
    val chamberSaturationPercent: Int = 35, // 0 to 100%
    val hasWaterOrMud: Boolean = false,
    
    // For Boîtier BPE:
    val bpeCapacityFO: Int = 72,
    val bpeSplicedCount: Int = 48,
    val bpeModel: String = "BPEO 2",
    
    // For PBO:
    val pboType: String = "Aérien", // Aérien, Façade, Chambre, Intérieur
    val pboCapacityPorts: Int = 8,
    val pboConnectedPorts: Int = 3,
    val opticalPowerDbm: Double = -18.4,
    
    // For Immeuble:
    val buildingDwellings: Int = 16,
    val buildingFloors: Int = 4,
    val hasPMI: Boolean = true, // Point de Mutualisation d'Immeuble
    val conduitAdduction: String = "Souterrain direct",
    
    // For Villa:
    val dropCableType: String = "Aérien 1FO",
    val privateConduitLengthMeters: Double = 22.0
)

@Entity(tableName = "ftth_links")
data class FtthLinkEntity(
    @PrimaryKey
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val cableType: String = "Distribution", // Transport, Distribution, Raccordement
    val installationType: String = "Aérien", // Aérien, Souterrain, Façade
    val capacityFO: Int = 24, // 144, 72, 48, 24, 12, 4
    val lengthMeters: Double = 45.0,
    val status: SurveyStatus = SurveyStatus.VALIDATED,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val technician: String,
    val action: String,
    val nodeId: String,
    val details: String
)
