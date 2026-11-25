# Lectures coptes (KatamerosCopte)

Application Android des **lectures quotidiennes de l'Église copte orthodoxe** (Katameros) pour le jour copte du jour, avec un **widget** qui affiche la date copte et le saint ou la fête du jour.

> État : lectures du jour, Synaxaire, réglages, widget et contrat d'intégration en place (phases 1 à 6). Intégration : voir [docs/INTEGRATION.md](docs/INTEGRATION.md).

## Fonctionnalités prévues
- Lectures du jour par office : Vêpres, Matines, Liturgie (Pauline, Catholicon, Praxis, Psaume, Évangile).
- Synaxaire : saints et événements du jour.
- Fêtes et jeûnes mobiles (Pâques copte, Carême, Ninive, Pentecôte, Apôtres).
- Français, arabe et anglais, avec le copte pour les titres. Fonctionne hors ligne, contenu mis à jour depuis GitHub sans nouvelle APK.
- Widget d'écran d'accueil.
- Liaison avec d'autres applications d'offices par le lien `copte://jour/AAAA-MM-JJ`.

## Structure
| Module | Rôle |
|---|---|
| `app/` | Écrans et données JSON (`assets/katameros/`) |
| `core-liturgy/` | Chargement des lectures et du Synaxaire pour un jour copte |
| `widget/` | Widget d'écran d'accueil |
| `scripts/` | Import, validation et manifest du contenu |

Le calendrier copte vient de la bibliothèque [`coptic-calendar-core`](https://github.com/asel-hue/coptic-calendar-core). Si elle est clonée à côté de ce dépôt, Gradle l'utilise directement.

## Build
```powershell
.\gradlew.bat assembleDebug
```

## Sources des textes
À compléter en phase 3. Les lectures doivent provenir du Katameros (`pierresaid/katameros-api`), sous réserve de l'accord sur la licence.

## Licence
PolyForm Noncommercial 1.0.0, voir `LICENSE`.
