# hop-interlis-plugin – Lokale Entwicklung, Packaging, Deployment und Release

## 1. Ziel

Die lokale Entwicklung muss so einfach sein, dass ein Entwickler nach einer Codeänderung mit **einem Befehl**:

1. abhängige lokale Plugins baut,
2. Tests ausführt,
3. die Plugin-Distribution erzeugt,
4. die Installation in `$HOP_HOME` aktualisiert,
5. Hop GUI neu startet,
6. sofort manuell testen kann.

Das bestehende `hop-geotools-plugin` verwendet bereits genau dieses Muster. `hop-interlis-plugin` soll denselben Arbeitsstil übernehmen und möglichst die gleichen Konventionen verwenden.

---

# 2. Empfohlenes lokales Verzeichnislayout

```text
sources/
├── hop-geometry-type-plugin/
├── hop-geotools-plugin/
└── hop-interlis-plugin/
```

Warum diese Reihenfolge?

`hop-interlis-plugin` verwendet den gemeinsamen Geometry Value Type:

```text
hop-geometry-type-plugin
         ^
         |
         +-----------------------+
         |                       |
hop-geotools-plugin      hop-interlis-plugin
```

GeoTools und INTERLIS teilen sich damit dieselbe Hop-Geometrieklasse.

---

# 3. Toolchain

Baseline der Spezifikation:

```text
Java       21+
Maven      aktuelle 3.x-Version
Apache Hop 2.18.1 Baseline (zentral im Parent-POM gepinnt, Property hop.version)
iox-ili    aktuelle kompatible 1.24.x-Linie
ili2c      dazu kompatible Version
Python     3.x für Distribution Checks
Bash       für macOS/Linux Dev Scripts
PowerShell für Windows Dev Script
```

Versionsnummern werden im Parent-POM zentral geführt.

Beispiel:

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
  <hop.version>2.18.1</hop.version>
  <iox.ili.version>...</iox.ili.version>
  <ili2c.version>...</ili2c.version>
  <hop.geometry.type.version>0.1.0-SNAPSHOT</hop.geometry.type.version>
</properties>
```

Vor der tatsächlichen Implementierung sind die konkreten iox-/ili2c-Artefaktkoordinaten anhand der verwendeten Distribution festzuzurren.

---

# 4. Maven-Modulstruktur

```text
hop-interlis-plugin/
├── pom.xml
│
├── hop-interlis-core/
│   ├── pom.xml
│   └── src/...
│
├── hop-interlis-transforms/
│   ├── pom.xml
│   └── src/...
│
├── hop-interlis-ui/
│   ├── pom.xml
│   └── src/...
│
├── hop-interlis-it/
│   ├── pom.xml
│   └── src/...
│
└── assemblies/
    └── assemblies-hop-interlis/
        ├── pom.xml
        └── src/assembly/assembly.xml
```

### Alternative

Wenn die SWT-Dialoge klein genug bleiben, kann `hop-interlis-ui` zunächst mit `hop-interlis-transforms` zusammengelegt werden.

Die Architektur soll UI-Services trotzdem vom SWT-Code trennen.

---

# 5. Dependency-Regeln

## 5.1 Von Hop bereitgestellt

Diese Dependencies dürfen nicht in der Plugin-ZIP landen:

```text
hop-core
hop-engine
hop-ui
```

Scope:

```xml
<scope>provided</scope>
```

---

## 5.2 Vom Geometry Plugin bereitgestellt

Ebenfalls nicht paketieren:

```text
hop-geometry-type
jts-core
```

Grund:

`hop-geotools-plugin` und `hop-interlis-plugin` müssen dieselbe `org.locationtech.jts.geom.Geometry`-Klasse sehen.

Doppelte JTS-JARs sind zu vermeiden.

---

## 5.3 Im INTERLIS-Plugin paketieren

Typischerweise Runtime-Abhängigkeiten wie:

```text
iox-ili
ili2c
ili2c-core-Abhängigkeiten
ilirepository / erforderliche Claeis-Komponenten
weitere nicht von Hop bereitgestellte Runtime-Libraries
```

Welche transitive Library tatsächlich in die ZIP gehört, wird über `mvn dependency:tree` und den Distributionstest geprüft.

---

# 6. Classloader Group

Für alle Transform-Plugins mit Geometry:

```java
@Transform(
    ...,
    classLoaderGroup = "sogeo-geometry")
