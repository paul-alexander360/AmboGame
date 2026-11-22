package com.cpd360.ambulancerun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.random.Random

fun fmt(s: Double): String {
    val t = Math.round(s).toInt()
    return "%d:%02d".format(t / 60, t % 60)
}

fun rating(user: Double, fast: Double): Int {
    val r = user / fast
    return if (r <= 1.05) 0 else if (r <= 1.25) 1 else if (r <= 1.6) 2 else 3
}

class RouteState {
    var level by mutableIntStateOf(1)
    var idx by mutableIntStateOf(0)
    var city by mutableStateOf(CityData(1, 0, Random.nextLong()))
    var version by mutableIntStateOf(0)
    var stage by mutableIntStateOf(0)
    val path = mutableStateListOf<Int>()
    var done by mutableStateOf(false)
    var started by mutableStateOf(false)
    var leg1User by mutableStateOf(0.0)
    var leg1Fast by mutableStateOf(0.0)
    var reached by mutableStateOf<Hospital?>(null)
    val needLevel = 4
    var focusTick by mutableIntStateOf(0)
    var focusPt by mutableStateOf(Offset.Zero)
    private var hospCycle = -1

    private fun focus(x: Float, y: Float) {
        focusPt = Offset(x, y)
        focusTick++
    }

    fun focusStation() {
        focus(city.slotX(city.station), city.slotY(city.station))
    }

    fun focusCall() {
        focus(city.slotX(city.call), city.slotY(city.call))
    }

    fun focusHospital() {
        val hs = city.hospitals
        if (hs.isEmpty()) return
        hospCycle = (hospCycle + 1) % hs.size
        focus(city.slotX(hs[hospCycle].slot), city.slotY(hs[hospCycle].slot))
    }

    fun startSlot(): Slot = if (stage == 0) city.station else city.call

    fun startPt(): Offset {
        val s = startSlot()
        return Offset(city.accX(s), city.accY(s))
    }

    fun endPt(): Offset {
        if (stage == 0) return Offset(city.accX(city.call), city.accY(city.call))
        val h = reached ?: return Offset(0f, 0f)
        return Offset(city.accX(h.slot), city.accY(h.slot))
    }

    fun correct(): List<Hospital> = city.hospitals.filter { it.level >= needLevel }

    fun starts(): IntArray = city.startNodes(startSlot())

    fun goals(): IntArray {
        if (stage == 0) return city.goalNodes(city.call)
        val l = ArrayList<Int>()
        for (h in correct()) for (g in city.goalNodes(h.slot)) l.add(g)
        return l.toIntArray()
    }

    fun fastest(): Double = city.fastest(starts(), goals())

    fun wrongHospital(): Boolean {
        val h = reached
        return stage == 1 && done && h != null && h.level < needLevel
    }

    fun load(l: Int, i: Int) {
        level = l
        idx = i
        city = CityData(l, i, Random.nextLong())
        hospCycle = -1
        resetRun()
        version++
    }

    private fun resetRun() {
        stage = 0
        path.clear()
        done = false
        started = false
        reached = null
    }

    fun reset() {
        resetRun()
        version++
    }

    fun nextCity() {
        var l = level
        var i = idx + 1
        if (i > 4) {
            i = 0
            l++
            if (l > 8) l = 1
        }
        load(l, i)
    }

    fun begin() {
        if (!done) started = true
    }

    fun startHospital() {
        leg1User = city.pathTime(path.toList())
        leg1Fast = fastest()
        stage = 1
        path.clear()
        done = false
        started = false
        reached = null
        version++
    }

    private fun arrive(n: Int) {
        if (stage == 0) {
            if (goals().contains(n)) done = true
            return
        }
        var found: Hospital? = null
        for (h in city.hospitals) {
            if (city.goalNodes(h.slot).contains(n)) {
                if (found == null || (h.level >= needLevel && found.level < needLevel)) found = h
            }
        }
        if (found != null) {
            reached = found
            done = true
        }
    }

    fun move(n: Int) {
        if (done) return
        if (path.isEmpty()) {
            if (starts().contains(n)) {
                path.add(n)
                started = true
                arrive(n)
            }
            return
        }
        val last = path.last()
        if (n == last) return
        if (path.size > 1 && n == path[path.size - 2]) {
            path.removeAt(path.size - 1)
            return
        }
        if (city.canGo(last, n) && !path.contains(n)) {
            path.add(n)
            arrive(n)
        }
    }
}
