package com.cpd360.ambulancerun

import kotlin.math.abs
import kotlin.random.Random

class Slot(val bc: Int, val br: Int, val corner: Int)

class Hospital(val slot: Slot, val level: Int, val name: String)

// Road events: each city from Level 2 has one (two from Level 4 sometimes)
const val EV_STORM = 0
const val EV_WORK = 1
const val EV_TRAIN = 2
const val EV_SCHOOL = 3
const val EV_RUSH = 4
val eventNames = listOf("Storm", "Road work", "Train crossing", "School zone", "Rush hour")
val eventGlyphs = listOf("⛈", "🚧", "🚆", "🏫", "🚗")
val eventTips = listOf(
    "A **storm** is soaking the city. Every road is a bit slower and crashes are more likely.",
    "**Road work** has closed one block. Look for the orange cones and drive around it.",
    "A **train** crosses the city. Each street that crosses the tracks adds a wait for the gates.",
    "There's a **school zone**. The streets around it are slow, but you won't crash there.",
    "It's **rush hour**. The two main roads in red are packed, slow and riskier."
)

object Levels {
    val sizes = arrayOf(
        intArrayOf(3, 4, 5, 5, 6),
        intArrayOf(5, 5, 6, 6, 6),
        intArrayOf(6, 6, 6, 6, 6),
        intArrayOf(6, 6, 6, 6, 7),
        intArrayOf(6, 6, 7, 7, 8),
        intArrayOf(8, 8, 8, 8, 10),
        intArrayOf(8, 8, 8, 8, 10),
        intArrayOf(10, 10, 10, 10, 10)
    )
    val driver = doubleArrayOf(0.2, 0.2, 0.1, 0.0, -0.1, -0.2, -0.3, -0.3)
    val distraction = doubleArrayOf(0.0, 0.0, 0.1, 0.1, 0.1, 0.2, 0.2, 0.3)
    val hospitals: Array<Array<IntArray>> = arrayOf(
        arrayOf(intArrayOf(4), intArrayOf(4), intArrayOf(4), intArrayOf(4), intArrayOf(4, 1)),
        arrayOf(intArrayOf(4), intArrayOf(4, 3, 1), intArrayOf(4, 3, 1), intArrayOf(4, 2, 1), intArrayOf(4, 2, 3)),
        arrayOf(intArrayOf(4), intArrayOf(4, 3), intArrayOf(4, 3), intArrayOf(4, 2), intArrayOf(4, 2, 1)),
        arrayOf(intArrayOf(4), intArrayOf(4, 1), intArrayOf(4, 1), intArrayOf(4, 2, 1), intArrayOf(4, 2, 3, 3)),
        arrayOf(intArrayOf(4), intArrayOf(4, 1), intArrayOf(4, 1), intArrayOf(4, 2), intArrayOf(4, 4, 3, 1)),
        arrayOf(intArrayOf(4), intArrayOf(4, 1), intArrayOf(4, 1), intArrayOf(4, 4, 2), intArrayOf(4, 4, 2, 2, 1)),
        arrayOf(intArrayOf(4), intArrayOf(4, 3, 1), intArrayOf(4, 3, 2, 2), intArrayOf(4, 4, 3), intArrayOf(4, 4, 3, 2, 2)),
        arrayOf(
            intArrayOf(4, 4, 3, 2), intArrayOf(4, 4, 3, 2), intArrayOf(4, 4, 3, 2),
            intArrayOf(4, 4, 3, 2, 2, 1), intArrayOf(4, 4, 3, 2, 2, 1)
        )
    )
}

class CityData(val level: Int, val idx: Int, seed: Long) {
    private val rnd = Random(seed)
    val n: Int = Levels.sizes[level - 1][idx]
    val m: Int = n + 1
    val w: Float = 80f * n + 38f
    val h: Float = 80f * n + 40f
    var drv: Double = 0.1
    var heavy: Double = 0.0
    var cross: Double = 1.0

