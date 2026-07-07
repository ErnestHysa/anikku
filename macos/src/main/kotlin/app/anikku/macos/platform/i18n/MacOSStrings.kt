package app.anikku.macos.platform.i18n

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Simple string resource loader for the macOS module.
 *
 * Loads strings from `MR/base/strings.xml` on the classpath using a proper
 * DOM XML parser. Supports standard Android/Moko-style positional format
 * arguments (e.g. `%1$s`, `%2$d`) via `java.util.Formatter`.
 *
 * Uses the Moko-standard `MR/base/` resource directory structure so that
 * converting to the full Moko Resources Gradle plugin later requires no
 * file moves — only adding the plugin and dropping this loader.
 *
 * Note: The Moko Resources Gradle plugin requires `kotlin("multiplatform")`.
 * Since the macOS project currently uses `kotlin("jvm")` (for `compose.desktop`
 * compatibility), we load resources at runtime via the classpath instead.
 */
object MacOSStrings {

    private val strings = mutableMapOf<String, String>()

    init {
        loadStrings()
    }

    private fun loadStrings() {
        try {
            val stream: InputStream? = javaClass.classLoader.getResourceAsStream("MR/base/strings.xml")
            if (stream != null) {
                parseAndroidStringResources(stream)
                stream.close()
            }
        } catch (e: Exception) {
            System.err.println("MacOSStrings: Failed to load string resources: ${e.message}")
        }
    }

    /**
     * Parse an Android-style string resource XML using a proper DOM parser.
     * Handles the `<resources><string name="key">value</string></resources>` format.
     */
    private fun parseAndroidStringResources(inputStream: InputStream) {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        doc.documentElement.normalize()

        val nodeList = doc.getElementsByTagName("string")
        for (i in 0 until nodeList.length) {
            val element = nodeList.item(i) as? Element ?: continue
            val key = element.getAttribute("name")
            val value = element.textContent
            if (key.isNotEmpty()) {
                strings[key] = value
            }
        }
    }

    /**
     * Get a string by its resource name.
     * @param key The string resource name (e.g., "app_name", "label_library")
     * @return The localized string, or the key itself if not found
     */
    fun get(key: String): String {
        return strings[key] ?: key
    }

    /**
     * Get a formatted string with arguments.
     *
     * Supports both:
     * - Positional format: `"Updated to v%1$s"` with args `"1.0"` (Android/Moko style)
     * - Simple format: `"HTTP %d error"` with args `404` (Java style)
     *
     * @param key The string resource name
     * @param args Format arguments
     * @return The formatted string, or the raw template if formatting fails
     */
    fun getFormatted(key: String, vararg args: Any): String {
        val template = get(key)
        if (template == key || args.isEmpty()) return template
        return try {
            // Use java.util.Formatter to support both %s and %1$s positional formats
            // NOTE: Bare '%' characters in template strings will cause formatting errors.
            // Guard against templates without format specifiers to avoid unnecessary exceptions.
            if (!template.contains('%')) return template
            template.format(*args)
        } catch (e: Exception) {
            System.err.println("MacOSStrings: Format error for key='$key', template='$template': ${e.message}")
            template
        }
    }

    /**
     * Check if a string resource exists.
     */
    fun containsKey(key: String): Boolean {
        return strings.containsKey(key)
    }

    /**
     * Get the number of loaded strings (useful for debugging).
     */
    fun size(): Int = strings.size
}