```

Der Name muss exakt mit dem Geometry Type Plugin und dem GeoTools Plugin übereinstimmen.

Für einen `ValueMetaInterlisObject` muss entschieden werden, ob er ebenfalls in derselben Group registriert wird. Empfehlung:

> Ja, wenn seine Klassen in denselben INTERLIS-Transforms verwendet werden und dadurch keine unnötige zweite Classloader-Insel entsteht.

Wichtig ist eine einzige konsistente Runtime-Welt für:

```text
hop-interlis classes
IomObject
Hop Geometry
JTS Geometry
```

innerhalb der INTERLIS-Transforms.

---

# 7. Plugin-Installationslayout

Empfehlung für die ZIP:

```text
hop-interlis-plugin-0.1.0-SNAPSHOT.zip
└── plugins/
    └── transforms/
        └── interlis/
            ├── hop-interlis-core-0.1.0-SNAPSHOT.jar
            ├── hop-interlis-transforms-0.1.0-SNAPSHOT.jar
            ├── hop-interlis-ui-0.1.0-SNAPSHOT.jar
            ├── iox-ili-*.jar
            ├── ili2c-*.jar
            └── weitere Runtime-JARs
```

Wenn Hop für den zusätzlichen ValueMeta-Plugin-Typ ein separates `misc`-Verzeichnis verlangen sollte, kann die Assembly entsprechend zwei Zielpfade enthalten. Bevorzugt wird aber eine möglichst einfache Distribution, sofern Hop Plugin Discovery dies sauber unterstützt.

---

# 8. Assembly Descriptor

Konzeptionell:

```xml
<assembly>
  <id>plugin</id>
  <formats>
    <format>zip</format>
  </formats>
  <includeBaseDirectory>false</includeBaseDirectory>

  <dependencySets>
    <dependencySet>
      <outputDirectory>plugins/transforms/interlis</outputDirectory>
      <useProjectArtifact>true</useProjectArtifact>
      <unpack>false</unpack>
      <scope>runtime</scope>
      <excludes>
        <exclude>org.apache.hop:*</exclude>
        <exclude>ch.so.agi:hop-geometry-type</exclude>
        <exclude>org.locationtech.jts:jts-core</exclude>
      </excludes>
    </dependencySet>
  </dependencySets>
</assembly>
```

Die exakten Group IDs müssen den realen Maven-Artefakten entsprechen.

---

# 9. `scripts/check-distribution.py`

Dieses Script ist Release-relevant.

Es erhält die erzeugte ZIP oder findet sie im Assembly-Target.

Prüfungen:

```text
[OK] plugin directory exists
[OK] hop-interlis-core jar exists
[OK] hop-interlis-transforms jar exists
[OK] iox-ili runtime exists
[OK] ili2c runtime exists
[OK] plugin icons/resources exist

[FAIL if present] hop-core
[FAIL if present] hop-engine
[FAIL if present] hop-ui
[FAIL if present] jts-core
[FAIL if present] hop-geometry-type
```

Zusätzlich:

- doppelte JAR-Dateinamen erkennen,
- `*-tests.jar` verbieten,
- `*-sources.jar` verbieten,
- `.DS_Store` verbieten,
- Pfadstruktur prüfen.

---

# 10. Ein-Befehl-Workflow macOS/Linux

Öffentlicher Einstiegspunkt:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

Wrapper:

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$SCRIPT_DIR/dev-install-and-run.sh" "$@"
```

Dies entspricht dem Muster des bestehenden GeoTools-Plugins.

---

# 11. Vorgeschlagenes `dev-install-and-run.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <HOP_HOME> [HOP_GEOMETRY_TYPE_REPO]"
  echo "Example: $0 ~/Applications/hop"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HOP_HOME="$(cd "$1" && pwd)"
GEOMETRY_REPO="${2:-${HOP_GEOMETRY_TYPE_REPO:-$PROJECT_DIR/../hop-geometry-type-plugin}}"

