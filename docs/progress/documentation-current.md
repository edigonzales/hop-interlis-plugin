# Dokumentationsabnahme des aktuellen Pluginstands

Stand: 30. September 2026. Produktionsbasis: Commit
`9532cc677c13fa38f2a2f710308084e7aa7176f6` mit der abgeschlossenen Prio-1/2-Implementierung.
Die Abnahme bezieht sich auf den Dokumentations-Working-Tree über dieser Basis.
Historische Phasenberichte und die [Prio-1/2-Abnahme](prio-1-2.md) behalten ihre
damaligen Ergebnisse.

## Umfang und Codeabgleich

Alle zehn Kapitel des [deutschen Benutzerhandbuchs](../biblios/user/master.adoc)
wurden mit Transform-Metadaten, Dialogen, Mappingplänen und Runtime abgeglichen.
README, Beispiel-README und betroffene Spezifikationsabschnitte beschreiben denselben
Bedienumfang. Änderungen betreffen Dokumentation, Handbuch-Fixtures, Beispiel-
Pipelines, Tests und deren Runner/Prüfskripte. Produktionsquellen, öffentliche APIs,
POMs, Bibliotheksversionen und CI-Pins sind unverändert.

Korrigiert beziehungsweise ergänzt wurden insbesondere:

- Hop 2.19.0, Java 21, reales ZIP-Layout und der Vector Writer aus Vector/Raster
  als getesteter GeoPackage-Pfad;
- tatsächliche Controls und Defaults statt Inline-Input-Validierung, UUID-Erzeugung,
  Basket-Policies, editierbarem Automapping und nicht vorhandenen Modellbaum-Aktionen;
- getrennte Dateivalidierung und Writer-Validierung vor Veröffentlichung,
  INI-Konfiguration sowie vollständig ausgegebene Diagnosen;
- primitive `_ili_value`-Kindzeilen, `_ili_child_object`, Feldprojektion,
  PRESERVE/REBUILD, Ersetzen der Sammlung, Kardinalität und LIST-Neuindizierung;
- neue BID-/Elternschlüssel und ausdrückliche Unterscheidung der XML-Ladedefaults
  alter und neuer Konfigurationen;
- skalare Referenz-TIDs/BIDs, Null-/Overlay-Verhalten sowie optionale TIDs für
  nicht identifizierbare Assoziationen;
- gemeinsame Puffer-/Diskbudgets pro Transform, Lookup-Zeilenlimit, Wartezeiten
  und weiterhin erforderlicher Heap für ein einzelnes Objekt;
- Veröffentlichung am erfolgreichen Pipeline-Ende, Atomarität pro Datei,
  zusammenhängende BIDs und gültige Ereignisfolgen;
- Erhaltungszusagen für Projektion, Quellobjekt-Overlay und unterstützten
  Ereignisstrom sowie fachliche Inhaltsvergleiche statt bloss gleicher Objektzahl;
- aktuelle Module und CI-Abläufe anstelle überholter Quellcode-Pins und
  Installations-/Testskriptentwürfe in der Deployment-Spezifikation.

Prio-1/2-Ergänzungen stehen nun in ihren Fachabschnitten. Der gemeinsame
[CI-Vertrag](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/ci-contract.md)
und die vorhandenen Workflow-/Helper-Revisionen wurden berücksichtigt; es erfolgte
keine CI-Migration.

## Reproduzierbare Tutorials

Die sechs bisherigen Handbuch-Tutorials wurden korrigiert. Die LIST-Pipeline 35
verwendet ausdrücklich Kindträger, PRESERVE und BID plus Elternschlüssel.
Legacy-Regressionspipelines bleiben erhalten. Der doppelte gerade Punkt unmittelbar
nach dem ARC-Endpunkt in der bisherigen Bogen-Fixture wurde entfernt: erst dadurch
ist auch diese Fixture vollständig gültig, bei erhaltenem ARC.

Drei weitere Tutorials verwenden minimale lokale INTERLIS-2.4-Fixtures ausschliesslich
unter `docs/biblios/user/examples/`. Das Handbuch bindet dieselben Dateien ein, die
Tests übernehmen sie als Ressourcen. Neue Pipelines werden direkt aus `examples/`
ausgeführt; es gibt keine zweite Fixture-Kopie.

