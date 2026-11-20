# Accessible Vision

Clientseitige Fabric-Mod für Minecraft Java 1.21.11 mit kontrastreichen Erzmarkierungen, einer Spielerübersicht und einem warmen Bildschirmfilter.

## Funktionen

- Markiert geladene Erzblöcke im Umkreis von 16 Blöcken mit kontrastreichen Farben. Ancient Debris ist rot hervorgehoben.
- Zeigt andere geladene Spieler im Umkreis von 32 Blöcken als rote Markierung und listet Name, Entfernung, Hand-Item und Haltbarkeit der getragenen Rüstung auf.
- Legt optional einen dezenten warmen Farbton über den Bildschirm.
- Alle Funktionen sind clientseitig und über die Minecraft-Steuerung umbelegbar.

## Tasten

Die Tasten lassen sich unter **Optionen → Steuerung → Tastenbelegung → Accessible Vision** ändern.

- `O`: Erzmarkierungen ein/aus
- `P`: Spieleranzeige ein/aus
- `V`: warmer Bildschirmfilter ein/aus
- `Rechts-Umschalt`: anklickbares Einstellungsmenü öffnen

## Bauen

Benötigt JDK 21. Im Projektordner ausführen:

```powershell
./gradlew.bat build
```

Das Mod-JAR liegt danach unter `build/libs/`. Fabric API wird benötigt.

## Hinweise

Die Mod scannt ausschließlich bereits geladene Client-Chunks. Sie lädt keine Chunks nach und sendet keine zusätzlichen Daten an den Server. Die Spielerübersicht nutzt Informationen, die der Client für geladene Spieler erhält.
