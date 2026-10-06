package com.example.data.local

object DefaultFtthData {
    const val CENTER_LAT = 34.0
    const val CENTER_LON = 9.5375

    fun getDefaultNodes(): List<FtthNodeEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            // SRO
            FtthNodeEntity(
                id = "SRO-01",
                type = FtthNodeType.SRO,
                name = "SRO S-04 (Sous-Répartiteur Optique)",
                latitude = 48.8576,
                longitude = 2.3506,
                status = NodeStatus.EXISTANT,
                etat = NodeConformity.CONFORME,
                address = "2 Avenue des Lilas, 75020 Paris",
                hasBoitierFtth = false,
                notes = "SRO tête de réseau, 3 modules de brassage installés",
                sroType = "armoire de rue",
                sroCapacity = "720 FO",
                photoCount = 2,
                updatedAt = now - 10000000
            ),
            // Poteau 1
            FtthNodeEntity(
                id = "POT-101",
                type = FtthNodeType.POTEAU,
                name = "Poteau Appui P-101",
                latitude = 48.8566,
                longitude = 2.3522,
                status = NodeStatus.EXISTANT,
                etat = NodeConformity.CONFORME,
                address = "14 Avenue des Lilas, 75020 Paris",
                hasBoitierFtth = true,
                notes = "Appui en bon état avec ferrure et pince d'ancrage en tête",
                poleNature = "béton",
                poleHeight = 8,
                photoCount = 2,
                updatedAt = now - 3600000
            ),
            // Chambre 1
            FtthNodeEntity(
                id = "CH-201",
                type = FtthNodeType.CHAMBRE,
                name = "Chambre Trottoir CH-201",
                latitude = 48.8572,
                longitude = 2.3514,
                status = NodeStatus.EXISTANT,
                etat = NodeConformity.CONFORME,
                address = "18 Avenue des Lilas, 75020 Paris",
                hasBoitierFtth = true,
                notes = "Chambre propre, tampons hydrauliques faciles d'accès",
                chamberType = "L2T",
                photoCount = 3,
                updatedAt = now - 7200000
            ),
            // Boîtier BPE
            FtthNodeEntity(
                id = "BPE-301",
                type = FtthNodeType.BOITIER,
                name = "Boîtier BPE 72FO (CH-201)",
                latitude = 48.85722,
                longitude = 2.35145,
                status = NodeStatus.EXISTANT,
                etat = NodeConformity.CONFORME,
                address = "Dans chambre CH-201, Avenue des Lilas",
                hasBoitierFtth = true,
                notes = "Manchon thermo BPEO Taille 2, 4 cassettes d'épissures",
                boitierType = "BPE",
                isSaturated = false,
                boitierSupport = "Chambre",
                photoCount = 1,
                updatedAt = now - 5400000
            ),
            // Poteau 2 (À remplacer / Non conforme)
            FtthNodeEntity(
                id = "POT-102",
                type = FtthNodeType.POTEAU,
                name = "Poteau Bois P-102 (Endommagé)",
                latitude = 48.8560,
                longitude = 2.3533,
                status = NodeStatus.A_REMPLACER,
                etat = NodeConformity.NON_CONFORME,
                address = "28 Avenue des Lilas, 75020 Paris",
                hasBoitierFtth = false,
                notes = "Fissure longitudinale sur le fût bois. Remplacement requis avant tirage",
                poleNature = "bois",
                poleHeight = 7,
                photoCount = 4,
                updatedAt = now - 1800000
            ),
            // Boîtier PBO
            FtthNodeEntity(
                id = "PBO-401",
                type = FtthNodeType.BOITIER,
                name = "PBO-01 Aérien (P-101)",
                latitude = 48.85665,
                longitude = 2.35225,
                status = NodeStatus.EXISTANT,
                etat = NodeConformity.CONFORME,
                address = "14 Avenue des Lilas (sur P-101)",
                hasBoitierFtth = true,
                notes = "PBO étanche 8FO avec 4 pigtails raccordés",
                boitierType = "PBO",
                isSaturated = false,
                boitierSupport = "Poteau",
                photoCount = 2,
                updatedAt = now - 900000
            ),
            // Boîtier Façade (À poser)
            FtthNodeEntity(
                id = "PBO-402",
                type = FtthNodeType.BOITIER,
                name = "PBO-02 Façade Immeuble",
                latitude = 48.8577,
                longitude = 2.3527,
                status = NodeStatus.A_POSER,
                etat = NodeConformity.CONFORME,
                address = "6 Rue de la Fontaine, 75020 Paris",
                hasBoitierFtth = true,
                notes = "À poser sur façade avec fixation chevillée discrète",
                boitierType = "PBO",
                isSaturated = false,
                boitierSupport = "Façade",
                photoCount = 0,
                updatedAt = now - 100000
            ),
            // Immeuble
            FtthNodeEntity(
                id = "IMM-501",
                type = FtthNodeType.IMMEUBLE,
                name = "Résidence Le Belvédère (Bât. A)",
                latitude = 48.8579,
                longitude = 2.3531,
                status = NodeStatus.EXISTANT,
                etat = NodeConformity.CONFORME,
                address = "10 Rue de la Fontaine, 75020 Paris",
                hasBoitierFtth = true,
                notes = "PMI installé au sous-sol. Gaine visitée et accessible",
                buildingFloors = 5,
                buildingDwellings = 24,
                hasLocalTechnique = true,
                hasGaineMontante = true,
                syndicAuthorization = "Accord obtenu",
                syndicContact = "Syndic Foncia Fontaine (01 42 00 11 22)",
                buildingConnectionMode = "souterrain",
                photoCount = 3,
                updatedAt = now - 2500000
            ),
            // Villa 1
            FtthNodeEntity(
                id = "VIL-601",
                type = FtthNodeType.VILLA,
                name = "Villa Les Magnolias",
                latitude = 48.8558,
                longitude = 2.3540,
                status = NodeStatus.EXISTANT,
                etat = NodeConformity.CONFORME,
                address = "2 Allée des Roses, 75020 Paris",
                hasBoitierFtth = false,
                notes = "Raccordement aéro-façade direct depuis PBO-401",
                villaConnectionMode = "aérien",
                photoCount = 1,
                updatedAt = now - 8000000
            ),
            // Villa 2 (À poser / Raccordement souterrain)
            FtthNodeEntity(
                id = "VIL-602",
                type = FtthNodeType.VILLA,
                name = "Pavillon Moderne n°7",
                latitude = 48.8554,
                longitude = 2.3529,
                status = NodeStatus.A_POSER,
                etat = NodeConformity.CONFORME,
                address = "7 Allée des Roses, 75020 Paris",
                hasBoitierFtth = false,
                notes = "Fourreau privatif débouchant en bordure de propriété",
                villaConnectionMode = "souterrain",
                photoCount = 1,
                updatedAt = now - 300000
            ),
            // Chambre 2 (Non conforme / À déposer ou réfection)
            FtthNodeEntity(
                id = "CH-202",
                type = FtthNodeType.CHAMBRE,
                name = "Chambre Carrefour CH-202",
                latitude = 48.8584,
                longitude = 2.3508,
                status = NodeStatus.A_REMPLACER,
                etat = NodeConformity.NON_CONFORME,
                address = "Angle Rue de la Fontaine & Av. Lilas",
                hasBoitierFtth = false,
                notes = "Masque endommagé, présence d'eau stagnante",
                chamberType = "K2C",
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
                fromNodeId = "SRO-01",
                toNodeId = "CH-201",
                cableType = "Transport",
                installationType = "Souterrain conduite",
                capacityFO = 144,
                lengthMeters = 85.0,
                status = NodeStatus.EXISTANT,
                updatedAt = now - 8000000
            ),
            FtthLinkEntity(
                id = "LNK-02",
                fromNodeId = "CH-201",
                toNodeId = "POT-101",
                cableType = "Distribution",
                installationType = "Souterrain / Aéro-souterrain",
                capacityFO = 48,
                lengthMeters = 68.0,
                status = NodeStatus.EXISTANT,
                updatedAt = now - 7000000
            ),
            FtthLinkEntity(
                id = "LNK-03",
                fromNodeId = "POT-101",
                toNodeId = "PBO-401",
                cableType = "Distribution",
                installationType = "Aérien sur appui",
                capacityFO = 8,
                lengthMeters = 4.0,
                status = NodeStatus.EXISTANT,
                updatedAt = now - 6000000
            ),
            FtthLinkEntity(
                id = "LNK-04",
                fromNodeId = "POT-101",
                toNodeId = "POT-102",
                cableType = "Distribution",
                installationType = "Aérien pleine portée",
                capacityFO = 24,
                lengthMeters = 84.0,
                status = NodeStatus.A_REMPLACER,
                updatedAt = now - 5000000
            ),
            FtthLinkEntity(
                id = "LNK-05",
                fromNodeId = "CH-201",
                toNodeId = "CH-202",
                cableType = "Transport",
                installationType = "Souterrain",
                capacityFO = 144,
                lengthMeters = 145.0,
                status = NodeStatus.A_REMPLACER,
                updatedAt = now - 4000000
            ),
            FtthLinkEntity(
                id = "LNK-06",
                fromNodeId = "CH-201",
                toNodeId = "IMM-501",
                cableType = "Distribution",
                installationType = "Souterrain adduction",
                capacityFO = 48,
                lengthMeters = 110.0,
                status = NodeStatus.EXISTANT,
                updatedAt = now - 3000000
            ),
            FtthLinkEntity(
                id = "LNK-07",
                fromNodeId = "PBO-401",
                toNodeId = "VIL-601",
                cableType = "Branchement",
                installationType = "Aérien traverse",
                capacityFO = 1,
                lengthMeters = 38.0,
                status = NodeStatus.EXISTANT,
                updatedAt = now - 2000000
            ),
            FtthLinkEntity(
                id = "LNK-08",
                fromNodeId = "POT-102",
                toNodeId = "VIL-602",
                cableType = "Branchement",
                installationType = "Aérien puis façade",
                capacityFO = 1,
                lengthMeters = 44.0,
                status = NodeStatus.A_POSER,
                updatedAt = now - 1000000
            ),
            FtthLinkEntity(
                id = "LNK-09",
                fromNodeId = "IMM-501",
                toNodeId = "PBO-402",
                cableType = "Distribution",
                installationType = "Façade chemin de câble",
                capacityFO = 12,
                lengthMeters = 32.0,
                status = NodeStatus.A_POSER,
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
                nodeId = "SRO-01",
                details = "SRO 720 FO audité conforme avec raccordement des têtes"
            ),
            SyncLogEntity(
                timestamp = now - 2400000,
                technician = "Tech-02 (Karim M.)",
                action = "ANOMALIE",
                nodeId = "CH-202",
                details = "Chambre déclarée non conforme : fissure et masque écrasé"
            ),
            SyncLogEntity(
                timestamp = now - 1200000,
                technician = "Tech-03 (Sophie L.)",
                action = "AJOUT_NOEUD",
                nodeId = "PBO-402",
                details = "Création du PBO façade pour raccordement immeuble"
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
