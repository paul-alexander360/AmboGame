package com.cpd360.ambulancerun

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Ambulance parts, in the order of the game doc
const val TIRES = 0
const val ENGINE = 1
const val BRAKES = 2
const val LIGHTS = 3
const val SIREN = 4
const val CAB = 5
const val COMPARTMENT = 6

// Character skills
const val SKILL_DRIVE = 0
const val SKILL_PED = 1
const val SKILL_CRASH = 2

const val MAX_UP = 5

fun upgradeCost(current: Int): Int = 100 shl current

// Levels and what Dr. Ray sells. Each level needs its test, its treatment (if it has one)
// and one more Patient Compartment step. You buy them during the level before, then clear City 5 to move up.
const val MAX_LEVEL = 8
const val RUNS_PER_CITY = 5

// The Patient Compartment goes up one step per level, so it tops out higher than the other upgrades
const val COMPARTMENT_MAX = MAX_LEVEL - 1

fun ambMax(k: Int): Int = if (k == COMPARTMENT) COMPARTMENT_MAX else MAX_UP

// One thing Dr. Ray sells: test k (treat = false) or treatment k. Indexes match testNames and treatNames.
data class DrItem(val treat: Boolean, val k: Int)

fun itemLevel(d: DrItem): Int = if (d.treat) treatLevels[d.k] else testLevels[d.k]

// The tests and treatments a level needs, tests first
fun itemsFor(level: Int): List<DrItem> =
    testLevels.indices.filter { testLevels[it] == level }.map { DrItem(false, it) } +
        treatLevels.indices.filter { treatLevels[it] == level }.map { DrItem(true, it) }

// A test and its treatment split the level's price 300/200 per level. A lone item costs 500 per level.
fun itemCost(d: DrItem): Int {
    val lv = itemLevel(d)
    return if (itemsFor(lv).size < 2) 500 * lv else if (d.treat) 200 * lv else 300 * lv
}

// A treatment opens once you own the test from the same level, if there is one.
// The new patients' treatments (5 and up) need no test.
fun itemNeeds(d: DrItem): DrItem? = if (d.treat && d.k < 5) itemsFor(itemLevel(d)).firstOrNull { !it.treat } else null

val shopTestNames = listOf("Vitals", "Pulse ox test", "Glucose test", "Stroke test", "ECG", "Trauma exam")
val shopTestDescs = listOf(
    "Blood pressure and pulse", "Checks oxygen levels", "Checks blood sugar",
    "Checks for signs of a stroke", "Reads the heart rhythm", "Checks for bleeding and broken bones"
)
val shopTestGlyphs = listOf("💓", "🫁", "🩸", "🧠", "📈", "🦴")
val shopTreatNames = treatNames
val shopTreatDescs = listOf(
    "Give oxygen when it's low", "Give sugar when it's low", "Stop bleeding and splint broken bones",
    "Start CPR when the heart stops", "Shock a heart that's too fast or too slow",
    "Opens the airways in an asthma attack", "Stops a bad allergic reaction", "Cools and covers a burn",
    "Wakes up someone who took too much medicine", "Cools down someone who is too hot",
    "Keeps someone safe after a seizure", "Warms up someone who is too cold"
)
val shopTreatGlyphs = listOf("💨", "🍬", "🩹", "❤️", "⚡", "🌬️", "💉", "🧊", "👃", "❄️", "🛡️", "🧣")

fun itemName(d: DrItem): String = if (d.treat) shopTreatNames[d.k] else shopTestNames[d.k]
fun itemDesc(d: DrItem): String = if (d.treat) shopTreatDescs[d.k] else shopTestDescs[d.k]
fun itemGlyph(d: DrItem): String = if (d.treat) shopTreatGlyphs[d.k] else shopTestGlyphs[d.k]

// What the Compartment step for each level makes room for (index = level)
val compRoom = listOf(
    "", "", "Room for the vitals kit", "Room for the oxygen tank", "Room for the glucose kit",
    "Room for splints and bandages", "Room for the CPR gear", "Room for the stroke kit", "Room for the ECG and defibrillator"
)

