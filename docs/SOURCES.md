# Sources des textes

## Katameros (lectures) et Synaxaire
- **Source** : base SQLite `Core/KatamerosDatabase.db` du projet [pierresaid/katameros-api](https://github.com/pierresaid/katameros-api), sous licence MIT (Copyright (c) 2022 katameros). L'import est figé sur le commit noté dans `.import/katameros-api/COMMIT`.
- **Accord** : confirmé par Antoine le 2026-11-25. Les textes sont publics et ses documents (Drive, Gmail) en contiennent une copie.
- **Import** : `scripts/import_katameros.py` génère `app/src/main/assets/katameros/`. Le choix des lectures du jour est porté en Kotlin (`core-liturgy/DaySelector.kt`) à partir de `LectionaryRepository`, `FeastsFactory` et `SpecialCases`.
- **Fêtes à lectures propres** (Semaine sainte, Croix, Ninive, Nativité…) : leur structure est capturée une fois depuis l'API publique avec `scripts/capture_feasts.py`, dans `scripts/data/feasts.json`. Ce fichier ne contient que des références, aucun texte.

## Bibles
| Langue | Version | Statut |
|---|---|---|
| Français | Louis Segond 1910 | Domaine public |
| Arabe | Van Dyck (avec diacritiques) | Domaine public |
| Copte | Bible copte bohaïrique (partielle : environ 10 % des versets cités manquent) | Texte traditionnel |
| Anglais | **NKJV** (New King James Version) | **© Thomas Nelson, sous copyright.** Choix d'Antoine (2026-11-25) : garder la NKJV de la base Katameros. Avant une publication sur le Play Store, il faudra une autorisation de l'éditeur ou un remplacement par une version du domaine public (KJV, WEB). |

Lacunes connues (avertissements du validateur, pas des erreurs) :
- Daniel 14 (Bel et le dragon) est absent des Bibles source.
- Quelques versets diffèrent à cause de la versification (Psaume 13, 1 Maccabées).
- La Bible copte est incomplète.

## Synaxaire
Français, anglais et arabe, repris de katameros-api. Les identifiants `id` (StoryId) sont communs aux trois langues. Le nombre de commémorations diffère pour une douzaine de jours selon la langue.

## Polices
Noto Sans Coptic, sous licence SIL OFL 1.1 (`app/src/main/assets/licenses/OFL-NotoSansCoptic.txt`).
