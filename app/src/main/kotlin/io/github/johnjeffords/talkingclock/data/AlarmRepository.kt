package io.github.johnjeffords.talkingclock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/**
 * Persists the alarm list as one JSON array in the settings DataStore.
 * JSON (via Android's built-in org.json — no new dependency) because
 * alarms are structured records, unlike the flat settings keys; a handful
 * of alarms doesn't warrant a database.
 *
 * Serialization is defensive on READ: a corrupt entry is dropped rather
 * than crashing the list — losing one alarm beats losing the app.
 */
class AlarmRepository(private val dataStore: DataStore<Preferences>) {

    val alarms: Flow<List<Alarm>> = dataStore.data.map { prefs ->
        parseAlarms(prefs[KEY_ALARMS].orEmpty())
    }

    /** Insert or replace (matched by [Alarm.id]). */
    suspend fun upsert(alarm: Alarm) {
        dataStore.edit { prefs ->
            val current = parseAlarms(prefs[KEY_ALARMS].orEmpty())
            val updated = current.filterNot { it.id == alarm.id } + alarm
            prefs[KEY_ALARMS] = serializeAlarms(updated.sortedBy { it.hour * 60 + it.minute })
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val current = parseAlarms(prefs[KEY_ALARMS].orEmpty())
            prefs[KEY_ALARMS] = serializeAlarms(current.filterNot { it.id == id })
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = parseAlarms(prefs[KEY_ALARMS].orEmpty())
            prefs[KEY_ALARMS] = serializeAlarms(
                current.map { if (it.id == id) it.copy(enabled = enabled) else it },
            )
        }
    }

    // --- JSON mapping ---------------------------------------------------------

    private fun serializeAlarms(alarms: List<Alarm>): String {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(
                JSONObject().apply {
                    put("id", alarm.id)
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("days", JSONArray(alarm.days.map { it.name }))
                    put("label", alarm.label)
                    put("announceTime", alarm.announceTime)
                    put("vibrate", alarm.vibrate)
                    alarm.handoffIntervalSeconds?.let { put("handoffIntervalSeconds", it) }
                    put("handoffMinutes", alarm.handoffMinutes)
                    put("enabled", alarm.enabled)
                },
            )
        }
        return array.toString()
    }

    private fun parseAlarms(json: String): List<Alarm> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                runCatching { parseAlarm(array.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseAlarm(o: JSONObject): Alarm = Alarm(
        id = o.getString("id"),
        hour = o.getInt("hour"),
        minute = o.getInt("minute"),
        days = o.optJSONArray("days")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                DayOfWeek.entries.find { it.name == array.getString(i) }
            }.toSet()
        } ?: emptySet(),
        label = o.optString("label"),
        announceTime = o.optBoolean("announceTime", true),
        vibrate = o.optBoolean("vibrate", true),
        handoffIntervalSeconds = if (o.has("handoffIntervalSeconds")) {
            o.getInt("handoffIntervalSeconds")
        } else {
            null
        },
        handoffMinutes = o.optInt("handoffMinutes", 30),
        enabled = o.optBoolean("enabled", true),
    )

    companion object {
        private val KEY_ALARMS = stringPreferencesKey("alarms_json")
    }
}