class PlayerData(private val ctx: Context) {
    var name by mutableStateOf("Player")
    // Unit number, set on the character screen
    var unit by mutableStateOf("1")
    // The organization you respond as, set on the character screen
    var org by mutableStateOf("County EMS")
    var orgColor = 0xFFE53935.toInt()
    var credits by mutableIntStateOf(0)
    // Premium currency
    var hearts by mutableIntStateOf(0)
    // Free Hearts from ads: the day (epoch day) and how many were watched that day
    var adDay = 0L
    var adsToday by mutableIntStateOf(0)
    // Siren boost bought with Hearts: true until the end of the next run
    var boost by mutableStateOf(false)
    // Hearts-only paints bought (paint indexes)
    val paintsOwned = mutableStateListOf<Int>()
    var totalXp by mutableIntStateOf(0)
    var heavy = 0

    // Where you are in the game: level 1 to 8, city 0 to 4, and runs with no F in this city
    var cityLevel = 1
    var cityIdx = 0
    var goodRuns by mutableIntStateOf(0)

    // Character look
    var female by mutableStateOf(false)
    var skin by mutableIntStateOf(0)
    var hairStyle by mutableIntStateOf(0)
    var hairColor by mutableIntStateOf(1)
    var uniform by mutableIntStateOf(0)

    // Ambulance look
    var ambColor by mutableIntStateOf(0)

    // Settings
    var sirenSound by mutableStateOf(true)
    var driveScenes by mutableStateOf(true)
    // Drive scene camera: true is the drone view above and behind the ambulance, false the cab
    var droneView by mutableStateOf(true)

    // Guide tips already shown by Dot and Dr. Ray
    val tipsSeen = mutableStateListOf<String>()

    // Tests and treatments bought from Dr. Ray. Indexes match testNames and treatNames.
    val testsOwned = mutableStateListOf(false, false, false, false, false, false)
    val treatsOwned = mutableStateListOf(*Array(treatNames.size) { false })

    // Certification moves up with skills: Unit until Vitals, Aid until the Stroke test, then Medic
    val cert: String
        get() = if (testsOwned[3]) "Medic" else if (testsOwned[0]) "Aid" else "Unit"

    // What Dot calls you, like "Aid 12"
    val callSign: String get() = cert + " " + (if (unit == "") "1" else unit)

    // Organization and call sign, like "County EMS · Aid 12"
    val orgLine: String get() = if (org == "") callSign else "$org · $callSign"

    fun seen(k: String): Boolean = tipsSeen.contains(k)

    fun markSeen(k: String) {
        if (!tipsSeen.contains(k)) tipsSeen.add(k)
        save()
    }

    fun replayTips() {
        tipsSeen.clear()
        save()
    }

    val charUp = mutableStateListOf(0, 0, 0)
    val ambUp = mutableStateListOf(0, 0, 0, 0, 0, 0, 0)

    // Rank goes up every 500 XP. Level always means the city level.
    val rank: Int get() = 1 + totalXp / 500
    val drive: Double
        get() = 0.1 - 0.005 * (rank - 1) - 0.02 * charUp[SKILL_DRIVE] -
            0.015 * (ambUp[TIRES] + ambUp[ENGINE] + ambUp[CAB])
    // Your speed against the standard medic of a level, on a typical road.
    // Above 0 means slower than the target, below 0 faster. 0.06 = 6% slower.
    fun speedVsTarget(lvl: Int): Double = (1.5 + drive) / (1.5 + Levels.driver[lvl - 1]) - 1.0

    val crossK: Double get() = 1.0 - 0.06 * (ambUp[LIGHTS] + ambUp[SIREN])
    val heavyBonus: Double get() = 0.1 * heavy

    // Crash risk multiplier from skills and upgrades. Never below 30% of the base.
    val riskMult: Double
        get() = maxOf(
            0.3,
            1.0 - 0.08 * (charUp[SKILL_PED] + charUp[SKILL_CRASH]) -
                0.04 * (ambUp[BRAKES] + ambUp[LIGHTS] + ambUp[SIREN])
        )

