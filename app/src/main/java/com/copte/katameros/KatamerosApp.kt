package com.copte.katameros

import android.app.Application
import com.copte.katameros.data.ContentStore
import com.copte.katameros.data.ContentUpdater

class KatamerosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ContentStore.init(this)
        Prefs(this).applyNightMode()
        ContentUpdater.checkIfDue(this)
        // Préchargement du lectionnaire et de la langue principale pendant que l'écran s'affiche.
        val primary = Prefs(this).primaryLanguage
        Thread({ runCatching { ContentStore.selector(); ContentStore.pack(primary) } }, "content-preload").start()
    }
}
