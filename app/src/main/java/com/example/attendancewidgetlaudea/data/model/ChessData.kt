package com.example.attendancewidgetlaudea.data.model

data class OnlinePlayer(
    val id: String = "",          // Firestore doc ID (hashed roll number)
    val displayName: String = "", // Anonymous name like "SilentKnight"
    val timestamp: Long = 0L     // Last seen timestamp
)

data class ChessChallenge(
    val id: String = "",
    val fromId: String = "",
    val fromName: String = "",
    val toId: String = "",
    val toName: String = "",
    val status: String = "pending", // pending, accepted, declined, expired
    val gameUrl: String = "",       // Lichess game URL for challenger
    val opponentUrl: String = "",   // Lichess game URL for opponent
    val timestamp: Long = 0L
)

// Fun anonymous chess names
val CHESS_NAMES = listOf(
    "SilentKnight", "BishopStorm", "RookRush", "PawnStar", "QueenGambit",
    "KingSlayer", "DarkBishop", "SwiftRook", "GhostPawn", "IronKnight",
    "ShadowQueen", "BlazeKing", "FrostBishop", "ThunderRook", "StealthPawn",
    "CrimsonKnight", "NeonQueen", "PhantomKing", "VortexBishop", "CosmicRook",
    "EmberPawn", "StormKnight", "MysticQueen", "ChaosKing", "ZenBishop",
    "NovaRook", "SpectralPawn", "TitanKnight", "CelestialQueen", "OmegaKing",
    "ArcticBishop", "VolcanicRook", "LunarPawn", "SolarKnight", "AbyssQueen",
    "ZephyrKing", "ObsidianBishop", "MeteorRook", "WraithPawn", "ApexKnight",
    "NebulQueen", "InfernoKing", "GlacialBishop", "CometRook", "ViperPawn",
    "AtlasKnight", "MirageQueen", "TempestKing", "CrystalBishop", "BoltRook"
)
