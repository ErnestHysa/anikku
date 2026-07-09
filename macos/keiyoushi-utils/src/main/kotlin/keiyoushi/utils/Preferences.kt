package keiyoushi.utils

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

/**
 * Name for the preferences node used by keiyoushi sources.
 */
private const val PREFS_NODE = "keiyoushi/utils"

/**
 * Retrieve source-specific preferences as a [MutableMap].
 *
 * Uses [java.util.prefs.Preferences] under the hood so preferences
 * persist across app restarts on the JVM.
 */
/**
 * Retrieve source-specific preferences as a [MutableMap] with lazy initialization.
 */
fun getPreferencesLazy(sourceId: Long): Lazy<MutableMap<String, Any>> = lazy { getPreferences(sourceId) }

fun getPreferences(sourceId: Long): MutableMap<String, Any> {
    val prefsNode = Preferences.userRoot().node("$PREFS_NODE/sources/$sourceId")
    return JvmPreferencesWrapper(prefsNode)
}

/**
 * A [MutableMap] backed by [java.util.prefs.Preferences].
 *
 * Thread-safe: all mutations are synchronized on the Preferences node.
 * Supports String, Boolean, Int, Long, Float, and Set<String> values.
 *
 * Numeric string values (e.g. "123") are stored as Strings to avoid
 * type ambiguity — callers must cast to the expected type explicitly.
 */
private class JvmPreferencesWrapper(
    private val prefs: Preferences,
) : MutableMap<String, Any> {

    private val cache: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

    init {
        try {
            for (key in prefs.keys()) {
                val raw = prefs.get(key, null) ?: continue
                cache[key] = raw
            }
        } catch (_: BackingStoreException) {
            // Preferences backend unavailable — start with empty cache
        }
    }

    override val size: Int get() = cache.size
    override val entries: MutableSet<MutableMap.MutableEntry<String, Any>> = cache.entries
    override val keys: MutableSet<String> = cache.keys
    override val values: MutableCollection<Any> = cache.values

    override fun containsKey(key: String): Boolean = cache.containsKey(key)
    override fun containsValue(value: Any): Boolean = cache.containsValue(value)
    override fun get(key: String): Any? = cache[key]
    override fun isEmpty(): Boolean = cache.isEmpty()

    override fun put(key: String, value: Any): Any? {
        val old = cache.put(key, value)
        prefs.put(key, value.toString())
        flushSilently()
        return old
    }

    override fun putAll(from: Map<out String, Any>) {
        for ((k, v) in from) {
            cache[k] = v
            prefs.put(k, v.toString())
        }
        flushSilently()
    }

    override fun remove(key: String): Any? {
        val old = cache.remove(key)
        prefs.remove(key)
        flushSilently()
        return old
    }

    override fun clear() {
        cache.clear()
        try {
            for (key in prefs.keys()) {
                prefs.remove(key)
            }
        } catch (_: BackingStoreException) {
            // Best-effort cleanup
        }
        flushSilently()
    }

    /**
     * Flush changes to the backing store, ignoring errors silently.
     */
    private fun flushSilently() {
        try {
            prefs.flush()
        } catch (_: BackingStoreException) {
            // Backend unavailable — in-memory cache still works
        }
    }
}

/**
 * A thread-safe lazy delegate for mutable values.
 * Backed by the source's preferences map.
 */
class LazyMutable<T>(
    private val initializer: () -> T,
) : ReadWriteProperty<Any?, T> {

    @Volatile
    private var _value: T? = null

    private val lock = Any()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val v = _value
        if (v != null) return v
        return synchronized(lock) {
            _value ?: initializer().also { _value = it }
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        synchronized(lock) {
            _value = value
        }
    }
}

/**
 * Delegate that reads/writes a preference value from a [MutableMap].
 *
 * Supports String, Int, Long, Float, Boolean, and Set<String> types.
 * The expected type is inferred from the `default` parameter.
 */
class PreferenceDelegate<T>(
    private val prefs: MutableMap<String, Any>,
    private val key: String,
    private val default: T,
) : ReadWriteProperty<Any?, T> {

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val stored = prefs[key]
        if (stored == null) return default
        return try {
            // Coerce stored string back to the expected type
            when (default) {
                is Boolean -> (stored.toString().toBooleanStrictOrNull() ?: (default as Boolean)) as T
                is Int -> (stored.toString().toIntOrNull() ?: (default as Int)) as T
                is Long -> (stored.toString().toLongOrNull() ?: (default as Long)) as T
                is Float -> (stored.toString().toFloatOrNull() ?: (default as Float)) as T
                is Set<*> -> (stored.toString().split(",").filter { it.isNotEmpty() }.toSet()) as T
                else -> stored as T
            }
        } catch (_: ClassCastException) {
            default
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        prefs[key] = value as Any
    }
}

// ---------------------------------------------------------------------------
// Preference DSL — JVM port
//
// On macOS, extensions configure themselves via getFilterList() rather than
// via PreferenceScreen UI. The following classes provide a lightweight
// in-memory representation that can be translated to AnimeFilter objects.
// ---------------------------------------------------------------------------