| Tutorial | Direkt ausgeführte Pipeline(s) | Geprüftes Ergebnis |
|---|---|---|
| Primitive Sammlungen | `primitive-collections/primitive-collections.hpl` | Text-, Boolean- und Enum-LIST-Reihenfolge; Zahlen-BAG 7/7/9; beide Elternidentitäten; leere Sammlungen des zweiten Objekts |
| Strukturkinder erhalten | `child-preserve/child-preserve.hpl` | Nur Name projiziert; ein Kind entfernt; verbleibender SpezialKind, Versteckt, Zusatz und verschachtelte Detail-LIST erhalten |
| Referenzen und sichere Ausgabe | `reference-roundtrip/reference-fields.hpl`, `reference-roundtrip.hpl`, `reference-check.hpl` | Interne z1-Referenz ohne BID, externe z2-Referenz mit b2; beide Baskets und Zielobjekte; vollständige validierte Ereignisausgabe; getrenntes erneutes Lesen |
| GeoPackage-Einstieg | `xtf-to-gpkg/xtf-to-gpkg.hpl` | Beide Gebäudewerte und Polygonkoordinaten, SRID 2056 und Geometriemetadaten |

Die neuen Pipelines deklarieren `E2E_INPUT_DIR` und `E2E_OUTPUT_DIR`. Das
[Beispiel-README](../../examples/README.md) nennt Voraussetzungen, Parameter und
Ergebnisse. Der dort dokumentierte Kommandozeilenaufruf mit zwei `-p`-Argumenten
wurde zusätzlich mit der Sammlungs-Pipeline tatsächlich ausgeführt.

## Tatsächlich ausgeführte technische Prüfungen

Lokales System: macOS, Temurin 21.0.10, Maven Wrapper mit Maven 3.9.9 und isoliertes
Apache Hop 2.19.0. Alle nachfolgenden Prüfungen waren erfolgreich; kein Test wurde
übersprungen. Die CI-Matrix auf anderen Betriebssystemen/JDK 25 wurde in diesem
lokalen Dokumentationslauf nicht ausgeführt.

| Prüfung | Ergebnis |
|---|---|
| Fokussierter `DocsExamplesTest` | 9 Tests, 0 Fehler, 0 übersprungen; alle neun Modelle kompiliert, acht gültige Transfers vollständig validiert, absichtlich ungültige Fixture mit erwarteter Diagnose geprüft, Feldschemas verglichen |
| `./mvnw -B -ntp clean verify` | 419 Tests: 193 Core und 226 Transforms; 0 Failures, 0 Errors, 0 Skipped; BUILD SUCCESS |
| `python3 scripts/check-distribution.py` | Distribution OK; Layout, Jandex, Runtime-Abhängigkeiten und ausgeschlossene gemeinsame/Test-JARs geprüft |
| Vollständige installierte Hop-E2E-Suite | 47 Pipeline-Aufrufe mit verpacktem Plugin; Safe Mode und Rowset-Grösse 2; erwartete Negativfälle geprüft; E2E OK |
| Verpflichtender GeoPackage-Pfad | `REQUIRE_VECTOR_RASTER_E2E=true`; bestehender Kurvenpfad und neuer Gebäude-Tutorialpfad erfolgreich |
| Semantische Handbuch-Ausgabeprüfung | Primitive Werte/Duplikate, Reihenfolge, leere Sammlungen, Carrier-Inhalte, Untertyp, Kindentfernung, Referenz-/Basket-Zuordnung und sämtliche Polygonkoordinaten verglichen |
| Handbuch-HTML-Prüfung | 10 Kapitel, 9 Tutorials, eindeutige Anker, interne Verweise, Includes, Code-Literale, lokale Assets und 12 ausführbare Pipeline-Linkziele geprüft |
| Shell-/Diff-Prüfung | `bash -n scripts/run-e2e.sh` und `git diff --check`; keine Produktions-, Versions- oder Workflow-Änderungen, keine generierten Artefakte im Diff |

Ausgeführte Hauptbefehle vom Repository-Root:

```bash
./mvnw -B -ntp -pl hop-interlis-transforms -am \
  -Dtest=DocsExamplesTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B -ntp clean verify
python3 scripts/check-distribution.py

export HOP_GEOMETRY_TYPE_ZIP=/absolute/path/to/geometry.zip
export HOP_VECTOR_RASTER_ZIP=/absolute/path/to/vector-raster.zip
export REQUIRE_VECTOR_RASTER_E2E=true
export KEEP_E2E_WORKDIR=true
bash scripts/run-e2e.sh /absolute/path/to/disposable-hop
```

