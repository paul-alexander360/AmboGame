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

// Medical kits. Each level has its own kit with the tests and treatments that level unlocks.
// You buy the next level's kit once City 5 of your level is cleared, and it moves you to City 1 of that level.
const val MAX_LEVEL = 8
const val RUNS_PER_CITY = 5

fun kitCost(level: Int): Int = 500 * level

// Each level's lesson from Dr. Ray: the tests and treatments that level unlocks
val kitNames = listOf(
    "Basics", "Vitals", "Pulse ox + Oxygen", "Glucose test + Glucose",
    "Splint / Stop bleeding", "CPR", "Stroke test", "ECG + Shock"
)
val kitDescs = listOf(
    "Talk to the patient, pick the hospital.",
    "Blood pressure and pulse.",
    "Check oxygen levels and give oxygen.",
    "Check blood sugar and give glucose.",
    "Stop bleeding and splint broken bones.",
    "Start CPR when the heart stops.",
    "Check for signs of a stroke.",
    "Read the heart rhythm and shock it."
)
val kitGlyphs = listOf("🩺", "💓", "🫁", "🩸", "🩹", "❤️", "🧠", "⚡")

class PlayerData(private val ctx: Context) {
    var name by mutableStateOf("Player")
    var unit = "1"
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

    // Guide tips already shown by Dot and Dr. Ray
    val tipsSeen = mutableStateListOf<String>()

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

    val pedBonus: Int get() = charUp[SKILL_PED]
    val mvcBonus: Int get() = charUp[SKILL_CRASH]
    val ambAvoid: Double
        get() = 0.005 * ambUp[TIRES] + 0.01 * (ambUp[BRAKES] + ambUp[LIGHTS] + ambUp[SIREN] + ambUp[CAB])

    val level: Int get() = 1 + totalXp / 500
    val drive: Double
        get() = 0.1 - 0.005 * (level - 1) - 0.02 * charUp[SKILL_DRIVE] -
            0.015 * (ambUp[TIRES] + ambUp[ENGINE] + ambUp[CAB])
    // Your speed against the standard medic of a level, on a typical road.
    // Above 0 means slower than the target, below 0 faster. 0.06 = 6% slower.
    fun speedVsTarget(lvl: Int): Double = (1.5 + drive) / (1.5 + Levels.driver[lvl - 1]) - 1.0

    val crossK: Double get() = 1.0 - 0.06 * (ambUp[LIGHTS] + ambUp[SIREN])
    val avoidance: Double get() = 0.01 * (level - 1)
    val dodge: Double get() = minOf(0.9, 0.05 + 0.01 * (level - 1))
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
        if (lv >= MAX_UP || credits < upgradeCost(lv)) return false
        credits -= upgradeCost(lv)
        ambUp[k] = lv + 1
        save()
        return true
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
            o.put("female", female)
            o.put("skin", skin)
            o.put("hairStyle", hairStyle)
            o.put("hairColor", hairColor)
            o.put("uniform", uniform)
            o.put("look2", true)
            o.put("ambColor", ambColor)
            o.put("sirenSound", sirenSound)
            o.put("driveScenes", driveScenes)
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

    private fun readList(o: JSONObject, key: String, into: MutableList<Int>) {
        val a = o.optJSONArray(key) ?: return
        for (i in into.indices) {
            if (i < a.length()) into[i] = a.optInt(i, 0).coerceIn(0, MAX_UP)
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
            readList(o, "charUp", charUp)
            readList(o, "ambUp", ambUp)
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
