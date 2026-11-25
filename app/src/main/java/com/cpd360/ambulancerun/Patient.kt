package com.cpd360.ambulancerun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

// Patient kinds
const val WELL = 0
const val CARDIAC = 1
const val ARREST = 2
const val RESP = 3
const val DIABETIC = 4
const val TRAUMA = 5
const val NEURO = 6
// New patients, each with a treatment of its own
const val ASTHMA = 7
const val ALLERGY = 8
const val BURN = 9
const val OVERDOSE = 10
const val HEAT = 11
const val SEIZURE = 12
const val COLD = 13

// The level each new patient first shows up, and the treatment that helps them
val newKindLevel = mapOf(ASTHMA to 3, ALLERGY to 4, BURN to 5, OVERDOSE to 6, HEAT to 6, SEIZURE to 7, COLD to 8)
val kindTreat = mapOf(ASTHMA to 5, ALLERGY to 6, BURN to 7, OVERDOSE to 8, HEAT to 9, SEIZURE to 10, COLD to 11)

// Tests: 0 vitals (BP and pulse), 1 pulse ox, 2 glucose, 3 stroke, 4 ECG, 5 trauma exam
val testNames = listOf("Vitals", "Pulse ox", "Glucose", "Stroke", "ECG", "Trauma")
val testLevels = intArrayOf(2, 3, 4, 7, 8, 5)

// Treatments: 0 oxygen, 1 glucose, 2 splint / stop bleeding, 3 CPR, 4 shock,
// then one for each new patient: 5 inhaler, 6 EpiPen, 7 burn dressing, 8 Narcan, 9 cooling,
// 10 seizure care, 11 warm blankets
val treatNames = listOf(
    "Oxygen", "Glucose", "Splint / Stop bleeding", "CPR", "Shock",
    "Inhaler", "EpiPen", "Burn dressing", "Narcan", "Cooling", "Seizure care", "Warm blankets"
)
val treatLevels = intArrayOf(3, 4, 5, 6, 8, 3, 4, 5, 6, 6, 7, 8)
// Short names for the round treatment buttons
fun treatShort(k: Int): String = treatNames[k].substringBefore(" /")

// crash >= 0 makes a car crash patient for a call with more than one patient:
// 2 is badly hurt (bleeding), 1 has a broken bone, 0 is only a little sore.
// addr gives every patient on a crash call the same address.
class Patient(val level: Int, rnd: Random, crash: Int = -1, addr: String? = null) {
    val kind: Int
    val female: Boolean = rnd.nextBoolean()
    val age: Int = 35 + rnd.nextInt(61)
    val skin: Int = rnd.nextInt(5)
    val hairStyle: Int = rnd.nextInt(3)
    val hairColor: Int = rnd.nextInt(4)
    val shirt: Int = rnd.nextInt(5)
    val pants: Int = rnd.nextInt(3)
    val address: String
    val title: String
    val complaint: String

    var sys by mutableIntStateOf(0)
    var dia by mutableIntStateOf(0)
    var hr by mutableIntStateOf(0)
    var ox by mutableIntStateOf(0)
    var glu by mutableIntStateOf(1)
    var stroke = false
    var heartAttack = false
    var injury = 0

    var hrNote by mutableStateOf("")
    var oxNote by mutableStateOf("")
    var gluNote by mutableStateOf("")
    var helped by mutableStateOf(false)
    var dead by mutableStateOf(false)

    val tested = mutableStateListOf(false, false, false, false, false, false)
    val treated = mutableStateListOf(*Array(treatNames.size) { false })
    // How bad a crash patient is (see crash above), or -1
    val severity = crash

    val startOx: Int
    val startGlu: Int
    val startHr: Int
    private val r = rnd

