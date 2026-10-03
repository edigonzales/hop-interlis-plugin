# Prio 3: Modellmigration und grafischer ilimap-Editor

Stand: 3. Oktober 2026. Änderungen betreffen dieses Repository und das separate
Repository `ilitransformer`. Vorhandene INTERLIS-, Hop- und CI-Pins sind unverändert.

## Gelieferter Umfang

- Nativer `.ilimap`-Dateityp im Hop Explorer. Klassen- und Attributzuordnung per
  Drag-and-drop, Regel-/Strukturkontexte, Ausdrucksbearbeitung, Funktionsauswahl,
  AST-Graph, strikte Enum-Wertetabellen und regelbezogene Referenzen.
- Editierbare DSL mit tolerantem Syntax-Highlighting, Zeilennummern, Einrückung,
  Klammermarkierung und Syntaxdiagnosen. Grafik und Text bearbeiten dasselbe
  Dokument; grafische Kommandos sind atomar rückgängig machbar und erhalten
  Kommentare und nicht betroffene Textbereiche. Technische Regel-IDs bleiben stabil.
- Modellvergleich erzeugt explizite kompatible Zuordnungen und Hinweise auf offene
  Entscheidungen. Modellfingerprints erkennen geänderte Modellquellen. Eine
  vorbereitete Migration muss vor der Ausführung ausdrücklich geprüft werden.
- `INTERLIS Migration` führt einen vollständigen XTF-Job über eine Java-API aus.
  Standard: validieren, nicht überschreiben. Original und Ziel müssen verschieden
  sein, auch bei Symlinks/Hardlinks. Geänderte Originaldateien und unvollständige
  Transfers führen zum Fehler; Veröffentlichung erfolgt atomar nach Validierung.
- Vorschau verarbeitet eine vollständige kleine XTF-Datei, erhält dadurch den
  Referenzkontext und zeigt höchstens 100 Objekte an. Die Anzeigegrenze begrenzt
  nicht die Verarbeitung. Produktionsdaten werden separat vollständig validiert.
- ilitransformer liefert dafür eine schlanke Maven-Bibliothek, gemeinsame
  Dokument-/Schema-/Migrationsdienste, Abbruchprüfungen und explizite eingebettete
  Strukturquellen. XYZ-Koordinaten behalten jetzt auch über den CoordValue-Pfad Z.

## Ausgeführte Prüfungen

Umgebung: macOS, Temurin 21.0.10, Maven Wrapper, isoliertes Apache Hop 2.19.0.
Maven-Einstellungen stammen vom gepinnten gemeinsamen CI-Helper; Geometry und
Vector/Raster wurden in den bestehenden Snapshot-Versionen installiert.

- ilitransformer: fokussierte Editor-/API-/Sicherheitsprüfungen sowie
  `./gradlew spotlessApply check publishCorePublicationToMavenLocal` erfolgreich.
  JUnit meldet 1450 Unit- und 181 Integrationstestfälle, ohne Fehler. Drei bestehende
  Fälle sind übersprungen: optionaler Shapefile-Performance-Test und zwei lokal
  nicht vorhandene reale XLSX/DMAV-Fixtures. Separate PostGIS-Opt-in-Tests wurden
  nicht ausgeführt. Spotless und Feature-Matrix-Prüfung sind erfolgreich.
- Hop: `./mvnw -s "$MAVEN_SETTINGS" -B -ntp clean verify` erfolgreich:
  **443 Tests**, davon 196 Core und 247 Transforms, ohne Fehler oder übersprungene
  Tests. Enthalten sind echte Action-XML-Roundtrips, Plugin-/Dialogvertrag,
  Variablen-/VFS-Pfade, UTF-8-Speicherung und externe Dateiänderungen.
- `python3 scripts/check-distribution.py`: erfolgreich; ZIP 21,6 MiB.
  INTERLIS-Laufzeit bleibt geteilt; weder Hop/Geometry noch CLI/LSP-Abhängigkeiten
  werden als zweite Implementierung mitgeliefert.
- `REQUIRE_VECTOR_RASTER_E2E=true bash scripts/run-e2e.sh "$HOP_HOME"`:
  **56 bestehende Paket-Pipelines plus Migrationsworkflow** erfolgreich.
  Das neue Workflow-Beispiel prüft Zielvalidierung, Umbenennung, Pflichtattribut,
  strikte Enum-Zuordnung, Referenz, ARC, XYZ, LIST-Reihenfolge und verschachtelte
  BAG-Duplikate. Der zweite Lauf wird wegen vorhandener Zieldatei abgelehnt;
  Original und Ziel bleiben unverändert.
