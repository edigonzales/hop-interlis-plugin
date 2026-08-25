# Startprompt für die Implementierung von `hop-interlis-plugin`

Du arbeitest an einem neuen Open-Source-Projekt **`hop-interlis-plugin`**: einem möglichst umfassenden INTERLIS-Plugin für Apache Hop.

## Zuerst lesen

Die verbindliche technische Spezifikation liegt unter `docs/spec/` bzw. im bereitgestellten Spezifikationsordner:

```text
README.md
00-overview.md
01-architecture-data-model.md
02-java-hop-implementation.md
03-gui-ux.md
04-testing-e2e.md
05-roadmap-phases.md
06-development-deployment.md
```

Lies außerdem die `AGENTS.md` vollständig, bevor du Änderungen ausführst.

Die Spezifikation ist die fachliche und technische Grundlage der Implementierung. Erfinde keine alternative Gesamtarchitektur, bevor du verstanden hast, warum die dort beschriebenen Entscheidungen getroffen wurden.

## Relevante Referenzprojekte

Analysiere bei Bedarf insbesondere:

```text
https://github.com/edigonzales/hop-geometry-type-plugin
https://github.com/edigonzales/hop-geotools-plugin
https://github.com/claeis/iox-ili
https://github.com/claeis/ili2fme
https://github.com/apache/hop
```

Das neue Plugin soll sich bezüglich Maven-Struktur, Apache-Hop-Plugin-Implementierung, Packaging, Classloader-Konzept, Tests, lokaler Entwicklung und Release-Mechanismus möglichst gut an `hop-geometry-type-plugin` und `hop-geotools-plugin` anlehnen.

## Zentrale Architekturprinzipien

Behalte insbesondere folgende Entscheidungen bei:

1. **Hop Rows sind das normale Benutzer-Datenmodell.** Normale Benutzer arbeiten mit typisierten Hop-Feldern und nicht direkt mit `IomObject`.
2. **INTERLIS-Klassen werden in homogene Row-Schemas projiziert.** Kein gigantischer Sparse-Row über sämtliche Klassen.
3. **Für den Advanced-/Transfer-Pfad gibt es ein homogenes INTERLIS-Envelope-Modell.** Dieses transportiert Klassenname, Basket, TID/OID, Operation und INTERLIS-Objekt.
4. **Geometrien sind echte Hop-Geometry-Felder.** Verwende den bestehenden Geometry Value Type aus `hop-geometry-type-plugin`; mehrere INTERLIS-Geometrieattribute werden zu mehreren Geometry-Feldern.
5. **Kurven müssen möglichst verlustfrei erhalten bleiben.** Bevorzuge den in der Spezifikation beschriebenen SQL/MM-WKB-Pfad über iox-ili (`Iox2wkb`, `Wkb2iox`). Vermeide unnötige Linearisation von Kreisbögen.
6. **Structures:** einfache `0..1`/`1`-Structures standardmäßig flatten; `BAG/LIST OF` über Child-Rows und später Structure Explode/Collect; `LIST`-Reihenfolge erhalten.
7. **Associations:** einfache Rollen als Referenzfelder; komplexe/m:n-Assoziationen als eigene Rows; XTF-spezifische Embedded-Link-Codierung vor normalen Benutzern verbergen.
8. **GUI ist Kernbestandteil.** Model Browser, Schema Preview und gute Auswahl von Klassen, Attributen, Structures und Rollen gehören zum Produkt.

## Vorgehen

Arbeite strikt phasenweise gemäß `05-roadmap-phases.md`.

Eine neue Phase darf erst begonnen werden, wenn die vorherige Phase vollständig implementiert ist, Build und Tests grün sind, die Definition of Done erfüllt ist und keine bekannte Regression offen bleibt.

Versuche nicht, Funktionen späterer Phasen nebenbei halb zu implementieren. Eine kleine, vollständig funktionierende und getestete Zwischenstufe ist besser als eine breite, unfertige Implementierung.

# Erster Auftrag: Phase 0

Beginne mit **Phase 0 – Projektfundament und technische Verifikation**.

Ziel ist ein belastbares Projektgerüst, auf dem Phase 1 ohne grundlegende Architekturänderungen aufgebaut werden kann.

Prüfe und implementiere insbesondere:

- Zielversion von Apache Hop entsprechend Spezifikation und vorhandenen Referenzplugins;
- Java-Version;
- Maven-Multimodulstruktur;
- Maven Wrapper;
- benötigte iox-ili-/ili2c-Abhängigkeiten;
- Abhängigkeit zu `hop-geometry-type-plugin`;
- Classloader-Konzept;
- Apache-Hop-Plugin-Discovery;
- Packaging des installierbaren Plugins;
- minimale Transform-Registrierung;
- lokale Installation in eine Hop-Installation;
- Start von Hop GUI mit installiertem Plugin;
- CI-fähiger Maven-Build.

