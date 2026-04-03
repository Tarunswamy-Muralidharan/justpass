package com.example.attendancewidgetlaudea.data.model

data class OnlinePlayer(
    val id: String = "",
    val displayName: String = "",
    val timestamp: Long = 0L,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val gamesPlayed: Int = 0,
    val isFriend: Boolean = false
) {
    val rating: Int get() = 1000 + (wins * 15) - (losses * 10) + (draws * 3)
}

data class ChessChallenge(
    val id: String = "",
    val fromId: String = "",
    val fromName: String = "",
    val toId: String = "",
    val toName: String = "",
    val status: String = "pending", // pending, accepted, declined, expired
    val gameUrl: String = "",       // URL for challenger
    val opponentUrl: String = "",   // URL for opponent (acceptor)
    val lichessGameId: String = "", // for tracking result
    val fromColor: String = "",     // "white" or "black" — challenger's color
    val resultChecked: Boolean = false,
    val timeControl: String = "rapid", // bullet, blitz, rapid, classical
    val timestamp: Long = 0L
)

/** Time control presets matching Lichess/chess.com conventions */
enum class TimeControl(
    val label: String,
    val icon: String,
    val clockLimit: Int,   // seconds
    val increment: Int,    // seconds per move
    val description: String
) {
    BULLET("Bullet", "\u26A1", 60, 0, "1 min"),
    BULLET_1_1("Bullet", "\u26A1", 60, 1, "1+1"),
    BLITZ_3("Blitz", "\uD83D\uDD25", 180, 0, "3 min"),
    BLITZ_3_2("Blitz", "\uD83D\uDD25", 180, 2, "3+2"),
    BLITZ_5("Blitz", "\uD83D\uDD25", 300, 0, "5 min"),
    BLITZ_5_3("Blitz", "\uD83D\uDD25", 300, 3, "5+3"),
    RAPID_10("Rapid", "\u23F1\uFE0F", 600, 0, "10 min"),
    RAPID_10_5("Rapid", "\u23F1\uFE0F", 600, 5, "10+5"),
    RAPID_15_10("Rapid", "\u23F1\uFE0F", 900, 10, "15+10"),
    CLASSICAL("Classical", "\uD83C\uDFF0", 1800, 0, "30 min");

    val paramString: String get() = "clock.limit=$clockLimit&clock.increment=$increment&rated=false"
}

data class ChessProfile(
    val id: String = "",
    val displayName: String = "",
    val nickname: String = "",      // custom nickname chosen by user
    val nameMode: String = "random", // "random", "custom", "real"
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val gamesPlayed: Int = 0,
    val lastOnline: Long = 0L
) {
    val rating: Int get() = 1000 + (wins * 15) - (losses * 10) + (draws * 3)
    val visibleName: String get() = when (nameMode) {
        "custom" -> nickname.ifBlank { displayName }
        "real" -> displayName
        else -> nickname // random name stored in nickname field
    }
}

data class FriendRequest(
    val id: String = "",
    val fromId: String = "",
    val fromName: String = "",
    val toId: String = "",
    val toName: String = "",
    val status: String = "pending" // pending, accepted, declined
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