    // Standard medic for this level. Drive grades compare you to this driver's best route.
    val targetDrv: Double = Levels.driver[level - 1]
    val distraction: Double = Levels.distraction[level - 1]
    val zone = IntArray(n * n)
    val dens = IntArray(n * n)
    val qH = IntArray(m * n)
    val qV = IntArray(m * n)
    val owH = IntArray(m)
    val owV = IntArray(m)
    val light = BooleanArray(m * m)
    val hospitals = ArrayList<Hospital>()
    val hospKey = HashMap<Int, Int>()
    var station: Slot = Slot(0, 0, 0)
    var call: Slot = Slot(0, 0, 0)
    private val cellX = floatArrayOf(18f, 46f, 18f, 46f)
    private val cellY = floatArrayOf(18f, 18f, 46f, 46f)
    private val trafficTbl = arrayOf(
        doubleArrayOf(0.1, 0.3, 0.5),
        doubleArrayOf(0.4, 0.6, 0.8),
        doubleArrayOf(0.5, 0.8, 1.0)
    )
    private val ratingTbl = doubleArrayOf(-0.6, 1.0, 2.0)
    private val pedTbl = arrayOf(
        doubleArrayOf(0.1, 0.2, 0.3),
        doubleArrayOf(0.2, 0.3, 0.4),
        doubleArrayOf(0.2, 0.2, 0.2)
    )
    private val mvcTbl = arrayOf(
        doubleArrayOf(0.1, 0.1, 0.3),
        doubleArrayOf(0.2, 0.3, 0.5),
        doubleArrayOf(0.4, 0.5, 0.6)
    )
    private val prefixes = listOf("General", "Sacred")
    private val roots = listOf(
        "Memorial", "Regional", "University", "Health", "Community", "Mercy", "Baptist",
        "Presbyterian", "Methodist", "Valley", "County", "Samaritan", "Foundation"
    )
    private val saints = listOf(
        "St Joseph", "St Mary", "St Luke", "St Francis", "St John", "St Michael", "St Thomas", "St Anthony"
    )
    private val suffixes = listOf("Hospital", "Medical Center")

    // Road events for this city (EV_ codes) and where they are
    val events = ArrayList<Int>()
    var workA = -1
    var workB = -1
    var trainRow = -1
    var schoolBlock = -1
    var rushRow = -1
    var rushCol = -1
    private var storm = false

    init {
        makeZones()
        makeDensity()
        for (k in qH.indices) qH[k] = roadQuality()
        for (k in qV.indices) qV[k] = roadQuality()
        for (k in light.indices) light[k] = rnd.nextInt(100) < 35
        if (level >= 3) {
            var ok = false
            var tries = 0
            while (!ok && tries < 40) {
                tries++
                for (k in 1 until n) {
                    owH[k] = if (rnd.nextInt(100) < 25) 1 + rnd.nextInt(2) else 0
                    owV[k] = if (rnd.nextInt(100) < 25) 1 + rnd.nextInt(2) else 0
                }
                ok = reach(true) == m * m && reach(false) == m * m
            }
            if (!ok) {
                owH.fill(0)
                owV.fill(0)
            }
        }
        val used = HashSet<Int>()
        // Station, hospitals and the call sit at least 2 blocks apart. If a small city
        // runs out of room the gap drops to 1 block, then to any free spot.
        val placed = ArrayList<Slot>()
        fun pick(): Slot {
            var tries = 0
            while (true) {
                val s = Slot(rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(4))
                tries++
                val gap = if (tries < 500) 2 else if (tries < 1000) 1 else 0
                if (placed.all { blockGap(it, s) >= gap } && used.add(key(s))) {
                    placed.add(s)
                    return s
                }
            }
        }
        val usedNames = HashSet<String>()
        station = pick()
        for (lv in Levels.hospitals[level - 1][idx]) {
            val s = pick()
            hospitals.add(Hospital(s, lv, genName(usedNames)))
            hospKey[key(s)] = lv
        }
        call = pick()
        makeEvents()
    }

    private fun makeEvents() {
        if (level < 2) return
        val count = if (level >= 4 && rnd.nextInt(100) < 40) 2 else 1
        val pool = mutableListOf(EV_STORM, EV_WORK, EV_TRAIN, EV_SCHOOL, EV_RUSH)
        repeat(count) {
            val e = pool.removeAt(rnd.nextInt(pool.size))
            when (e) {
                EV_STORM -> storm = true
                EV_WORK -> if (!pickWork()) return@repeat
                EV_TRAIN -> trainRow = rnd.nextInt(n)
                EV_SCHOOL -> {
                    val homes = (0 until n * n).filter { zone[it] == 0 }
                    schoolBlock = if (homes.isEmpty()) rnd.nextInt(n * n) else homes[rnd.nextInt(homes.size)]
                }
                else -> {
                    rushRow = 1 + rnd.nextInt(n - 1)
                    rushCol = 1 + rnd.nextInt(n - 1)
                }
            }
            events.add(e)
        }
    }