Orientiere dich eng an `hop-geotools-plugin` und `hop-geometry-type-plugin`. Übernimm bewährte Strukturen, sofern kein sachlicher Grund dagegen spricht.

## Technische Spikes in Phase 0

Verifiziere mit kleinen automatisierten Tests mindestens folgende kritische Annahmen.

### 1. INTERLIS-Modell laden

Ein kleines `.ili`-Modell muss mittels ili2c geladen werden können und als `TransferDescription` verfügbar sein.

### 2. Modell introspektieren

Aus einer Klasse müssen mindestens ermittelt werden können:

- qualified class name;
- Attribute;
- Datentypen;
- Kardinalitäten;
- geerbte Attribute;
- Geometrieattribute;
- Structures;
- Rollen/Associations.

### 3. XTF lesen

Ein minimales XTF muss mittels iox-ili gelesen werden können. Aus einem Objekt müssen mindestens Object Tag, TID/OID, Attributwerte und Geometriewerte verfügbar sein.

### 4. Geometry-Roundtrip

Verifiziere einen echten Roundtrip:

```text
INTERLIS IomObject geometry
        |
        v
iox-ili SQL/MM WKB
        |
        v
Hop Geometry type
        |
        v
SQL/MM WKB
        |
        v
IomObject geometry
```

Der Test muss mindestens umfassen:

- COORD / Point;
- POLYLINE / LineString;
- SURFACE / Polygon;
- POLYLINE mit ARC.

Für ARC muss ausdrücklich geprüft werden, dass der Kreisbogen nicht stillschweigend linearisiert wird.

### 5. Apache-Hop-Transform

Implementiere einen minimalen Test-Transform, der von Hop gefunden wird, im Plugin-Registry-System registriert ist, ein einfaches `IRowMeta` erzeugt und programmatisch in einer Pipeline ausgeführt werden kann.

Der Transform darf später ersetzt oder weiterentwickelt werden.

# Danach: Phase 1

Wenn und nur wenn Phase 0 vollständig grün ist, beginne mit **Phase 1**.

Ziel ist ein vorzeigbarer vertikaler Schnitt:

```text
+--------------------------+
| INTERLIS Input           |
|--------------------------|
| file: buildings.xtf      |
| model: ExampleModel      |
| class: Buildings         |
+------------+-------------+
             |
             | typed Hop rows
             v
+--------------------------+
| Preview / normal Hop     |
| transform                |
+--------------------------+
```

Zusätzlich soll möglichst früh dieser Pfad funktionieren:

```text
INTERLIS Input
      |
      v
Vector Writer
      |
      v
GeoPackage
```

## INTERLIS Input – Phase-1-Scope

Mindestens:

- XTF;
- Modell aus XTF bzw. explizite Modellangabe;
- model directories / model repositories gemäß Spezifikation;
- Auswahl genau einer konkreten INTERLIS-Klasse;
- TID;
- Basket-ID;
- primitive Attribute;
- Enumerationen;
- Boolean;
- Integer;
- Decimal;
- Text;
- Date/DateTime soweit unterstützt;
- mehrere Geometrieattribute;
- echte Hop Geometry Values;
- geerbte Attribute;
- einfache `0..1`-Structure durch Flattening;
- einfache Referenzrolle als `*_ref`.

Noch nicht erforderlich in Phase 1:

- vollständiger Writer;
- BAG/LIST Explode;
- komplexe Associations;
- ITF;
- Validation Transform;
- Event/Transfer Advanced Mode.

# Design-Time-Schema und `IRowMeta`

Die Design-Time-Metadaten sind zentral.

Die Modell-/Schema-Logik gehört nicht in die SWT-GUI. Implementiere eine wiederverwendbare Core-Schicht entsprechend der Spezifikation, z. B. `InterlisModelService`, `InterlisSchemaService`, `InterlisClassSchema`, `InterlisField`, `InterlisRowSchemaBuilder` und `InterlisRowMappingPlan`.

Die Meta-Klasse verwendet diese Services in `getFields(...)` und erzeugt daraus `IRowMeta`.

GUI, `getFields()` und Runtime müssen denselben Mapping-Plan verwenden:

```text
INTERLIS model
      |
      v
InterlisClassSchema
      |
      v
InterlisRowMappingPlan
      |
      +--------> IRowMeta
      |
      +--------> IomObject -> Object[]
      |
      +--------> GUI schema preview
```

Es darf keine unabhängige zweite oder dritte Schema-Interpretation geben.

# `IomObject -> Object[]`

Implementiere den Mapper als eigene getestete Klasse und nicht ad hoc in `processRow()`.

Sinngemäß:

```java
public interface InterlisObjectToRowMapper {
  Object[] map(
      InterlisObjectEnvelope object,
      InterlisRowMappingPlan mappingPlan);
}
```

Der Mapping-Plan kennt vorab die Zielposition jedes Feldes und die notwendige Konvertierung.

