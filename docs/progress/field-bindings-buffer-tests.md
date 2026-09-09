# Einheitliche Feldbindung und Pufferspeichertests

Status: Implementiert und automatisiert vollständig verifiziert.

Umfang: Gemeinsame, unveränderliche Bindung für Output, Row to Object, Object to
Row, Transfer Output, Structure Explode/Collect und Role Join. Typprüfung,
namensbasierte Zuordnung und Hop-Speichernormalisierung sind zentral; die
transformabhängigen Pflicht-/Optional-/Ersatzwertregeln bleiben in kleinen
Adaptern. Explode und Role Join verwenden gemeinsame Ausgabepläne. Collect passt
die Speicher-Metadaten seines aktualisierten Carriers an.

Bewusste Verhaltensänderungen: technische IDs/Schlüssel müssen String sein;
fehlende explizit ausgewählte Parent-/Lookup-Felder und mehrdeutige Namen sind
Fehler. Fachliche Typkonvertierung muss vor dem Transform erfolgen. Unveränderte
Pass-through-Felder behalten ihre Metadaten und Werte.

Puffer: unabhängige Batches, idempotentes Leeren und Freigabe von Puffer/
Ausgabeiterator bei Abschluss, Fehler und Abbruch. Die Speichergrenze richtet
sich weiterhin nach Basket-Grösse und ausstehender Ausgabe. Der isolierte Heap-Test
ist ein Regressionsnachweis und kein neues Produktionslimit.

Ausserhalb des Umfangs: Validator-Auslagerung, neue Pipeline-Eigenschaften,
Bibliotheksupdates, Partitionierung und Spill-to-disk. Die interaktive SWT-Abnahme
bleibt separat offen, wie im P2-Bericht dokumentiert.

Die Bindungsregressionen wurden auch gegen den unveränderten Ausgangscommit in
einer isolierten Kopie ausgeführt. Falsche Fachtypen und nicht normalisierte
Lazy-Strings reproduzierten die bisherigen Fehler. Der ergänzte Mehrdeutigkeitstest
stellt die doppelten Namen ausdrücklich her, da Hop beim Hinzufügen sonst umbenennt.

Abnahme am 9. September 2026 unter macOS mit Temurin 21.0.7:

- `./mvnw -B -ntp clean verify`: erfolgreich, 382 Tests (176 Core und 206
  Transforms), keine Fehler oder übersprungenen Tests.
- Heap-Test: 16'384 Zeilen mit insgesamt 536'870'912 Byte Nutzdaten in einer
  separaten JVM mit maximal 128 MiB Heap; Zeilenzahl und Prüfsumme stimmen.
- Pipeline-Lifecycle: jeweils Input und Object to Row mit acht Baskets und
  langsamem Verbraucher; Erfolg, Mappingfehler und Benutzerabbruch geprüft.
  Puffer und Ausgabeiteratoren sind nach Abschluss/Dispose freigegeben.
- Distributionsprüfung erfolgreich; Paket 4.9 MiB, gemeinsame Runtime-Bibliotheken
  bleiben provided.
- Alle 31 Paket-E2E-Pipelines gegen isoliertes Hop 2.18.1 erfolgreich,
  einschliesslich GeoPackage und erwarteter Fehlerfälle. Umgeordnete
  Explode-/Collect- und Join-Streams erhalten die erwarteten Werte.
- Diff und Git-Status geprüft: keine generierten Build-Artefakte, lokalen
  Konfigurationen oder Entwicklerpfade im Änderungssatz.

Die bestehende separate SWT-Abnahme wurde für dieses Paket nicht wiederholt.
