# KatamerosCopte : consignes pour Claude

Application Android (Kotlin natif, vues XML + ViewBinding, Material 3) des **lectures quotidiennes de l'Église copte orthodoxe** (Katameros), avec un **widget** affichant la date copte et le saint ou la fête du jour (Synaxaire). C'est l'application sœur de **LivreAGPIA** (`../LivreAGPIA`), dont elle reprend les choix techniques.

## Dépôts liés
- `../coptic-calendar-core` (`asel-hue/coptic-calendar-core`) : bibliothèque Kotlin/JVM pure, qui contient le calendrier copte, le comput pascal et le modèle du jour liturgique. Si ce dossier existe, `settings.gradle` l'inclut en *composite build*. Sinon, la version JitPack `rootProject.ext.copticCalendarCore` est utilisée. **Toute règle de calendrier va dans la bibliothèque, jamais dans l'application.**
- `../LivreAGPIA` : modèle à reprendre pour `ContentUpdater`, `ContentManifest`, `scripts/build_content_manifest.py`, `PreferencesManager`, `SettingsActivity` et `Insets`.
- Agents Claude partagés : plugin `asel-hue/coptic-claude-plugin` (dossier `../coptic-claude-plugin`).

## Modules
- `app/` : écrans (Aujourd'hui, lectures par office, Synaxaire, calendrier, réglages), données JSON dans `assets/katameros/`.
- `core-liturgy/` : chargement des lectures et du Synaxaire pour une `CopticDate`.
- `widget/` : `AppWidgetProvider`, mise à jour à minuit, sans réseau.
- `scripts/` : import du Katameros vers JSON, validation, manifest sha256.

## Règles
- Langues : français (principale), arabe (RTL), anglais. Le copte sert aux titres et aux répons, avec la police `res/font/noto_sans_coptic.ttf` appliquée via `CopticText`.
- Textes liturgiques : appliquer les skills `glossaire-copte-orthodoxe` et `sources-citations-coptes`. Ne jamais inventer un texte ni une référence biblique.
- Source des lectures : `pierresaid/katameros-api` (MIT, accord confirmé par Antoine). Détails et réserve sur la NKJV dans `docs/SOURCES.md`.
- Les identifiants (lectures, saints, fêtes) et les clés de jour « MM-JJ » sont stables : ne jamais les renuméroter.
- Contrat d'intégration avec les autres applications d'offices :
  - deep link `copte://jour/{AAAA-MM-JJ}` ;
  - plus tard, un ContentProvider en lecture seule protégé par une permission de signature.
  Toute modification de ce contrat doit rester rétrocompatible.
- minSdk 26, compile et target 36, JVM 17, AGP 9.2.1 (Kotlin intégré : ne pas ajouter `org.jetbrains.kotlin.android`).

## Build (Windows, OneDrive)
- Gradle ne peut pas ouvrir de connexion locale depuis le bac à sable de Claude. Il faut lancer les builds dans le **terminal intégré** (`run_in_terminal`) ou dans Android Studio.
- `.\gradlew.bat assembleDebug`, `.\gradlew.bat test`, et dans `../coptic-calendar-core` : `.\gradlew.bat test`.
- Le cache de build est désactivé à cause de OneDrive (`gradle.properties`).

## Phases
1. Squelette : dépôts, modules, agents. **Fait.**
2. `coptic-calendar-core` 0.2.0 : conversion des dates, Pâques, fêtes mobiles, `LiturgicalDay`, tests. **Fait.** Points liturgiques à faire confirmer : voir le README de la bibliothèque.
3. Import des données et validation. **Fait.**
   - `scripts/import_katameros.py` puis `scripts/validate_content.py --write` : à relancer après toute modification du contenu.
   - Le choix des lectures du jour est dans `core-liturgy/DaySelector.kt`.
   - Les documents d'Antoine (traductions de Naguy Wasfy, diaporamas READINGS_*) sont dans `../ressources-coptes/` (voir `INDEX.md`). Ils pourront servir de texte français relu, après conversion des polices coptes anciennes.
4. Écran Aujourd'hui et lecteur des lectures.
5. Widget.
6. Contrat d'intégration (deep links, ContentProvider), puis publication sur JitPack.