    // Close one road piece that isn't in front of the station, a hospital or the call,
    // and only if every corner can still reach every other one
    private fun pickWork(): Boolean {
        val keep = HashSet<Long>()
        keep.add(edgeKey(segNodes(station)))
        for (h in hospitals) keep.add(edgeKey(segNodes(h.slot)))
        keep.add(edgeKey(segNodes(call)))
        repeat(30) {
            val a: Int
            val b: Int
            if (rnd.nextBoolean()) {
                a = rnd.nextInt(m) * m + rnd.nextInt(n)
                b = a + 1
            } else {
                a = rnd.nextInt(n) * m + rnd.nextInt(m)
                b = a + m
            }
            if (keep.contains(edgeKey(intArrayOf(a, b)))) return@repeat
            workA = a
            workB = b
            if (reach(true) == m * m && reach(false) == m * m) return true
        }
        workA = -1
        workB = -1
        return false
    }

    private fun edgeKey(e: IntArray): Long = minOf(e[0], e[1]).toLong() * 100000L + maxOf(e[0], e[1])

    // The closed road piece, either way
    fun isWork(a: Int, b: Int): Boolean = workA >= 0 && ((a == workA && b == workB) || (a == workB && b == workA))

    // A road piece you can drive now: right way on a one-way street, and not closed for road work
    fun open(a: Int, b: Int): Boolean = canGo(a, b) && !isWork(a, b)

    // The street crosses the train tracks, which run through the middle of block row trainRow
    fun isTrain(a: Int, b: Int): Boolean = trainRow >= 0 && a % m == b % m && minOf(a / m, b / m) == trainRow

    // One of the four streets around the school block
    fun isSchool(a: Int, b: Int): Boolean {
        if (schoolBlock < 0) return false
        val bc = schoolBlock % n
        val br = schoolBlock / n
        val i1 = a % m
        val j1 = a / m
        val i2 = b % m
        val j2 = b / m
        return if (j1 == j2) minOf(i1, i2) == bc && (j1 == br || j1 == br + 1)
        else minOf(j1, j2) == br && (i1 == bc || i1 == bc + 1)
    }

    // On the main road row or column during rush hour
    fun isRush(a: Int, b: Int): Boolean {
        if (rushRow < 0) return false
        return if (a / m == b / m) a / m == rushRow else a % m == rushCol
    }

    // Time multiplier and crash multiplier from the road events
    private fun evTime(a: Int, b: Int): Double =
        (if (storm) 1.1 else 1.0) * (if (isSchool(a, b)) 1.5 else 1.0) * (if (isRush(a, b)) 1.4 else 1.0)

    private fun evRisk(a: Int, b: Int): Double =
        if (isSchool(a, b)) 0.0 else (if (storm) 1.3 else 1.0) * (if (isRush(a, b)) 1.5 else 1.0)

    // Blocks that share a side with block i
    private fun nbrBlocks(i: Int): IntArray {
        val c = i % n
        val r = i / n
        val l = ArrayList<Int>(4)
        if (c > 0) l.add(i - 1)
        if (c < n - 1) l.add(i + 1)
        if (r > 0) l.add(i - n)
        if (r < n - 1) l.add(i + n)
        return l.toIntArray()
    }

