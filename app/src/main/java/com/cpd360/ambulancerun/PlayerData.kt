package com.cpd360.ambulancerun

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

class PlayerData(private val ctx: Context) {
    var name = "Player"
    var unit = "1"
    var org = "County EMS"
    var orgColor = 0xFFE53935.toInt()
    var credits by mutableIntStateOf(0)
    var totalXp by mutableIntStateOf(0)
    var heavy = 0
    var pedBonus = 0
    var mvcBonus = 0
    var ambAvoid = 0.0

    val level: Int get() = 1 + totalXp / 500
    val drive: Double get() = 0.1 - 0.005 * (level - 1)
    val avoidance: Double get() = 0.01 * (level - 1)
    val dodge: Double get() = minOf(0.9, 0.05 + 0.01 * (level - 1))
    val heavyBonus: Double get() = 0.1 * heavy

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
            o.put("ped", pedBonus)
            o.put("mvc", mvcBonus)
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
            pedBonus = o.optInt("ped", 0)
            mvcBonus = o.optInt("mvc", 0)
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
