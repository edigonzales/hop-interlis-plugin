# Release-Prozess

## Versionsschema

Die Projektversion wird ausschliesslich in `pom.xml` gepflegt (Single-Source).
Tag-Konvention: `v<version>`, z. B. `v1.0.0`.

## Schritte für ein Release

1. **Version setzen** in `pom.xml` (Parent-Version; Module erben sie):

   ```bash
   # z.B. mit dem Maven Versions-Plugin
   ./mvnw versions:set -DnewVersion=1.0.0
   ```

2. **Vollständige Verifikation:**

   ```bash
   ./mvnw -B -ntp clean verify
   python scripts/check-distribution.py
   ```

3. **E2E-Suite** (paketiertes Plugin, isoliertes Hop, `hop-run`):

   ```bash
   bash scripts/run-e2e.sh "$HOP_HOME"
   ```

4. **Committen und taggen:**

   ```bash
   git commit -am "chore: prepare release 1.0.0"
   git tag v1.0.0
   git push origin main --tags
   ```

5. **GitHub Release:** der Workflow `.github/workflows/release.yml` baut auf
   den Tag automatisch die Distribution, prüft sie mit
   `check-distribution.py` und hängt das Plugin-ZIP
   (`hop-interlis-plugin-<version>.zip`) an das Release.

6. **Installation** beim Endanwender: ZIP nach `hop/plugins/hop-interlis-plugin`
   entpacken (Hop-GUI/`hop-run` neu starten); das Plugin benötigt
   `hop-geometry-type-plugin` in derselben Installation.

## Kompatibilität

- Minimale unterstützte Hop-Version: 2.18.1 (Property `hop.version` im
  Parent-POM); Java ≥ 21 (Compiler-Release 21).
- CI-Matrix: OS (Linux/macOS/Windows) × JDK (21, 25) – siehe
  `.github/workflows/ci.yml`. Neue Hop-Releases werden durch Erweiterung der
  Matrix getestet.

## Rollback

Ein Release ist ein Tag auf `main`; ein fehlerhaftes Release wird durch ein
neues Patch-Release ersetzt (keine History-Rewrites).
