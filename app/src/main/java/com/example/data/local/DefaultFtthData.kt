package com.example.data.local

object DefaultFtthData {
    // Base coordinate center (Parisian suburban telecom sector)
    const val CENTER_LAT = 48.8566
    const val CENTER_LON = 2.3522

    fun getDefaultNodes(): List<FtthNodeEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            FtthNodeEntity(
                id = "POT-101",
                type = FtthNodeType.POTEAU,
                name = "Appui Enedis P-101 (Béton 8m)",
                latitude = 48.8566,
                longitude = 2.3522,
                status = SurveyStatus.VALIDATED,
                address = "14 Avenue des Lilas",
                referenceCadastre = "AC-042",
                notes = "Poteau vérifié conforme. Tirant en place, hauteur sous câble respectée (4.80m).",
                poleMaterial = "Béton",
                poleHeightMeters = 8.0,
                poleResidualLoadDan = 380,
                poleOwner = "Enedis (Convention Partagée)",
                photoCount = 2,
                updatedAt = now - 3600000
            ),
            FtthNodeEntity(
                id = "CH-201",
                type = FtthNodeType.CHAMBRE,
                name = "Chambre L2T Trottoir CH-201",
                latitude = 48.8572,
                longitude = 2.3514,
                status = SurveyStatus.VALIDATED,
                address = "Face au 18 Avenue des Lilas",
                referenceCadastre = "AC-045",
                notes = "Tampons hydrauliques conformes. Masque propre, 2 alvéoles libres vers CH-202.",
                chamberType = "L2T",
                chamberCoverState = "Bon état",
                chamberSaturationPercent = 40,
                hasWaterOrMud = false,
                photoCount = 3,
                updatedAt = now - 7200000
            ),
            FtthNodeEntity(
                id = "BPE-301",
                type = FtthNodeType.BOITIER_BPE,
                name = "Boîtier BPE 72FO (CH-201)",
                latitude = 48.8572,
                longitude = 2.3514,
                status = SurveyStatus.VALIDATED,
                address = "Dans chambre CH-201",
                referenceCadastre = "AC-045",
                notes = "Manchon thermo-rétractable neuf. Cassettes 1 à 4 lovées et étiquetées.",
                bpeCapacityFO = 72,
                bpeSplicedCount = 48,
                bpeModel = "BPEO Taille 2",
                photoCount = 1,
                updatedAt = now - 5400000
            ),
            FtthNodeEntity(
                id = "POT-102",
                type = FtthNodeType.POTEAU,
                name = "Poteau Bois P-102 (À renforcer)",
                latitude = 48.8560,
                longitude = 2.3533,
                status = SurveyStatus.NEEDS_REPLACEMENT,
                address = "28 Avenue des Lilas",
                referenceCadastre = "AC-050",
                notes = "Fissure longitudinale sur le fût bois + pourriture au pied. Remplacement obligatoire par poteau composite ou béton.",
                poleMaterial = "Bois",
                poleHeightMeters = 7.0,
                poleResidualLoadDan = 110,
                poleOwner = "Orange",
                photoCount = 4,
                updatedAt = now - 1800000
            ),
            FtthNodeEntity(
                id = "PBO-401",
                type = FtthNodeType.PBO,
                name = "PBO-01 Aérien 8FO (sur P-101)",
                latitude = 48.85665,
                longitude = 2.35225,
                status = SurveyStatus.VALIDATED,
                address = "14 Avenue des Lilas (Poteau P-101)",
                referenceCadastre = "AC-042",
                notes = "PBO étanche IP68. 4 pigtails connectés, puissance optique -18.4 dBm vérifiée au photomètre.",
                pboType = "Aérien",
                pboCapacityPorts = 8,
                pboConnectedPorts = 4,
                opticalPowerDbm = -18.4,
                photoCount = 2,
                updatedAt = now - 900000
            ),
            FtthNodeEntity(
                id = "PBO-402",
                type = FtthNodeType.PBO,
                name = "PBO-02 Façade 12FO",
                latitude = 48.8577,
                longitude = 2.3527,
                status = SurveyStatus.PENDING,
                address = "6 Rue de la Fontaine",
                referenceCadastre = "BD-012",
                notes = "À auditer : vérifier l'accord du propriétaire pour fixation murale et pose d'un capot discret.",
                pboType = "Façade",
                pboCapacityPorts = 12,
                pboConnectedPorts = 2,
                opticalPowerDbm = -19.1,
                photoCount = 0,
                updatedAt = now - 100000
            ),
            FtthNodeEntity(
                id = "IMM-501",
                type = FtthNodeType.IMMEUBLE,
                name = "Résidence Le Belvédère (Bât. A)",
                latitude = 48.8579,
                longitude = 2.3531,
                status = SurveyStatus.VALIDATED,
                address = "10 Rue de la Fontaine",
                referenceCadastre = "BD-015",
                notes = "Gaine technique visitée. PMI (Point de Mutualisation d'Immeuble) installé au RDC avec 32 positions.",
                buildingDwellings = 24,
                buildingFloors = 5,
                hasPMI = true,
                conduitAdduction = "Fourreau souterrain Ø45",
                photoCount = 3,
                updatedAt = now - 2500000
            ),
            FtthNodeEntity(
                id = "VIL-601",
                type = FtthNodeType.VILLA,
                name = "Villa Les Magnolias",
                latitude = 48.8558,
                longitude = 2.3540,
                status = SurveyStatus.VALIDATED,
                address = "2 Allée des Roses",
                referenceCadastre = "AC-088",
                notes = "Raccordement possible depuis PBO-401 par traverse aérienne. Longueur de câble estimée : 35m.",
                dropCableType = "Aérien 1FO renforcé",
                privateConduitLengthMeters = 18.0,
                photoCount = 1,
                updatedAt = now - 8000000
            ),
            FtthNodeEntity(
                id = "VIL-602",
                type = FtthNodeType.VILLA,
                name = "Pavillon Moderne n°7",
                latitude = 48.8554,
                longitude = 2.3529,
                status = SurveyStatus.PENDING,
                address = "7 Allée des Roses",
                referenceCadastre = "AC-092",
                notes = "Fourreau privatif bouché au niveau de la bordure trottoir. Prévoir aiguillage test ou passage en façade.",
                dropCableType = "Fourreau souterrain privatif",
                privateConduitLengthMeters = 32.0,
                photoCount = 1,
                updatedAt = now - 300000
            ),
            FtthNodeEntity(
                id = "CH-202",
                type = FtthNodeType.CHAMBRE,
                name = "Chambre K2C Carrefour CH-202",
                latitude = 48.8584,
                longitude = 2.3508,
                status = SurveyStatus.NON_CONFORMANT,
                address = "Angle Rue de la Fontaine & Av. Lilas",
                referenceCadastre = "AD-001",
                notes = "Anomalie critique : 40cm d'eau stagnante + masque effondré sur tubulure nord. Hydrocurage requis.",
                chamberType = "K2C Chaussée",
                chamberCoverState = "Tampon fissuré",
                chamberSaturationPercent = 85,
                hasWaterOrMud = true,
                photoCount = 3,
                updatedAt = now - 600000
            )
        )
    }

    fun getDefaultLinks(): List<FtthLinkEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            FtthLinkEntity(
                id = "LNK-01",
                fromNodeId = "CH-201",
                toNodeId = "POT-101",
                cableType = "Distribution",
                installationType = "Souterrain / Montée d'aéro-souterrain",
                capacityFO = 48,
                lengthMeters = 68.0,
                status = SurveyStatus.VALIDATED,
                updatedAt = now - 7000000
            ),
            FtthLinkEntity(
                id = "LNK-02",
                fromNodeId = "POT-101",
                toNodeId = "PBO-401",
                cableType = "Distribution",
                installationType = "Aérien sur appui",
                capacityFO = 8,
                lengthMeters = 4.0,
                status = SurveyStatus.VALIDATED,
                updatedAt = now - 6000000
            ),
            FtthLinkEntity(
                id = "LNK-03",
                fromNodeId = "POT-101",
                toNodeId = "POT-102",
                cableType = "Distribution",
                installationType = "Aérien pleine portée",
                capacityFO = 24,
                lengthMeters = 84.0,
                status = SurveyStatus.NEEDS_REPLACEMENT,
                updatedAt = now - 5000000
            ),
            FtthLinkEntity(
                id = "LNK-04",
                fromNodeId = "CH-201",
                toNodeId = "CH-202",
                cableType = "Transport",
                installationType = "Souterrain conduite PVC",
                capacityFO = 144,
                lengthMeters = 145.0,
                status = SurveyStatus.NON_CONFORMANT,
                updatedAt = now - 4000000
            ),
            FtthLinkEntity(
                id = "LNK-05",
                fromNodeId = "CH-201",
                toNodeId = "IMM-501",
                cableType = "Distribution",
                installationType = "Souterrain adduction",
                capacityFO = 48,
                lengthMeters = 110.0,
                status = SurveyStatus.VALIDATED,
                updatedAt = now - 3000000
            ),
            FtthLinkEntity(
                id = "LNK-06",
                fromNodeId = "PBO-401",
                toNodeId = "VIL-601",
                cableType = "Branchement Client",
                installationType = "Aérien traverse",
                capacityFO = 1,
                lengthMeters = 38.0,
                status = SurveyStatus.VALIDATED,
                updatedAt = now - 2000000
            ),
            FtthLinkEntity(
                id = "LNK-07",
                fromNodeId = "POT-102",
                toNodeId = "VIL-602",
                cableType = "Branchement Client",
                installationType = "Aérien puis façade",
                capacityFO = 1,
                lengthMeters = 44.0,
                status = SurveyStatus.PENDING,
                updatedAt = now - 1000000
            ),
            FtthLinkEntity(
                id = "LNK-08",
                fromNodeId = "IMM-501",
                toNodeId = "PBO-402",
                cableType = "Distribution",
                installationType = "Façade chemin de câble",
                capacityFO = 12,
                lengthMeters = 32.0,
                status = SurveyStatus.PENDING,
                updatedAt = now - 500000
            )
        )
    }

    fun getDefaultLogs(): List<SyncLogEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            SyncLogEntity(
                timestamp = now - 3600000,
                technician = "Tech-01 (Moi)",
                action = "VALIDATION",
                nodeId = "POT-101",
                details = "Appui Enedis validé avec calcul de charge"
            ),
            SyncLogEntity(
                timestamp = now - 2400000,
                technician = "Tech-02 (Karim M.)",
                action = "ANOMALIE",
                nodeId = "CH-202",
                details = "Chambre inondée, demande d'hydrocurage envoyée"
            ),
            SyncLogEntity(
                timestamp = now - 1200000,
                technician = "Tech-03 (Sophie L.)",
                action = "AJOUT_NOEUD",
                nodeId = "PBO-402",
                details = "Création du PBO façade 12FO pour raccordement immeuble"
            ),
            SyncLogEntity(
                timestamp = now - 300000,
                technician = "Système Cloud",
                action = "SYNC_REUSSIE",
                nodeId = "SECTEUR-FTTH-01",
                details = "Synchronisation temps réel avec le référentiel SIG centralisé"
            )
        )
    }
}
