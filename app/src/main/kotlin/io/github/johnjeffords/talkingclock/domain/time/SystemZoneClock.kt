package io.github.johnjeffords.talkingclock.domain.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The device clock, re-reading the CURRENT default time zone on every call.
 *
 * [Clock.systemDefaultZone] captures `ZoneId.systemDefault()` once, at
 * construction. This app's clock objects are process-scoped singletons, so a
 * captured zone means that after the device changes time zone — travel, or
 * an automatic network update — the app keeps displaying AND speaking the
 * old local time until the process happens to restart. For a talking clock
 * that is the worst possible failure: it states the wrong time confidently.
 *
 * Reading the zone per call costs a cached lookup and removes the whole
 * class of staleness, so no time-zone broadcast is needed to keep the
 * display and announcements honest. (Alarms are different — AlarmManager
 * holds absolute epoch times that must be recomputed on a zone change; see
 * TimeChangeReceiver.)
 */
object SystemZoneClock : Clock() {

    override fun getZone(): ZoneId = ZoneId.systemDefault()

    /** A fixed-zone clock, per the [Clock] contract. */
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
