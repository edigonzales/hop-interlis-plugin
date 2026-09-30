# GUI- und UX-Spezifikation

Diese Spezifikation beschreibt die vorhandenen Dialoge des aktuellen Pluginstands
(Hop 2.19.0). Das [Benutzerhandbuch](../biblios/user/master.adoc) erklärt dieselben
Controls mit ausführbaren Beispielen. Ein Mehrklassen-Assistent, editierbares
Output-Automapping und weitere Modellbrowser-Aktionen gehören nicht zum aktuellen
Funktionsumfang.

## 1. Gemeinsame Prinzipien

- Normale Anwender sehen typisierte Hop-Felder. Objektträger sind gezielte technische
  Optionen für erhaltende Verarbeitung.
- Modellanalyse erfolgt in gemeinsamen Schema-/Mappingdiensten, nicht in SWT.
  Vorschau, `getFields` und Laufzeit verwenden denselben Plan.
- Modell-, Datei- oder Repository-Probleme verhindern nicht das Öffnen des Dialogs.
  Ein nicht-modaler Statusbereich zeigt eine verständliche Diagnose.
- Variablen werden vor dem Probing aufgelöst. Unaufgelöste Variablen verhindern eine
  Vorschau, nicht das Bearbeiten der Konfiguration.
- `TextVar`, `ComboVar`, `BaseTransformDialog` und `PropsUi` folgen Hop-Konventionen.
- `Options…` enthält selten benötigte Ressourcen-/Erhaltungs-/Validierungsoptionen.
  Numerische Budgets werden vor Übernahme geprüft; Abbrechen verwirft Änderungen.

## 2. Modellquelle und asynchrone Probe

`Models`, `Model dirs` und gegebenenfalls `Class` sind die Modellcontrols. Bei
Dateiinputs kommt `Data file` hinzu. `%DATA` ermittelt Modelle aus dem Transfer;
`%XTF_DIR` bezeichnet dessen Verzeichnis. Modellnamen und Modellverzeichnisse
werden mit Semikolon getrennt. Standardverzeichnisse sind
`%XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch`.

Technische Importmodelle werden nicht als fachliche Klassen angeboten. Die
Klassenauswahl enthält transferierbare Klassen und Assoziationen. Nach erfolgreicher
Modellprobe darf die Klasse leer bleiben; erst die Auswahl liefert die Vorschau.

Input, Output, Object to Row, Row to Object, Explode/Collect und Role Join verwenden
`InterlisProbeCoordinator`: 300 ms Debounce, eine laufende und eine ersetzbare
Anfrage pro Dialog. Explizites `Reload` startet ohne Debounce und invalidiert den
kompilierten Modellcache. Konfiguration und Variablen werden vor dem Hintergrundlauf
kopiert. Nur die aktuelle Anfrage darf Ergebnisse im SWT-Thread anzeigen;
Schliessen verwirft ausstehende Ergebnisse.

Der typisierte Status unterscheidet INFO, SUCCESS, WARNING und ERROR; er wird nicht
aus englischen Meldungstexten abgeleitet. Der Bereich bricht Text um und passt seine
Höhe beim Resize an. Eine leere oder nicht verfügbare Vorschau ist kein Beleg für
Laufzeitfähigkeit; Runtime bleibt strikt.

## 3. INTERLIS Input

Vorhandene Controls:

| Control | Default |
|---|---|
| Data file | leer |
| Models | `%DATA` |
| Model dirs | Standardverzeichnisse |
| Class + Reload model | leer |
| Reserved fields | TID/BID an; Klasse/Topic/Operation aus |
| Default SRID | leer |
| Keep source object for Structure Explode | aus |
| Source object field | `_ili_source_object` |
| Options… | Puffer-Einstellungen |

