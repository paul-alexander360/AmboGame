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

fun creditFor(grade: Int): Int = when (grade) {
    0 -> 50
    1 -> 35
    2 -> 20
    else -> 10
}

class RouteState(val player: PlayerData) {
    var level by mutableIntStateOf(1)
    var idx by mutableIntStateOf(0)
    var run by mutableIntStateOf(1)
    var city by mutableStateOf(makeCity(1, 0))
    var version by mutableIntStateOf(0)
    var stage by mutableIntStateOf(0)
    val path = mutableStateListOf<Int>()
    var done by mutableStateOf(false)
    var started by mutableStateOf(false)
    var leg1User by mutableStateOf(0.0)
    var leg1Fast by mutableStateOf(0.0)
    var leg1Grade by mutableIntStateOf(0)
    var reached by mutableStateOf<Hospital?>(null)
    var popup by mutableStateOf<Hospital?>(null)
    var accSeg by mutableIntStateOf(-1)
    var accType by mutableStateOf("")
    var runOver by mutableStateOf(false)
    var failReason by mutableStateOf("")
    var earned by mutableIntStateOf(0)
    private var awarded = false
    var focusTick by mutableIntStateOf(0)
    var focusPt by mutableStateOf(Offset.Zero)
    private var hospCycle = -1
    var patient by mutableStateOf(Patient.make(1))

    private fun makeCity(l: Int, i: Int): CityData {
        val c = CityData(l, i, Random.nextLong())
        c.drv = player.drive
        c.heavy = player.heavyBonus
        return c
    }

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

    fun showHospital(h: Hospital) {
        popup = h
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

    fun correct(): List<Hospital> = city.hospitals.filter { patient.okAt(it.level) }

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
        return stage == 1 && done && h != null && !patient.okAt(h.level)
    }

    fun load(l: Int, i: Int) {
        level = l
        idx = i
        run = 1
        city = makeCity(l, i)
        hospCycle = -1
        resetRun()
        version++
    }

    private fun resetRun() {
        popup = null
        patient = Patient.make(level)
        stage = 0
        path.clear()
        done = false
        started = false
        reached = null
        accSeg = -1
        accType = ""
        runOver = false
        failReason = ""
        earned = 0
        awarded = false
    }

    fun reset() {
        resetRun()
        awarded = true
        version++
    }

    private fun advanceCity() {
        var l = level
        var i = idx + 1
        if (i > 4) {
            i = 0
            l++
            if (l > 8) l = 1
        }
        load(l, i)
    }

    fun nextCity() {
        advanceCity()
    }

    fun nextRun() {
        run++
        if (run > 5) {
            advanceCity()
        } else {
            city.newCall()
            resetRun()
            version++
        }
    }

    fun begin() {
        if (!done) started = true
    }

    fun examDone() {
        if (patient.dead) {
            runOver = true
            failReason = "Patient did not survive"
            award(creditFor(leg1Grade))
            version++
            return
        }
        val miss = patient.missing()
        if (miss != "") {
            runOver = true
            failReason = miss
            award(0)
            version++
            return
        }
        startHospital()
    }

    fun startHospital() {
        stage = 1
        path.clear()
        done = false
        started = false
        reached = null
        version++
    }

    private fun award(c: Int) {
        if (awarded) return
        awarded = true
        earned = c
        player.credits += c
        player.totalXp += c
        player.save()
    }

    private fun finalizeLeg() {
        val user = city.pathTime(path.toList())
        val fast = fastest()
        accSeg = -1
        accType = ""
        loop@ for (k in 1 until path.size) {
            val a = path[k - 1]
            val b = path[k]
            for (type in 0..1) {
                val ped = type == 0
                val x = city.segRisk(a, b, ped)
                val s = 0.1 * (if (ped) player.pedBonus else player.mvcBonus)
                val chance = city.accidentChance(x, player.ambAvoid, s, player.avoidance)
                if (Random.nextDouble() < chance && Random.nextDouble() >= player.dodge) {
                    accSeg = k
                    accType = if (ped) "pedestrian" else "car crash"
                    break@loop
                }
            }
        }
        val grade = rating(user, fast)
        if (stage == 0) {
            leg1User = user
            leg1Fast = fast
            leg1Grade = grade
        }
        var fail = ""
        if (accSeg >= 0) fail = "Accident with a " + accType
        else if (wrongHospital()) fail = "Wrong hospital"
        else if (user > fast * 1.6) fail = "Too slow"
        if (fail != "") {
            runOver = true
            failReason = fail
            award(0)
            return
        }
        if (stage == 1) {
            runOver = true
            award(creditFor(leg1Grade) + creditFor(grade))
        }
    }

    private fun arrive(n: Int) {
        if (stage == 0) {
            if (goals().contains(n)) {
                done = true
                finalizeLeg()
            }
            return
        }
        var found: Hospital? = null
        for (h in city.hospitals) {
            if (city.goalNodes(h.slot).contains(n)) {
                if (found == null || (patient.okAt(h.level) && !patient.okAt(found.level))) found = h
            }
        }
        if (found != null) {
            reached = found
            done = true
            finalizeLeg()
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
