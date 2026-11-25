# Intégration avec les autres applications coptes

KatamerosCopte expose le **jour liturgique** aux autres applications coptes : LivreAGPIA, Taaspi copte et les futures applications d'offices. Le contrat est défini dans la bibliothèque [`coptic-calendar-core`](https://github.com/asel-hue/coptic-calendar-core), objet `LiturgyContract`.

**Version du contrat : 1.** Règle d'évolution : on ajoute des colonnes, des chemins ou des paramètres, on ne modifie ni ne retire jamais rien. Chaque ajout augmente `LiturgyContract.VERSION` et se note dans le journal en bas de ce fichier.

## 1. Liens (aucune permission)

| Lien | Effet |
|---|---|
| `copte://jour/2026-11-25` | Ouvre les lectures de ce jour |
| `copte://lectures/2026-11-25/vepres` | Ouvre ce jour sur l'office de la Veille |
| `copte://lectures/2026-11-25/matines` | … sur l'office du Matin |
| `copte://lectures/2026-11-25/liturgie` | … sur la Messe |

- La date est grégorienne, au format ISO (AAAA-MM-JJ).
- Un office absent ce jour-là (pas de Veille pendant la Semaine sainte, par exemple) ouvre le jour en haut.
- Si l'application n'est pas installée, `startActivity` lève `ActivityNotFoundException` : à intercepter.

```kotlin
// Kotlin (LivreAGPIA…)
val uri = Uri.parse(LiturgyContract.readingsLink(LocalDate.now(), LiturgyContract.Office.LITURGY))
try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) { /* proposer l'installation */ }
```

```dart
// Flutter (Taaspi copte), avec le paquet url_launcher
await launchUrl(Uri.parse('copte://jour/2026-11-25'));
```

## 2. ContentProvider (lecture seule, même signature)

| URI | Contenu |
|---|---|
| `content://com.copte.katameros.liturgy/day/2026-11-25` | Le jour demandé |
| `content://com.copte.katameros.liturgy/today` | Le jour du téléphone (fuseau local) |
| `…?lang=ar` | Titres et Synaxaire en `fr` (par défaut), `ar` ou `en` |

Une seule ligne est renvoyée. Ses colonnes sont `LiturgyContract.Columns` :

| Colonne | Type | Exemple |
|---|---|---|
| `contract_version` | int | `1` |
| `date` | texte ISO | `2026-11-25` |
| `coptic_year`, `coptic_month`, `coptic_day` | int | `1743`, `3`, `16` |
| `coptic_key` | texte « MM-JJ » | `03-16` |
| `season_id` | texte | `nativity_fast` (voir `Season`) |
| `feast_ids` | tableau JSON | `["nativity"]` (voir `Feast`) |
| `title` | texte | `Jeûne de la Nativité` (fête, sinon temps, sinon vide) |
| `lang` | texte | `fr` |
| `commemorations` | tableau JSON | `[{"id":"31601","title":"Début du jeûne…"}]` |
| `readings_source` | texte | `annual:03-16` (débogage) |
| `day_link` | texte | `copte://jour/2026-11-25` |

### Accès
La lecture est protégée par la permission `com.copte.katameros.permission.READ_LITURGY`, de niveau **signature**. Seule une application signée avec la **même clé** que KatamerosCopte peut lire. Les autres reçoivent une `SecurityException`. En debug, toutes tes applications utilisent la clé de debug de ton poste. En release, il faut signer toutes les applications avec la même clé.

Ce que l'application cliente déclare dans son manifeste :

```xml
<uses-permission android:name="com.copte.katameros.permission.READ_LITURGY" />
<!-- Android 11+ : visibilité du fournisseur -->
<queries>
    <provider android:authorities="com.copte.katameros.liturgy" />
</queries>
```

### Exemple Kotlin

```kotlin
implementation("com.github.asel-hue:coptic-calendar-core:0.3.0") // repositories { maven("https://jitpack.io") }

fun copticToday(context: Context): String? = try {
    context.contentResolver.query(Uri.parse(LiturgyContract.todayUri("fr")), null, null, null, null)?.use { c ->
        if (!c.moveToFirst()) return null
        val title = c.getString(c.getColumnIndexOrThrow(LiturgyContract.Columns.TITLE))
        val key = c.getString(c.getColumnIndexOrThrow(LiturgyContract.Columns.COPTIC_KEY))
        "$key $title"
    }
} catch (e: SecurityException) {
    null // pas la même signature
}
// Sans KatamerosCopte installée, query() renvoie null : on peut calculer la date copte
// avec la bibliothèque seule (LiturgicalCalendar.dayOf), sans titres ni Synaxaire.
```

### Flutter
Il n'existe pas d'accès direct aux ContentProvider en Dart : il faut passer par un `MethodChannel` côté Android, avec le même code Kotlin que ci-dessus. Le manifeste Android de l'application Flutter doit contenir la même déclaration.

## Vérifier depuis un ordinateur

```powershell
adb shell am start -a android.intent.action.VIEW -d "copte://lectures/2026-04-10/matines"
adb shell content query --uri content://com.copte.katameros.liturgy/today
```

La seconde commande doit être **refusée** (`SecurityException`), car l'utilisateur `shell` n'a pas la signature. C'est le comportement attendu.

## Journal du contrat
- **1** (2026-11-25) : liens `copte://jour`, `copte://lectures` ; fournisseur `day/{date}` et `today`, paramètre `lang`, colonnes ci-dessus.
