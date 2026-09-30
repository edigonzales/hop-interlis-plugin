# Prio 1 und 2: Semantik, Spill und sichere Ausgabe

Stand: 30. September 2026. Implementierung, Full Verify, Distributionsprüfung
und vollständige installierte E2E-Abnahme erfolgreich.

## Implementierter Umfang

- Die zentrale Schemaextraktion erhält primitive Kardinalitäten und LIST/BAG
  auch durch Typalias-Ketten. Referenzattribute besitzen eigene Descriptoren
  mit Zielklasse und External-Eigenschaft. Angeforderte unbekannte Typen werden
  mit Attribut-/Klassen-/Typkontext abgelehnt.
- Primitive Sammlungen verwenden die bestehenden Structure Explode/Collect
  Plugin-IDs und typisierte `_ili_value`-Kindzeilen. LIST-Reihenfolge,
  BAG-Duplikate, leere Sammlungen und Kardinalität werden berücksichtigt.
  Klassenprojektionen lassen Sammlungen mit einer ausdrücklichen Diagnose aus;
  Quellobjekt-Overlay erhält ihre Inhalte.
- Skalare Referenzattribute, auch in abgeflachten Strukturen, verwenden
  `<Pfad>_ref` und `<Pfad>_ref_bid`. Lesen und Schreiben behandeln IOM-Referenzen;
  null entfernt eine projizierte Referenz, BID ohne TID ist ein Fehler.
- Strukturkinder können `_ili_child_object` mitführen. `PRESERVE` kopiert den
  konkreten zulässigen Untertyp und überschreibt ausgewählte Felder; `REBUILD`
  erzeugt das deklarierte Kind neu. Composition-Restrictions und abstrakte
  Typen werden bei der Planerstellung berücksichtigt. Der Kindstrom ersetzt
  immer die gesamte ausgewählte Sammlung, sodass Filtern Kinder entfernt.
- Neue Explode-Konfigurationen aktivieren den Kind-Carrier; neue Collect-
  Konfigurationen verwenden `PRESERVE` und `(BID, Elternschlüssel)`. Alte XML-
  Konfigurationen ohne diese Eigenschaften laden weiterhin ohne Kind-Carrier,
  mit `REBUILD` und ihrer bisherigen Schlüsselwahl. XML-Abnahme verwendet
  Hops tatsächlichen Metadatenserializer.
- H2 2.4.240 ist `provided` und wird durch Hops JDBC-Classloader geladen.
  Sequenzen, Lookup-Indizes und Kindsortierung wechseln automatisch auf Disk.
  Core-Codecs haben keine Hop-Abhängigkeit; Hop-Zeilen verwenden die binären
  ValueMeta-Konverter. H2 und ImageN werden nicht ins Plugin-ZIP gepackt.
- Input/Object to Row projizieren Basket-Zeilen einzeln; eine zweite vollständige
  Hop-Zeilenliste entfällt. Widersprüchliche Assoziationslinks werden abgelehnt.
  Role Join und Collect lesen Eingänge abwechselnd und puffern über Spill;
  Explode erzeugt Kinder einzeln.
- Pufferdefaults: `bufferMemoryMiB=64`, `spillDirectory=""` (Java-Temp),
  `maxSpillMiB=0` (keine Disk-Obergrenze). Teilpuffer eines Transforms teilen
  die gemeinsame Disk-Obergrenze. Role Join behält zusätzlich das Limit von
  500'000 Eingangszeilen; 0 erlaubt unbegrenzt viele. Aufräumen erfolgt bei
  Abschluss, Fehler und Stop.
- Beide Writer bereiten eindeutige temporäre Dateien im Zielverzeichnis vor.
  Veröffentlichung erfolgt erst nach vollständigem Transfer, erfolgreichem
  Schliessen und erfolgreichem Pipeline-Abschluss. Überschreiben verwendet
  atomisches Ersetzen; ohne Überschreiben verwendet die Veröffentlichung ein
  atomisches Hard-Link-Anlegen ohne Ersetzungsrecht. Es gibt keinen unsicheren
  Fallback bei fehlender Dateisystemunterstützung.
- Ein pipelineeigener Koordinator berücksichtigt aufgeschobene Validate-Fehler
  vor jeder Veröffentlichung. Veröffentlichungsfehler zählen als Writer- und
  Pipelinefehler. Der Koordinator zählt und protokolliert sie, ohne aus dem
  Abschluss-Listener zu werfen: Hop 2.19.0 würde andernfalls das Signal an
  `waitUntilFinished()` überspringen.
- `validateBeforePublish=false` bleibt Default. Optional prüft der gemeinsame
  Validierungsdienst die vollständige temporäre Datei einschliesslich zweitem
  Durchlauf und optionaler Konfigurationsdatei. Fehler, Stop oder unvollständige
  Validierung verhindern die Veröffentlichung. Wiederverwendete BIDs werden
  zentral in typisierter Ausgabe sowie generischem Objekt-/Ereignismodus
  abgelehnt.
- Options-Dialoge und Vorschauen zeigen die neuen Einstellungen. Spezifikation
  und Benutzerhandbuch unterscheiden Projektion, Quellobjekt-Overlay und den
  vollständigen Ereignispfad. Vier ausführbare Paket-Pipelines ergänzen die
  bisherigen Beispiele und E2E-Prüfungen.

## Bibliotheken und Kompatibilität

Hop 2.19.0, ili2c 5.6.8, iox-ili 1.24.4, iox-api 1.0.3 und ehibasics 1.4.1
sowie die Workflow-/Helper-Pins bleiben unverändert. Der fehlende ImageN-
Testclasspath des aktuellen Geometry-Snapshots ist mit `imagen-core` 0.9.2
im Testscope ergänzt; das Release-Repository von OSGeo stellt dieses Artefakt
bereit.

