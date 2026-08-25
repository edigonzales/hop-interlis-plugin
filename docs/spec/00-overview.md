# hop-interlis-plugin – Gesamtspezifikation

Status: Entwurf für Implementierung  
Zielplattform: Apache Hop 2.18.1, Java 21  
Primärformat Phase 1–4: INTERLIS 2 XTF 2.3/2.4  
Spätere Erweiterung: INTERLIS 1 ITF, vollständiger Event-/Basket-Modus, zusätzliche Komfortfunktionen

## 1. Ziel

`hop-interlis-plugin` soll eine möglichst umfassende INTERLIS-Unterstützung für Apache Hop bereitstellen. Das Plugin soll nicht versuchen, das FME-Datenmodell nachzubauen. Stattdessen wird die INTERLIS-Semantik gezielt auf das Row-/Stream-Modell von Hop abgebildet.

Leitidee:

```text
FME:
INTERLIS object --> FME feature
                    |-- feature type
                    |-- attributes
                    `-- geometry

Hop:
INTERLIS object --> generic INTERLIS envelope --> typed Hop row
                    |                         |
                    |                         `-- stable IRowMeta per stream
                    `-- advanced/lossless backbone
```

Die zwei Ebenen sind absichtlich getrennt:

1. **Advanced/transport layer**: Ein einheitlicher Row-Stream trägt `InterlisObjectEnvelope`-Objekte und Transfermetadaten.
2. **Typed user layer**: Ein modellbewusster Projektionsschritt wandelt INTERLIS-Objekte in normale Hop-Rows mit stabilem Schema um.

Dadurch kann ein Benutzer in normalen Pipelines mit klassischen Hop-Feldern arbeiten, während das Plugin intern genug INTERLIS-Semantik erhält, um Strukturen, Rollen, Assoziationen, Baskets, OIDs und XTF-Roundtrips korrekt zu behandeln.

## 2. Explizite Produktziele

Das Plugin muss:

- XTF 2.3 und 2.4 lesen und schreiben.
- Modelle automatisch aus dem Transfer erkennen können, sofern möglich.
- Modelle aus lokalen Verzeichnissen und INTERLIS-Modell-Repositories auflösen können.
- normale INTERLIS-Klassen als typisierte Hop-Rows darstellen.
- primitive Attribute typgerecht abbilden.
- mehrere Geometrieattribute einer Klasse als mehrere echte Hop-`Geometry`-Felder unterstützen.
- Kreisbögen und SQL/MM-Curve-Geometrien im XTF-Pfad verlustfrei erhalten.
- Vererbung korrekt auflösen und für ETL-Zwecke standardmässig als vollständiges flaches Row-Schema anbieten.
- `STRUCTURE 0..1` / `STRUCTURE 1` komfortabel flatten können.
- `BAG/LIST OF STRUCTURE` als Child-Rows explodieren und wieder sammeln können.
- einfache Rollen als Referenzfelder darstellen.
- komplexe bzw. m:n-Assoziationen als eigene Rows/Streams behandeln.
- `ORDERED` und `LIST`-Reihenfolgen erhalten.
- Basket-Informationen mindestens über `_ili_bid` verfügbar machen.
- einen Advanced/Event-Modus für Start/End Transfer, Start/End Basket, Object, Delete Object vorbereiten und später vollständig bereitstellen.
- INTERLIS-Validierung über iox-ili/ilivalidator-nahe APIs integrieren.
- eine gute Hop-GUI bieten: Model Browser, Schema Preview, sinnvolle Defaults, klare Hinweise für Strukturen und Rollen.
- lokale Entwicklung, Build, Installation und Neustart von Hop mit einem Befehl ermöglichen.
- auf Linux, macOS und Windows CI-fähig sein.
- umfangreiche Unit-, Integration-, Roundtrip- und echte Hop-E2E-Tests besitzen.

## 3. Nicht-Ziele der ersten vorzeigbaren Version

Die erste vorzeigbare Version soll bewusst nicht gleichzeitig alle Spezialfälle lösen. Insbesondere zunächst nicht zwingend:

- INTERLIS 1 ITF inklusive sämtlicher AREA/SURFACE-Linetable-Varianten.
- vollständige Nachbildung aller ili2fme-Kompatibilitätsoptionen.
- FME-spezifische Geometry-Encoding-Modi.
- beliebige Mischstreams mit wechselndem `IRowMeta`.
- ein generisches Objektmodell als primäre Benutzeroberfläche.
- automatische fachliche Joins über das gesamte Modell ohne explizite Pipeline-Struktur.

Diese Funktionen sind jedoch in der Zielarchitektur vorgesehen, sodass ihre spätere Implementierung keinen Architekturbruch erfordert.

## 4. Technischer Ausgangspunkt

Die Spezifikation orientiert sich an den bereits vorhandenen Plugins:

- `edigonzales/hop-geometry-type-plugin`
- `edigonzales/hop-geotools-plugin`

Der aktuelle GeoTools-Plugin-Parent verwendet:

```text
Java             17
Apache Hop       2.17.0
GeoTools         35.0
JTS              1.20.0
JUnit            5.12.0
AssertJ          3.27.3
```

Für `hop-interlis-plugin` ist die Ziel-Baseline **Apache Hop 2.18.1** (Java 21):

```text
Java             21       <- Hop 2.18-Artefakte sind Java-21-Bytecode
Apache Hop       2.18.1   <- verbindliche Baseline, zentral im Parent-POM gepinnt
iox-ili          1.24.4
ili2c            5.6.8
```

Der Hop-Versions-Pin liegt ausschliesslich in der Property `hop.version` des Parent-POM; alle
Module beziehen ihre Hop-Artefakte (`hop-core`, `hop-engine`, Test-Transforms) von dort.
Ein Versionswechsel erfolgt nur über diese eine Stelle plus die unten referenzierten
Spezifikationsstellen; es darf keinen zweiten hartkodierten Hop-Versionswert im Projekt geben.

Für INTERLIS wird als initialer Baseline-Stand vorgeschlagen:

```text
iox-ili          1.24.4
ili2c            5.6.8
```

Diese Kombination wird aktuell auch im ili2db-Umfeld verwendet. Versionen sind zentral im Parent-POM zu verwalten und regelmässig separat zu aktualisieren.

### 4.1 Classloader-Vorgabe

Da `hop-interlis-plugin` echte Hop-`Geometry`-Werte erzeugt, darf innerhalb derselben Pipeline keine zweite inkompatible `org.locationtech.jts.geom.Geometry`-Klasse entstehen.

Vorgabe:

```text
classLoaderGroup = "sogeo-geometry"
```

für alle Transform- und Value-Type-Plugins, die `ValueMetaGeometry` oder JTS-Geometrien direkt verwenden.

`hop-interlis-plugin` deklariert `hop-geometry-type` und das von dort bereitgestellte JTS als `provided`. Die Distribution enthält diese Artefakte nicht doppelt.

## 5. Geplante Repository-Struktur

```text
hop-interlis-plugin/
|
|-- pom.xml
|-- README.md
|-- LICENSE
|
|-- hop-interlis-core/
|   |-- pom.xml
|   `-- src/main/java/ch/so/agi/hop/interlis/core/...
|
|-- hop-interlis-valuetype/
|   |-- pom.xml
|   `-- src/main/java/ch/so/agi/hop/interlis/value/...
|
|-- hop-interlis-transforms/
|   |-- pom.xml
|   `-- src/main/java/ch/so/agi/hop/interlis/transforms/...
|
|-- assemblies/
|   `-- assemblies-hop-interlis/
|       |-- pom.xml
|       `-- src/assembly/assembly.xml
|
|-- scripts/
|   |-- dev-sync-hop-plugin.sh
|   |-- dev-install-and-run.sh
|   |-- run-e2e.sh
|   `-- check-distribution.py
|
|-- test-models/
|   |-- simple/
|   |-- structures/
|   |-- associations/
|   |-- geometry/
|   |-- inheritance/
|   `-- baskets/
|
|-- test-data/
|   |-- xtf23/
|   |-- xtf24/
|   `-- invalid/
|
`-- e2e/
    |-- project/
    |-- pipelines/
    |-- expected/
    `-- scripts/
