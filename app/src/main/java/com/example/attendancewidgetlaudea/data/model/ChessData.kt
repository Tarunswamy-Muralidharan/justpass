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
    val timeControl: Any = "rapid", // bullet, blitz, rapid, classical — Any to handle Firestore type mismatches
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

/** Board color themes — CSS overrides injected into Lichess WebView */
enum class BoardTheme(
    val label: String,
    val lightSquare: String,  // hex color
    val darkSquare: String,
    val preview: Long         // Compose Color bits for preview swatch (dark square)
) {
    CHESS_COM("Chess.com", "#EBECD0", "#779556", 0xFF779556),
    LICHESS("Lichess", "#F0D9B5", "#B58863", 0xFFB58863),
    BLUE("Ice Blue", "#DEE3E6", "#8CA2AD", 0xFF8CA2AD),
    PURPLE("Royal Purple", "#E8DAF5", "#9B72CF", 0xFF9B72CF),
    GREEN("Emerald", "#FFFFDD", "#86A666", 0xFF86A666),
    WOOD("Walnut", "#F0D9B5", "#946F51", 0xFF946F51),
    PINK("Bubblegum", "#F5E0E8", "#D87093", 0xFFD87093),
    GREY("Slate", "#DEE3E6", "#788B97", 0xFF788B97),
    CORAL("Coral", "#FFE4C4", "#CD6839", 0xFFCD6839),
    MIDNIGHT("Midnight", "#C8C8D5", "#5D5D8A", 0xFF5D5D8A);

    /** CSS to inject after Lichess page loads */
    val css: String get() = """
        cg-board{background-image:none!important}
        cg-board square.white,cg-board square.light{background:${lightSquare}!important}
        cg-board square.black,cg-board square.dark{background:${darkSquare}!important}
    """.trimIndent().replace("\n", "")
}

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
