package com.userexec.soneme.trend.data

import android.content.Context
import com.userexec.soneme.trend.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class RegistryStore(context: Context) {
    private val prefs = context.getSharedPreferences("trend_registry", Context.MODE_PRIVATE)

    fun load(): RegistryState {
        val raw = prefs.getString("state", null) ?: return RegistryState()
        return try {
            val root = JSONObject(raw)
            val datums = mutableListOf<DatumDefinition>()
            val d = root.optJSONArray("datums") ?: JSONArray()
            for (i in 0 until d.length()) {
                val o = d.getJSONObject(i)
                datums += DatumDefinition(o.getString("uid"), o.getString("name"), o.getString("csvFilename"), o.optInt("order", i))
            }
            val correlations = mutableListOf<CorrelationDefinition>()
            val c = root.optJSONArray("correlations") ?: JSONArray()
            for (i in 0 until c.length()) {
                val o = c.getJSONObject(i)
                val members = o.optJSONArray("datumUids") ?: JSONArray()
                correlations += CorrelationDefinition(
                    o.getString("uid"), o.getString("name"),
                    List(members.length()) { members.getString(it) }, o.optInt("order", i)
                )
            }
            RegistryState(datums.sortedBy { it.order }, correlations.sortedBy { it.order })
        } catch (_: Exception) {
            RegistryState()
        }
    }

    fun save(state: RegistryState) {
        val root = JSONObject()
        root.put("datums", JSONArray().apply {
            state.datums.sortedBy { it.order }.forEachIndexed { i, datum ->
                put(JSONObject().apply {
                    put("uid", datum.uid); put("name", datum.name); put("csvFilename", datum.csvFilename); put("order", i)
                })
            }
        })
        root.put("correlations", JSONArray().apply {
            state.correlations.sortedBy { it.order }.forEachIndexed { i, correlation ->
                put(JSONObject().apply {
                    put("uid", correlation.uid); put("name", correlation.name); put("order", i)
                    put("datumUids", JSONArray(correlation.datumUids))
                })
            }
        })
        prefs.edit().putString("state", root.toString()).apply()
    }

    fun addDatum(name: String, filename: String): DatumDefinition {
        val state = load()
        val datum = DatumDefinition(UUID.randomUUID().toString(), name, filename, state.datums.size)
        save(state.copy(datums = state.datums + datum))
        return datum
    }

    fun updateDatum(updated: DatumDefinition) {
        val s = load()
        save(s.copy(datums = s.datums.map { if (it.uid == updated.uid) updated else it }))
    }

    fun deleteDatum(uid: String) {
        val s = load()
        val datums = s.datums.filterNot { it.uid == uid }.mapIndexed { i, d -> d.copy(order = i) }
        val correlations = s.correlations.mapNotNull { c ->
            val members = c.datumUids.filterNot { it == uid }
            if (members.size < 2) null else c.copy(datumUids = members)
        }.mapIndexed { i, c -> c.copy(order = i) }
        save(RegistryState(datums, correlations))
    }

    fun moveDatumUp(uid: String) {
        val s = load(); val list = s.datums.sortedBy { it.order }.toMutableList(); val i = list.indexOfFirst { it.uid == uid }
        if (i > 0) { val t = list[i - 1]; list[i - 1] = list[i]; list[i] = t; save(s.copy(datums = list.mapIndexed { n, d -> d.copy(order = n) })) }
    }

    fun addCorrelation(name: String, members: List<String>): CorrelationDefinition {
        val s = load(); val c = CorrelationDefinition(UUID.randomUUID().toString(), name, members, s.correlations.size)
        save(s.copy(correlations = s.correlations + c)); return c
    }

    fun updateCorrelation(updated: CorrelationDefinition) {
        val s = load(); save(s.copy(correlations = s.correlations.map { if (it.uid == updated.uid) updated else it }))
    }

    fun deleteCorrelation(uid: String) {
        val s = load(); save(s.copy(correlations = s.correlations.filterNot { it.uid == uid }.mapIndexed { i, c -> c.copy(order = i) }))
    }

    fun moveCorrelationUp(uid: String) {
        val s = load(); val list = s.correlations.sortedBy { it.order }.toMutableList(); val i = list.indexOfFirst { it.uid == uid }
        if (i > 0) { val t = list[i - 1]; list[i - 1] = list[i]; list[i] = t; save(s.copy(correlations = list.mapIndexed { n, c -> c.copy(order = n) })) }
    }
}