```

### Warum drei Java-Module?

`hop-interlis-core` enthält INTERLIS-Logik ohne SWT und weitgehend ohne Hop-GUI-Abhängigkeiten. Dadurch kann die zentrale Mapping-Logik sehr schnell und breit unit-getestet werden.

`hop-interlis-valuetype` enthält den optionalen internen `InterlisObject`-Value-Type.

`hop-interlis-transforms` enthält Hop Meta/Data/Main/Dialog-Klassen.

Eine spätere Aufteilung in weitere Module ist möglich, aber für den Start nicht nötig.

## 6. Plugin-Transforms – Zielumfang

### 6.1 Benutzerorientierte Transforms

| Transform | Zweck | Priorität |
|---|---|---:|
| INTERLIS Input | XTF lesen und genau eine Klasse als normale Rows ausgeben | sehr hoch |
| INTERLIS Output | normale Rows einer konfigurierten Klasse als XTF schreiben | sehr hoch |
| INTERLIS Validate | Datei oder Envelope-Stream validieren | hoch |
| INTERLIS Structure Explode | BAG/LIST-Struktur in Child-Rows zerlegen | hoch |
| INTERLIS Structure Collect | Child-Rows wieder als Strukturwerte sammeln | hoch |
| INTERLIS Role Join | modellbewusster Join über eine Rolle | mittel |
| INTERLIS Enumerations | Enumerationswerte eines Modells als Rows ausgeben | mittel |

### 6.2 Advanced-Transforms

| Transform | Zweck | Priorität |
|---|---|---:|
| INTERLIS Transfer Input | gesamten Transfer als einheitlichen Envelope-Stream lesen | hoch |
| INTERLIS Object to Row | Envelope einer Klasse in typisierte Rows projizieren | hoch |
| INTERLIS Row to Object | typisierte Rows in Envelope zurückwandeln | hoch |
| INTERLIS Transfer Output | Envelope-Stream als XTF schreiben | hoch |
| INTERLIS Basket Mapper | BID/Topic/Basket-Eigenschaften setzen bzw. umschreiben | später |
| INTERLIS Event Filter | Events/Object/Delete selektieren | später |
| INTERLIS Association Explode/Collect | explizite Link-Objekte komfortabel behandeln | später |

## 7. Die zentrale UX-Idee

Die normale Benutzeroberfläche soll die Transfercodierung möglichst verbergen.

Der Benutzer soll statt XTF-Interna sehen:

```text
DMAVTYM_...
`-- Bodenbedeckung
    `-- Gebaeude
        |-- TID
        |-- Art                  Enumeration
        |-- Geometrie            SURFACE
        |-- Adresse              STRUCTURE {0..1}
        |   |-- Strasse
        |   `-- Nummer
        |-- Qualitaeten          LIST {0..*}
        |   `-- ...
        `-- Gemeinde             -> Gemeinde {1}
```

