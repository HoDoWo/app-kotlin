package com.hodowo.data

import com.hodowo.viewmodel.ManualsCache
import com.hodowo.viewmodel.RecentManual
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Keeps the Home lists as JSON files in [directory] (the app's private storage),
 * so "Recent Manuals" and "Most popular" open instantly and also work offline.
 *
 * Errors are never thrown: an unreadable file simply means "no saved copy".
 */
class FileManualsCache(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ManualsCache {

    override suspend fun load(name: String): List<RecentManual>? = withContext(ioDispatcher) {
        val file = fileOf(name)
        if (!file.isFile) return@withContext null

        try {
            ManualJson.parse(JSONObject(file.readText()))
        } catch (_: IOException) {
            null
        } catch (_: JSONException) {
            null
        }
    }

    override suspend fun save(name: String, manuals: List<RecentManual>) {
        withContext(ioDispatcher) {
            writeAtomically(fileOf(name), ManualJson.toJson(manuals).toString())
        }
    }

    private fun fileOf(name: String): File =
        File(directory, name.replace(UNSAFE_CHARACTERS, "_") + ".json")

    companion object {
        private val UNSAFE_CHARACTERS = Regex("[^A-Za-z0-9_-]")
    }
}

/**
 * Writes [text] to a temporary file and then renames it, so a crash in the
 * middle of a write never leaves a half-written file behind.
 */
internal fun writeAtomically(file: File, text: String) {
    try {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            file.writeText(text)
            temporary.delete()
        }
    } catch (_: IOException) {
        // Saving the copy is best effort: the app still works without it.
    }
}