    init {
        val kinds = ArrayList<Int>()
        kinds.add(WELL)
        if (level >= 2) kinds.add(CARDIAC)
        if (level >= 3) kinds.add(RESP)
        if (level >= 4) kinds.add(DIABETIC)
        if (level >= 5) kinds.add(TRAUMA)
        if (level >= 6) kinds.add(ARREST)
        if (level >= 7) kinds.add(NEURO)
        for ((k, lv) in newKindLevel) if (level >= lv) kinds.add(k)
        val newest = newestKind(level)
        kind = if (crash >= 0) TRAUMA
            else if (level >= 2 && rnd.nextInt(100) < 35) (if (rnd.nextBoolean()) newest else newestNew(level, rnd) ?: newest)
            else kinds[rnd.nextInt(kinds.size)]

        var bp = 1
        var hc = 1
        var oc = 0
        var t = ""
        var c = ""
        when (kind) {
            CARDIAC -> {
                do {
                    bp = rnd.nextInt(3)
                    hc = rnd.nextInt(3)
                } while (bp == 1 && hc == 1)
                heartAttack = rnd.nextBoolean()
                t = "Chest pain"
                c = listOf("My chest feels so tight!", "My heart is pounding!", "I feel faint and my chest hurts.")[rnd.nextInt(3)]
            }
            ARREST -> {
                t = "Not breathing"
                c = "(not responding)"
            }
            RESP -> {
                val q = rnd.nextInt(4)
                oc = if (q < 2) 2 else if (q == 2) 1 else 0
                t = "Trouble breathing"
                c = listOf("I can't catch my breath!", "I have a bad cough and a fever.")[rnd.nextInt(2)]
            }
            DIABETIC -> {
                glu = if (rnd.nextBoolean()) 0 else 2
                t = "Feels weak"
                c = if (glu == 0) "I feel shaky and sweaty..." else "I'm so thirsty and tired."
            }
            TRAUMA -> if (crash >= 0) {
                t = "Car crash"
                when (crash) {
                    2 -> { injury = 1; bp = 0; hc = 2; c = "(not answering, breathing fast)" }
                    1 -> { injury = 2; c = "My wrist! It's bent wrong!" }
                    else -> { injury = 0; c = "I'm okay, my neck's just a bit sore." }
                }
            } else {
                when (rnd.nextInt(4)) {
                    0 -> { injury = 1; t = "Bad cut"; c = "I cut my arm and it won't stop bleeding!" }
                    1 -> { injury = 0; t = "Minor injury"; c = "I bumped my head. It's just sore." }
                    2 -> { injury = 1 + rnd.nextInt(2); t = "Car crash"; c = if (injury == 1) "I'm bleeding from the glass!" else "My leg! I think it's broken!" }
                    else -> { injury = 2; t = "Fall"; c = "I fell and my wrist is bent wrong!" }
                }
            }
            ASTHMA -> {
                oc = 1 + rnd.nextInt(2)
                hc = 2
                t = "Asthma attack"
                c = "I can't... breathe out... *wheeze*"
            }
            ALLERGY -> {
                bp = 0
                hc = 2
                oc = 1
                t = "Allergic reaction"
                c = listOf("My throat feels tight! I ate shrimp.", "I got stung by a bee and my lips are swelling!")[rnd.nextInt(2)]
            }
            BURN -> {
                hc = 2
                t = "Burn"
                c = listOf("I burned my arm on the stove!", "Hot oil splashed on my hand!")[rnd.nextInt(2)]
            }
            OVERDOSE -> {
                oc = 2
                hc = 0
                t = "Overdose"
                c = "(very sleepy, hard to wake)"
            }
            HEAT -> {
                bp = 0
                hc = 2
                t = "Heat stroke"
                c = "So hot... I feel confused..."
            }
            SEIZURE -> {
                hc = 2
                t = "Seizure"
                c = "(shook all over, now very sleepy)"
            }
            COLD -> {
                hc = 0
                bp = 0
                t = "Hypothermia"
                c = "So... c-c-cold..."
            }
            NEURO -> {
                stroke = rnd.nextBoolean()
                t = "Dizzy"
                c = "I feel dizzy and strange."
            }
            else -> {
                t = "Not feeling well"
                c = if (level == 1) "I don't feel good." else listOf(
                    "I don't feel good.", "I've been throwing up.", "I have diarrhea.",
                    "It burns when I pee.", "I think I have the flu."
                )[rnd.nextInt(5)]
            }
        }
        if (kind == ARREST) {
            sys = 0
            dia = 0
            hr = 0
            ox = 0
        } else {
            sys = when (bp) { 0 -> rr(70, 99); 2 -> rr(160, 200); else -> rr(100, 159) }
            dia = when (bp) { 0 -> rr(20, 57); 2 -> rr(81, 100); else -> rr(58, 80) }
            hr = when (hc) { 0 -> rr(50, 79); 2 -> rr(121, 180); else -> rr(80, 120) }
            ox = when (oc) { 2 -> rr(85, 89); 1 -> rr(90, 92); else -> rr(93, 99) }
        }
        startOx = ox
        startGlu = glu
        startHr = hr
        title = t
        complaint = c
        val streets = listOf("Elm St", "Oak Ave", "Maple Dr", "Pine St", "Cedar Ln", "Main St", "Lake Rd", "Park Ave", "Hill St", "River Rd")
        val a = "" + (10 + rnd.nextInt(990)) + " " + streets[rnd.nextInt(streets.size)]
        address = addr ?: a
    }