und darunter ein Mapping:

```text
Output mapping

[x] _ili_tid
[x] _ili_bid
[x] Art
[x] Geometrie

Adresse
  (*) Flatten
  ( ) Keep as INTERLIS object
  Prefix: Adresse_

Qualitaeten
  LIST {0..*}
  -> use "INTERLIS Structure Explode"

Gemeinde
  [x] Output reference
      field: Gemeinde_ref
```

Leitprinzip:

> INTERLIS konzeptionell zeigen, Hop relational ausgeben, XTF-Codierung verstecken.

## 8. Schnell vorzeigbarer erster Meilenstein

Der erste sinnvolle Demo-Meilenstein soll bewusst klein, aber bereits nützlich sein:

```text
Phase 1 Demo
============

input.xtf
   |
   v
INTERLIS Input
Class: Model.Topic.Building
   |
   |-- _ili_tid       String
   |-- _ili_bid       String
   |-- name           String
   |-- type           String
   `-- geometry       Geometry
   |
   v
Vector Writer
   |
   v
output.gpkg
```

Muss bereits können:

- Modell automatisch oder explizit laden.
- Klassen in GUI anzeigen.
- Felder vorab anzeigen.
- primitive Typen korrekt ausgeben.
- genau ein oder mehrere Geometrieattribute als `ValueMetaGeometry` ausgeben.
- Bögen erhalten.
- Unit- und echte XTF-Integrationstests besitzen.
- Plugin per Script bauen, installieren und Hop GUI starten.

Damit ist früh etwas sichtbar, das real eingesetzt und demonstriert werden kann.

## 9. Phasenübersicht

```text
Phase 0  Foundation / Build / model compiler / curve spike / test harness
   |