Beispiel:

```text
0 -> _tid
1 -> _bid
2 -> Name
3 -> Art
4 -> Lage
5 -> Achse
6 -> Adresse_Strasse
7 -> Adresse_Nummer
8 -> Gemeinde_ref
```

Pro Row darf keine teure Modellanalyse stattfinden.

# GUI

`03-gui-ux.md` ist verbindlich.

Der normale Benutzer soll keine Qualified Names auswendig eintippen müssen. `INTERLIS Input` braucht mindestens Transfer-Datei, Modelle/Model-Pfade, Class Browser, TID/Basket-Optionen und eine Design-Time-Schema-Preview.

Der Class Browser muss Modelle, Topics, Klassen, Attribute, Geometrien, Structures und Rollen verständlich darstellen.

GUI-Code konsumiert Modellanalyse; er implementiert sie nicht selbst.

Fehler beim Schema-Probing dürfen den Dialog nicht unbenutzbar machen. Unresolved variables, fehlende Repositories, ungültige Preview-Dateien und ähnliche Fehler müssen als verständliche Preview-Meldung erscheinen.

# Tests

Tests sind Teil jeder Implementierung und werden nicht nachträglich ergänzt.

Verwende mindestens JUnit 5 und AssertJ sowie die in der Spezifikation vorgesehenen Hop-Testmechanismen.

Für jede Mapping-Regel mindestens Happy Path, null/optional, invalid input und Roundtrip wo sinnvoll.

Besonders wichtig sind primitive Typen, Enumeration, Inheritance, mehrere Geometrien, Curve-Geometrien, optionale Structure-Flattening-Regeln, References, falscher Object Tag, fehlende Mandatory-Werte, unbekannte Attribute, leere und mehrere Baskets sowie heterogene Klassen im Transfer.

## E2E

Phase 1 ist erst abgeschlossen, wenn ein echter automatisierter E2E-Test existiert, der:

1. das Plugin baut;
2. eine isolierte Apache-Hop-Installation/Testdistribution verwendet;
3. das gebaute Plugin installiert;
4. nötigenfalls `hop-geometry-type-plugin` installiert;
5. eine echte `.hpl`-Pipeline via `hop-run` ausführt;
6. ein echtes XTF liest;
7. Ergebniswerte automatisiert prüft.

Mindestens ein E2E-Test enthält Geometrie, mindestens einer einen Kreisbogen.

# Lokaler Entwicklungsworkflow

Implementiere früh:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

Das Script soll ungefähr:

1. `hop-geometry-type-plugin` finden/bauen;
2. `hop-interlis-plugin` bauen;
3. alle Tests ausführen;
4. Distribution prüfen;
5. altes Plugin aus `HOP_HOME` entfernen;
6. neue Plugin-ZIP installieren;
7. laufendes Hop GUI beenden;
8. Hop GUI neu starten;
9. Startup-Log ausgeben.

Nach einer Codeänderung soll die neue Version mit einem Befehl in Hop GUI ausprobierbar sein.

# Qualitätsanforderungen

Beachte durchgehend:

- Java-Version gemäß Projektvorgabe;
- Maven Wrapper;
- saubere Paketstruktur;
- keine God Classes;
- kleine testbare Services;
- UI, Mapping, Modellanalyse und I/O klar trennen;
- keine unnötige Reflection;
- keine globale mutable State;
- Ressourcen zuverlässig schließen;
- verständliche Exceptions;
- Logging über Apache Hop;
- kein `System.out.println()` in Produktivcode;
- keine Modellanalyse pro Row;
- keine Geometrie-Stringkonvertierung als interner Standardpfad;
- keine stille Linearisation von Kreisbögen;
- keine unnötige GeoTools-Abhängigkeit;
- `hop-geometry-type-plugin` als gemeinsame Geometry-Schicht.

# Arbeitsweise

Bevor du Code änderst:

1. lies `AGENTS.md` und die gesamte Spezifikation;
2. analysiere die relevanten bestehenden Projekte und tatsächlichen APIs;
3. beschreibe kurz den konkreten Umsetzungsplan für Phase 0;
4. identifiziere technische Risiken oder Widersprüche;
5. wenn keine Blocker bestehen, beginne unmittelbar mit der Implementierung.

Bei Unsicherheit zuerst bestehenden Code und APIs untersuchen. Keine Apache-Hop- oder iox-ili-APIs aus dem Gedächtnis erfinden.

Nach jedem Arbeitspaket passende Tests ausführen. Ein Arbeitspaket gilt nicht als abgeschlossen, solange Tests rot sind.

Am Ende jeder Phase liefere:

```text
Implemented
-----------
...

Tests
-----
...

Manual verification
-------------------
...

Remaining limitations
---------------------
...

Next phase
----------
...
```

Beginne jetzt mit dem vollständigen Lesen von `AGENTS.md` und der Spezifikation und anschließend mit **Phase 0**.
