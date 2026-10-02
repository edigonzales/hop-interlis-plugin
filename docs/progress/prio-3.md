# Prio 3: Mehrklassenexport, Update und Struktur-Update

Stand: 2. Oktober 2026. Implementierung und lokale Abnahme erfolgreich.
Modellmigration, ilitransformer-Anbindung und grafischer Mappingdesigner gehören
nicht zu diesem Paket. Bibliotheks-, Hop- und CI-Pins bleiben unverändert.

## Gelieferte Funktionen

1. **Mehrklassenexport:** Der bestehende `INTERLIS Output` besitzt einen neuen
   Modus mit separat konfigurierten Klasseneingängen. Jeder Eingang behält seinen
   Mappingplan, seine Feldbindungen und sein Hop-Schema. Sämtliche physischen
   RowSets werden abwechselnd gelesen. Ein gemeinsamer Spill-Speicher gruppiert
   die erzeugten Objekte nach Basket. Der Modus ist ein abschliessender Writer.
   TIDs stammen ausdrücklich aus Eingabefeldern. Die Standard-Basketstrategie
   erzeugt UUID-BIDs je Topic; bei speziellen Basket-OID-Domänen werden explizite
   BID-Felder verlangt. Eine BID darf nicht verschiedenen Topics gehören.
2. **Objekt-Update:** `INTERLIS Update` sammelt Änderungen in einem Spill-Index
   und liest danach den vollständigen Originaltransfer. Der Objektschlüssel
   besteht aus Klasse, BID und TID. Nur ausgewählte Fachattribute werden auf
   einer tiefen Kopie geändert, einschliesslich Geometrien und Attributen in
   einwertigen Strukturen. Null setzt das gewählte Attribut auf undefiniert.
   Fehlende Änderungszeilen erhalten das Original; doppelte und unbekannte
   Schlüssel führen zum Fehler. Identitäten, Beziehungen und Löschereignisse
   können nicht auf diesem Weg geändert werden.
3. **Struktur-Update:** Explode kann `_ili_update_ref` liefern. Der versionierte
   Schlüssel enthält Elternidentität, Strukturpfad, ursprüngliche Position und
   einen einmal pro Elternobjekt berechneten SHA-256 über kanonischen IOM-Inhalt.
   Update prüft ihn gegen das Original und ersetzt nur ausgewählte Kindattribute.
   Filtern entfernt keine Kinder; Sortieren verändert ihre Zuordnung nicht.
   LIST-Reihenfolge, BAG-Duplikate, Untertypen und tiefere Sammlungen bleiben
   erhalten. Primitive Sammlungen verwenden `_ili_value`; ein undefinierter
   primitiver Eintrag ist kein Löschbefehl. Eine Sammlungsebene darf auch innerhalb
   einwertiger Strukturen liegen. Objekt- und Strukturänderungen sind bei
   nicht überlappenden Pfaden kombinierbar.

Input bietet eine explizite Feldauswahl: „alle“ und „ausgewählte Felder“ sind
unterschiedliche Zustände. Eine leere explizite Auswahl liefert nur aktivierte
technische Felder und gegebenenfalls das Quellobjekt. Die neuen Dialoge arbeiten
mit privaten Entwürfen und übernehmen Änderungen erst mit OK. Modellproben
laufen asynchron; Fehler lassen die Dialoge weiterhin bedienbar.

## Architektur und Kompatibilität

- Bestehende Modell-, Feldschreib-, Geometrie-, Spill-, Validierungs- und
  Veröffentlichungsdienste werden wiederverwendet. `InterlisPatchPlan` schreibt
  nur ausgewählte Fachattribute. Update und Transfer Output teilen den
  Ereignisschreiber `InterlisEventWriter`.
- Neue Output-Konfigurationen verwenden den Mehrklassenmodus. Altes XML ohne
  Modus bleibt im Einklassenmodus mit bisherigem Schema, Durchreichen und
  Validierungsdefault. Neue Writer und Update validieren standardmässig;
  Überschreiben ist standardmässig ausgeschaltet.
- Neue Explode-Konfigurationen aktivieren die Update-Referenz. Altes XML ohne
  Option behält das bisherige Schema. Collect und seine Ersetzungssemantik
  bleiben unverändert.
- Original und Ziel werden einschliesslich Symlinks und Hardlinks auf Gleichheit
  geprüft. Dateigrösse, Änderungszeit und Dateiidentität des Originals werden vor
  und nach dem Lesen sowie unmittelbar vor der Veröffentlichung geprüft.
- Veröffentlichung erfolgt erst nach erfolgreichem Abschluss der gesamten
  Pipeline. Fehler anderer Zweige und Stop verhindern die Veröffentlichung.
  Temporäre Ausgabe und Spill-Daten werden aufgeräumt.