if [[ ! -f "$HOP_HOME/hop-gui.sh" ]]; then
  echo "Not an Apache Hop home (hop-gui.sh missing): $HOP_HOME" >&2
  exit 1
fi

if [[ ! -f "$GEOMETRY_REPO/pom.xml" ]]; then
  echo "Geometry type repository not found: $GEOMETRY_REPO" >&2
  echo "Clone hop-geometry-type-plugin next to this repository or pass argument 2." >&2
  exit 1
fi

GEOMETRY_REPO="$(cd "$GEOMETRY_REPO" && pwd)"
GEOMETRY_PLUGIN_DIR="$HOP_HOME/plugins/misc/hop-geometry-type"
INTERLIS_PLUGIN_DIR="$HOP_HOME/plugins/transforms/interlis"
LOG_FILE="${TMPDIR:-/tmp}/hop-interlis-dev-hop.log"

echo "==> Building and testing hop-geometry-type-plugin"
mvn -f "$GEOMETRY_REPO/pom.xml" -U -B -ntp clean install

GEOMETRY_ZIP="$(find \
  "$GEOMETRY_REPO/assemblies/assemblies-hop-geometry-type/target" \
  -maxdepth 1 \
  -name 'hop-geometry-type-plugin-*.zip' \
  -print | head -n 1)"

if [[ -z "$GEOMETRY_ZIP" || ! -f "$GEOMETRY_ZIP" ]]; then
  echo "Geometry type plugin ZIP was not created" >&2
  exit 1
fi

echo "==> Installing Geometry type plugin"
rm -rf "$GEOMETRY_PLUGIN_DIR"
unzip -q -o "$GEOMETRY_ZIP" -d "$HOP_HOME"

echo "==> Building and testing hop-interlis-plugin"
(
  cd "$PROJECT_DIR"
  mvn -U -B -ntp clean verify
  python3 scripts/check-distribution.py
)

INTERLIS_ZIP="$(find \
  "$PROJECT_DIR/assemblies/assemblies-hop-interlis/target" \
  -maxdepth 1 \
  -name 'hop-interlis-plugin-*.zip' \
  -print | head -n 1)"

if [[ -z "$INTERLIS_ZIP" || ! -f "$INTERLIS_ZIP" ]]; then
  echo "INTERLIS plugin ZIP was not created" >&2
  exit 1
fi

echo "==> Installing INTERLIS plugin"
rm -rf "$INTERLIS_PLUGIN_DIR"
unzip -q -o "$INTERLIS_ZIP" -d "$HOP_HOME"

echo "==> Restarting Hop GUI"
if pgrep -f 'org\\.apache\\.hop\\.ui\\.hopgui\\.HopGui' >/dev/null 2>&1; then
  pkill -f 'org\\.apache\\.hop\\.ui\\.hopgui\\.HopGui' || true
  for _ in {1..20}; do
    if ! pgrep -f 'org\\.apache\\.hop\\.ui\\.hopgui\\.HopGui' >/dev/null 2>&1; then
      break
    fi
    sleep 0.25
  done
fi

(
  cd "$HOP_HOME"
  nohup bash ./hop-gui.sh >"$LOG_FILE" 2>&1 &
)

echo "Installed: $GEOMETRY_PLUGIN_DIR"
echo "Installed: $INTERLIS_PLUGIN_DIR"
echo "Hop GUI restarted. Startup log: $LOG_FILE"
```

Dieses Script ist absichtlich dem vorhandenen GeoTools-Dev-Script ähnlich.

---

# 12. Optional: GeoTools Plugin ebenfalls lokal synchronisieren

Für den häufigen Demo-Pfad:

```text
INTERLIS Input -> Vector Writer
```

kann ein zusätzliches Script angeboten werden:

```text
scripts/dev-sync-all-geo-plugins.sh
```

Layout:

```text
1 geometry type
2 geotools
3 interlis
4 restart Hop once
```

Wichtig:

Nicht drei Scripts nacheinander Hop neu starten lassen.

---

# 13. Fast Development Mode

Vollständiges `clean verify` ist die sichere Default-Variante.

Für schnelle Core-Iteration zusätzlich:

```bash
mvn -pl hop-interlis-core -am test
```

Mapper-spezifisch:

```bash
mvn -pl hop-interlis-core \
  -Dtest=IomToRowMapperTest,RowToIomMapperTest test