    // Block types are random, but every block touches at least one block of its own type,
    // so no type shows up as a single block. Each type gets at least 2 blocks.
    private fun makeZones() {
        val total = n * n
        for (attempt in 0 until 50) {
            for (i in 0 until total) {
                val r = rnd.nextInt(100)
                zone[i] = if (r < 45) 0 else if (r < 75) 1 else 2
            }
            for (t in 0..2) {
                if (zone.count { it == t } < 2) {
                    val i = rnd.nextInt(total)
                    val nb = nbrBlocks(i)
                    zone[i] = t
                    zone[nb[rnd.nextInt(nb.size)]] = t
                }
            }
            for (pass in 0 until 10) {
                var changed = false
                for (i in 0 until total) {
                    val nb = nbrBlocks(i)
                    if (nb.none { zone[it] == zone[i] }) {
                        zone[i] = zone[nb[rnd.nextInt(nb.size)]]
                        changed = true
                    }
                }
                if (!changed) break
            }
            if ((0..2).all { t -> zone.count { it == t } >= 2 }) return
        }
    }

    // Density is random, but it changes in steps: low only touches low or medium,
    // and high only touches medium or high.
    private fun makeDensity() {
        val total = n * n
        for (i in 0 until total) dens[i] = rnd.nextInt(3)
        for (pass in 0 until 30) {
            var changed = false
            for (i in 0 until total) {
                for (k in nbrBlocks(i)) {
                    if (abs(dens[i] - dens[k]) > 1) {
                        if (rnd.nextBoolean()) dens[i] = 1 else dens[k] = 1
                        changed = true
                    }
                }
            }
            if (!changed) break
        }
    }

    private fun genName(used: HashSet<String>): String {
        var name: String
        do {
            val suffix = suffixes[rnd.nextInt(suffixes.size)]
            name = if (rnd.nextInt(100) < 35) {
                saints[rnd.nextInt(saints.size)] + " " + suffix
            } else {
                val pre = if (rnd.nextInt(100) < 15) prefixes[rnd.nextInt(prefixes.size)] + " " else ""
                pre + roots[rnd.nextInt(roots.size)] + " " + suffix
            }
        } while (!used.add(name))
        return name
    }

    private fun roadQuality(): Int {
        val r = rnd.nextInt(100)
        return if (r < 70) 1 else if (r < 85) 2 else 0
    }

    fun key(s: Slot): Int = (s.br * n + s.bc) * 4 + s.corner

    // Blocks between two spots: 0 is the same block, 1 is a touching block (corners count)
    fun blockGap(a: Slot, b: Slot): Int = maxOf(abs(a.bc - b.bc), abs(a.br - b.br))

    // A new call at least 2 blocks from the station and every hospital, when there is room
    fun newCall() {
        val others = ArrayList<Slot>()
        others.add(station)
        for (h in hospitals) others.add(h.slot)
        var tries = 0
        while (true) {
            val s = Slot(rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(4))
            tries++
            val gap = if (tries < 500) 2 else if (tries < 1000) 1 else 0
            if (key(s) == key(station) || hospKey.containsKey(key(s)) || key(s) == key(call)) continue
            // Not on the block closed for road work
            val sg = segNodes(s)
            if (isWork(sg[0], sg[1])) continue
            if (others.all { blockGap(it, s) >= gap }) {
                call = s
                return
            }
        }
    }

    fun nodeX(i: Int): Float = 19f + 80f * i
    fun nodeY(j: Int): Float = 20f + 80f * j
    fun slotX(s: Slot): Float = 27f + 80f * s.bc + cellX[s.corner]
    fun slotY(s: Slot): Float = 28f + 80f * s.br + cellY[s.corner]
    fun accX(s: Slot): Float = when (s.corner) {
        0 -> nodeX(s.bc)
        3 -> nodeX(s.bc + 1)
        else -> slotX(s)
    }
    fun accY(s: Slot): Float = when (s.corner) {
        1 -> nodeY(s.br)
        2 -> nodeY(s.br + 1)
        else -> slotY(s)
    }

    fun nbr(u: Int): IntArray {
        val i = u % m
        val j = u / m
        val l = ArrayList<Int>(4)
        if (i > 0) l.add(u - 1)
        if (i < n) l.add(u + 1)
        if (j > 0) l.add(u - m)
        if (j < n) l.add(u + m)
        return l.toIntArray()
    }

    fun canGo(a: Int, b: Int): Boolean {
        val i1 = a % m
        val j1 = a / m
        val i2 = b % m
        val j2 = b / m
        if (j1 == j2 && abs(i1 - i2) == 1) {
            val o = owH[j1]
            return o == 0 || (o == 1 && i2 > i1) || (o == 2 && i2 < i1)
        }
        if (i1 == i2 && abs(j1 - j2) == 1) {
            val o = owV[i1]
            return o == 0 || (o == 1 && j2 < j1) || (o == 2 && j2 > j1)
        }
        return false
    }

