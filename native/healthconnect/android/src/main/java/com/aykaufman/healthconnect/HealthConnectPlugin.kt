package com.aykaufman.healthconnect

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@CapacitorPlugin(name = "HealthConnect")
class HealthConnectPlugin : Plugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val perms = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    private fun client() = HealthConnectClient.getOrCreate(context)
    private fun sdk() = HealthConnectClient.getSdkStatus(context)

    private fun grantedResult(granted: Set<String>): JSObject {
        val r = JSObject()
        r.put("available", true)
        r.put("connected", granted.containsAll(perms))
        r.put("canWriteWorkouts", granted.contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class)))
        r.put("canReadWeight", granted.contains(HealthPermission.getReadPermission(WeightRecord::class)))
        r.put("canWriteWeight", granted.contains(HealthPermission.getWritePermission(WeightRecord::class)))
        r.put("canReadHeartRate", granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class)))
        return r
    }

    @PluginMethod
    fun status(call: PluginCall) {
        val s = sdk()
        if (s != HealthConnectClient.SDK_AVAILABLE) {
            val r = JSObject()
            r.put("available", false)
            r.put("needsInstall", s == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
            r.put("connected", false)
            call.resolve(r)
            return
        }
        scope.launch {
            try {
                call.resolve(grantedResult(client().permissionController.getGrantedPermissions()))
            } catch (e: Exception) {
                call.reject(e.message ?: "status failed")
            }
        }
    }

    @PluginMethod
    fun request(call: PluginCall) {
        if (sdk() != HealthConnectClient.SDK_AVAILABLE) {
            call.reject("Health Connect is not available")
            return
        }
        val intent = PermissionController.createRequestPermissionResultContract().createIntent(context, perms)
        startActivityForResult(call, intent, "onPermissions")
    }

    @ActivityCallback
    private fun onPermissions(call: PluginCall?, result: ActivityResult) {
        if (call == null) return
        scope.launch {
            try {
                call.resolve(grantedResult(client().permissionController.getGrantedPermissions()))
            } catch (e: Exception) {
                call.reject(e.message ?: "permission check failed")
            }
        }
    }

    @PluginMethod
    fun saveWorkout(call: PluginCall) {
        val startMs = call.data.optLong("start", 0L)
        val endMs = call.data.optLong("end", 0L)
        if (startMs <= 0L || endMs <= startMs) {
            call.reject("start and end are required")
            return
        }
        val title = call.getString("title") ?: "Strength training"
        val kcal = call.data.optDouble("kcal", 0.0).let { if (it.isNaN()) 0.0 else it }
        val start = Instant.ofEpochMilli(startMs)
        val end = Instant.ofEpochMilli(endMs)
        val rules = ZoneId.systemDefault().rules
        scope.launch {
            try {
                val records = mutableListOf<Record>(
                    ExerciseSessionRecord(
                        startTime = start, startZoneOffset = rules.getOffset(start),
                        endTime = end, endZoneOffset = rules.getOffset(end),
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                        title = title
                    )
                )
                if (kcal > 0) {
                    records.add(
                        ActiveCaloriesBurnedRecord(
                            startTime = start, startZoneOffset = rules.getOffset(start),
                            endTime = end, endZoneOffset = rules.getOffset(end),
                            energy = Energy.kilocalories(kcal)
                        )
                    )
                }
                client().insertRecords(records)
                call.resolve()
            } catch (e: Exception) {
                call.reject(e.message ?: "save failed")
            }
        }
    }

    @PluginMethod
    fun saveWeight(call: PluginCall) {
        val timeMs = call.data.optLong("time", System.currentTimeMillis())
        val kg = call.data.optDouble("kg", 0.0)
        if (kg.isNaN() || kg <= 0) {
            call.reject("kg is required")
            return
        }
        val time = Instant.ofEpochMilli(timeMs)
        scope.launch {
            try {
                client().insertRecords(
                    listOf(WeightRecord(time = time, zoneOffset = ZoneId.systemDefault().rules.getOffset(time), weight = Mass.kilograms(kg)))
                )
                call.resolve()
            } catch (e: Exception) {
                call.reject(e.message ?: "save failed")
            }
        }
    }

    @PluginMethod
    fun readWeights(call: PluginCall) {
        val sinceMs = call.data.optLong("since", System.currentTimeMillis() - 90L * 24 * 3600 * 1000)
        scope.launch {
            try {
                val res = client().readRecords(
                    ReadRecordsRequest(WeightRecord::class, timeRangeFilter = TimeRangeFilter.after(Instant.ofEpochMilli(sinceMs)))
                )
                val arr = JSArray()
                for (w in res.records) {
                    val o = JSObject()
                    o.put("time", w.time.toEpochMilli())
                    o.put("kg", w.weight.inKilograms)
                    o.put("source", w.metadata.dataOrigin.packageName)
                    arr.put(o)
                }
                val r = JSObject()
                r.put("weights", arr)
                call.resolve(r)
            } catch (e: Exception) {
                call.reject(e.message ?: "read failed")
            }
        }
    }

    @PluginMethod
    fun readHeartRate(call: PluginCall) {
        val startMs = call.data.optLong("start", 0L)
        val endMs = call.data.optLong("end", 0L)
        if (startMs <= 0L || endMs <= startMs) {
            call.reject("start and end are required")
            return
        }
        val start = Instant.ofEpochMilli(startMs)
        val end = Instant.ofEpochMilli(endMs)
        scope.launch {
            try {
                val res = client().readRecords(
                    ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
                )
                val arr = JSArray()
                for (rec in res.records) {
                    for (s in rec.samples) {
                        if (s.time.isBefore(start) || s.time.isAfter(end)) continue
                        val o = JSObject()
                        o.put("time", s.time.toEpochMilli())
                        o.put("bpm", s.beatsPerMinute)
                        arr.put(o)
                    }
                }
                val r = JSObject()
                r.put("samples", arr)
                call.resolve(r)
            } catch (e: Exception) {
                call.reject(e.message ?: "read failed")
            }
        }
    }

    @PluginMethod
    fun openSettings(call: PluginCall) {
        try {
            val i = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "could not open settings")
        }
    }

    @PluginMethod
    fun install(call: PluginCall) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "could not open the store")
        }
    }
}