- Teilpuffer teilen die Disk-Obergrenze; das RAM-Budget wird auf die beteiligten
  Speicher verteilt. Das ist ein Budget für die Spill-Puffer, keine Obergrenze
  für den gesamten JVM-Heap. Ein einzelnes Objekt und seine Bearbeitung müssen
  weiterhin in den Arbeitsspeicher passen.

## Ausgeführte Prüfungen

Umgebung: macOS, Temurin 21.0.10, Maven Wrapper, offizielle isolierte Hop-2.19.0-
Installation. Die Maven-Repositories wurden über den bereits gepinnten gemeinsamen
CI-Helper vorbereitet. Geometry 0.2.0-SNAPSHOT und Vector/Raster 0.1.0-SNAPSHOT
wurden als ZIPs entsprechend der bestehenden CI-Konfiguration installiert.

- Fokussierte Prio-3-Pipelineprüfungen: 12 Tests erfolgreich. Dazu kommen
  Metadaten-/XML-, Herkunftsschlüssel- und Gruppierungstests.
- `./mvnw -s "$MAVEN_SETTINGS" -B -ntp clean verify`: **439 Tests**, davon
  196 Core und 243 Transforms; keine Fehler und keine übersprungenen Tests.
- `python3 scripts/check-distribution.py`: erfolgreich; Plugin-ZIP 5,0 MiB,
  Jandex-Index, Runtime-Layout und geteilte Abhängigkeiten geprüft.
- `REQUIRE_VECTOR_RASTER_E2E=true bash scripts/run-e2e.sh "$HOP_HOME"`:
  **56 Paket-Pipelines** erfolgreich, inklusive erwarteter Fehlerfälle,
  GeoPackage und aller Anwenderbeispiele. Die semantischen Ergebnisprüfungen
  und die Dokumentationsbeispiel-Prüfung sind erfolgreich.
- Die neuen Paket-Pipelines 43–48 prüfen Mehrklassenexport mit drei vorgelagerten
  Kopien, mehrere Baskets, selektives Objekt-/Struktur-/Primitiv-Update,
  XYZ-/ARC-Erhaltung und fehlerhafte Herkunftsreferenzen. 600 grosse Kinder
  erzwingen Auslagerung bei 1 MiB Pufferbudget. Kein temporärer Ausgabe- oder
  Spill-Rest bleibt zurück.
- Pipelineprüfungen ergänzen verschiedene Topics und automatische UUID-BIDs,
  widersprüchliche BIDs, leere Eingänge, Safe Mode und Queues der Grösse 1,
  umgekehrte Änderungsreihenfolge, BAG-Duplikate, konkrete Untertypen,
  kombinierte Geometrie-/Strukturänderung mit einer enthaltenen Sammlung,
  Nullwerte, doppelte/unbekannte/veraltete Schlüssel sowie Pfad-Aliase.
- Die Veröffentlichungstests prüfen beide neuen Writer gegen verspäteten
  Erfolg, Fehler, Stop und Originaländerung in anderen Pipelinezweigen.
  Vorherige Zielinhalte bleiben bei Fehlern bestehen.
- Interaktive SWT-Abnahme am installierten Plugin: Output- und Update-Dialog,
  klassenspezifische Feldzuordnung, gleichnamige Zuordnung, Speichern,
  Input-Feldauswahl einschliesslich leerer Auswahl, Explode-Optionen und
  `_ili_update_ref`-Vorschau; Modell laden/neu laden, ungültiger Strukturpfad,
  nicht auflösbare Variablen und Abbrechen laufender Proben. Änderungen eines
  abgebrochenen Mappingdialogs wurden beim erneuten Öffnen nicht übernommen.
  Ein dabei gefundener Konflikt zwischen Hops Hilfe-Button und GridLayout wurde
  behoben und erneut geprüft.

Die lokale Abnahme ersetzt keinen erneut ausgeführten CI-Lauf auf Linux,
Windows und JDK 25; diese Matrix wurde hier nicht ausgeführt.

## Beispiele und Grenzen

Ausführbare Beispiele und Aufruf stehen in [examples/README.md](../../examples/README.md#prio-3-direct-writer-and-selective-updates).
Die Spezifikation und das deutsche Benutzerhandbuch beschreiben Bedienung,
Schlüssel, Defaults und Erhaltungsumfang.

Erhaltung bedeutet semantische Erhaltung im unterstützten Ereignisumfang,
nicht byteidentisches XML. Nicht unterstützte Headerinhalte und unvollständige
Transfers werden abgelehnt. Zusätzliche Objekte/Kinder, Entfernen von Kindern,
Umklassieren und Änderungen an Referenzen/Identitäten sind kein Update-Modus.
Dafür bleiben die expliziten bisherigen Aufbauwege und später die Modellmigration
zuständig. Unveränderte Geometrien werden bei Update nicht konvertiert.
