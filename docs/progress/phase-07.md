# Phase 7 – ITF / INTERLIS 1

Status: **bewusst übersprungen** (Entscheid vom 2026-08-26)

INTERLIS 1 (ITF) wird von `hop-interlis-plugin` **nicht unterstützt**. Der
Kundenbedarf besteht nicht; der Fokus liegt auf INTERLIS 2 (XTF). Phase 8
(Hardening, Performance, Kompatibilität) wurde stattdessen vorgezogen und
vollständig umgesetzt.

## Konsequenzen

- Die Roadmap-Reihenfolge (Phase 7 vor Phase 8) wurde geändert: Phase 8 hängt
  technisch nicht von Phase 7 ab, die umgekehrte Reihenfolge ist
  dokumentiert.
- ITF-Dateien werden **explizit abgelehnt** statt halb interpretiert:
  `XtfTransferReader.rejectUnsupportedFormat(...)` und `INTERLIS Validate`
  werfen bei `.itf`/`.ili1`-Dateien eine klare Fehlermeldung mit Hinweis auf
  Konvertierung nach INTERLIS 2 (z. B. mit ili2c). Damit kann ein Anwender
  nie stillschweigend falsche Daten aus einer ITF-Datei erhalten.
- Der ITF-Unterordner aus dem Beispiekatalog (05-Roadmap 8.5) entfällt;
  `examples/` enthält kein `itf/`-Beispiel.
- Transforms (`INTERLIS Input`, `INTERLIS Transfer Input`, `INTERLIS
  Validate`) behandeln `.itf` als Konfigurationsfehler mit Actionable
  Message.

## Was eine spätere Umsetzung umfassen würde (Roadmap 7)

- ITF Reader/Writer über den ReaderFactory-/iox-Pfad;
- AREA/SURFACE-Modi (Polygon / Raw / Polygon+Raw) mit eigener
  Schemastrategie (kein Mischen zweier Row-Schemas in einen Stream);
- optionale Linetable-Hilfstransforms nur nach UX-Spike;
- Legacy-Option für rohe ITF-Enum-Codes (Advanced Mode);
- ITF-E2E-Tests.

## Tests

- Core: `XtfTransferReaderTest.rejects_interlis_1_itf_transfers_with_a_clear_message`.