Die vollständige Abnahme wurde nach der letzten Fixture-Formatierung wiederholt.
Lokale Logs: `hop-interlis-doc-focused-final.log`,
`hop-interlis-doc-verify-final.log`, `hop-interlis-doc-distribution-final.log` und
`hop-interlis-doc-e2e-acceptance.log` im temporären Arbeitsbereich. Die Logs und
E2E-Ausgaben sind keine Repository-Artefakte.

## Renderer und Sichtprüfung

thoth-biblios wurde einmal über die Snapshot-Metadaten aufgelöst:
**`0.0.1-20260928.154224-53`**, Fat-JAR mit Classifier `all`.
Sämtliche Builds und Browserprüfungen dieses Laufs verwendeten genau diese JAR und
Java 21. Die lokale Config verweist per `file://` auf den aktuellen Checkout;
`--use-local-working-tree` rendert ausdrücklich unveröffentlichte Änderungen.

```bash
java -jar /absolute/path/to/thoth-biblios-0.0.1-20260928.154224-53-all.jar build \
  --config docs/biblios/biblios.local.yml --use-local-working-tree
python3 scripts/check-user-manual.py docs/biblios/build/docs-site
python3 -m http.server 8765 --directory docs/biblios/build/docs-site
```

Im Browser wurden alle zehn Kapitel und neun Tutorials bei **1440 × 1000** und
**720 × 1000** geprüft. Zusätzliche Ansichten kontrollierten Kindfeldtabellen,
PRESERVE-Einstellungen, Pipeline-Aufrufe und die getrennten Referenz-Prüfläufe.
Inhaltsverzeichnis und interne Sprünge funktionieren; Modell-/Transfer-Includes
werden aufgelöst und die zwölf Pipeline-Verweise haben vorhandene lokale
Repository-Ziele. Diese Linkprüfung behauptet keine Veröffentlichung neuer Dateien
auf GitHub.

Zwei Renderingfehler wurden gefunden und korrigiert: Unterstriche in Inline-Code
führten zu versehentlicher AsciiDoc-Hervorhebung; eine zusammenhängende
Modellrepository-URL-Liste erzeugte einen falschen Link und eine zu breite Tabelle.
Code-Literale sind nun geschützt, die Modellquellen stehen getrennt unter der
Tabelle. Die schmale Seite hat keinen horizontalen Seitenüberlauf. Lange XML-/CSV-
Listingzeilen bleiben innerhalb ihres Codeblocks horizontal lesbar.

Die lokale Config und gerenderte Site liegen in ignorierten Bereichen. Die
Browserprüfung des Handbuchs ist kein Ersatz für eine interaktive SWT-Abnahme;
diese wurde im Dokumentationsnachlauf nicht zusätzlich behauptet.

## Verwendete Artefakte

SHA-256 der lokal geprüften Dateien:

| Artefakt | SHA-256 |
|---|---|
| INTERLIS `hop-interlis-plugin-0.1.0-SNAPSHOT.zip` | `411e68c64088f40431e1ed951dd2e3b255d9255e4dec15d8033940e15400dc92` |
| Geometry `0.2.0-SNAPSHOT` ZIP | `01469b5e4316bc0a73d85cd0f18ba769f3c6d14dd5e759ecde0bc530007a8c34` |
| Vector/Raster `0.1.0-SNAPSHOT` ZIP | `cb17d9dc3a8cc3941992ca29753ccc8b7c297a4afcb9a5c4cb887d380015035b` |
| thoth-biblios `0.0.1-20260928.154224-53-all.jar` | `04f25e9968f61112ce84e4d0d42fa4cd0c27b34d93c1b04b94f141c4d23fe345` |

## Dokumentierte Grenzen

Explode bewahrt eine gespeicherte `selectedChildFields`-Auswahl, bietet dafür aber
noch keinen eigenen Dialog-Control; das Erhaltungstutorial liefert die vorbereitete
Name-Projektion. Verschachtelte Sammlungen werden über den Kindträger erhalten;
ihre zusätzliche Bearbeitung ist kein Bestandteil dieses Nachlaufs.

Dateipublikation ist pro Datei atomar, keine gemeinsame Transaktion mehrerer Writer.
Einzelobjekte müssen in den Heap passen. Ereignisverarbeitung bleibt auf die
unterstützten Metadaten beschränkt, insbesondere mit den dokumentierten
OID-Space-Grenzen der gepinnten Bibliotheken. Mehrklassen-Assistent, Prio 3 und
FME-Performancevergleich bleiben spätere Arbeiten.
