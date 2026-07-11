@file:Suppress("unused")

package org.json

/**
 * Stub for [org.json.JSONArray] on macOS JVM.
 */
open class JSONArray {
    private val list = mutableListOf<Any?>()

    constructor()
    constructor(source: String?) {
        // No-op stub
    }
    constructor(collection: Collection<*>?) {
        collection?.forEach { list.add(it) }
    }

    fun put(value: Any?): JSONArray = apply { list.add(value) }
    fun put(index: Int, value: Any?): JSONArray = apply {
        while (list.size <= index) list.add(null)
        list[index] = value
    }
    fun getString(index: Int): String = list.getOrElse(index) { "" }?.toString() ?: ""
    fun getInt(index: Int): Int = (list.getOrNull(index) as? Number)?.toInt() ?: 0
    fun getLong(index: Int): Long = (list.getOrNull(index) as? Number)?.toLong() ?: 0L
    fun getDouble(index: Int): Double = (list.getOrNull(index) as? Number)?.toDouble() ?: 0.0
    fun getBoolean(index: Int): Boolean = list.getOrNull(index) as? Boolean ?: false
    fun optString(index: Int, fallback: String = ""): String = list.getOrNull(index)?.toString() ?: fallback
    fun getJSONObject(index: Int): JSONObject = list.getOrNull(index) as? JSONObject ?: JSONObject()
    fun optJSONObject(index: Int): JSONObject? = list.getOrNull(index) as? JSONObject
    fun length(): Int = list.size
    override fun toString(): String = list.toString()
    fun toString(indentFactor: Int): String = list.toString()
}
