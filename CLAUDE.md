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
- Source des lectures : `pierresaid/katameros-api`. **L'import est bloqué tant que la licence et l'accord de l'auteur ne sont pas confirmés.**
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
2. `coptic-calendar-core` : conversion des dates, Pâques, fêtes mobiles, `LiturgicalDay`, tests.
3. Import des données (après l'accord de licence) et validation.
4. Écran Aujourd'hui et lecteur des lectures.
5. Widget.
6. Contrat d'intégration (deep links, ContentProvider), puis publication sur JitPack.
