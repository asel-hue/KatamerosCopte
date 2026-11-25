package com.copte.katameros.data

import org.json.JSONException
import org.json.JSONObject

data class FileEntry(val sha256: String, val size: Long)

/** `katameros/manifest.json` (écrit par `scripts/validate_content.py --write`). */
data class ContentManifest(
    val schemaVersion: Int,
    val contentVersion: Int,
    val updatedAt: String,
    val languages: List<String>,
    val files: Map<String, FileEntry>,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val FILE_NAME = "manifest.json"

        @Throws(JSONException::class)
        fun parse(json: String): ContentManifest {
            val root = JSONObject(json)
            val langs = root.getJSONArray("languages")
            val files = root.getJSONObject("files")
            return ContentManifest(
                schemaVersion = root.getInt("schemaVersion"),
                contentVersion = root.getInt("contentVersion"),
                updatedAt = root.optString("updatedAt", ""),
                languages = (0 until langs.length()).map { langs.getJSONObject(it).getString("code") },
                files = files.keys().asSequence().associateWith { key ->
                    if (key.contains("..") || key.startsWith("/")) throw JSONException("Chemin invalide : $key")
                    val e = files.getJSONObject(key)
                    val sha = e.getString("sha256").lowercase()
                    if (!sha.matches(Regex("[0-9a-f]{64}"))) throw JSONException("sha256 invalide pour $key")
                    FileEntry(sha, e.getLong("size"))
                },
            )
        }
    }
}