    private fun rr(a: Int, b: Int): Int = a + r.nextInt(b - a + 1)

    fun bubble(): String = when {
        dead -> "(not responding)"
        helped -> "That helps. I feel better!"
        kind == ARREST && treated[3] -> "Ugh... what happened?"
        else -> complaint
    }

    fun test(k: Int) {
        tested[k] = true
    }

    fun treat(k: Int) {
        if (dead) return
        treated[k] = true
        when (k) {
            0 -> if (ox in 1..92) {
                ox = rr(94, 98)
                oxNote = "after oxygen"
                helped = true
            }
            1 -> if (glu == 0) {
                glu = 1
                gluNote = "after glucose"
                helped = true
            }
            2 -> if (injury > 0) helped = true
            3 -> if (kind == ARREST && hr == 0) {
                if (r.nextInt(100) < 30) {
                    hr = rr(80, 110)
                    sys = rr(90, 99)
                    dia = rr(50, 57)
                    ox = rr(90, 92)
                    hrNote = "after CPR"
                } else {
                    dead = true
                }
            }
            4 -> if (kind == CARDIAC && (hr < 80 || hr > 120)) {
                hr = rr(85, 110)
                hrNote = "after shock"
                helped = true
            }
            else -> if (kindTreat[kind] == k) {
                // The new patients' own treatment brings their numbers back toward normal
                helped = true
                if (hr > 120) hr = rr(95, 115)
                if (hr in 1..59) hr = rr(62, 80)
                if (sys in 1..99) {
                    sys = rr(105, 125)
                    dia = rr(65, 80)
                }
                if (ox in 1..92) {
                    ox = rr(93, 96)
                    oxNote = "after " + treatNames[k].lowercase()
                }
                if (hr != startHr) hrNote = "after " + treatNames[k].lowercase()
            }
        }
    }

    // Can a hospital of this level take this patient?
    fun okAt(hLevel: Int): Boolean = when (kind) {
        CARDIAC, ARREST, TRAUMA -> hLevel >= 4
        NEURO -> if (stroke) hLevel >= 3 else hLevel >= 2
        RESP, DIABETIC, ASTHMA, ALLERGY, OVERDOSE, HEAT, COLD -> hLevel >= 2
        SEIZURE -> hLevel >= 3
        BURN -> hLevel >= 4
        else -> true
    }

    // Tests this patient should get, only the ones unlocked at this level
    fun neededTests(): List<Int> {
        val l = ArrayList<Int>()
        if (kind != ARREST) l.add(0)
        when (kind) {
            CARDIAC -> l.add(4)
            RESP -> l.add(1)
            DIABETIC -> l.add(2)
            TRAUMA -> l.add(5)
            NEURO -> {
                l.add(3)
                l.add(2)
            }
            ASTHMA, OVERDOSE -> l.add(1)
            BURN -> l.add(5)
            SEIZURE -> l.add(2)
        }
        return l.filter { level >= testLevels[it] }
    }

    fun missedTests(): List<Int> = neededTests().filter { !tested[it] }

    // Treatments this patient needed but did not get
    fun missedTreats(): List<Int> {
        val l = ArrayList<Int>()
        if (level >= 3 && startOx in 1..89 && !treated[0]) l.add(0)
        if (level >= 4 && startGlu == 0 && !treated[1]) l.add(1)
        if (level >= 5 && injury > 0 && !treated[2]) l.add(2)
        if (level >= 8 && kind == CARDIAC && (startHr < 80 || startHr > 120) && !treated[4]) l.add(4)
        val own = kindTreat[kind]
        if (own != null && !treated[own]) l.add(own)
        return l
    }

    companion object {
        // The newest of the new patients at this level, if one starts here
        private fun newestNew(level: Int, rnd: Random): Int? = newKindLevel.entries.filter { it.value == level }.map { it.key }.randomOrNull(rnd)

        private fun newestKind(level: Int): Int = when (level) {
            1 -> WELL
            2 -> CARDIAC
            3 -> RESP
            4 -> DIABETIC
            5 -> TRAUMA
            6 -> ARREST
            7 -> NEURO
            else -> CARDIAC
        }
    }
}
