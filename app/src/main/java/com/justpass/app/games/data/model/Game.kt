package com.justpass.app.games.data.model

import androidx.compose.ui.graphics.Color
import com.justpass.app.games.ui.theme.BBButter
import com.justpass.app.games.ui.theme.BBHot
import com.justpass.app.games.ui.theme.BBLilac
import com.justpass.app.games.ui.theme.BBLime
import com.justpass.app.games.ui.theme.BBMint

/**
 * Catalog of all mini-games. Each entry maps to an in-app screen route
 * and a Supabase `game_id` used for leaderboard storage.
 *
 * `lowerIsBetter` flips the leaderboard sort + the comparison used when
 * deciding whether a new attempt beats the previous best.
 */
enum class Category { REFLEX, MEMORY, SKILL }

enum class Game(
    val id: String,
    val title: String,
    val shortName: String,
    val code: String,
    val tagline: String,
    val accent: Color,
    val lowerIsBetter: Boolean,
    val unit: String,
    val category: Category,
    val instructions: List<String>
) {
    REACTION_TIME(
        id = "reaction_time",
        title = "Reaction Time",
        shortName = "REACTION",
        code = "01 / Reflex",
        tagline = "Lights out, F1 style",
        accent = BBHot,
        category = Category.REFLEX,
        lowerIsBetter = true,
        unit = "ms",
        instructions = listOf(
            "Tap once to arm the start sequence.",
            "5 red lights light up one by one.",
            "After a random pause, all lights turn green — that's lights out.",
            "Tap as fast as you can when they go green.",
            "Tapping while lights are red = jump start. Try again."
        )
    ),
    SEQUENCE_MEMORY(
        id = "sequence_memory",
        title = "Sequence Memory",
        shortName = "SEQUENCE",
        code = "02 / Memory",
        tagline = "Repeat the lit pattern",
        accent = BBLime,
        lowerIsBetter = false,
        unit = "lvl",
        category = Category.MEMORY,
        instructions = listOf(
            "Watch the cells light up in order.",
            "Tap them back in the same order.",
            "Each round adds one more cell.",
            "One wrong tap ends the round.",
            "Score = highest level reached."
        )
    ),
    AIM_TRAINER(
        id = "aim_trainer",
        title = "Aim Trainer",
        shortName = "AIM",
        code = "03 / Precision",
        tagline = "Hit 30 targets fast",
        accent = BBHot,
        lowerIsBetter = true,
        unit = "ms",
        category = Category.REFLEX,
        instructions = listOf(
            "Tap the circle as soon as it appears.",
            "It jumps to a new spot every hit.",
            "Hit 30 in a row.",
            "Score = average ms per target. Lower is better."
        )
    ),
    NUMBER_MEMORY(
        id = "number_memory",
        title = "Number Memory",
        shortName = "NUMBER",
        code = "04 / Recall",
        tagline = "Recall the digits",
        accent = BBLilac,
        lowerIsBetter = false,
        unit = "digits",
        category = Category.MEMORY,
        instructions = listOf(
            "A number flashes for a few seconds.",
            "Type it back exactly when prompted.",
            "Each correct answer adds a digit.",
            "One wrong answer ends the run.",
            "Score = longest digit count remembered."
        )
    ),
    VERBAL_MEMORY(
        id = "verbal_memory",
        title = "Verbal Memory",
        shortName = "VERBAL",
        code = "05 / Lexicon",
        tagline = "Have you seen this word?",
        accent = BBButter,
        lowerIsBetter = false,
        unit = "score",
        category = Category.MEMORY,
        instructions = listOf(
            "A word appears.",
            "Tap NEW if you haven't seen it before.",
            "Tap SEEN if it's already shown up.",
            "Wrong answer or 3 strikes ends the run.",
            "Score = words answered correctly."
        )
    ),
    VISUAL_MEMORY(
        id = "visual_memory",
        title = "Visual Memory",
        shortName = "VISUAL",
        code = "06 / Pattern",
        tagline = "Remember the squares",
        accent = BBMint,
        lowerIsBetter = false,
        unit = "lvl",
        category = Category.MEMORY,
        instructions = listOf(
            "Some squares flash on a grid.",
            "After they fade, tap every flashed square.",
            "Each level adds more squares + bigger grid.",
            "3 wrong picks ends the round.",
            "Score = highest level reached."
        )
    ),
    TYPING(
        id = "typing",
        title = "Typing",
        shortName = "TYPING",
        code = "07 / Tempo",
        tagline = "Words per minute",
        accent = BBLime,
        lowerIsBetter = false,
        unit = "wpm",
        category = Category.SKILL,
        instructions = listOf(
            "Type the shown passage as fast and accurate as you can.",
            "Timer starts on the first keystroke.",
            "Score = words per minute on completion.",
            "Backspace allowed to fix mistakes."
        )
    ),
    CHIMP_TEST(
        id = "chimp_test",
        title = "Chimp Test",
        shortName = "CHIMP",
        code = "08 / Spatial",
        tagline = "Numbers in order, from memory",
        accent = BBHot,
        lowerIsBetter = false,
        unit = "lvl",
        category = Category.SKILL,
        instructions = listOf(
            "Numbered tiles appear on the grid.",
            "Tap them in ascending order.",
            "After your first tap, the rest blank out.",
            "Each level adds one more number.",
            "3 strikes ends the run.",
            "Score = highest level reached."
        )
    );

    companion object {
        fun fromId(id: String): Game? = entries.firstOrNull { it.id == id }
    }
}
