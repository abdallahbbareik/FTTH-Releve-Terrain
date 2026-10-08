package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FtthNodeType(val label: String, val category: String) {
    POTEAU("Poteau", "Appui aérien / Façade"),
    CHAMBRE("Chambre", "Génie civil souterrain"),
    BOITIER("Boîtier", "BPE / PBO / PB / Autre"),
    SRO("SRO", "Sous-Répartiteur Optique"),
    IMMEUBLE("Immeuble", "Bâtiment collectif"),
    VILLA("Villa / Pavillon", "Habitation individuelle")
}

enum class NodeStatus(val label: String) {
    EXISTANT("Existant"),
    EN_CONSTRUCTION("En construction"),
    A_POSER("À poser"),
    A_REMPLACER("À remplacer"),
    A_DEPOSER("À déposer")
}

enum class NodeConformity(val label: String) {
    CONFORME("Conforme"),
    NON_CONFORME("Non conforme")
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
    val status: NodeStatus = NodeStatus.EXISTANT,
    val etat: NodeConformity = NodeConformity.CONFORME,
    val address: String = "",
    val hasBoitierFtth: Boolean = false, // Non, Oui (sauf Boîtier, Villa et SRO)
    val notes: String = "", // Commentaire
    val technicianName: String = "Tech-01 (Moi)",
    val photoCount: Int = 0,
    val photos: List<String> = emptyList(), // Chemins absolus des photos réelles stockées sur l'appareil
    val syncState: SyncState = SyncState.SYNCED,
    val updatedAt: Long = System.currentTimeMillis(),

    // Spécifique Poteau
    val poleNature: String = "bois", // bois, métal, béton, composite, façade
    val poleHeight: Int = 8, // 7, 8, 9, 10

    // Spécifique Chambre
    val chamberType: String = "L2T", // L0T, L1T, L2T, L3T, L4T, 1/2 L4T, K1C, K2C, Autre

    // Spécifique Boîtier
    val boitierType: String = "PBO", // BPE, PBO, PRI, Autre
    val isSaturated: Boolean = false, // Saturé : Non, Oui
    val boitierSupport: String = "Poteau", // Poteau, Chambre, Façade, Sous-Sol
    val hasSplitter: Boolean = false, // Présence Splitter (Coupleur optique) : Oui/Non
    val splitterType: String = "1:8", // Type de splitter (1:2, 1:4, 1:8, 1:16, 1:32, 1:64)

    // Spécifique SRO
    val sroType: String = "armoire de rue", // armoire de rue, local
    val sroCapacity: String = "360 FO", // Capacité (ex: 144 FO, 360 FO, 720 FO, 1000 FO)

    // Spécifique Immeuble
    val buildingFloors: Int = 4, // Nombre étages
    val buildingDwellings: Int = 16, // Nombre logements
    val buildingBoitiersEtage: Int = 4, // Nombre boîtiers d'étage
    val hasLocalTechnique: Boolean = true, // Local technique : Oui/Non
    val hasGaineMontante: Boolean = true, // Gaine montante : oui/non
    val syndicAuthorization: String = "Accord obtenu", // Accord obtenu, En attente, Refusé
    val syndicContact: String = "", // Contact syndic (facultatif)
    val buildingConnectionMode: String = "souterrain", // façade, souterrain, aérien

    // Spécifique Villa/Pavillon
    val villaConnectionMode: String = "aérien" // aérien, souterrain, façade
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
    val status: NodeStatus = NodeStatus.EXISTANT,
    val updatedAt: Long = System.currentTimeMillis(),
    val associatedTrackId: String = "" // Id du tracé / infra_lineaire (GC / Façade / Aérien) emprunté pour cheminement non-linéaire
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

enum class TrackType(val label: String) {
    GC("GC"),
    AERIEN("Aérien"),
    FACADE("Façade")
}

@Entity(tableName = "gps_tracks")
data class GpsTrackEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String = "GC", // GC, Aérien, Façade
    val etat: String = "Conforme", // Conforme, Non conforme
    val conduitAudit: String = "Libres", // Libres, Occupés, Bouchés
    val conduitType: String = "PEHD", // PEHD, PVC, Autre
    val conduitCount: Int = 1,
    val conduitDiameters: String = "Ø 40",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val pointCount: Int = 0,
    val isActive: Boolean = true
)

@Entity(
    tableName = "gps_track_points",
    indices = [androidx.room.Index(value = ["trackId"])]
)
data class GpsTrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val speed: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)

