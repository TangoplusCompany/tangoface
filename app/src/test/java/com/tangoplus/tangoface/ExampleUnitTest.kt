package com.tangoplus.tangoface

import org.junit.Test

import org.junit.Assert.*
import java.lang.Math.toDegrees
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }


    @Test
    fun calculateAngle() {
//        val chin1 = Pair(559.2246, 340.30695) // 58 왼쪽 사각턱 점
//        val chin2 = Pair(644.1502,351.1653) // 152
//        val chin3 = Pair(740.2037, 344.6672) // 288 오른쪽 사각턱 점
//        val earFlap1 = Pair(556.297,284.96625)
//        val earFlap2 = Pair(750.1916,291.1114)
//        println("턱 중앙: " + calculateAngle(chin1.first, chin1.second, chin2.first, chin2.second, chin3.second, chin3.second))
//        println("턱 왼쪽: " + calculateAngle(earFlap1.first, earFlap1.second, chin1.first, chin1.second, chin2.second, chin2.second))
//        println("턱 오른쪽: " + calculateAngle(earFlap2.first, earFlap2.second, chin3.first, chin3.second, chin2.second, chin2.second))
        // 결과
        // 턱 중앙: 6.043153527582898
        //턱 왼쪽: 89.95927979725006
        //턱 오른쪽: 101.5209511705131

//        val chin1 = Pair(534.79504, 289.2024) // 58 왼쪽 사각턱 점
//        val chin2 = Pair(626.7436, 297.11832) // 152
//        val chin3 = Pair(722.2081, 289.97626) // 288 오른쪽 사각턱 점
//        val earFlap1 = Pair(528.77045,236.25986) // 234
//        val earFlap2 = Pair(728.8677, 237.04642) // 454
//        println("턱 중앙: " + calculateAngle(chin1.first, chin1.second, chin2.first, chin2.second, chin3.second, chin3.second))
//        println("턱 왼쪽: " + calculateAngle(earFlap1.first, earFlap1.second, chin1.first, chin1.second, chin2.second, chin2.second))
//        println("턱 오른쪽: " + calculateAngle(earFlap2.first, earFlap2.second, chin3.first, chin3.second, chin2.second, chin2.second))
//        // 턱 중앙: 3.705574502807713
//        // 턱 왼쪽: 85.4155140919514
//        // 턱 오른쪽: 98.13378969436341

        val chin1 = Pair(560.13184, 327.5407) // 58 왼쪽 사각턱 점
        val chin2 = Pair(627.47406, 322.58765) // 152
        val chin3 = Pair(708.4629, 335.13) // 288 오른쪽 사각턱 점
        val earFlap1 = Pair(554.329, 293.19363) // 234
        val earFlap2 = Pair(717.7128, 300.40973) // 454
        println("턱 중앙: " + calculateAngle(chin1.first, chin1.second, chin2.first, chin2.second, chin3.second, chin3.second))
        println("턱 왼쪽: " + calculateAngle(earFlap1.first, earFlap1.second, chin1.first, chin1.second, chin2.second, chin2.second))
        println("턱 오른쪽: " + calculateAngle(earFlap2.first, earFlap2.second, chin3.first, chin3.second, chin2.second, chin2.second))
        // 턱 중앙: 1.7499182116758887
        // 턱 왼쪽: 79.21608857457207
        // 턱 오른쪽: 103.05613305559454
    }

    @Test
    fun calculateSideHeadAngle() {
        val chin00 = Pair(534.357, 259.94064)
        val chin01 = Pair(531.6538, 279.92352)
        val chin02 = Pair(540.1704, 290.1008)

        val chin10 = Pair(716.28125, 255.25368)
        val chin11 = Pair(719.7607, 275.3083)
        val chin12 = Pair(711.40234, 286.522)

        println("좌측 함몰: " + "${180 - calculateAngle(chin00.first, chin00.second, chin01.first, chin01.second, chin02.second, chin02.second)}")
        println("우측 함몰: " + calculateAngle(chin10.first, chin10.second, chin11.first, chin11.second, chin12.second, chin12.second))
        // 좌측 함몰: 100.11656527602321
        // 우측 함몰: 81.63991900419478

        // 180에서 뺐을 때
        // 좌측 함몰: 79.88343472397679
        // 우측 함몰: 81.63991900419478

    }


    @Test
    fun calculateCheekSize() {
        val leftCheek = listOf(
            Pair(546.35944, 295.75433),
            Pair(557.7693, 297.8536),
            Pair(573.1019, 306.97113),
            Pair(564.8581, 332.6677),
            Pair(545.6726, 345.29987),
            Pair(532.1117, 320.8714),
            )
        val rightCheek = listOf(
            Pair(705.2586, 292.5131),
            Pair(693.5222, 295.18817),
            Pair(677.8716, 305.08008),
            Pair(686.4264, 330.89975),
            Pair(706.8309, 342.8121),
            Pair(720.3008, 317.6081),
        )
        println("왼쪽 광대 크기 " + calculatePolygonArea(leftCheek))
        println("오른쪽 광대 크기 " + calculatePolygonArea(rightCheek))
        // 절대좌표일때 / 100 해서 약 0.6 정도 차이남 이 부분은 1000이 맞는지 100이 맞는지 판단해야 함.
        // 왼쪽 광대 크기 12.410206332969828
        // 오른쪽 광대 크기 13.068035858355142
    }




    fun calculateAngle(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3:Double): Double {
        val v1x = x1 - x2
        val v1y = y1 - y2
        val v2x = x3 - x2
        val v2y = y3 - y2
        val dotProduct = v1x * v2x + v1y * v2y
        val magnitude1 = sqrt(v1x * v1x + v1y * v1y)
        val magnitude2 = sqrt(v2x * v2x + v2y * v2y)

        val cosTheta = dotProduct / (magnitude1 * magnitude2)
        val angleRadians = acos(cosTheta)
        return toDegrees(angleRadians)
    }

    fun calculatePolygonArea(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0  // 다각형이 안 됨

        var sum = 0.0
        val n = points.size

        for (i in 0 until n) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[(i + 1) % n]  // 마지막 점은 첫 번째와 연결
            sum += (x1 * y2) - (x2 * y1)
        }
        return (abs(sum) / 2.0) / 100
    }

    fun get3DDistance(p1: Triple<Float, Float, Float>, p2: Triple<Float, Float, Float>): Float {
        val dx = p2.first - p1.first
        val dy = p2.second - p1.second
        val dz = p2.third - p1.third
        return (sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))) * 100
    }

    @Test
    fun calculateDistance() {
        val point0 = Triple(0.0024269582f, -0.014702449f, 0.457f)
        val point1 = Triple(0.0024269582f, -0.014702449f, 0.457f)
//        (0.005133214, 0.0018166818, 0.463), (0.0044646533, 0.0018166818, 0.463)
        println(get3DDistance(point0, point1))
        println(get3DDistance(point1, point0))
    }
    @Test
    fun calculateExtent() {
        val points = listOf(
            Triple(-0.002965345f, -0.07009019f, 0.475f),
            Triple(-0.08932465f,  -0.052377924f,0.594f),
            Triple(-0.08953834f,  -0.033184625f,0.601f),
            Triple(-0.08617718f,  -0.011529851f,0.601f),
            Triple(-0.07733687f,   0.008961621f,0.579f),

            Triple(-0.06757554f,   0.022362107f,0.559f),
            Triple(-0.054863643f,  0.029091537f,0.521f),
            Triple(-0.047717165f,  0.038438145f,0.559f),
            Triple(-0.036751613f,  0.04246842f, 0.539f),
            Triple(-0.026041413f,  0.045204513f,0.526f),

            Triple(-0.015050208f,  0.046284523f,0.513f),
            Triple(-0.000233994f,  0.046008218f,0.502f)
        )

        val m = calculateAreaAndExtents(points)
        println("area = ${m.areaM2} m^2  (${m.areaCm2} cm^2)")
        println("d0-12 = ${m.d0to12_m} m  (${m.d0to12_cm} cm)")
        println("d0-maxX = ${m.d0toMaxX_m} m  (${m.d0toMaxX_cm} cm)")
    }

    fun calculate2DAreaWithDepthScale(
        points: List<Triple<Float, Float, Float>>,

        ): Float {
        // 유효성 체크
        if (points.size < 3) {
            return 0.0f
        }

        // x,y 추출 (Double로 변환)
        val xs = DoubleArray(points.size) { points[it].first.toDouble() }
        val ys = DoubleArray(points.size) { points[it].second.toDouble() }
        val n = xs.size

        // 1) 쇼레이스 공식으로 부호있는 면적(2A) 계산
        var sum = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += xs[i] * ys[j] - xs[j] * ys[i]
        }
        val signedArea2 = sum
        val area = kotlin.math.abs(signedArea2) * 0.5 // m^2

        // 2) 둘레 길이
        var peri = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = xs[j] - xs[i]
            val dy = ys[j] - ys[i]
            peri += kotlin.math.hypot(dx, dy)
        }

        return area.toFloat()
    }


    /** 첫 인덱스 ↔ 마지막 인덱스 직선 길이 (점이 2개 미만이면 0) */
    private fun chordFirstLast(points3D: List<Triple<Float, Float, Float>>): Double {
        if (points3D.size < 2) return 0.0
        val p0 = points3D.first()
        val pn = points3D.last()
        val dx = pn.first - p0.first
        val dy = pn.second - p0.second
        return kotlin.math.hypot(dx.toDouble(), dy.toDouble())
    }
    fun calculateAreaAndExtents(
        points: List<Triple<Float, Float, Float>>
    ): CheekMetrics {
        val n = points.size
        // ── 면적(쇼레이스) ─────────────────────────────
        val areaM2 = if (n >= 3) {
            var sum = 0f
            for (i in 0 until n) {
                val j = (i + 1) % n
                val (xi, yi) = points[i].let { it.first to it.second }
                val (xj, yj) = points[j].let { it.first to it.second }
                sum += xi * yj - xj * yi
            }
            kotlin.math.abs(sum * 0.5f)
        } else 0f

        // ── 거리: index 0 ↔ index 12 ──────────────────
        val d0to12_m = if (n > 11) {
            val p0 = points[0]; val p12 = points[11]
            val dx = p12.first - p0.first
            val dy = p12.second - p0.second
            kotlin.math.hypot(dx, dy)
        } else 0f

        // ── 거리: index 0 ↔ x가 최대인 점 ───────────────
        val d0toMaxX_m = if (n >= 2) {
            var maxIdx = 0
            var maxX = points[0].first
            for (i in 1 until n) {
                val x = points[i].first
                if (x > maxX) { maxX = x; maxIdx = i }
            }
            val p0 = points[0]; val pMax = points[maxIdx]
            val dx = pMax.first - p0.first
            val dy = pMax.second - p0.second
            kotlin.math.hypot(dx, dy)
        } else 0f

        return CheekMetrics(areaM2, d0to12_m, d0toMaxX_m)
    }
    data class CheekMetrics(
        val areaM2: Float,        // 다각형 면적 (m^2)
        val d0to12_m: Float,      // index0 ~ index12 직선거리 (m), 없으면 0f
        val d0toMaxX_m: Float     // index0 ~ (x가 최대인 점) 직선거리 (m), 없으면 0f
    ) {
        val areaCm2: Float get() = areaM2 * 10_000f
        val d0to12_cm: Float get() = d0to12_m * 100f
        val d0toMaxX_cm: Float get() = d0toMaxX_m * 100f
    }

}