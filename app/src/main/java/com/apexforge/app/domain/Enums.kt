package com.apexforge.app.domain

/** Exercise categories shown as filters in the library. */
enum class Category(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    LEGS("Legs"),
    QUADS("Quadriceps"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    CORE("Abs & Core"),
    FULL_BODY("Full Body"),
    CARDIO("Cardio"),
    BODYWEIGHT("Bodyweight"),
    MOBILITY("Stretch & Mobility"),
    FUNCTIONAL("Functional");

    companion object {
        fun fromName(name: String?): Category? = entries.firstOrNull { it.name == name }
    }
}

enum class Equipment(val label: String) {
    DUMBBELL("Dumbbells"),
    BARBELL("Barbells"),
    CABLE("Cable machines"),
    MACHINE("Gym machines"),
    SMITH("Smith machine"),
    BANDS("Resistance bands"),
    KETTLEBELL("Kettlebells"),
    PULLUP_BAR("Pull-up bar"),
    BODYWEIGHT("Bodyweight"),
    CARDIO_MACHINE("Cardio machines");

    companion object {
        fun fromName(name: String?): Equipment? = entries.firstOrNull { it.name == name }
    }
}

enum class Difficulty(val label: String, val rank: Int) {
    BEGINNER("Beginner", 0),
    INTERMEDIATE("Intermediate", 1),
    ADVANCED("Advanced", 2);

    companion object {
        fun fromName(name: String?): Difficulty? = entries.firstOrNull { it.name == name }
    }
}

enum class Goal(val label: String, val blurb: String) {
    BUILD_MUSCLE("Build muscle", "Add size with progressive training"),
    GET_STRONGER("Get stronger", "Lift heavier, track every PR"),
    LOSE_FAT("Lose fat", "Stay consistent and burn more"),
    STAY_ACTIVE("Stay active", "Build a healthy weekly habit"),
    ENDURANCE("Build endurance", "Last longer, recover faster");
}

enum class Experience(val label: String, val blurb: String) {
    BEGINNER("Beginner", "New to lifting or returning after a break"),
    INTERMEDIATE("Intermediate", "Training consistently for 1-3 years"),
    ADVANCED("Advanced", "Several years of structured training");
}

enum class UnitSystem(val label: String, val weightUnit: String) {
    METRIC("Metric (kg)", "kg"),
    IMPERIAL("Imperial (lb)", "lb");
}

enum class WaterUnit(val label: String, val short: String) {
    ML("Milliliters", "ml"),
    FL_OZ("Fluid ounces", "fl oz");
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light");
}

enum class RecordType(val label: String) {
    MAX_WEIGHT("Heaviest weight"),
    BEST_E1RM("Estimated 1RM"),
    BEST_SET_VOLUME("Best set volume"),
    MAX_REPS("Most reps (bodyweight)");
}

enum class RangeOption(val label: String, val days: Int?) {
    W4("4W", 28),
    M3("3M", 90),
    M6("6M", 180),
    Y1("1Y", 365),
    ALL("All", null);
}
