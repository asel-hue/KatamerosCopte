package com.copte.katameros.data

import android.content.Context
import com.copte.katameros.liturgy.Bible
import com.copte.katameros.liturgy.DaySelector
import com.copte.katameros.liturgy.LanguagePack
import com.copte.katameros.liturgy.Lectionary
import com.copte.katameros.liturgy.Synaxarium
import com.copte.katameros.liturgy.Texts
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Accès au contenu Katameros : version téléchargée (`filesDir/katameros/`) si elle existe et est complète,
 * sinon version embarquée (`assets/katameros/`). Les fichiers sont lus à la demande puis gardés en mémoire.
 * Les lectures de fichiers doivent se faire hors du thread principal (quelques centaines de ms pour une Bible).
 */
object ContentStore {
    const val DIR = "katameros"

    enum class Source { EMBEDDED, DOWNLOADED }

    private lateinit var app: Context
    @Volatile var source = Source.EMBEDDED
        private set
    private var lectionary: Lectionary? = null
    private val packs = HashMap<String, LanguagePack>()

    /** Idempotent : appelé par le ContentProvider (créé en premier) puis par l'Application. */
    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        reload()
    }

    fun downloadDir(context: Context): File = File(context.filesDir, DIR)

    /** Oublie le contenu en mémoire (après une mise à jour ou une restauration). */
    @Synchronized
    fun reload() {
        source = if (File(downloadDir(app), ContentManifest.FILE_NAME).isFile) Source.DOWNLOADED else Source.EMBEDDED
        lectionary = null
        downloadedManifest = null
        packs.clear()
    }

    val embeddedManifest: ContentManifest by lazy {
        ContentManifest.parse(app.assets.open("$DIR/${ContentManifest.FILE_NAME}").use { it.readBytes().toString(Charsets.UTF_8) })
    }

    private var downloadedManifest: ContentManifest? = null

    @Synchronized
    fun manifest(): ContentManifest = when (source) {
        Source.DOWNLOADED -> downloadedManifest
            ?: ContentManifest.parse(File(downloadDir(app), ContentManifest.FILE_NAME).readText()).also { downloadedManifest = it }
        Source.EMBEDDED -> embeddedManifest
    }

    @Synchronized
    fun selector(): DaySelector = DaySelector(lectionary ?: Lectionary.parse(read("lectionary.json")!!).also { lectionary = it })

    /** Données d'une langue, ou `null` si la langue n'existe pas dans le contenu. */
    @Synchronized
    fun pack(code: String): LanguagePack? {
        packs[code]?.let { return it }
        if (code !in manifest().languages) return null
        val pack = LanguagePack(
            code = code,
            rtl = code == "ar",
            texts = read("$code/texts.json")?.let(Texts::parse) ?: Texts.EMPTY,
            bible = Bible { book -> read("$code/bible/$book.json") },
            synaxarium = if (exists("$code/synaxarium/01.json")) {
                Synaxarium { month -> read("$code/synaxarium/${monthFile(month)}") }
            } else null,
        )
        packs[code] = pack
        return pack
    }

    private fun monthFile(month: Int) = String.format(java.util.Locale.ROOT, "%02d.json", month)

    private fun exists(path: String): Boolean = path in manifest().files

    private fun read(path: String): String? {
        if (source == Source.DOWNLOADED) {
            val f = File(downloadDir(app), path)
            if (f.isFile) return f.readText(Charsets.UTF_8)
        }
        return openEmbedded(app, path)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun openEmbedded(context: Context, path: String): InputStream? =
        try { context.assets.open("$DIR/$path") } catch (_: java.io.IOException) { null }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        input.use { while (true) { val n = it.read(buffer); if (n <= 0) break; digest.update(buffer, 0, n) } }
        // Locale.ROOT : en arabe, %02x produirait des chiffres arabes orientaux.
        return digest.digest().joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    }
}
