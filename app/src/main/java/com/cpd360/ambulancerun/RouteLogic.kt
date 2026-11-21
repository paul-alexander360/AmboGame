package com.cpd360.ambulancerun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

object City {
    val xs = floatArrayOf(19f, 99f, 179f, 259f, 339f)
    val ys = floatArrayOf(20f, 100f, 180f, 260f)
    private val zones = arrayOf("rrcc", "rccc", "iirr")
    const val START = 16
    const val CALL = 7
    val STARTS = intArrayOf(16, 8)
    val STARTPT = arrayOf(floatArrayOf(50f, 260f), floatArrayOf(205f, 100f))
    val ENDPT = arrayOf(floatArrayOf(205f, 100f), floatArrayOf(285f, 180f))

    fun isGoal(stage: Int, n: Int): Boolean = if (stage == 0) n == CALL else (n == 13 || n == 14)

    private fun zt(r: Int, c: Int): Double? {
        if (r < 0 || r > 2 || c < 0 || c > 3) return null
        return if (zones[r][c] == 'r') 0.2 else 0.5
    }

    private fun isLight(n: Int): Boolean {
        val i = n % 5
        val j = n / 5
        return (i == 1 && j == 1) || (i == 2 && j == 1) || (i == 1 && j == 2)
    }

    fun canGo(a: Int, b: Int): Boolean {
        val i1 = a % 5
        val j1 = a / 5
        val i2 = b % 5
        val j2 = b / 5
        if (j1 == j2 && abs(i1 - i2) == 1) return j1 != 1 || i2 > i1
        if (i1 == i2 && abs(j1 - j2) == 1) return i1 != 2 || j2 < j1
        return false
    }

    private fun segTime(a: Int, b: Int): Double {
        val i1 = a % 5
        val j1 = a / 5
        val i2 = b % 5
        val j2 = b / 5
        val t: List<Double?> = if (j1 == j2) {
            val i = minOf(i1, i2)
            listOf(zt(j1 - 1, i), zt(j1, i))
        } else {
            val j = minOf(j1, j2)
            listOf(zt(j, i1 - 1), zt(j, i1))
        }
        val v = t.filterNotNull()
        val traffic = if (v.isEmpty()) 0.0 else v.average()
        return 30.0 * (1.0 + traffic + 0.2)
    }

    private fun nodeTime(n: Int): Double = 1.0 + (if (isLight(n)) 4.0 else 2.0)

    fun pathTime(path: List<Int>): Double {
        var total = 0.0
        for (k in 1 until path.size) {
            total += segTime(path[k - 1], path[k])
            if (k < path.size - 1) total += nodeTime(path[k])
        }
        return total
    }

    fun fastest(stage: Int): Double {
        val dist = DoubleArray(20) { Double.MAX_VALUE }
        val seen = BooleanArray(20)
        dist[STARTS[stage]] = 0.0
        repeat(20) {
            var u = -1
            for (n in 0 until 20) if (!seen[n] && (u == -1 || dist[n] < dist[u])) u = n
            if (u == -1 || dist[u] == Double.MAX_VALUE) return@repeat
            seen[u] = true
            for (v in 0 until 20) if (canGo(u, v)) {
                val c = dist[u] + segTime(u, v) + (if (isGoal(stage, v)) 0.0 else nodeTime(v))
                if (c < dist[v]) dist[v] = c
            }
        }
        var best = Double.MAX_VALUE
        for (n in 0 until 20) if (isGoal(stage, n) && dist[n] < best) best = dist[n]
        return best
    }

    fun rating(user: Double, fast: Double): Int {
        val r = user / fast
        return if (r <= 1.05) 0 else if (r <= 1.25) 1 else if (r <= 1.6) 2 else 3
    }

    fun fmt(s: Double): String {
        val t = Math.round(s).toInt()
        return "%d:%02d".format(t / 60, t % 60)
    }
}

class RouteState {
    var stage by mutableStateOf(0)
    val path = mutableStateListOf(City.STARTS[0])
    var done by mutableStateOf(false)
    var started by mutableStateOf(false)
    var leg1User by mutableStateOf(0.0)
    var leg1Fast by mutableStateOf(0.0)

    fun reset() {
        stage = 0
        path.clear()
        path.add(City.STARTS[0])
        done = false
        started = false
    }

    fun begin() {
        if (!done) started = true
    }

    fun startHospital() {
        leg1User = City.pathTime(path.toList())
        leg1Fast = City.fastest(0)
        stage = 1
        path.clear()
        path.add(City.STARTS[1])
        done = false
        started = false
    }

    fun move(n: Int) {
        if (done) return
        val last = path.last()
        if (n == last) return
        if (path.size > 1 && n == path[path.size - 2]) {
            path.removeAt(path.size - 1)
            return
        }
        if (City.canGo(last, n) && !path.contains(n)) {
            path.add(n)
            if (City.isGoal(stage, n)) done = true
        }
    }
}
