package com.copte.katameros.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.copte.katameros.BuildConfig
import com.copte.katameros.Prefs
import com.copte.katameros.liturgy.Bible
import com.copte.katameros.liturgy.Lectionary
import com.copte.katameros.liturgy.Synaxarium
import com.copte.katameros.liturgy.Texts
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mise à jour du contenu depuis GitHub, sans republier l'application (reprise de LivreAGPIA).
 *
 * Télécharge `manifest.json` ; si sa `contentVersion` dépasse la version locale, prépare un dossier
 * temporaire avec tous les fichiers (réutilisés localement si le sha256 est identique), vérifie chaque
 * empreinte, vérifie que chaque JSON se lit, puis bascule atomiquement vers `filesDir/katameros/`.
 * Toute erreur laisse l'ancien contenu en place.
 */
object ContentUpdater {
    private const val TAG = "ContentUpdater"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_FILE_BYTES = 16L * 1024 * 1024
    private const val CHECK_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

    sealed class Result {
        object UpToDate : Result()
        data class Updated(val version: Int) : Result()
        data class Failed(val reason: String, val network: Boolean) : Result()
    }

    private class ContentException(message: String, val network: Boolean = false, cause: Throwable? = null) :
        Exception(message, cause)

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "content-updater") }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    private fun baseUrl(): String = BuildConfig.CONTENT_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }

    /** Vérification silencieuse, au plus une fois par semaine. */
    fun checkIfDue(context: Context) {
        val now = System.currentTimeMillis()
        if (now - Prefs(context).lastUpdateCheck in 0 until CHECK_INTERVAL_MS) return
        check(context, null)
    }

    fun check(context: Context, callback: ((Result) -> Unit)?) {
        val app = context.applicationContext
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            val result = try {
                update(app)
            } catch (e: ContentException) {
                Log.w(TAG, "Mise à jour abandonnée : ${e.message}", e.cause)
                Result.Failed(e.message ?: "", e.network)
            } catch (e: Exception) {
                Log.w(TAG, "Mise à jour : erreur inattendue", e)
                Result.Failed(e.toString(), network = false)
            } finally {
                Prefs(app).lastUpdateCheck = System.currentTimeMillis()
                running.set(false)
            }
            if (callback != null) mainHandler.post { callback(result) }
        }
    }

    /** Supprime le contenu téléchargé et revient au contenu embarqué. */
    fun restoreEmbedded(context: Context) {
        ContentStore.downloadDir(context.applicationContext).deleteRecursively()
        ContentStore.reload()
    }

    private fun update(context: Context): Result {
        val base = baseUrl()
        val remoteJson = String(download("$base${ContentManifest.FILE_NAME}"), Charsets.UTF_8)
        val remote = try {
            ContentManifest.parse(remoteJson)
        } catch (e: org.json.JSONException) {
            throw ContentException("Manifeste distant invalide", cause = e)
        }
        if (remote.schemaVersion > ContentManifest.SUPPORTED_SCHEMA_VERSION) {
            throw ContentException("Schéma ${remote.schemaVersion} non supporté par cette version")
        }
        val local = maxOf(
            ContentStore.embeddedManifest.contentVersion,
            if (ContentStore.source == ContentStore.Source.DOWNLOADED) ContentStore.manifest().contentVersion else 0,
        )
        if (remote.contentVersion <= local) return Result.UpToDate

        val target = ContentStore.downloadDir(context)
        val staging = File(context.filesDir, "${ContentStore.DIR}_tmp")
        val old = File(context.filesDir, "${ContentStore.DIR}_old")
        staging.deleteRecursively()
        old.deleteRecursively()
        try {
            if (!staging.mkdirs()) throw ContentException("Dossier temporaire impossible à créer")
            for ((path, entry) in remote.files) {
                val bytes = reuseLocal(context, target, path, entry) ?: downloadVerified("$base$path", entry, path)
                File(staging, path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
            }
            validate(staging, remote)
            File(staging, ContentManifest.FILE_NAME).writeText(remoteJson, Charsets.UTF_8)
            if (target.exists() && !target.renameTo(old)) throw ContentException("Ancien contenu impossible à écarter")
            if (!staging.renameTo(target)) {
                if (old.exists()) old.renameTo(target)
                throw ContentException("Nouveau contenu impossible à installer")
            }
            old.deleteRecursively()
        } finally {
            staging.deleteRecursively()
        }
        ContentStore.reload()
        return Result.Updated(remote.contentVersion)
    }

    private fun reuseLocal(context: Context, downloaded: File, path: String, entry: FileEntry): ByteArray? {
        val candidates = sequence {
            File(downloaded, path).takeIf { it.isFile }?.let { yield(it.readBytes()) }
            ContentStore.openEmbedded(context, path)?.use { yield(it.readBytes()) }
        }
        return candidates.firstOrNull { it.size.toLong() == entry.size && ContentStore.sha256(it.inputStream()) == entry.sha256 }
    }

    private fun downloadVerified(url: String, entry: FileEntry, path: String): ByteArray {
        val bytes = download(url)
        if (bytes.size.toLong() != entry.size) throw ContentException("Taille inattendue pour $path")
        if (ContentStore.sha256(bytes.inputStream()) != entry.sha256) throw ContentException("sha256 invalide pour $path")
        return bytes
    }

    private fun download(url: String): ByteArray {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
        } catch (e: IOException) {
            throw ContentException("URL invalide : $url", network = true, cause = e)
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw ContentException("HTTP $code pour $url", network = true)
            return connection.inputStream.use(::readLimited)
        } catch (e: IOException) {
            throw ContentException("Réseau indisponible ($url)", network = true, cause = e)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_FILE_BYTES) throw ContentException("Fichier trop volumineux")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Chaque fichier doit se lire avec le même code que l'application. */
    private fun validate(dir: File, manifest: ContentManifest) {
        fun text(path: String) = File(dir, path).takeIf { it.isFile }?.readText(Charsets.UTF_8)
        try {
            Lectionary.parse(text("lectionary.json") ?: throw ContentException("lectionary.json manquant"))
            for (code in manifest.languages) {
                if (manifest.files.keys.none { it.startsWith("$code/bible/") }) throw ContentException("$code/bible/ vide")
            }
            for (path in manifest.files.keys) {
                val content = text(path) ?: throw ContentException("$path manquant")
                when {
                    "/bible/" in path -> Bible.parseBook(content)
                    "/synaxarium/" in path -> Synaxarium.parse(content)
                    path.endsWith("/texts.json") -> Texts.parse(content)
                }
            }
        } catch (e: org.json.JSONException) {
            throw ContentException("Contenu illisible : ${e.message}", cause = e)
        }
    }
}
