package com.apexforge.app.domain

import kotlin.math.roundToInt

const val KG_PER_LB = 0.45359237
const val ML_PER_FL_OZ = 29.5735295625
const val CM_PER_INCH = 2.54

/** All weights are stored in kilograms. These helpers only convert for display and input. */
fun UnitSystem.fromKg(kg: Double): Double = if (this == UnitSystem.METRIC) kg else kg / KG_PER_LB

fun UnitSystem.toKg(value: Double): Double = if (this == UnitSystem.METRIC) value else value * KG_PER_LB

/** Weight rounded to one decimal, the precision the input fields accept. */
fun UnitSystem.displayWeight(kg: Double): Double = (fromKg(kg) * 10.0).roundToInt() / 10.0

fun WaterUnit.fromMl(ml: Int): Double = if (this == WaterUnit.ML) ml.toDouble() else ml / ML_PER_FL_OZ

fun WaterUnit.toMl(value: Double): Int =
    if (this == WaterUnit.ML) value.roundToInt() else (value * ML_PER_FL_OZ).roundToInt()

fun cmToInches(cm: Double): Double = cm / CM_PER_INCH

fun inchesToCm(inches: Double): Double = inches * CM_PER_INCH