```

Integration:

```bash
mvn -pl hop-interlis-it -am verify
```

Vor manueller Installation muss aber mindestens die für den geänderten Pfad relevante Testmenge ausgeführt werden.

---

# 14. Dev Script ohne Hop-Restart

Zusätzlich nützlich:

```text
scripts/dev-install.sh
```

macht:

```text
build
verify
install ZIP into HOP_HOME
```

aber startet GUI nicht.

Verwendung etwa für:

- CI-Debugging,
- manuell bereits beendete Hop-Instanz,
- Remote-Umgebungen.

---

# 15. E2E Script

```text
scripts/run-e2e.sh
```

Vorgeschlagener Aufbau:

```bash
#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <HOP_HOME>"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HOP_HOME="$(cd "$1" && pwd)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hop-interlis-e2e.XXXXXX")"

cleanup() {
  if [[ "${KEEP_E2E_WORKDIR:-false}" != "true" ]]; then
    rm -rf "$WORK_DIR"
  else
    echo "Keeping E2E work dir: $WORK_DIR"
  fi
}
trap cleanup EXIT

export HOP_INTERLIS_E2E_DIR="$WORK_DIR"

run_pipeline() {
  local pipeline="$1"
  echo "==> E2E: $(basename "$pipeline")"
  "$HOP_HOME/hop-run.sh" \
    -r local \
    -f "$pipeline"
}

run_pipeline "$PROJECT_DIR/e2e/pipelines/01-xtf-to-csv.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/04-xtf-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/10-curves-roundtrip.hpl"

mvn -pl hop-interlis-it \
  -De2e.workDir="$WORK_DIR" \
  -Dtest=XtfE2eOutputAssertionsTest test
```

Die tatsächlichen Pipeline-Dateien und Assertion-Helfer werden im Projekt gepflegt.

---

# 16. Windows PowerShell

Für Windows soll ein äquivalenter Einstieg existieren:

```text
scripts/dev-sync-hop-plugin.ps1
```

Aufruf:

```powershell
./scripts/dev-sync-hop-plugin.ps1 -HopHome C:\tools\hop
```

Verhalten identisch:

```text
Build Geometry
Build INTERLIS
Check ZIP
Replace plugin directory
Restart Hop GUI
```

Keine Bash-Abhängigkeit als Voraussetzung für Windows-Entwickler.

---

# 17. Hop GUI Restart – Sicherheitsregeln

Das Dev Script darf nur Entwicklungsprozesse beenden, die eindeutig Hop GUI entsprechen.

Nicht:

```bash
pkill java
```

sondern gezielt:

```text
org.apache.hop.ui.hopgui.HopGui
```

Log:

```text
${TMPDIR:-/tmp}/hop-interlis-dev-hop.log
```

Nach Start soll das Script den Log-Pfad ausgeben.

---

# 18. Fehlerfreundlicher Dev-Workflow

Wenn ein Build fehlschlägt:

- altes installiertes Plugin **nicht löschen**, bevor die neue ZIP erfolgreich gebaut wurde.

Reihenfolge:

```text
BUILD NEW ZIP
     |
     | success
     v
REMOVE OLD INSTALLATION
     |
     v