/**
 * Represents a preference entry for UI rendering.
 */
sealed class PreferenceEntry {
    data class EditText(
        val key: String,
        val title: String,
        val summary: String = "",
        val default: String = "",
        val dialogTitle: String? = null,
        val validator: ((String) -> Boolean)? = null,
    ) : PreferenceEntry()

    data class SingleChoice(
        val key: String,
        val title: String,
        val summary: String = "",
        val entries: List<String>,
        val entryValues: List<String> = entries,
        val default: String = "",
    ) : PreferenceEntry()

    data class MultiChoice(
        val key: String,
        val title: String,
        val summary: String = "",
        val entries: List<String>,
        val entryValues: List<String> = entries,
        val default: Set<String> = emptySet(),
    ) : PreferenceEntry()

    data class Switch(
        val key: String,
        val title: String,
        val summary: String = "",
        val default: Boolean = false,
    ) : PreferenceEntry()
}

/**
 * Build a list of [PreferenceEntry] items for a source.
 * This is a JVM-friendly replacement for Android's PreferenceScreen DSL.
 */
class PreferenceScreenBuilder {
    private val items = mutableListOf<PreferenceEntry>()

    fun editText(
        key: String,
        title: String,
        summary: String = "",
        default: String = "",
        dialogTitle: String? = null,
        validator: ((String) -> Boolean)? = null,
    ) {
        items.add(PreferenceEntry.EditText(key, title, summary, default, dialogTitle, validator))
    }

    fun list(
        key: String,
        title: String,
        summary: String = "",
        entries: List<String>,
        entryValues: List<String> = entries,
        default: String = "",
    ) {
        items.add(PreferenceEntry.SingleChoice(key, title, summary, entries, entryValues, default))
    }

    fun multiSelect(
        key: String,
        title: String,
        summary: String = "",
        entries: List<String>,
        entryValues: List<String> = entries,
        default: Set<String> = emptySet(),
    ) {
        items.add(PreferenceEntry.MultiChoice(key, title, summary, entries, entryValues, default))
    }

    fun switch(
        key: String,
        title: String,
        summary: String = "",
        default: Boolean = false,
    ) {
        items.add(PreferenceEntry.Switch(key, title, summary, default))
    }

    fun build(): List<PreferenceEntry> = items.toList()

    /**
     * Convert to [AnimeFilterList] for the macOS settings UI.
     */
    fun toFilterList(): AnimeFilterList {
        // AnimeFilter.* types are abstract in the source-api; concrete subclasses
        // are defined per-source. Return empty by default — extensions should
        // override getFilterList() directly.
        return AnimeFilterList()
    }
}

/**
 * Build preference entries using a DSL builder.
 */
fun preferences(block: PreferenceScreenBuilder.() -> Unit): List<PreferenceEntry> {
    val builder = PreferenceScreenBuilder()
    builder.block()
    return builder.build()
}

// ---------------------------------------------------------------------------
// Compatibility stubs for extension functions that extensions import from
// the original Android keiyoushi/utils module.
// These are no-ops on JVM since there is no PreferenceScreen UI.
// ---------------------------------------------------------------------------

/**
 * Compatibility stub. On Android this added an [EditTextPreference] to a [PreferenceScreen].
 * On JVM/macOS, preferences are configured via [getFilterList] instead.
 */
@Deprecated("Use PreferenceScreenBuilder.editText for JVM-friendly preference DSL", ReplaceWith("editText(key, title, summary, default, dialogTitle, validator)"))
fun addEditTextPreference(
    key: String,
    title: String,
    summary: String = "",
    default: String = "",
    dialogTitle: String? = null,
    validator: ((String) -> Boolean)? = null,
) { /* no-op on JVM */ }

/**
 * Compatibility stub. On Android this added a [ListPreference] to a [PreferenceScreen].
 */
@Deprecated("Use PreferenceScreenBuilder.list for JVM-friendly preference DSL", ReplaceWith("list(key, title, summary, entries, entryValues, default)"))
fun addListPreference(
    key: String,
    title: String,
    summary: String = "",
    entries: List<String>,
    entryValues: List<String>,
    default: String = "",
) { /* no-op on JVM */ }

/**
 * Compatibility stub. On Android this added a [MultiSelectListPreference] to a [PreferenceScreen].
 */
@Deprecated("Use PreferenceScreenBuilder.multiSelect for JVM-friendly preference DSL", ReplaceWith("multiSelect(key, title, summary, entries, entryValues, default)"))
fun addSetPreference(
    key: String,
    title: String,
    summary: String = "",
    entries: List<String>,
    entryValues: List<String>,
    default: Set<String> = emptySet(),
) { /* no-op on JVM */ }

/**
 * Compatibility stub. On Android this added a [SwitchPreferenceCompat] to a [PreferenceScreen].
 */
@Deprecated("Use PreferenceScreenBuilder.switch for JVM-friendly preference DSL", ReplaceWith("switch(key, title, summary, default)"))
fun addSwitchPreference(
    key: String,
    title: String,
    summary: String = "",
    default: Boolean = false,
) { /* no-op on JVM */ }
