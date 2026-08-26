# Phase 8 – Hardening, Performance und Kompatibilität

Status: **abgeschlossen** (Stand 2026-08-26)

Phase 8 wurde vor Phase 7 vorgezogen (INTERLIS 1/ITF wird nicht unterstützt,
siehe `phase-07.md`).

## Implemented

### Arbeitspaket 8.1 – Performance

- **Statischer Model-Cache:** `InterlisModelServiceImpl` cached kompilierte
  Modelle jetzt JVM-weit (statisch) statt pro Service-Instanz – in Pipelines
  mit mehreren Transforms wird ein Modell nur noch einmal kompiliert
  (Compile ist durch den bestehenden Lock serialisiert; das kompilierte
  `CompiledInterlisModel` ist unveränderlich und damit gefahrlos teilbar).
- **Large-Transfer-Baseline:** `LargeTransferStreamingTest` generiert
  50'000 Objekte (~4 MB XTF) on-the-fly, mappt und schreibt sie
  streaming (Input → Output) und liest das Ergebnis zurück
  (Count-Check). Gemessen auf dem Dev-Rechner (Apple Silicon):
  **~0.7 s** für den kompletten Roundtrip. Ein grosszügiger Sanity-Bound
  (180 s) fängt pathologische Nicht-Streaming-Regressionen in CI ab.
- Streaming ist über alle Reader/Writer/Transforms konstruktionsbedingt
  gegeben (Zeilen-orientierte `putRow`/`writeObject`-Pfade, keine
  Vollspeicher-Puffer ausser den dokumentierten Basket-/Link-Puffern für
  Referenzauflösung).
- Keine Optimierung wurde auf Kosten der Semantik-Klarheit vorgenommen
  (Roadmap-Vorgabe).

### Arbeitspaket 8.2 – Threading

