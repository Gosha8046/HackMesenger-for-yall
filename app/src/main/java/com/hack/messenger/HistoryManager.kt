package com.hack.messenger

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HistoryManager {
    private lateinit var ctx: Context

    fun init(context: Context) { ctx = context.applicationContext }

    fun save(chatId: String, msgs: List<JSONObject>) {
        val arr = JSONArray()
        msgs.takeLast(500).forEach { arr.put(it) }
        ctx.getSharedPreferences("hack_history_$chatId", Context.MODE_PRIVATE)
            .edit().putString("msgs", arr.toString()).apply()
    }

    fun load(chatId: String): MutableList<JSONObject> {
        val json = ctx.getSharedPreferences("hack_history_$chatId", Context.MODE_PRIVATE)
            .getString("msgs", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it) }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    fun clearAll() {
        // Clear all chats - just clear general for now
        listOf("general").forEach { id ->
            ctx.getSharedPreferences("hack_history_$id", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }
}