    private fun reach(forward: Boolean): Int {
        val seen = BooleanArray(m * m)
        val stack = ArrayList<Int>()
        stack.add(0)
        seen[0] = true
        var count = 1
        while (stack.isNotEmpty()) {
            val u = stack.removeAt(stack.size - 1)
            for (v in nbr(u)) {
                val ok = if (forward) open(u, v) else open(v, u)
                if (ok && !seen[v]) {
                    seen[v] = true
                    count++
                    stack.add(v)
                }
            }
        }
        return count
    }

    private fun segNodes(s: Slot): IntArray = when (s.corner) {
        0 -> intArrayOf(s.br * m + s.bc, (s.br + 1) * m + s.bc)
        3 -> intArrayOf(s.br * m + s.bc + 1, (s.br + 1) * m + s.bc + 1)
        1 -> intArrayOf(s.br * m + s.bc, s.br * m + s.bc + 1)
        else -> intArrayOf((s.br + 1) * m + s.bc, (s.br + 1) * m + s.bc + 1)
    }

    fun startNodes(s: Slot): IntArray {
        val sg = segNodes(s)
        val l = ArrayList<Int>()
        if (canGo(sg[0], sg[1])) l.add(sg[1])
        if (canGo(sg[1], sg[0])) l.add(sg[0])
        return l.toIntArray()
    }

    fun goalNodes(s: Slot): IntArray {
        val sg = segNodes(s)
        val l = ArrayList<Int>()
        if (canGo(sg[0], sg[1])) l.add(sg[0])
        if (canGo(sg[1], sg[0])) l.add(sg[1])
        return l.toIntArray()
    }

    private fun blockTraffic(bc: Int, br: Int): Double? {
        if (bc < 0 || bc >= n || br < 0 || br >= n) return null
        return trafficTbl[zone[br * n + bc]][dens[br * n + bc]]
    }

    private fun segTime(a: Int, b: Int, std: Boolean = false): Double {
        val i1 = a % m
        val j1 = a / m
        val i2 = b % m
        val j2 = b / m
        val q: Int
        val list: List<Double?>
        if (j1 == j2) {
            val i = minOf(i1, i2)
            q = qH[j1 * n + i]
            list = listOf(blockTraffic(i, j1 - 1), blockTraffic(i, j1))
        } else {
            val j = minOf(j1, j2)
            q = qV[i1 * n + j]
            list = listOf(blockTraffic(i1 - 1, j), blockTraffic(i1, j))
        }
        val v = list.filterNotNull()
        var traffic = if (v.isEmpty()) 0.0 else v.average()
        if (traffic > 0.5 && !std) traffic = maxOf(0.0, traffic - heavy)
        // The train gates add a wait of 6 seconds on every street that crosses the tracks
        return 30.0 * maxOf(0.2, ratingTbl[q] + traffic + (if (std) targetDrv else drv)) * evTime(a, b) +
            (if (isTrain(a, b)) 6.0 else 0.0)
    }

    private fun blockRisk(tbl: Array<DoubleArray>, bc: Int, br: Int): Double? {
        if (bc < 0 || bc >= n || br < 0 || br >= n) return null
        return tbl[zone[br * n + bc]][dens[br * n + bc]]
    }

    fun segRisk(a: Int, b: Int, ped: Boolean): Double {
        val i1 = a % m
        val j1 = a / m
        val i2 = b % m
        val j2 = b / m
        val tbl = if (ped) pedTbl else mvcTbl
        val list: List<Double?> = if (j1 == j2) {
            val i = minOf(i1, i2)
            listOf(blockRisk(tbl, i, j1 - 1), blockRisk(tbl, i, j1))
        } else {
            val j = minOf(j1, j2)
            listOf(blockRisk(tbl, i1 - 1, j), blockRisk(tbl, i1, j))
        }
        val v = list.filterNotNull()
        return if (v.isEmpty()) 0.0 else v.average()
    }