Die Vorschau zeigt das projizierte Klassenschema. Einwertige Strukturen sind
abgeflacht; Mehrfachattribute verweisen auf Explode/Collect. Referenzattribute
zeigen Zielklasse und External-Eigenschaft. Unbekannte angeforderte Mappingtypen
scheitern mit Kontext. Es gibt keine Inline-Validierung, DELETE-Policy, auswählbare
Flatten-Policy oder editierbare Klassenfeldselektion.

Die Vorschau erklärt die unterschiedlichen Zusagen von Projektion,
Quellobjekt-Overlay und vollständigem Ereignisstrom. Ein Quellobjekt erhält
Objektinhalte; ein Klasseninput selektiert genau die konfigurierte Klasse.

## 4. INTERLIS Output

| Control | Default |
|---|---|
| XTF file / Overwrite existing file | leer / aus |
| Models / Model dirs / Class + Reload model | leer / Standardverzeichnisse / leer |
| Object ID field | `_ili_tid` |
| BID field / Default BID | `_ili_bid` / `b1` |
| Source object field / Operation field | leer / leer |
| Options… | Validierung vor Veröffentlichung |

Die Vorschau zeigt erwartete Mappingfelder. Die Bindung erfolgt über Namen; es
existieren weder `Auto-map by name` noch `Get incoming fields` als editierbare
Mappingaktionen. UUID-Erzeugung und Basket-Policy-Auswahl sind keine Controls.

TIDs müssen bereitstehen. BIDs müssen zusammenhängend gruppiert sein. Ausgabe wird
auf einer temporären Datei vorbereitet und am erfolgreichen Pipeline-Ende
veröffentlicht. Ein Reader derselben Pipeline kann nicht die neue Ausgabe prüfen.

## 5. Transfer Input/Output und Object↔Row

Transfer Input bietet Data file, Models, Model dirs und Mode (`OBJECTS` als Default,
`EVENTS` für Grenzereignisse). Das kanonische Schema enthält 15 Felder, einschliesslich
Basket-Metadaten und Transfer-Metadaten-JSON. Löschobjekte verwenden OBJECT und
Operation DELETE.

Transfer Output bietet Data file, Models, Model dirs, Overwrite und Event mode
(Default aus), dazu die Writer-Validierungsoptionen. Objektmodus benötigt
zusammenhängende BID-Gruppen; Ereignismodus die vollständige gültige Transferfolge.

Object to Row bietet Models, Model dirs, Class + Reload, Object field (`_ili_object`),
Default SRID, Include TID/BID (an), Append typed fields to envelope fields (aus)
und die Pufferoptionen.

Row to Object bietet Models, Model dirs, Class + Reload, Basket ID field (`_ili_bid`),
Source object field und Operation field. Leere Träger-/Operationsfelder erlauben
automatische Erkennung der technischen Standardfelder. Ausgabe ist das
Envelope-Schema; Modellinterpretation bleibt im Mappingdienst.

## 6. Structure Explode

Models, Model dirs, Class + Reload und Structure wählen die Sammlung. Weitere
Controls sind Source object field (`_ili_source_object`), Parent TID field
(`_ili_tid`), Parent BID field (`_ili_bid`), Parent key field (`_ili_parent_tid`),
Index field (`_ili_index`), Copy parent fields, Emit parent BID field (an) und
Emit technical index for BAG (an).

`Options…` bietet Keep child source object (bei neuen Konfigurationen an).
Strukturkinder führen damit `_ili_child_object` mit. Primitive Sammlungen liefern
`_ili_value` mit dem Elementtyp und brauchen keinen Kindträger.

`selectedChildFields` ist eine gespeicherte Projektion von Attributpfaden. Der
aktuelle Explode-Dialog hat keinen eigenen Feldauswahl-Control. Eine in der `.hpl`
gespeicherte Auswahl bleibt erhalten; das Erhaltungstutorial enthält `Name`.
Die Vorschau wird aus demselben Kindplan wie die Laufzeit erzeugt.

## 7. Structure Collect