INSTALL NEW ZIP
```

Damit bleibt die lokale Hop-Installation bei Kompilierungsfehlern funktionsfähig.

---

# 19. Plugin-Version in Hop sichtbar machen

Es soll eine zentrale Klasse geben:

```java
public final class InterlisPluginVersion {
  public static String version();
  public static String gitCommit();
  public static String buildTime();
}
```

Optional Build-Metadaten über generierte Properties:

```text
hop-interlis-build.properties
```

Nützlich für Logs:

```text
INTERLIS plugin 0.3.0-SNAPSHOT (commit abc1234)
iox-ili 1.x
ili2c 5.x
Hop 2.18.1
```

Keine geheimen oder maschinenspezifischen Build-Pfade einbetten.

---

# 20. Runtime Diagnostics

Beim ersten Initialisieren eines INTERLIS-Transforms auf Basic/Debug-Level sinnvoll loggen:

```text
INTERLIS plugin version
iox-ili version
ili2c version
selected model(s)
model repositories/directories
selected class
selected mapping mode
input/output file
```

Nicht bei jedem Row dieselben Metadaten loggen.

---

# 21. Model Repository Offline Development

Lokale Testmodelle müssen standardmässig ohne Internet funktionieren.

Empfohlen:

```text
src/test/resources/models
```

und E2E:

```text
e2e/models
```

Das verhindert, dass lokale Entwicklung an einem temporär unerreichbaren Modellserver scheitert.

---

# 22. Lokales Testprojekt für Hop GUI

Repository enthält:

```text
dev-hop-project/
├── hop-config.json / environment files soweit nötig
├── pipelines/
│   ├── input-preview.hpl
│   ├── xtf-to-gpkg.hpl
│   ├── roundtrip.hpl
│   ├── structures.hpl
│   └── associations.hpl
├── data/
└── README.md
```

Das Dev-Sync Script kann optional ausgeben:

```text
Open example pipeline:
  <repo>/dev-hop-project/pipelines/input-preview.hpl
```

Nicht zwingend automatisch öffnen; der GUI-Neustart reicht.

---

# 23. Maven Profiles

Mögliche Profile:

```text
-Pintegration
-Pe2e
-Pcoverage
-Pitf
```

Default `verify` soll alle stabilen obligatorischen Tests enthalten.

Netzwerk- oder sehr grosse Performance-Tests bleiben ausserhalb des normalen Builds.

---

# 24. `mvn dependency:tree` als Architekturprüfung

Regelmässig prüfen:

```bash
mvn dependency:tree
```

Besonders nach Updates von:

- iox-ili,
- ili2c,
- Hop,
- Geometry plugin.

Zu kontrollieren:

- alte `com.vividsolutions:jts`-Abhängigkeiten innerhalb iox dürfen nicht in normalen Hop-Geometry-Objektpfad leaken,
- `org.locationtech.jts` genau einmal in gemeinsamer Runtime,
- keine Hop-Version aus Transitiven überschreibt Parent-Version.

---

# 25. Umgang mit altem JTS in iox-ili

iox-ili enthält historisch Klassen, die `com.vividsolutions.jts` benutzen, etwa ältere `Iox2jts`-Utilities.

Architekturvorgabe:

> Diese Klassen dürfen nicht als primäre Hop-Geometry-Bridge verwendet werden.

Stattdessen:

```text
Iox2wkb / Wkb2iox
```

mit SQL/MM-WKB.

Wenn iox-ili intern eine alte JTS-Runtime benötigt, darf diese als isolierte transitive Library im INTERLIS-Plugin vorhanden sein, **aber sie darf niemals mit dem Hop-Geometry-Value-Type verwechselt werden**.

Distribution Checks sollen deshalb zwischen:

```text
com.vividsolutions legacy dependency
```

und:

```text
org.locationtech.jts shared Hop geometry
```

unterscheiden.

---

# 26. Release-Artifact

Ein Release enthält genau ein primäres installierbares Asset:

```text
hop-interlis-plugin-<version>.zip
```

Optional zusätzlich:

```text
SHA256SUMS
```

Nicht notwendig als Release Assets:

- einzelne Modul-JARs,
- Sources-ZIPs,
- unstrukturierte Dependency-JAR-Sammlungen.

Maven-Artefakte können zusätzlich separat publiziert werden, falls andere Java-Projekte sie benötigen.

---

# 27. GitHub Actions – CI

Beispielstruktur:

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  verify:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - checkout
      - setup-java 21
      - build geometry dependency or restore artifact
      - mvn -B -ntp clean verify
      - python scripts/check-distribution.py
```

Für Windows kann `py`/`python` je nach Runner beachtet werden; bevorzugt standardisierte Setup-Actions verwenden.

---

# 28. CI – E2E mit realem Hop

Separater Job:

```text
hop-e2e
```

Schritte:

```text
checkout
setup Java
build geometry plugin
build interlis plugin
download supported Hop distribution
unpack
install geometry ZIP
install interlis ZIP
run e2e pipelines via hop-run
assert outputs
```

Diese Stage ist Release-blockierend.

---

# 29. Release Workflow

Trigger z.B.:

```text
push tag vX.Y.Z
```

oder bestehende Repository-Konvention.

Ablauf:

```text
verify all platforms
       |
       v
build ZIP once from clean checkout
       |
       v
check distribution
       |
       v
fresh-Hop E2E
       |
       v
create GitHub Release
       |
       v
upload hop-interlis-plugin-X.Y.Z.zip
```

Snapshots auf `main` können analog zum bestehenden Plugin als automatisierte Releases publiziert werden, wenn dies zur bestehenden Distributionstrategie passt.

---

# 30. Reproducible-ish Builds

Soweit praktikabel:

- stabile Dependency-Versionen,
- keine `LATEST`-Dependencies,
- Maven Wrapper optional,
- kein Download von Testdaten während Package-Schritt,
- Build-Metadaten kontrolliert.

Ein Release muss aus einem sauberen Checkout reproduzierbar gebaut werden können.

---

# 31. Installation durch Benutzer

Dokumentierter manueller Installationsweg:

```text
1. Install hop-geometry-type-plugin
2. Unzip hop-interlis-plugin-<version>.zip into HOP_HOME
3. Restart Hop GUI
```

Nach dem Entpacken:

```text
$HOP_HOME/plugins/misc/hop-geometry-type/
$HOP_HOME/plugins/transforms/interlis/
```

In Hop Palette:

```text
INTERLIS
  INTERLIS Input
  INTERLIS Output
  ...
```

---

# 32. Plugin-Abhängigkeit benutzerfreundlich prüfen

Wenn Geometry Type Plugin fehlt, soll der Fehler möglichst früh und verständlich sein.

Beispiel:

```text
INTERLIS plugin requires hop-geometry-type-plugin.
Install the matching Geometry Type Plugin and restart Apache Hop.
```

Nicht nur:

```text
NoClassDefFoundError: com/atolcd/hop/core/row/value/ValueMetaGeometry
```

Dafür kann ein Runtime Check existieren:

```java
final class InterlisRuntimeSupport {
  static void initialize();
  static void verifyGeometryPluginAvailable();
  static RuntimeVersions versions();
}
```

---

# 33. Versionskompatibilität

Dokumentation führt eine kleine Matrix:

```text
hop-interlis-plugin | Apache Hop | geometry plugin
--------------------+------------+----------------
0.1.x               | 2.18.x     | 0.1.x
...
```

Nicht unnötig harte Version Checks im Code einbauen, solange ABI/API tatsächlich kompatibel ist.

---

# 34. Upgrade-Tests

Wenn Meta-Konfigurationen später erweitert werden, alte `.hpl`-Dateien weiterhin lesen.

Test-Fixtures:

```text
src/test/resources/pipelines/metadata-v0.1/*.hpl
src/test/resources/pipelines/metadata-v0.2/*.hpl
```

Test:

```java
old_pipeline_metadata_loads_with_new_plugin()
```

Neue Optionen brauchen sinnvolle Defaults.

---

# 35. Snapshot-Entwicklung

Snapshot-Version:

```text
0.1.0-SNAPSHOT
```

Lokaler Build des Geometry Plugins zuerst:

```bash
mvn -f ../hop-geometry-type-plugin/pom.xml -U clean install
```

Dann:

```bash
mvn -U clean verify
```

Der Dev-Sync Wrapper automatisiert beides.

---

# 36. Entwicklung mit IDE

Empfohlener Ablauf:

```text
Import Maven project
        |
        v
run unit tests in IDE
        |
        v
for real Hop UI:
  scripts/dev-sync-hop-plugin.sh HOP_HOME
```

Hop GUI selbst muss nicht als IDE-Run-Konfiguration nachgebaut werden, solange der Ein-Befehl-Workflow schneller und robuster ist.

Optional kann später eine IDE-Run-Konfiguration dokumentiert werden.

---

