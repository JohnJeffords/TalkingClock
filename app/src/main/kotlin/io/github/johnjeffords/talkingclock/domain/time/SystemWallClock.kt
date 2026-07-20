package io.github.johnjeffords.talkingclock.domain.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** System time whose zone is re-read instead of captured at process start. */
object SystemWallClock : Clock() {
    override fun instant(): Instant = Instant.now()
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
}
