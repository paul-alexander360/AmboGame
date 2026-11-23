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

class PlayerData(private val ctx: Context) {
    var name by mutableStateOf("Player")
    var unit = "1"
    var org = "County EMS"
    var orgColor = 0xFFE53935.toInt()
    var credits by mutableIntStateOf(0)
    var totalXp by mutableIntStateOf(0)
    var heavy = 0

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
    val crossK: Double get() = 1.0 - 0.06 * (ambUp[LIGHTS] + ambUp[SIREN])
    val avoidance: Double get() = 0.01 * (level - 1)
    val dodge: Double get() = minOf(0.9, 0.05 + 0.01 * (level - 1))
    val heavyBonus: Double get() = 0.1 * heavy

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
            o.put("xp", totalXp)
            o.put("heavy", heavy)
            o.put("female", female)
            o.put("skin", skin)
            o.put("hairStyle", hairStyle)
            o.put("hairColor", hairColor)
            o.put("uniform", uniform)
            o.put("ambColor", ambColor)
            o.put("sirenSound", sirenSound)
            o.put("driveScenes", driveScenes)
            o.put("charUp", JSONArray(charUp.toList()))
            o.put("ambUp", JSONArray(ambUp.toList()))
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
            totalXp = o.optInt("xp", 0)
            heavy = o.optInt("heavy", 0)
            female = o.optBoolean("female", false)
            skin = o.optInt("skin", 0).coerceIn(0, 2)
            hairStyle = o.optInt("hairStyle", 0).coerceIn(0, 2)
            hairColor = o.optInt("hairColor", 1).coerceIn(0, 3)
            uniform = o.optInt("uniform", 0).coerceIn(0, 3)
            ambColor = o.optInt("ambColor", 0).coerceIn(0, 4)
            sirenSound = o.optBoolean("sirenSound", true)
            driveScenes = o.optBoolean("driveScenes", true)
            readList(o, "charUp", charUp)
            readList(o, "ambUp", ambUp)
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