Phase 1  Typed XTF Input MVP  <---- erster vorzeigbarer Stand
   |
Phase 2  Typed Output + Roundtrip
   |
Phase 3  Structures vollständig
   |
Phase 4  Associations + Role Join
   |
Phase 5  Advanced Envelope + Transfer Input/Output
   |
Phase 6  Validation + Enumerations + Basket/Operations + UX
   |
Phase 7  INTERLIS 1 / ITF
   |
Phase 8  Hardening, performance, full compatibility matrix
```

Jede Phase hat eigene Abnahmekriterien. Eine Phase wird erst begonnen, wenn Build und Tests der vorherigen Phase vollständig grün sind.

## 10. Dokumente dieser Spezifikation

- `00-overview.md` – Ziele, Umfang, Gesamtbild.
- `01-architecture-data-model.md` – kanonisches Datenmodell, Strukturen, Assoziationen, Geometrien, Baskets.
- `02-java-hop-implementation.md` – konkrete Java-Klassen, Methoden, Hop-Interfaces, `IRowMeta` und Mapping-Algorithmen.
- `03-gui-ux.md` – detaillierte GUI-Spezifikation mit ASCII-Mockups.
- `04-testing-e2e.md` – Unit-, Integration-, Roundtrip- und automatisierte Hop-E2E-Tests.
- `05-roadmap-phases.md` – genaue phasenweise Umsetzung und Definition of Done.
- `06-development-deployment.md` – lokaler Entwicklungs-, Installations- und CI-Workflow.

## 11. Verbindliche Architekturprinzipien

1. **Stable schema per stream.** Kein wechselndes `IRowMeta` innerhalb eines normalen Datenstroms.
2. **Model-driven.** Feldschema und Semantik stammen aus der `TransferDescription`, nicht aus zufälligen Werten des ersten Objekts.
3. **Typed rows first.** Normale Benutzer arbeiten mit Standard-Hop-Typen.
4. **INTERLIS envelope only where necessary.** Der generische Objektwert ist eine Advanced-Schnittstelle, kein Default-UX.
5. **Roundtrip-aware.** Bei jeder Mapping-Entscheidung muss klar sein, ob sie verlustfrei zurückgeschrieben werden kann.
6. **Curves stay curves.** Keine implizite Linearisierung im INTERLIS-XTF-Pfad.
7. **Structures are structural, not magic JSON.** Einwertige Strukturen können flatten; mehrwertige Strukturen werden als Child-Rows behandelt.
8. **Roles look like references.** Einfache Rollen werden als `*_ref`-Felder sichtbar; komplexe Assoziationen als eigene Streams.
9. **Transfer encoding is hidden.** Embedded Links vs. explizite Link-Objekte sollen möglichst nicht die Benutzer-API bestimmen.
10. **GUI is part of the product.** Schema- und Modellinspektion dürfen kein nachträgliches Add-on sein.
11. **No duplicate JTS.** Geometry Value Type und JTS stammen aus dem bestehenden Geometry-Plugin.
12. **Core logic without SWT.** Mapping- und Modelllogik ist UI-unabhängig und gut testbar.
13. **Build must be reproducible.** Installation und lokales Starten von Hop müssen skriptbar sein.
14. **E2E is mandatory.** Nicht nur Mapper testen; reale XTF-Dateien müssen durch reale Hop-Pipelines laufen.

## 12. Referenzquellen

Technische Basis der Spezifikation:

- Apache Hop 2.18 development/user manuals: https://hop.apache.org/
- Apache Hop repository: https://github.com/apache/hop
- ili2fme: https://github.com/claeis/ili2fme
- iox-ili: https://github.com/claeis/iox-ili
- ili2c: https://github.com/claeis/ili2c
- ilivalidator: https://github.com/claeis/ilivalidator
- hop-geometry-type-plugin: https://github.com/edigonzales/hop-geometry-type-plugin
- hop-geotools-plugin: https://github.com/edigonzales/hop-geotools-plugin

