package com.cpd360.ambulancerun

import kotlin.math.abs
import kotlin.random.Random

class Slot(val bc: Int, val br: Int, val corner: Int)

class Hospital(val slot: Slot, val level: Int, val name: String)

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
    val driver: Double = Levels.driver[level - 1]
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
    private val prefixes = listOf("General", "Sacred")
    private val roots = listOf(
        "Memorial", "Regional", "University", "Health", "Community", "Mercy", "Baptist",
        "Presbyterian", "Methodist", "Valley", "County", "Samaritan", "Foundation"
    )
    private val saints = listOf(
        "St Joseph", "St Mary", "St Luke", "St Francis", "St John", "St Michael", "St Thomas", "St Anthony"
    )
    private val suffixes = listOf("Hospital", "Medical Center")

    init {
        for (i in 0 until n * n) {
            val r = rnd.nextInt(100)
            zone[i] = if (r < 45) 0 else if (r < 75) 1 else 2
            dens[i] = rnd.nextInt(3)
        }
        for (t in 0..2) {
            while (zone.count { it == t } < 2) {
                val big = (0..2).maxByOrNull { k -> zone.count { it == k } } ?: 0
                val cands = (0 until n * n).filter { zone[it] == big }
                zone[cands[rnd.nextInt(cands.size)]] = t
            }
        }
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
        fun pick(): Slot {
            while (true) {
                val s = Slot(rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(4))
                if (used.add(key(s))) return s
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

    fun newCall() {
        var s: Slot
        do {
            s = Slot(rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(4))
        } while (key(s) == key(station) || hospKey.containsKey(key(s)) || key(s) == key(call))
        call = s
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
                val ok = if (forward) canGo(u, v) else canGo(v, u)
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

    private fun segTime(a: Int, b: Int): Double {
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
        val traffic = if (v.isEmpty()) 0.0 else v.average()
        val drv = driver + (if (level == 8 && traffic > 0.5) -0.2 else 0.0)
        return 30.0 * maxOf(0.2, ratingTbl[q] + traffic + drv)
    }

    private fun nodeTime(u: Int): Double = 1.0 + (if (light[u]) 4.0 else 2.0)

    fun pathTime(path: List<Int>): Double {
        var total = 0.0
        for (k in 1 until path.size) {
            total += segTime(path[k - 1], path[k])
            if (k < path.size - 1) total += nodeTime(path[k])
        }
        return total
    }

    fun fastest(starts: IntArray, goals: IntArray): Double {
        val total = m * m
        val dist = DoubleArray(total) { Double.MAX_VALUE }
        val seen = BooleanArray(total)
        val isGoal = BooleanArray(total)
        for (g in goals) isGoal[g] = true
        for (s in starts) dist[s] = 0.0
        repeat(total) {
            var u = -1
            for (k in 0 until total) {
                if (!seen[k] && dist[k] < Double.MAX_VALUE && (u == -1 || dist[k] < dist[u])) u = k
            }
            if (u == -1) return@repeat
            seen[u] = true
            for (v in nbr(u)) {
                if (canGo(u, v)) {
                    val c = dist[u] + segTime(u, v) + (if (isGoal[v]) 0.0 else nodeTime(v))
                    if (c < dist[v]) dist[v] = c
                }
            }
        }
        var best = Double.MAX_VALUE
        for (g in goals) if (dist[g] < best) best = dist[g]
        return best
    }
}
