# P2-Korrekturpaket

Status: Implementiert. Automatisierte Abnahme erfolgreich; interaktive SWT-Abnahme
noch offen. P2 ist deshalb noch nicht vollständig abgeschlossen.

Ausgangspunkt: P1-Commit 315ab2e. Umfang: Review-Befunde 8–15 und die Zusatzbefunde
zu Enumerationen, expliziten Modelldateien und Release-Absicherung.

Umgesetzt: tiefe Projektion; Inhaltsprüfung des Modellcaches und Reload;
namensbasierte Envelope-Bindung; Header-/Basket-/Carrier-/Operationssemantik;
Writer-Abschluss und Restkinder; asynchrone Modellproben mit typisiertem Status;
strikte Datumswerte und DST; vollständige Enumeration-Suche; gemeinsamer
Basket-Puffer; wiederverwendbare CI und Release-Abhängigkeit von Matrix und E2E.

Bewusste Einschränkungen: OID-Space-Namen können mit dem gepinnten XTF-2.3-Reader
nicht rekonstruiert werden; dieser Fall wird bei Event-Ausgabe abgelehnt. Reload
umgeht den kompilierten Cache, nicht die Downloadcache-TTL der INTERLIS-Bibliothek.
Die P1-Einschränkung für echte 3D-Kurven bleibt bestehen.

Verifikation am 9. September 2026 unter macOS mit Temurin 21.0.7:

- `./mvnw -B -ntp clean verify`: erfolgreich, 367 Tests (173 Core, 194 Transforms),
  keine Fehler und keine übersprungenen Tests.
- `python3 scripts/check-distribution.py`: erfolgreich; ZIP 4.9 MiB, gemeinsame
  Geometry-/Hop-/JSON-Abhängigkeiten werden nicht zusätzlich gebündelt.
- Die für CI gepinnte GeoTools-Revision wurde separat mit Hop 2.18.1 und Geometry
  0.2.0-SNAPSHOT vollständig gebaut und getestet.
- Paket-E2E: alle 27 Pipelines einschliesslich GeoPackage und der erwarteten
  Fehlerfälle erfolgreich, ausgeführt mit frisch gebauten Plugins in einer neu
  entpackten, isolierten Hop-2.18.1-Installation. Inhaltliche Ausgabeprüfungen
  einschliesslich ARC, XYZ, Header, Carrier/BAG und Diagnosen erfolgreich.
- Shell-Syntax und Workflow-YAML geprüft. Die neue GitHub-Matrix (Linux, macOS,
  Windows; JDK 21 und 25) wurde hier nicht ausgeführt.
- Diff, Git-Status und alle 74 geänderten/neuen Dateien geprüft: keine generierten
  Build-Artefakte, lokalen Konfigurationen oder Entwicklerpfade im Änderungssatz.

Regressionen wurden zunächst reproduziert, darunter tiefe Feldauswahl,
Datums-/DST-Werte, explizite Modelldateien und Cache-Invalidierung. Bei der letzten
Kontrolle wurde zusätzlich eine unerkannte widersprüchliche Basket-ID im Eventmodus
reproduziert und behoben; passende Eventströme bleiben erfolgreich.

Die Paketprüfung entdeckte zudem eine fehlende Runtime-Klasse in der zunächst
gewählten GeoTools-Distribution. Die CI nutzt deshalb die vollständig geprüfte
Vector-Revision `578cacc6eb6e34657d1823f5f88036e7f8751144`; es wurde keine fremde
Plugin-Implementierung in dieses Repository übernommen.

Offene SWT-Abnahme: Die isolierte Hop-GUI wurde gestartet, konnte aber vom
UI-Werkzeug nicht erreicht werden (Timeout). Daher wurden weder die sichtbaren
Dialoge noch deren interaktive Bedienung als erfolgreich geprüft gewertet. Die
Testinstanz wurde anschliessend beendet. Automatisierte Koordinator-Tests decken
Debounce, veraltete laufende/queued Ergebnisse und Schliessen ab. Noch manuell zu
prüfen: betroffene Dialoge öffnen, schnell Modelle/Klassen ändern, Reload auslösen
und während einer laufenden Probe schliessen; Vorschau und Status müssen aktuell
bleiben und die Oberfläche bedienbar bleiben.
