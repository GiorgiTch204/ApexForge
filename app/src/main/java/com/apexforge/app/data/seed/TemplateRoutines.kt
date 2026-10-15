package com.apexforge.app.data.seed

/**
 * Built-in starter routines. Items reference exercises by id (the slug of the exercise name).
 * Users can edit or delete any of them; Settings can restore missing ones.
 */
object TemplateRoutines {
    data class Item(val exerciseId: String, val sets: Int, val reps: Int, val restSec: Int)
    data class Def(val key: String, val name: String, val description: String, val items: List<Item>)

    private fun i(exerciseId: String, sets: Int, reps: Int, restSec: Int = 90) = Item(exerciseId, sets, reps, restSec)

    val ALL: List<Def> = listOf(
        Def(
            key = "ppl-push",
            name = "Push Day",
            description = "Push / Pull / Legs: chest, shoulders and triceps.",
            items = listOf(
                i("barbell-bench-press", 4, 8, 120),
                i("incline-dumbbell-press", 3, 10),
                i("seated-dumbbell-shoulder-press", 3, 10),
                i("dumbbell-lateral-raise", 3, 15, 60),
                i("rope-pushdown", 3, 12, 60),
                i("overhead-dumbbell-triceps-extension", 3, 12, 60),
            ),
        ),
        Def(
            key = "ppl-pull",
            name = "Pull Day",
            description = "Push / Pull / Legs: back and biceps.",
            items = listOf(
                i("pull-up", 4, 8, 120),
                i("barbell-row", 4, 8, 120),
                i("seated-cable-row", 3, 12),
                i("face-pull", 3, 15, 60),
                i("barbell-curl", 3, 10, 60),
                i("hammer-curl", 3, 12, 60),
            ),
        ),
        Def(
            key = "leg-day",
            name = "Leg Day",
            description = "Quads, hamstrings, glutes and calves. Also the legs day of Push / Pull / Legs.",
            items = listOf(
                i("barbell-back-squat", 4, 6, 150),
                i("romanian-deadlift", 3, 10, 120),
                i("leg-press", 3, 12),
                i("lying-leg-curl", 3, 12, 60),
                i("leg-extension", 3, 15, 60),
                i("standing-calf-raise-machine", 4, 15, 45),
            ),
        ),
        Def(
            key = "upper",
            name = "Upper Body",
            description = "Upper / Lower split: a balanced push and pull session.",
            items = listOf(
                i("barbell-bench-press", 4, 8, 120),
                i("barbell-row", 4, 8, 120),
                i("overhead-press", 3, 8, 120),
                i("lat-pulldown", 3, 10),
                i("dumbbell-curl", 3, 12, 60),
                i("triceps-pushdown", 3, 12, 60),
            ),
        ),
        Def(
            key = "lower",
            name = "Lower Body",
            description = "Upper / Lower split: squat, hinge and single-leg work.",
            items = listOf(
                i("barbell-back-squat", 4, 6, 150),
                i("romanian-deadlift", 3, 8, 120),
                i("walking-lunge", 3, 12),
                i("lying-leg-curl", 3, 12, 60),
                i("seated-calf-raise", 4, 15, 45),
                i("plank", 3, 45, 45),
            ),
        ),
        Def(
            key = "full-body",
            name = "Full Body",
            description = "One session that trains every major muscle group.",
            items = listOf(
                i("barbell-back-squat", 3, 8, 120),
                i("dumbbell-bench-press", 3, 10),
                i("one-arm-dumbbell-row", 3, 10),
                i("overhead-press", 3, 8, 120),
                i("romanian-deadlift", 3, 10, 120),
                i("plank", 3, 45, 45),
            ),
        ),
        Def(
            key = "chest-triceps",
            name = "Chest & Triceps",
            description = "A focused pressing session.",
            items = listOf(
                i("barbell-bench-press", 4, 8, 120),
                i("incline-barbell-bench-press", 3, 10, 120),
                i("cable-crossover", 3, 12, 60),
                i("close-grip-bench-press", 3, 10),
                i("skull-crusher", 3, 12, 60),
                i("triceps-dip", 3, 10, 90),
            ),
        ),
        Def(
            key = "back-biceps",
            name = "Back & Biceps",
            description = "A focused pulling session.",
            items = listOf(
                i("conventional-deadlift", 3, 5, 180),
                i("lat-pulldown", 3, 10),
                i("chest-supported-dumbbell-row", 3, 10),
                i("seated-cable-row", 3, 12),
                i("ez-bar-curl", 3, 10, 60),
                i("incline-dumbbell-curl", 3, 12, 60),
            ),
        ),
        Def(
            key = "beginner-full-body",
            name = "Beginner Full Body",
            description = "Simple machine and dumbbell moves to learn the basics. Start light.",
            items = listOf(
                i("dumbbell-goblet-squat", 3, 10),
                i("machine-chest-press", 3, 10),
                i("machine-row", 3, 10),
                i("machine-shoulder-press", 2, 10),
                i("glute-bridge", 3, 12, 60),
                i("plank", 2, 30, 45),
            ),
        ),
    )
}
