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

// Pedestrian and car accidents. Each road piece gets one small roll set by its traffic.
// Set to false to turn them off.
const val ACCIDENTS_ON = true

// Grades: 0 A, 1 B, 2 C, 3 F, -1 N/A
val gradeNames = listOf("A", "B", "C", "F")

fun gradeName(g: Int): String = if (g < 0) "N/A" else gradeNames[g.coerceIn(0, 3)]

// Experience (and credits) for one section of the run. F and N/A give nothing.
fun xpFor(grade: Int): Int = when (grade) {
    0 -> 50
    1 -> 35
    2 -> 20
    else -> 0
}

class RouteState(val player: PlayerData) {
    var level by mutableIntStateOf(player.cityLevel)
    var idx by mutableIntStateOf(player.cityIdx)
    var city by mutableStateOf(makeCity(player.cityLevel, player.cityIdx))
    var version by mutableIntStateOf(0)
    var stage by mutableIntStateOf(0)
    val path = mutableStateListOf<Int>()
    var done by mutableStateOf(false)
    var driveShown by mutableStateOf(false)
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
    var gTo by mutableIntStateOf(-1)
    var gExam by mutableIntStateOf(-1)
    var gHosp by mutableIntStateOf(-1)
    var nTo by mutableStateOf("")
    var nExam by mutableStateOf("")
    var nHosp by mutableStateOf("")
    private var awarded = false
    // True when the last finished run had no F and filled a dot
    var counted by mutableStateOf(false)
    var focusTick by mutableIntStateOf(0)
    var focusPt by mutableStateOf(Offset.Zero)
    private var hospCycle = -1
    var patient by mutableStateOf(Patient.make(player.cityLevel))

    private fun makeCity(l: Int, i: Int): CityData {
        val c = CityData(l, i, Random.nextLong())
        c.drv = player.drive
        c.heavy = player.heavyBonus
        c.cross = player.crossK
        return c
    }