    fun addHearts(n: Int) {
        hearts += n
        save()
    }

    fun spendHearts(n: Int): Boolean {
        if (hearts < n) return false
        hearts -= n
        save()
        return true
    }

    fun ownsPaint(i: Int): Boolean = i < FIRST_HEART_PAINT || paintsOwned.contains(i)

    fun buyChar(k: Int): Boolean {
        val lv = charUp[k]
        if (lv >= MAX_UP || credits < upgradeCost(lv)) return false
        credits -= upgradeCost(lv)
        charUp[k] = lv + 1
        save()
        return true
    }

    fun buyAmb(k: Int): Boolean {
        val lv = ambUp[k]
        if (lv >= ambMax(k) || credits < upgradeCost(lv)) return false
        credits -= upgradeCost(lv)
        ambUp[k] = lv + 1
        save()
        return true
    }

    fun hasTest(k: Int): Boolean = testsOwned[k]
    fun hasTreat(k: Int): Boolean = treatsOwned[k]
    fun owns(d: DrItem): Boolean = if (d.treat) treatsOwned[d.k] else testsOwned[d.k]

    // The Compartment step a level needs
    fun compFor(level: Int): Int = level - 1

    // Only the next level's items can be bought, and a treatment needs its test first
    fun itemOpen(d: DrItem): Boolean {
        val need = itemNeeds(d)
        return !owns(d) && itemLevel(d) == cityLevel + 1 && (need == null || owns(need))
    }

    fun buyItem(d: DrItem): Boolean {
        if (!itemOpen(d) || credits < itemCost(d)) return false
        credits -= itemCost(d)
        if (d.treat) treatsOwned[d.k] = true else testsOwned[d.k] = true
        save()
        return true
    }

    // Everything a level needs is bought: its tests, treatments and Compartment step
    fun readyFor(level: Int): Boolean = itemsFor(level).all { owns(it) } && ambUp[COMPARTMENT] >= compFor(level)

    // Gives every test and treatment up to this level. Used for old saves and the test tools.
    fun grantUpTo(level: Int) {
        for (k in testLevels.indices) if (testLevels[k] <= level) testsOwned[k] = true
        for (k in treatLevels.indices) if (treatLevels[k] <= level) treatsOwned[k] = true
    }

    fun save() {
        try {
            val o = JSONObject()
            o.put("name", name)
            o.put("unit", unit)
            o.put("org", org)
            o.put("orgColor", orgColor)
            o.put("credits", credits)
            o.put("hearts", hearts)
            o.put("adDay", adDay)
            o.put("adsToday", adsToday)
            o.put("boost", boost)
            o.put("paintsOwned", JSONArray(paintsOwned.toList()))
            o.put("xp", totalXp)
            o.put("heavy", heavy)
            o.put("cityLevel", cityLevel)
            o.put("cityIdx", cityIdx)
            o.put("goodRuns", goodRuns)
            o.put("testsOwned", JSONArray(testsOwned.toList()))
            o.put("treatsOwned", JSONArray(treatsOwned.toList()))
            o.put("female", female)
            o.put("skin", skin)
            o.put("hairStyle", hairStyle)
            o.put("hairColor", hairColor)
            o.put("uniform", uniform)
            o.put("look2", true)
            o.put("ambColor", ambColor)
            o.put("sirenSound", sirenSound)
            o.put("driveScenes", driveScenes)
            o.put("droneView", droneView)
            o.put("charUp", JSONArray(charUp.toList()))
            o.put("ambUp", JSONArray(ambUp.toList()))
            o.put("tipsSeen", JSONArray(tipsSeen.toList()))
            val f = File(ctx.filesDir, "player.json")
            val t = File(ctx.filesDir, "player.tmp")
            t.writeText(o.toString())
            if (!t.renameTo(f)) {
                f.delete()
                t.renameTo(f)
            }
        } catch (e: Exception) {
        }
    }

    // Test tools: delete the save so the next start is a brand new character
    fun wipe() {
        try {
            File(ctx.filesDir, "player.json").delete()
            File(ctx.filesDir, "player.tmp").delete()
        } catch (e: Exception) {
        }
    }