    // Average traffic of the blocks beside a road piece, 0.1 (quiet) to 1.0 (busiest)
    fun segTraffic(a: Int, b: Int): Double {
        val i1 = a % m
        val j1 = a / m
        val i2 = b % m
        val j2 = b / m
        val list: List<Double?> = if (j1 == j2) {
            val i = minOf(i1, i2)
            listOf(blockTraffic(i, j1 - 1), blockTraffic(i, j1))
        } else {
            val j = minOf(j1, j2)
            listOf(blockTraffic(i1 - 1, j), blockTraffic(i1, j))
        }
        val v = list.filterNotNull()
        return if (v.isEmpty()) 0.1 else v.average()
    }

    // Traffic multiplier for a road piece: 1x on the quietest blocks up to 3x on the busiest
    fun segMult(a: Int, b: Int): Double = 1.0 + 2.0 * ((segTraffic(a, b) - 0.1) / 0.9).coerceIn(0.0, 1.0)

    // Accident chance for one road piece before skills. The base shrinks as cities grow,
    // so a normal run (both legs) adds up to about 5% in any city.
    fun segCrash(a: Int, b: Int): Double = 0.02 / m * segMult(a, b) * evRisk(a, b)

    fun accidentChance(x: Double, z: Double, s: Double, l: Double): Double {
        val c = 0.07 * (1.0 + x + distraction - (z + s + l))
        return Math.round(maxOf(0.01, c) * 1000.0) / 1000.0
    }

    private fun nodeTime(u: Int, std: Boolean = false): Double = (1.0 + (if (light[u]) 4.0 else 2.0)) * (if (std) 1.0 else cross)

    fun pathTime(path: List<Int>): Double {
        var total = 0.0
        for (k in 1 until path.size) {
            total += segTime(path[k - 1], path[k])
            if (k < path.size - 1) total += nodeTime(path[k])
        }
        return total
    }

    // Best time from starts to goals. std = true times it for the standard medic of this level.
    // avoid: road corners the route can't pass through (beside a wrong hospital, where the drive would end).
    fun fastest(starts: IntArray, goals: IntArray, std: Boolean = false, avoid: IntArray = IntArray(0)): Double {
        val total = m * m
        val dist = DoubleArray(total) { Double.MAX_VALUE }
        val prev = IntArray(total) { -1 }
        return search(starts, goals, std, dist, prev, avoid)
    }

    // The nodes of the best route from starts to goals, start first. Empty if there is none.
    fun fastestPath(starts: IntArray, goals: IntArray, std: Boolean = false, avoid: IntArray = IntArray(0)): List<Int> {
        val total = m * m
        val dist = DoubleArray(total) { Double.MAX_VALUE }
        val prev = IntArray(total) { -1 }
        search(starts, goals, std, dist, prev, avoid)
        var end = -1
        for (g in goals) if (dist[g] < Double.MAX_VALUE && (end == -1 || dist[g] < dist[end])) end = g
        if (end == -1) return emptyList()
        val out = ArrayList<Int>()
        var u = end
        while (u != -1) {
            out.add(u)
            u = prev[u]
        }
        out.reverse()
        return out
    }

    private fun search(starts: IntArray, goals: IntArray, std: Boolean, dist: DoubleArray, prev: IntArray, avoid: IntArray): Double {
        val total = m * m
        val seen = BooleanArray(total)
        val isGoal = BooleanArray(total)
        for (g in goals) isGoal[g] = true
        val blocked = BooleanArray(total)
        for (a in avoid) if (!isGoal[a]) blocked[a] = true
        for (s in starts) dist[s] = 0.0
        repeat(total) {
            var u = -1
            for (k in 0 until total) {
                if (!seen[k] && dist[k] < Double.MAX_VALUE && (u == -1 || dist[k] < dist[u])) u = k
            }
            if (u == -1) return@repeat
            seen[u] = true
            for (v in nbr(u)) {
                if (open(u, v) && !blocked[v]) {
                    val c = dist[u] + segTime(u, v, std) + (if (isGoal[v]) 0.0 else nodeTime(v, std))
                    if (c < dist[v]) {
                        dist[v] = c
                        prev[v] = u
                    }
                }
            }
        }
        var best = Double.MAX_VALUE
        for (g in goals) if (dist[g] < best) best = dist[g]
        return best
    }
}
