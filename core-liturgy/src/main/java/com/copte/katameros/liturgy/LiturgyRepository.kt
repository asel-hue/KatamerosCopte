package com.copte.katameros.liturgy

import com.copte.calendar.CopticDate

/**
 * Point d'accès unique aux données du jour (lectures du Katameros, commémorations du Synaxaire).
 * Phase 3 : chargement des JSON depuis `assets/katameros/` (ou leur copie mise à jour dans `filesDir`),
 * sur le modèle de `BookRepository` de LivreAGPIA.
 */
interface LiturgyRepository {
    /** Identifiants stables des commémorations du jour (Synaxaire), dans l'ordre d'affichage. */
    fun commemorationIds(date: CopticDate): List<String>
}