    private fun readList(o: JSONObject, key: String, into: MutableList<Int>, max: (Int) -> Int) {
        val a = o.optJSONArray(key) ?: return
        for (i in into.indices) {
            if (i < a.length()) into[i] = a.optInt(i, 0).coerceIn(0, max(i))
        }
    }

    private fun readFlags(o: JSONObject, key: String, into: MutableList<Boolean>) {
        val a = o.optJSONArray(key) ?: return
        for (i in into.indices) {
            if (i < a.length()) into[i] = a.optBoolean(i, false)
        }
    }

    private fun loadFrom() {
        try {
            val f = File(ctx.filesDir, "player.json")
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            name = o.optString("name", name)
            unit = o.optString("unit", unit)
            org = o.optString("org", org)
            orgColor = o.optInt("orgColor", orgColor)
            credits = o.optInt("credits", 0)
            hearts = o.optInt("hearts", 0).coerceAtLeast(0)
            adDay = o.optLong("adDay", 0L)
            adsToday = o.optInt("adsToday", 0)
            boost = o.optBoolean("boost", false)
            val po = o.optJSONArray("paintsOwned")
            if (po != null) for (i in 0 until po.length()) {
                val v = po.optInt(i, -1)
                if (v >= FIRST_HEART_PAINT && v < AmbColors.size && !paintsOwned.contains(v)) paintsOwned.add(v)
            }
            totalXp = o.optInt("xp", 0)
            heavy = o.optInt("heavy", 0)
            cityLevel = o.optInt("cityLevel", 1).coerceIn(1, MAX_LEVEL)
            cityIdx = o.optInt("cityIdx", 0).coerceIn(0, 4)
            goodRuns = o.optInt("goodRuns", 0).coerceIn(0, RUNS_PER_CITY)
            // Saves from before items were sold one at a time own everything up to their level
            // and anything new added to a level they've already reached (like the Trauma exam)
            if (o.has("testsOwned")) {
                readFlags(o, "testsOwned", testsOwned)
                readFlags(o, "treatsOwned", treatsOwned)
            }
            grantUpTo(cityLevel)
            female = o.optBoolean("female", false)
            // Saves from before the painted look had 3 skins and 3 hair styles. Map them across once.
            val newLook = o.optBoolean("look2", false)
            skin = if (newLook) o.optInt("skin", 0).coerceIn(0, 4) else o.optInt("skin", 0).coerceIn(0, 2) * 2
            val hs = o.optInt("hairStyle", 0)
            hairStyle = if (newLook) hs.coerceIn(0, 5)
                else if (female) intArrayOf(HS_LONG, HS_PONY, HS_BUN)[hs.coerceIn(0, 2)]
                else intArrayOf(HS_SHORT, HS_BUZZ, HS_CURLY)[hs.coerceIn(0, 2)]
            hairColor = o.optInt("hairColor", 1).coerceIn(0, 4)
            uniform = o.optInt("uniform", 0).coerceIn(0, 4)
            ambColor = o.optInt("ambColor", 0).coerceIn(0, AmbColors.size - 1)
            if (!ownsPaint(ambColor)) ambColor = 0
            sirenSound = o.optBoolean("sirenSound", true)
            driveScenes = o.optBoolean("driveScenes", true)
            droneView = o.optBoolean("droneView", true)
            readList(o, "charUp", charUp) { MAX_UP }
            readList(o, "ambUp", ambUp) { ambMax(it) }
            val ts = o.optJSONArray("tipsSeen")
            if (ts != null) for (i in 0 until ts.length()) tipsSeen.add(ts.optString(i))
            if (!o.has("charUp")) {
                charUp[SKILL_PED] = o.optInt("ped", 0).coerceIn(0, MAX_UP)
                charUp[SKILL_CRASH] = o.optInt("mvc", 0).coerceIn(0, MAX_UP)
            }
        } catch (e: Exception) {
        }
    }

    companion object {
        fun load(ctx: Context): PlayerData {
            val p = PlayerData(ctx.applicationContext)
            p.loadFrom()
            return p
        }
    }
}
