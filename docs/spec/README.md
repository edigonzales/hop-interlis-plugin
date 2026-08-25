# Spezifikation `hop-interlis-plugin`

Diese Dokumente beschreiben eine implementierungsnahe Architektur und einen phasenweisen Umsetzungsplan für ein umfassendes INTERLIS-Plugin für Apache Hop.

Ziel ist **nicht** ein mechanischer Port von `ili2fme`, sondern eine Hop-gerechte Abbildung:

```text
INTERLIS Transfer / IOM
          |
          v
model-aware mapping
          |
          v
stabile, typisierte Hop Rows
          |
          v
normale Hop-Transforms
```

Für fortgeschrittene Anwendungen existiert zusätzlich ein stabiles generisches Envelope-Schema:

```text
INTERLIS Transfer Input
          |
          v
InterlisObjectEnvelope
          |
          +--> Object to Row --> typed rows
          |
          +--> Routing / Filter
          |
          v
Row to Object
          |
          v
INTERLIS Transfer Output
```

## Dokumente

1. [`00-overview.md`](00-overview.md)  
   Zielbild, Scope, Transform-Katalog, Modulstruktur, Architekturprinzipien und Gesamtphasen.

2. [`01-architecture-data-model.md`](01-architecture-data-model.md)  
   INTERLIS-zu-Hop-Datenmodell, Klassen, Attribute, Geometrien, Strukturen, Assoziationen, Vererbung, Baskets, OIDs und Advanced/Event Mode.

3. [`02-java-hop-implementation.md`](02-java-hop-implementation.md)  
   Konkrete Java-Klassen und Methoden, Hop-Plugin-Interfaces, `IRowMeta`-Erzeugung, Mapping-Pläne sowie der exakte `IomObject -> Object[]`- und inverse Algorithmus.

4. [`03-gui-ux.md`](03-gui-ux.md)  
   GUI-/UX-Spezifikation mit ASCII-Mockups für Model Browser, Input/Output, Structures, Associations, Role Join, Validation und Advanced Mode.

5. [`04-testing-e2e.md`](04-testing-e2e.md)  
   Testpyramide, Testmodelle, Mapper-/Geometry-/Structure-/Association-Tests, echte Hop-Pipeline-Integration, `hop-run`-E2E, Distribution- und Release-Smoke-Tests.

6. [`05-roadmap-phases.md`](05-roadmap-phases.md)  
   Umsetzungsphasen mit vertikalen Arbeitspaketen und Definition of Done. Phase 1 ist bewusst bereits ein vorzeigbarer `INTERLIS Input` mit guter GUI.

7. [`06-development-deployment.md`](06-development-deployment.md)  
   Maven-/Classloader-/Packaging-Konzept, lokale Ein-Befehl-Installation, Hop-GUI-Restart, E2E-Scripts, CI und Releaseprozess.

8. [`AGENTS.md`](AGENTS.md)  
   Persistente Arbeitsregeln für Coding-Agents: Java/SDKMAN, Maven, Architektur-Invarianten, Tests, E2E, Logging und Completion Criteria.

9. [`STARTPROMPT.md`](STARTPROMPT.md)  
   Startprompt für einen Coding-Agenten. Er zwingt die Umsetzung auf Phase 0 und danach den vorzeigbaren Phase-1-Vertikalschnitt.

10. [`.sdkmanrc.example`](.sdkmanrc.example)  
    Vorlage für ein projektlokal gepinntes JDK. Vor Verwendung auf einen tatsächlich installierten SDKMAN-Identifier anpassen und als `.sdkmanrc` ablegen.

## Wichtigste Architekturentscheidungen

### Typed Rows sind der normale Benutzerpfad

Ein Benutzer, der eine konkrete INTERLIS-Klasse verarbeitet, erhält ein normales, stabiles Hop-Schema:

```text
_ili_tid       String
_ili_bid       String
Name           String
Hoehe          BigNumber
Lage           Geometry
Umriss          Geometry
Gemeinde_ref   String
```

Mehrere INTERLIS-Geometrieattribute werden als mehrere echte Hop-`Geometry`-Felder repräsentiert.

### Heterogene Klassen nur im generischen Envelope

Nicht erlaubt:

```text
Row 1 = Schema Gebaeude
Row 2 = Schema Gemeinde
```

im selben normalen Hop-Stream.

Dafür existiert:

```text
_ili_class
_ili_topic
_ili_bid
_ili_tid
_ili_operation
_ili_object
```

mit stabiler Row-Struktur.

### Structures

```text
STRUCTURE 0..1 / 1
    -> standardmässig flatten

LIST/BAG OF STRUCTURE
    -> INTERLIS Structure Explode / Collect
```

`LIST` erhält eine explizite Reihenfolge.

### Associations

```text
einfache Rolle
    -> <role>_ref

komplexe / m:n / n-äre Assoziation
    -> eigener Row-Stream

ORDERED
    -> _ili_order_pos
```

`INTERLIS Role Join` nutzt die Modellinformation, damit Benutzer nicht manuell Foreign-Key-Felder zusammensuchen müssen.

### Geometrie und Kreisbögen

Das Plugin verwendet den bestehenden gemeinsamen Hop Geometry Value Type.

Für die INTERLIS-Grenze wird bevorzugt:

```text
IomObject
   -> iox-ili Iox2wkb
   -> SQL/MM WKB
   -> Hop Geometry
```

und zurück:

```text
Hop Geometry
   -> SQL/MM WKB
   -> iox-ili Wkb2iox
   -> IomObject
```

Damit werden Kreisbögen nicht bereits beim INTERLIS-Input implizit linearisiert.

### Gute GUI ist Teil der Architektur

Die normalen Transforms verstecken IOM-/XTF-Transferdetails und zeigen stattdessen:

- Models,
- Topics,
- Classes,
- Attributes,
- Structures,
- Roles,
- Geometry types,
- Cardinalities.

Beispiel:

```text
+------------------------------------------------------------+
| Model browser                                              |
+------------------------------------------------------------+
| v DMAV...                                                  |
|   v Bodenbedeckung                                         |
|     v Gebaeude                                             |
|       [x] Art                         TEXT                  |
|       [x] Geometrie                   SURFACE               |
|       v Adresse                       STRUCTURE 0..1         |
|         [x] Strasse                                        |
|         [x] Nummer                                         |
|       -> Gemeinde                     ROLE {1}              |
+------------------------------------------------------------+
```

## Empfohlene Lieferfolge

```text
Phase 0  Build + Model + Curve Spike + Dev Install
   |
   v
Phase 1  INTERLIS Input + Model Browser + XTF -> Hop
   |     <-- erster bewusst vorzeigbarer Stand
   v
Phase 2  INTERLIS Output + Roundtrip
   |
   v
Phase 3  Structures
   |
   v
Phase 4  Associations + Role Join
   |
   v
Phase 5  Advanced Envelope + Transfer I/O
   |
   v
Phase 6  Validation + Enums + Basket/Operations + UX
   |
   v
Phase 7  ITF / INTERLIS 1
   |
   v
Phase 8  Hardening + Performance + Compatibility
```

## Lokale Entwicklung

Zielworkflow:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

Der Befehl soll:

```text
Geometry Plugin bauen
        |
INTERLIS Plugin bauen + testen
        |
Distribution prüfen
        |
Plugins nach HOP_HOME installieren
        |
Hop GUI neu starten
```

## Verbindliche Qualitätsregel

Keine Phase gilt als abgeschlossen, wenn nur Java-Unit-Tests grün sind. Für zentrale Benutzerpfade sind echte Hop-Pipeline- und `hop-run`-E2E-Tests vorgesehen.

