# Eigene Modelle mit Blockbench MCP

Die Monsterfiguren stammen aus YGOMCModels und werden automatisch konvertiert. Diese Assets entstehen von Hand
mit Blockbench, gesteuert über das Blockbench-MCP-Plugin aus einer lokalen Claude-Code-Session:

- Duel Disk
- Duel Arena und Arena-Controller
- die sieben Millenniums-Gegenstände
- optional Deck Box und Kartentisch

Bis ein Asset existiert, nutzt die Mod einen Platzhalter mit allen Pflicht-Bones. Den aktuellen Stand zeigt
`docs/AssetStatus.md`.

**Quelle der Wahrheit** ist `assets-src/asset-contract.json`. Dort steht pro Asset:

- Pflicht-Bones mit ihrem Zweck
- Pflicht-Animationen
- Texturgröße
- Cube-Budget
- Ursprung und Ausrichtung

Die Bone-Namen müssen **exakt** stimmen, denn der Code hängt Karten, Hologramme und Effekte daran.

## Einmalige Einrichtung (auf deinem PC)

1. Installiere [Blockbench](https://www.blockbench.net) (Desktop, ≥ 5.0).
2. Installiere das Plugin **GeckoLib Models & Animations**: *File → Plugins → Available*.
3. Lade das Blockbench-MCP-Plugin: *File → Plugins → Load Plugin from URL* →
   `https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js`.
   Der MCP-Server läuft danach in Blockbench unter `http://localhost:3000/bb-mcp`. Port und Pfad änderst du unter
   *Settings → General*.
4. Klone das Repo und richte die Build-Umgebung ein: JDK 25, `git submodule update --init --recursive`.
5. Verbinde Claude Code mit Blockbench:
   `claude mcp add --transport http blockbench http://localhost:3000/bb-mcp`

## Ablauf pro Asset

1. **Vertrag lesen.** Starte Claude Code im Repo und gib z. B. diesen Auftrag:
   „Lies `assets-src/asset-contract.json` und baue in Blockbench das Asset `duel_disk`.“
2. **Projekt anlegen.** Neues Projekt *GeckoLib Animated Model* (Typ Item oder Block, siehe `kind`). Setze die
   Texturgröße auf den Wert `texture` aus dem Vertrag.
3. **Modellieren.**
   - Lege jeden Pflicht-Bone als Gruppe mit genau diesem Namen an.
   - Anker (`anchor_*`, `card_slot_*`, `*_slot`) sind **leere Gruppen**. Ihr Pivot ist der Ankerpunkt.
   - Weitere Bones darfst du ergänzen.
   - Halte das Cube-Budget `maxCubes` ein.
4. **Animationen anlegen.** Lege jede Animation aus `animations` mit genau diesem Namen an. „loop“ bedeutet
   Schleife, „once“ einmaliges Abspielen.
5. **Textur malen.** Entweder mit den Paint-Tools des MCP oder in Blockbench selbst.
6. **Speichern.** Lege die Dateien ins Repo (Dateiname = Asset-ID):
   - `assets-src/blockbench/<id>.bbmodel` – vorher *Referenzbilder entfernen*. Die Validierung lehnt Modelle mit
     Referenzbildern oder absoluten Pfaden ab.
   - `assets-src/blockbench/<id>.animation.json` – über *File → Export → GeckoLib Animations*, per MCP mit
     `geckolib_export_animations`.
   - optional `assets-src/blockbench/<id>.png`. Ohne diese Datei wird die im Modell eingebettete Textur verwendet.
7. **Prüfen und konvertieren.**
   ```bash
   ./gradlew :tools:validateAssets   # prüft gegen den Vertrag
   ./gradlew :tools:processAssets    # erzeugt GeckoLib-Assets, Arena-Layouts und docs/AssetStatus.md
   ```
8. **Im Spiel ansehen** (sobald das Mod-Modul existiert): `./gradlew :neoforge:runClient`, dann
   `/ygo debug spawnasset <id>`.
9. **Committen.** Branch `assets/<id>` anlegen, alles committen und pushen. CI validiert die Assets erneut.

## Hinweise zu einzelnen Assets

- **Duel Arena:** Der Ursprung ist die Feldmitte auf Bodenhöhe.
  - Team 0 steht bei −Z und schaut nach +Z, Team 1 gegenüber.
  - Zone 0 ist aus Sicht des Duellanten die linke Zone. Für Team 0 liegt sie bei +X.
  - Die Positionen der `anchor_*`-Gruppen werden 1:1 zu `data/minecraftygo/arena_layout/standard_{1v1,2v2}.json`.
    Ohne Modell gilt das Standard-Layout aus `ArenaLayouts`: 9 × 13 Blöcke, Zonenabstand 1,2 Blöcke.
  - Eine Karte auf dem Feld ist 10 × 14 px groß.
- **Duel Disk:** Wird am linken Unterarm getragen.
  - `deploy` klappt die Klinge auf.
  - `card_slot_0..4` markieren die Kartenpositionen auf der Klinge.
  - `lp_display` ist eine flache Fläche, auf die der Code die Lebenspunkte zeichnet.
- **Millennium Ring:** Die Zeiger `pointer_0..4` drehen sich im Spiel zum Ziel. Ihr Pivot liegt deshalb am
  Aufhängepunkt.