    // Call after upgrades so the current city uses the new values
    fun applyPlayer() {
        city.drv = player.drive
        city.heavy = player.heavyBonus
        city.cross = player.crossK
        version++
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

    // Key of the building the current leg drives to, for the drive scene marker
    fun destKey(): Int {
        if (stage == 0) return city.key(city.call)
        val h = reached ?: return -1
        return city.key(h.slot)
    }

    // Where the drive scene ends: the end of the leg, or the middle of the crash block
    fun driveEnd(): Offset {
        if (accSeg >= 1 && accSeg < path.size) {
            val a = path[accSeg - 1]
            val b = path[accSeg]
            val m = city.m
            return Offset(
                (city.nodeX(a % m) + city.nodeX(b % m)) / 2f,
                (city.nodeY(a / m) + city.nodeY(b / m)) / 2f
            )
        }
        return endPt()
    }

    fun driveNodes(): List<Int> = if (accSeg >= 1 && accSeg < path.size) path.subList(0, accSeg).toList() else path.toList()

    fun correct(): List<Hospital> = city.hospitals.filter { patient.okAt(it.level) }

    fun starts(): IntArray = city.startNodes(startSlot())

    fun goals(): IntArray {
        if (stage == 0) return city.goalNodes(city.call)
        val l = ArrayList<Int>()
        for (h in correct()) for (g in city.goalNodes(h.slot)) l.add(g)
        return l.toIntArray()
    }

    fun fastest(): Double = city.fastest(starts(), goals())

    // Chance of an accident on the route drawn so far, after skills and upgrades
    fun routeRisk(): Double {
        var safe = 1.0
        for (k in 1 until path.size) safe *= 1.0 - city.segCrash(path[k - 1], path[k]) * player.riskMult
        return 1.0 - safe
    }

    fun wrongHospital(): Boolean {
        val h = reached
        return stage == 1 && done && h != null && !patient.okAt(h.level)
    }

    fun load(l: Int, i: Int) {
        level = l
        idx = i
        player.cityLevel = l
        player.cityIdx = i
        player.goodRuns = 0
        player.save()
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
        driveShown = false
        started = false
        reached = null
        accSeg = -1
        accType = ""
        runOver = false
        failReason = ""
        earned = 0
        awarded = false
        counted = false
        gTo = -1
        gExam = -1
        gHosp = -1
        nTo = ""
        nExam = ""
        nHosp = ""
    }

    fun passed(): Boolean = gTo != 3 && gExam != 3 && gHosp != 3

    fun reset() {
        resetRun()
        awarded = true
        version++
    }

    // City 5 is cleared: 5 good runs there
    fun cityCleared(): Boolean = idx == 4 && player.goodRuns >= RUNS_PER_CITY

    // City 5 is cleared and there is a next level kit to buy
    fun kitNeeded(): Boolean = cityCleared() && level < MAX_LEVEL

    fun nextKitCost(): Int = kitCost(level + 1)

    // Buy the next level's kit and move to City 1 of that level
    fun buyKit(): Boolean {
        if (!kitNeeded()) return false
        val c = nextKitCost()
        if (player.credits < c) return false
        player.credits -= c
        load(level + 1, 0)
        return true
    }

    // Testing button: moves to the next city. In City 5 it fills the runs so the kit can be bought.
    fun nextCity() {
        if (idx < 4) {
            load(level, idx + 1)
        } else {
            player.goodRuns = RUNS_PER_CITY
            player.save()
            city.newCall()
            resetRun()
            version++
        }
    }

    fun nextRun() {
        if (player.goodRuns >= RUNS_PER_CITY && idx < 4) {
            load(level, idx + 1)
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
        val p = patient
        if (p.dead || (p.kind == ARREST && !p.treated[3])) {
            gExam = 3
            nExam = if (p.dead) "patient died" else "needed CPR"
            runOver = true
            failReason = if (p.dead) "Patient did not survive" else "Patient needed CPR"
            award()
            version++
            return
        }
        val mt = p.missedTests()
        val mx = p.missedTreats()
        gExam = minOf(3, mt.size + mx.size)
        val names = mt.map { testNames[it] } + mx.map { treatNames[it] }
        nExam = if (names.isEmpty()) "nothing missed" else "missed: " + names.joinToString(", ")
        startHospital()
    }

    fun startHospital() {
        stage = 1
        path.clear()
        done = false
        driveShown = false
        started = false
        reached = null
        version++
    }

    private fun award() {
        if (awarded) return
        awarded = true
        val c = xpFor(gTo) + xpFor(gExam) + xpFor(gHosp)
        earned = c
        player.credits += c
        player.totalXp += c
        if (passed() && player.goodRuns < RUNS_PER_CITY) {
            player.goodRuns++
            counted = true
        }
        player.save()
    }

    private fun finalizeLeg() {
        val user = city.pathTime(path.toList())
        val fast = fastest()
        accSeg = -1
        accType = ""
        loop@ for (k in 1 until path.size) {
            if (!ACCIDENTS_ON) break
            val a = path[k - 1]
            val b = path[k]
            if (Random.nextDouble() < city.segCrash(a, b) * player.riskMult) {
                // Pedestrian or car crash, weighted by what this block is like
                val pr = city.segRisk(a, b, true)
                val cr = city.segRisk(a, b, false)
                val ped = Random.nextDouble() * (pr + cr) < pr
                accSeg = k
                accType = if (ped) "pedestrian" else "car crash"
                break@loop
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
        val g = if (fail != "") 3 else grade
        val note = if (accSeg >= 0) "accident" else if (wrongHospital()) "wrong hospital"
            else fmt(user) + " (best " + fmt(fast) + ")"
        if (stage == 0) {
            gTo = g
            nTo = note
        } else {
            gHosp = g
            nHosp = note
        }
        if (fail != "") {
            runOver = true
            failReason = fail
            award()
            return
        }
        if (stage == 1) {
            runOver = true
            award()
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