- API-Sicherheitstests prüfen Modelländerung, Originaländerung während des Jobs,
  ungültige Zielwerte, Abbruch, abgeschnittene Transfers auch ohne Validierung,
  Pfad-Aliase und Bereinigung temporärer Ausgaben.
- Handbuch mit thoth-biblios `0.0.1-20260928.154224-53` aus dem Working Tree
  gerendert; `check-user-manual.py` bestätigt Kapitel, Includes, Anker, Assets
  und alle zwölf Pipelineverweise.

## Interaktive SWT-Abnahme

Das installierte Plugin öffnet `.ilimap` im Hop-Explorer mit sichtbarem
Syntax-Highlighting. Die automatisierte Bedienoberfläche exponiert die Inhalte
dieses Explorer-Tabs auf macOS nicht vollständig. Deshalb wurden die eigentlichen
Produktionswidgets zusätzlich über `MigrationEditorGuiHarness` in einer nativen
SWT-Shell mit derselben Hop-/Plugin-Laufzeit interaktiv bedient. Der Harness ist
ein manueller Testeinstieg und speichert die übergebene Datei nicht.

Erfolgreich geprüft: Modelle laden, echtes Attribut-Drag-and-drop, Undo, Funktion
einfügen, Ausdruck ändern und aktualisierten Graphen betrachten, Strukturattribut
bearbeiten, Referenzzuordnung ohne duplizierten Block, vollständige Enum-Tabelle
anlegen, Mappingprüfung und validierte Dateivorschau. Eine absichtlich unvollständige
DSL pausiert grafische Änderungen und bleibt editierbar; Undo stellt das Mapping
wieder her. Der Action-Dialog zeigt seine Defaults und den bestehenden Namen;
Änderungen an Name, Zieldatei und Überschreiboption werden bei Cancel verworfen.
DSL, Diagnostics und Data preview verwenden Hops konfigurierte Festbreitenschrift.
Die Darstellung aller drei Bereiche einschliesslich Syntaxfarben und Zeilennummern
sowie das Schliessen und erneute Öffnen des Editors wurden interaktiv geprüft.

Dabei gefundene Fehler bei Enum-Ausdrücken, Graphaktualisierung und Action-Namen
wurden korrigiert und erneut geprüft. Die Enum-Erzeugung besitzt zusätzlich einen
Regressionstest durch dieselbe Java-API bis zur validierten XTF-Datei.
Hop-Explorer-Speichern/Schliessen wurde nicht vollständig interaktiv durchgespielt;
exakte Dateispeicherung und externe Änderungen sind durch automatisierte Tests geprüft.
Linux, Windows und JDK 25 wurden in dieser lokalen Abnahme nicht ausgeführt.

## Lieferung und Grenzen

Die Maven-Abhängigkeit ist `guru.interlis:ilitransformer-core:0.1.0-SNAPSHOT`.
Die Core-Bibliothek wurde vor diesem Plugin publiziert. Ein erneuter vollständiger
`clean verify`-Build mit frisch aus dem Maven-Repository aufgelöstem Snapshot
`0.1.0-20261003.184843-1` ist erfolgreich (443 Tests). Für diese Auflösung wurde ein
separater Cache ohne die lokal publizierte ilitransformer-Bibliothek verwendet.
Die optionale Variable `ILITRANSFORMER_REPO` aktiviert den lokalen Build im bestehenden
Entwicklungsskript; normale Builds verwenden die Maven-Abhängigkeit.

Die erste Version unterstützt einen XTF-Eingang und einen XTF-Ausgang sowie eine
grafisch bearbeitbare Sammlungsebene. Tiefere explizite Mappings bleiben in der
DSL erhalten. Konkrete Strukturuntertypen, die der gewählte Plan nicht abbildet,
werden abgelehnt. Joins, Klassenteilungen, freie Hop-Unterpipelines und
Vervollständigung/Hover im Texteditor haben noch keine grafischen Bedienelemente.
Die Engine hält Indizes und Zielobjekte im Speicher; die Spill-Budgets von
INTERLIS Output/Update gelten hier nicht. Migration übernimmt nur die im Mapping
definierten Inhalte und verspricht keine byteidentische XML-Erhaltung.

Einstieg: [Beispiel und Bedienung](../../examples/migration/README.md).
