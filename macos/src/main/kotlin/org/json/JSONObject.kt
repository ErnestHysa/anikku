@file:Suppress("unused")

package org.json

/**
 * Stub for [org.json.JSONObject] on macOS JVM.
 */
open class JSONObject {
    companion object {
        val NULL: Any? = null
    }

    private val map = linkedMapOf<String, Any?>()

    constructor()
    constructor(source: String?) {
        // No-op stub
    }
    constructor(map: Map<*, *>?) {
        // No-op stub
    }

    fun put(key: String, value: Any?): JSONObject = apply { map[key] = value }
    fun getString(key: String): String = map[key]?.toString() ?: ""
    fun getInt(key: String): Int = (map[key] as? Number)?.toInt() ?: 0
    fun getLong(key: String): Long = (map[key] as? Number)?.toLong() ?: 0L
    fun getDouble(key: String): Double = (map[key] as? Number)?.toDouble() ?: 0.0
    fun getBoolean(key: String): Boolean = map[key] as? Boolean ?: false
    fun optString(key: String, fallback: String = ""): String = map[key]?.toString() ?: fallback
    fun optInt(key: String, fallback: Int = 0): Int = (map[key] as? Number)?.toInt() ?: fallback
    fun optLong(key: String, fallback: Long = 0L): Long = (map[key] as? Number)?.toLong() ?: fallback
    fun optDouble(key: String, fallback: Double = 0.0): Double = (map[key] as? Number)?.toDouble() ?: fallback
    fun optBoolean(key: String, fallback: Boolean = false): Boolean = map[key] as? Boolean ?: fallback
    fun has(key: String): Boolean = key in map
    fun getJSONArray(key: String): JSONArray = map[key] as? JSONArray ?: JSONArray()
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun getJSONObject(key: String): JSONObject = map[key] as? JSONObject ?: JSONObject()
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun keys(): Iterator<String> = map.keys.iterator()
    fun length(): Int = map.size
    override fun toString(): String = map.toString()
    fun toString(indentFactor: Int): String = map.toString()
}
