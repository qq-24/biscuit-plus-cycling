package net.telent.biscuit

// Feature: cycling-ux-enhancements, Property 4: GPX Waypoint 往返属性

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Property 4: GPX Waypoint 往返属性
 *
 * 对任意 Waypoint 列表，序列化为 GPX 后反序列化应产生等价列表。
 * serialize(trackpoints, name, waypoints) → deserializeFull → waypoints 应一致。
 */
class GpxWaypointRoundTripPropertyTest : FunSpec({

    test("Property 4: GPX Waypoint 序列化后反序列化应还原等价列表") {
        val latArb = Arb.double(-90.0, 90.0)
        val lngArb = Arb.double(-180.0, 180.0)
        val tsArb = Arb.long(1_000_000_000_000L, 2_000_000_000_000L)
        val countArb = Arb.int(0, 10)

        checkAll(50, countArb, latArb, lngArb, tsArb) { count, baseLat, baseLng, baseTs ->
            // 生成 waypoints
            val waypoints = (1..count).map { i ->
                Waypoint(
                    index = i,
                    lat = (baseLat + i * 0.001).coerceIn(-90.0, 90.0),
                    lng = (baseLng + i * 0.001).coerceIn(-180.0, 180.0),
                    timestamp = Instant.ofEpochMilli(baseTs + i * 1000L)
                )
            }

            // 生成一些 trackpoints 作为载体
            val trackpoints = (1..3).map { i ->
                Trackpoint(
                    timestamp = Instant.ofEpochMilli(baseTs + i * 500L),
                    lat = baseLat + i * 0.0001,
                    lng = baseLng + i * 0.0001,
                    speed = 10.0f
                )
            }

            val gpxXml = GpxSerializer.serialize(trackpoints, "测试骑行", waypoints)
            val result = GpxSerializer.deserializeFull(gpxXml)

            // 验证 waypoint 数量一致
            result.waypoints.size shouldBe waypoints.size

            // 验证每个 waypoint 的字段一致
            for (i in waypoints.indices) {
                val original = waypoints[i]
                val parsed = result.waypoints[i]
                parsed.index shouldBe original.index
                parsed.lat shouldBe original.lat
                parsed.lng shouldBe original.lng
                // 时间戳精度：GPX 使用 ISO 格式，毫秒级精度
                parsed.timestamp.toEpochMilli() shouldBe original.timestamp.toEpochMilli()
            }

            // 验证 trackpoints 也正确还原
            result.trackpoints.size shouldBe trackpoints.size
        }
    }

    test("空 waypoints 列表的往返属性") {
        val trackpoints = listOf(
            Trackpoint(
                timestamp = Instant.parse("2025-01-01T00:00:00Z"),
                lat = 39.9, lng = 116.4, speed = 15.0f
            )
        )
        val gpxXml = GpxSerializer.serialize(trackpoints, "空标记测试", emptyList())
        val result = GpxSerializer.deserializeFull(gpxXml)

        result.waypoints.size shouldBe 0
        result.trackpoints.size shouldBe 1
    }
})