- **Explizite Threading-Policy** (`InterlisParallelCopies`):
  - Datei-basierte Transforms (`INTERLIS Input`, `INTERLIS Transfer Input`,
    `INTERLIS Output`, `INTERLIS Transfer Output`, `INTERLIS Validate`,
    `INTERLIS Enumerations`) lehnen parallele Kopien mit klarer
    Fehlermeldung ab („Set Number of copies back to 1") – mehrere Kopien
    würden Rows duplizieren bzw. dieselbe Zieldatei korrumpieren.
  - Row-stateless Transforms (`INTERLIS Structure Explode`,
    `INTERLIS Structure Collect`, `INTERLIS Role Join`,
    `INTERLIS Object to Row`, `INTERLIS Row to Object`) unterstützen
    parallele Kopien (per Test verifiziert).
- Thread-sichere geteilte Dienste: `InterlisModelServiceImpl`
  (statischer Cache + Compile-Lock), Mapper/Pläne sind pro Transform
  unveränderlich nach Init; Reader/Writer sind pro Instanz und werden nie
  zwischen Kopien geteilt (Invariante aus 01-Arch §21).

### Arbeitspaket 8.3 – Hop-Kompatibilität

- **Compatibility Matrix in CI:** `.github/workflows/ci.yml` testet jetzt
  die Matrix OS (Linux/macOS/Windows) × JDK (21, 25); die unterstützte
  Hop-Version ist über die Maven-Property `hop.version` (aktuell 2.18.1,
  minimale unterstützte Release-Linie) zentral – neue Hop-Releases lassen
  sich durch Ergänzen einer Matrix-Zeile testen, ohne POM-Änderungen.
- Compiler-Release 21 (Parent-POM) definiert die minimale Java-Version;
  keine Copy/Paste-Divergenz bei API-Änderungen – die Codebasis verwendet
  konsistent die Hop-2.18-APIs der gebundenen Version (Policy in AGENTS.md).

### Arbeitspaket 8.4 – Modellrepository-Resilienz

- **`http(s)://`-Modellrepositorys** in den Model-Directories: nicht lokal
  gefundene Modelle werden über die ilirepository-Maschinerie
  (ili2c-tool, bereits Dependency) aufgelöst – Index (`ilimodels.xml`) und
  Modell-Dateien landen im lokalen Cache (`~/.ilicache` Default, 24 h TTL,
  15 s/40 s Connect/Read-Timeout; über System-Property
  `hop.interlis.repository.cache` überschreibbar). Imports innerhalb des
  Repositorys werden durch den ili2c `IliManager` mit aufgelöst.
- **Diagnostics:** nicht erreichbares Repository → „unreachable (offline?)";
  Modell im Repository unbekannt → klare Meldung mit Repository-Liste und
  Hinweis auf lokalen Override (`<name>.ili` in einem Model-Directory).
- **Lokale Overrides haben Vorrang:** lokale Directories werden zuerst
  konsultiert; das Repository wird nur angefragt, wenn nichts gefunden
  wurde.
- **Offline-taugliche Tests:** eigener eingebetteter HTTP-Server
  (`InterlisModelRepositoryTest`) – Auflösung inkl. Import, lokaler
  Override, Unreachable-/Unbekannt-Diagnostics; kein Netzwerkzugriff.
- Proxy: Standard-JVM-Proxyeinstellungen (`-Dhttp.proxyHost/Port`) gelten
  für die Repository-Downloads (HttpURLConnection-Pfad).

### Arbeitspaket 8.5 – Dokumentation und Beispiele

- **`examples/`** mit lauffähigen, parametrisierten Pipelines
  (Umgebungsvariablen `E2E_INPUT_DIR`/`E2E_OUTPUT_DIR`, Ausführung via
  `hop-run`): `xtf-to-csv/`, `xtf-roundtrip/`, `structures/`,
  `associations/`, `validation/`, `advanced-envelope/` – plus
  `examples/README.md`. `itf/` entfällt bewusst (Phase 7 übersprungen).
- **Releaseprozess automatisiert:** `.github/workflows/release.yml` baut
  auf Version-Tags (`v*`) die Distribution und hängt das Plugin-ZIP an das
  GitHub Release; zusätzlich `docs/release-process.md`.

## Tests

`./mvnw -B -ntp clean verify` grün (292 Tests: Core 146 + Transforms 146).
Neu u. a.:

- `InterlisModelServiceTest.model_cache_is_shared_across_service_instances`
  (statischer Cache);
- `InterlisModelRepositoryTest` (4 Fälle, eingebetteter HTTP-Server);
- `XtfTransferReaderTest.rejects_interlis_1_itf_transfers_...` (ITF-Fail-fast);
- `ParallelCopiesPolicyTest` (4 Fälle: Guard-Unit, Pipeline-Copies-Reject,
  Single-Copy-OK, Row-Transforms-mit-2-Kopien);
- `LargeTransferStreamingTest` (50k-Objekt-Streaming-Baseline, Sanity-Bound).

## Decisions / notes

- Reihenfolge Phase 8 vor Phase 7 (Kundenentscheid, dokumentiert in
  `phase-07.md`).
- ITF-Fail-fast statt stiller Halb-Interpretation.
- Der Repository-Lookup ist Version-agnostisch (Modellname pinnt die
  Version; die Sprachversion des Index-Eintrags wird nicht erzwungen).
- Der statische Model-Cache gilt pro JVM-Lauf (Hop-Sitzung); der
  Repositorium-Download-Cache persistiert in `~/.ilicache`.

## Known limitations

- Repository-Auflösung gilt für Top-Level-Modelle und deren Imports;
  `ilidata:`-Einzeldatei-URIs als Model-Directory-Eintrag werden nicht
  aufgelöst (http(s)-Repositorys sind der unterstützte Pfad).
- Performance-Baseline ist eine lokale Einzelmessung (dokumentiert, nicht
  als CI-Benchmark verdrahtet); die Sanity-Bounds in CI sind bewusst
  grosszügig.
- Hop-Kompatibilität wird gegen die gebundene Version (2.18.1) getestet;
  weitere Releases werden ergänzt, sobald verfügbar (Matrix vorbereitet).

## Roadmap-Abschluss

Mit Phase 8 ist die Roadmap (05-roadmap-phases.md) umgesetzt – mit einer
bewussten Ausnahme: Phase 7 (ITF/INTERLIS 1) ist als nicht unterstützt
dokumentiert (`phase-07.md`). Der Releaseprozess (Tag → Build → GitHub
Release mit Plugin-ZIP) ist automatisiert; der nächste Schritt ist ein
Release-Kandidat nach Bedarf.