Für iox-ili 1.24.4 sind zwei eng begrenzte Adapter erforderlich und mit
Regressionen abgesichert: Der XTF-2.4-Writer ergänzt ausgelassene externe
Referenz-BIDs an dessen geschütztem StAX-Ausgang. Reader und Validator korrigieren
das genau identifizierte zusätzliche leere REF-Mitglied, das der gepinnte Reader
bei eigenständigen Assoziationsrollen mit BID erzeugt. Modell-/Transferkodierung
bleibt in ili2c/iox-ili; beliebige fehlerhafte Objekte werden nicht bereinigt.

## Ausgeführte Abnahme

Umgebung: macOS, Temurin 21.0.10; Maven Wrapper, isoliertes offizielles Hop 2.19.0.
Geometry 0.2.0-SNAPSHOT und Vector/Raster 0.1.0-SNAPSHOT werden entsprechend
dem bestehenden Workflow als ZIPs installiert. E2E verwendet das gebaute
INTERLIS-ZIP statt Klassen aus dem Maven-Testclasspath.

- `./mvnw -B -ntp clean verify`: erfolgreich; 415 Tests (193 Core, 222
  Transforms), keine Fehler, keine übersprungenen Tests.
- `python3 scripts/check-distribution.py`: erfolgreich; ZIP 4.9 MiB, Jandex-
  Index und Runtime-Layout geprüft, keine zusätzlich gepackten H2-/ImageN-JARs.
- `REQUIRE_VECTOR_RASTER_E2E=true bash scripts/run-e2e.sh "$HOP_HOME"`:
  erfolgreich; alle 41 Paket-Pipelines einschliesslich GeoPackage und erwarteter
  Fehlerfälle ausgeführt, keine Pipeline übersprungen. Die neuen Pipelines
  39–42 prüfen primitive Sammlungen, Kind-Erhaltung, den vollständigen externen
  Referenz-Roundtrip mit Vollvalidierung und 600 grosse Strukturkinder mit
  erzwungenem Spill bei 1 MiB Pufferbudget. Die erzeugten Ausgaben werden
  semantisch geprüft; temporäre Ausgabe-/Spill-Dateien bleiben nicht zurück.
- Fixtures und Roundtrips prüfen Text, Zahlen, Boolean, Enum, Duplikate,
  Reihenfolge, Null-/Kardinalitätsfehler, interne/externe Referenzen,
  Quellobjekt-Overlay, Untertypen, versteckte Felder und verschachtelte Listen.
- Pipeline-Tests prüfen Queue-Grössen 1 und 2, gemeinsame Produzenten und
  langsame Verbraucher. Memory-/Disk-Ergebnisse stimmen für ARC, XYZ, SRID,
  Referenzmetadaten und Carrier-Inhalte überein.
- Bestehende Dateien bleiben bei Mapping-, Close-, Validierungs-, Stop- und
  späteren Zweigfehlern erhalten. Ein konkurrierendes Anlegen des Ziels ohne
  Überschreibrecht erzeugt einen Pipelinefehler und erhält die konkurrierende
  Datei; der Lauf beendet sich und temporäre Dateien werden entfernt.

Zwei getrennte JVMs mit `-Xmx128m` verarbeiten jeweils 9'000 Einträge. Messwerte
des abschliessenden Full-Verify-Laufs; Heap ist das alle 5 ms beobachtete Maximum
der HeapMemoryUsage, Disk der beobachtete temporäre Dateibedarf. Der Consumer
akkumuliert keine Ergebniszeilen.

| Fall | Serialisierte Daten (Byte) | Beobachteter Heap (Byte) | Temp-Disk (Byte) | Erste Ausgabe | Gesamtdauer |
|---|---:|---:|---:|---:|---:|
| Ein Basket | 752'751'476 | 123'719'344 | 762'322'944 | 1'362 ms | 2'376 ms |
| Ein Lookup | 368'883'000 | 131'137'936 | 372'924'416 | 840 ms | 1'240 ms |

Das sind reproduzierbare lokale Regressionstests und Messungen mit synthetischen
Daten, keine allgemeinen Performancezusagen und kein Vergleich mit ili2fme.

## Verbleibende Grenzen

- Ein einzelnes IOM-Objekt, die aktuelle Zeile und das bearbeitete Elternobjekt
  müssen weiterhin in den Heap passen; das Pufferbudget ist kein JVM-Heaplimit.
- Assoziationsabhängige Projektion wartet weiterhin auf den vollständigen
  Basket; Role Join wartet auf den vollständigen Lookup.
- Collect verlangt nach Elternidentität sortierte Eingänge. Bei deaktivierter
  strikter Reihenfolge übernimmt Spill die Indexsortierung innerhalb einer
  Kindgruppe. Eine globale Neuordnung aller Eltern ist nicht enthalten.
- Verschachtelte Mehrfachstrukturen werden im Carrier erhalten; ihre zusätzliche
  Bearbeitung ist nicht Bestandteil dieses Laufs. Primitive Referenzsammlungen
  besitzen keinen typisierten Explode/Collect-Pfad und werden dort abgelehnt.
- Atomarität gilt pro Datei. Mehrere Writer bilden keine gemeinsame Transaktion;
  ein späterer Veröffentlichungsfehler kann frühere Veröffentlichungen nicht
  zurückrollen.
- Die automatisierten Dialog-/Metadatenprüfungen ersetzen keine interaktive
  SWT-Abnahme. Die OS-/JDK-CI-Matrix wurde lokal nicht vollständig ausgeführt.
- Prio 3, Mehrklassen-Assistent, neue Release-Kombinationen und FME-Vergleich
  bleiben für einen späteren Lauf.