Die Hauptcontrols sind Parent input transform, Child input transform, Parent key
field, Child parent key field, Child index field, Models, Model dirs, Class + Reload,
Structure und Source object field. Strict LIST ordering (contiguous indexes),
Fail on duplicate index und Fail on child without parent sind standardmässig an.

`Options…` enthält die Pufferoptionen sowie:

| Control | Default bei neuen Konfigurationen |
|---|---|
| Child attribute paths (comma separated, empty: all) | leer |
| Collect mode | PRESERVE |
| Parent basket field | `_ili_bid` |
| Child parent basket field | `_ili_parent_bid` |

PRESERVE verlangt einen kompatiblen Kind-Carrier und überschreibt nur gewählte
Attribute. REBUILD erzeugt ein Kind aus projizierten Werten. Collect ersetzt die
gewählte Sammlung vollständig; Filtern entfernt Kinder. Die Vorschau erläutert
Träger, Kardinalität und die Erwartung sortierter Eltern-/Kindschlüssel.

Beim Laden alter XML-Dateien ohne neue Optionen gilt: kein Explode-Kindträger,
Collect REBUILD und bisherige Schlüsselwahl ohne BID. Neue Defaults gelten beim
Anlegen neuer Konfigurationen, nicht als stille Migration alter Dateien.

## 8. Role Join

Controls: Main input, Lookup input, Models, Model dirs, Class + Reload, Role,
Main ref field, Lookup TID field (`_ili_tid`), Output prefix, Fields to add,
Max lookup rows (500000), Fail on missing mandatory reference (an) und
Fail on duplicate lookup TID (an). Options… bietet Puffer-Einstellungen.

Die modellbasierte Vorschau zeigt hinzugefügte Zielfelder. Lookup-Überschreitung ist
ein Fehler; 0 bedeutet unbegrenzt. Beide Eingänge werden fair bedient, bis Lookup
vollständig ist. Die erste Ausgabe kann deshalb warten.

## 9. Validate und Enumerations

Validate bietet Data file, Models (`%DATA`), Model dirs, Validator config (INI),
Max errors (10000; 0 unbegrenzt), Validate attribute/role multiplicity (an),
Stop after first error (aus), Emit warnings (an), Emit info findings (aus) und
Fail the pipeline when errors were found (aus). Es ist ein Dateiinput mit
13 Diagnosefeldern, kein Inline-Schalter im Klasseninput.

Enumerations bietet Models und Model dirs. Ausgabe: enum_definition, enum_value,
enum_path, parent_value, depth, is_leaf. Eine GUI-Enumeration-Filteroption ist
nicht Bestandteil dieses Dialogs.

## 10. Gemeinsame Options-Controls

Input, Object to Row, Role Join und Collect bieten Buffer memory (MiB) (64),
Spill directory (empty: system temp) und Maximum spill (MiB, 0: unlimited) (0: keine zusätzliche Obergrenze).
Teilpuffer teilen Budget und Disk-Grenze pro Transform. Die Beschreibung muss
Einzelobjektgrenzen und mögliche Wartezeiten bis Basket-/Lookup-Abschluss erklären.

Beide Writer bieten Validate before publication (aus) und Validation configuration
(leer). Die vollständige temporäre Datei wird einschliesslich zweitem Durchlauf
geprüft. Publikation setzt den erfolgreichen Abschluss der gesamten Pipeline voraus.

## 11. GUI-Abnahme

Automatisierte Tests prüfen Probe-Koordination, veraltete Resultate, Reload und
Schliessen sowie XML-Lade-/Speicherdefaults. Eine interaktive SWT-Abnahme ist ein
eigener Prüfschritt und wird nicht durch Browser-Sichtprüfung des Handbuchs ersetzt.

Handbuchtabellen müssen die vorhandenen Controls und Defaults nennen. Die
Dokumentationsabnahme rendert den aktuellen Working Tree und prüft Kapitel,
Tabellen, Listings, Includes und Links bei breitem und schmalem Browserfenster.
Historische Abnahmeberichte dokumentieren weiterhin ihren damaligen Prüfstand.