# 37. Debugging in Hop GUI

Für Java-Debugging kann ein optionales Script gestartet werden mit JDWP:

```text
scripts/dev-run-hop-debug.sh
```

Konzeptionell:

```bash
export HOP_OPTIONS="${HOP_OPTIONS:-} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
exec "$HOP_HOME/hop-gui.sh"
```

Die tatsächlich von Hop verwendete JVM-Options-Variable ist vor Implementierung zu verifizieren.

Dokumentation soll keine ungetestete Variable als garantiert darstellen.

---

# 38. Logging während Entwicklung

Das Plugin verwendet Hop Logging APIs, nicht `System.out.println`.

Beispiele:

```java
if (isBasic()) {
  logBasic("Reading INTERLIS class " + className);
}

if (isDetailed()) {
  logDetailed("Compiled INTERLIS model " + modelName);
}

if (isDebug()) {
  logDebug("Generated row schema: " + schemaDescription);
}
```

Keine Row-by-Row-Debuglogs im Normalbetrieb.

---

# 39. Lokale Beispieldaten

Repository:

```text
examples/data/
```

nur kleine Daten einchecken.

Grosse reale Datensätze:

- Download-Script optional,
- nicht Bestandteil normaler Unit Tests,
- Checksumme dokumentieren.

---

# 40. `scripts/check-env.sh`

Optional hilfreich:

```text
Java version
Maven version
HOP_HOME
Geometry repo
Hop version
```

Ausgabe etwa:

```text
[OK] Java 21
[OK] Maven
[OK] Hop home: /Applications/hop
[OK] hop-gui.sh
[OK] geometry repo
```

Das reduziert Debugging bei neuen Entwicklerumgebungen.

---

# 41. Clean Uninstall

Dev Script:

```text
scripts/dev-uninstall.sh
```

entfernt nur:

```text
$HOP_HOME/plugins/transforms/interlis
```

und auf Wunsch nicht automatisch das Geometry Plugin, weil dieses auch vom GeoTools Plugin benötigt werden kann.

---

# 42. Distribution Checker gegen Classloader-Probleme

Zusätzliche ZIP-Prüfung:

JAR-Inhalte können auf doppelte Klassen geprüft werden.

Besonders:

```text
org/locationtech/jts/
com/atolcd/hop/core/row/value/ValueMetaGeometry.class
```

Diese dürfen nicht in INTERLIS-eigenen Runtime-JARs hineingeshadet sein.

---

# 43. Keine Shading-Lösung als Default

Nicht einfach alles in ein riesiges Fat JAR shaden.

Gründe:

- Plugin-Classloader wird undurchsichtiger.
- doppelte Services/META-INF können problematisch sein.
- Geometry/JTS-Sharing wird erschwert.
- Diagnostik schlechter.

Bevorzugt:

```text
normal module JARs + runtime dependency JARs
```

in einem klaren Plugin-Verzeichnis.

---

# 44. Security / Netzwerkzugriff

Model repositories bedeuten potentiell HTTP/HTTPS-Zugriff.

Konfiguration:

- Timeout,
- Proxy-Unterstützung über etablierte ili2c/ilirepository-Mechanismen,
- keine TLS-Validierung abschalten,
- keine Credentials loggen.

E2E-Tests für Netzwerkzugriff separat von Offline-Core-Tests.

---

# 45. Release Notes

Jeder Release beschreibt mindestens:

```text
Supported Hop version
Required Geometry Type Plugin version
Supported transfer formats
Major new transforms
Known limitations
Upgrade notes
```

Beispiel frühes Release:

```text
Supports XTF Input/Output.
LIST/BAG structures require Structure Explode/Collect.
ITF is not yet supported.
```

---

# 46. Entwickler-README Quick Start

Repository README sollte oben einen sehr kurzen Pfad haben:

```bash
git clone .../hop-geometry-type-plugin

git clone .../hop-interlis-plugin

cd hop-interlis-plugin
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

Danach:

```text
Open Hop GUI -> INTERLIS -> INTERLIS Input
```

Die ausführlichen Details verweisen auf dieses Dokument.

---

# 47. Troubleshooting

## `NoClassDefFoundError: ValueMetaGeometry`

Prüfen:

```text
$HOP_HOME/plugins/misc/hop-geometry-type
```

Das `hop-geometry-type-plugin` wird weiterhin separat installiert und nicht in das
INTERLIS-Plugin-ZIP kopiert. Die INTERLIS-Schemafabrik initialisiert beim Erzeugen von
Hop-Row-Metadaten die gemeinsame `sogeo-geometry`-Classloader-Gruppe. Damit funktionieren auch
Design-Time-Schema-Previews mit `GeometryCHLV95_V1.MultiSurface` und anderen Geometriefeldern.

Wenn das Plugin fehlt, inkompatibel ist oder die Classloader-Gruppe trotz Installation nicht
geladen werden kann, bleibt der Dialog geöffnet und zeigt den Fehler im Schema-Preview an.
Nach Installation oder Aktualisierung beider Plugins muss Hop vollständig neu gestartet werden.

## `ClassCastException` zwischen zwei Geometry-Klassen

Verdacht:

- doppeltes `jts-core`,
- falsche `classLoaderGroup`,
- Geometry Plugin in INTERLIS-ZIP mitverpackt.

Ausführen:

```bash
python3 scripts/check-distribution.py
```

## Modell wird im Dialog nicht gefunden

Prüfen:

- Model name,
- model dirs,
- repository URLs,
- Variablenauflösung,
- Debug Log.

## Dialog öffnet wegen Modellproblem nicht

Das darf nicht passieren.

Design-Time Model Probe muss Fehler im Preview-Bereich anzeigen und den Dialog bedienbar lassen.

## XTF wird geschrieben, aber Validator meldet Fehler

Output Mapping überprüfen:

- mandatory attributes,
- OID,
- BID/topic,
- references,
- structure/list cardinality.

---

# 48. Vorgeschlagene Script-Sammlung

```text
scripts/
├── check-env.sh
├── check-distribution.py
├── dev-sync-hop-plugin.sh
├── dev-install-and-run.sh
├── dev-install.sh
├── dev-uninstall.sh
├── dev-sync-all-geo-plugins.sh
├── dev-sync-hop-plugin.ps1
├── run-e2e.sh
└── run-compatibility-tests.sh
```

Nicht alle Scripts müssen in Phase 0 existieren. Verbindlich für Phase 0:

```text
dev-sync-hop-plugin.sh
check-distribution.py
```

und spätestens mit dem ersten E2E:

```text
run-e2e.sh
```

---

# 49. Minimaler täglicher Entwicklerworkflow

```text
Code ändern
   |
   v
IDE/JUnit Test
   |
   v
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
   |
   v
Hop GUI öffnet
   |
   v
Example Pipeline ausführen
```

Für rein technische Core-Änderungen:

```text
Code ändern
   |
   v
mvn -pl hop-interlis-core -am test
```

Vor Push:

```bash
mvn clean verify
python3 scripts/check-distribution.py
```

---

# 50. Release-Gate

Kein Release ohne:

```text
clean checkout
   |
   v
mvn clean verify
   |
   v
create plugin ZIP
   |
   v
check-distribution.py
   |
   v
fresh Hop install
   |
   v
install geometry + interlis plugin
   |
   v
hop-run E2E
   |
   v
release
```

Damit wird der lokale „bei mir funktioniert es“-Effekt möglichst weit reduziert.

---

# 51. Quellen / bestehende Konventionen

Bestehendes Geometry Plugin:

https://github.com/edigonzales/hop-geometry-type-plugin

Bestehendes GeoTools Plugin:

https://github.com/edigonzales/hop-geotools-plugin

Das GeoTools-Projekt besitzt bereits einen `dev-sync-hop-plugin.sh`-/`dev-install-and-run.sh`-Workflow, der Geometry Plugin und GeoTools Plugin baut, installiert und Hop GUI neu startet. Dieses Muster soll für `hop-interlis-plugin` konsistent weitergeführt werden.

Apache Hop Developer Documentation:

https://hop.apache.org/dev-manual/latest/

Apache Hop Runner:

https://hop.apache.org/manual/latest/hop-run/hop-run.html
