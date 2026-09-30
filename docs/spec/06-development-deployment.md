# Lokale Entwicklung, Packaging, Deployment und Release

Diese Spezifikation beschreibt die vorhandenen Module, Skripte und Workflows.
Historische Phasenabnahmen stehen unter `docs/progress/`; der aktuelle
[Dokumentationsbericht](../progress/documentation-current.md) ergänzt sie.

## 1. Toolchain und Pins

| Komponente | Aktueller Stand |
|---|---|
| Java | Compiler-Release 21; lokale Abnahme mit JDK 21, CI zusätzlich JDK 25 |
| Apache Hop | 2.19.0, Parent-Property `hop.version` |
| Maven | Wrapper `./mvnw` bevorzugen |
| ili2c / iox-ili | 5.6.8 / 1.24.4 |
| iox-api / ehibasics | 1.0.3 / 1.4.1 |
| Geometry Type | 0.2.0-SNAPSHOT, `provided` |
| H2 | 2.4.240, `provided` aus Hop JDBC |
| Vector/Raster | 0.1.0-SNAPSHOT für verpflichtende GeoPackage-E2E |

Die INTERLIS-Bibliotheken bilden eine gepinnte Kombination. Bibliotheks- und
CI-Revisionen werden nicht beiläufig aktualisiert. Snapshot-Koordinaten können
später andere Artefakte liefern; eine reproduzierbare Abnahme hält die verwendeten
ZIPs und Prüfsummen fest.

Auf dem SDKMAN-Entwicklungsrechner:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env
java -version
```

Die `.sdkmanrc` wählt einen JDK-21-Kandidaten. Fehlt dieser, werden ausschliesslich
`$HOME/.sdkman/candidates/java` und die Maven-Anforderungen herangezogen.
`scripts/lib-java.sh` verwendet einen geeigneten `HOP_JAVA_HOME`/`JAVA_HOME`, sonst
SDKMAN-Kandidaten ab Java 21, bevorzugt Temurin. Ungeeignete JDKs und der
`current`-Symlink werden ignoriert; fehlende Kandidaten führen zu einer Diagnose.

## 2. Module und gemeinsame Geometrie

```text
sources/
├── hop-geometry-type-plugin/
├── hop-vector-raster-plugin/        # optionaler lokaler E2E-Build
└── hop-interlis-plugin/
    ├── pom.xml
    ├── hop-interlis-core/
    ├── hop-interlis-transforms/     # Runtime, ValueMeta und SWT-Dialoge
    └── assemblies/assemblies-hop-interlis/
```

Ein eigenes UI-, ValueType- oder Integrationstest-Modul existiert nicht. Core und
Transform-Modul enthalten JUnit-/Pipeline-Tests; installierte Paket-E2E liegen
unter `e2e/` und `examples/`. Die zentrale Modell-/Mappinglogik bleibt vom SWT-Code
getrennt.

Alle betroffenen Plugins verwenden `classLoaderGroup="sogeo-geometry"`. Geometry
und `org.locationtech.jts` kommen aus dem gemeinsamen Geometry-Plugin. Es gibt
keine zweite Hop-Geometrieklasse, keinen zusätzlichen GeoTools-Konverter und kein
Shading als Ersatz für diese gemeinsame Classloader-Grenze.

iox-ili benötigt daneben das alte `com.vividsolutions.jts` (1.14). Dessen getrennten
Namespace enthält das INTERLIS-ZIP genau einmal; er ist kein Hop-Werttyp.

## 3. Dependency- und ZIP-Regeln

Hop core/engine/ui, Geometry, aktuelles JTS, H2 und json-simple werden als
`provided` verwendet und nicht ins INTERLIS-ZIP kopiert. Core bleibt in seinen
Codec-Schnittstellen Hop-neutral; Hop-Zeilenadapter liegen im Transform-Modul.

H2 wird von Hop 2.19.0 unter `lib/jdbc` bereitgestellt. Betroffene Transform-Plugins
binden Treiber über `isIncludeJdbcDrivers=true` ein. ImageN 0.9.2 aus OSGeo ist eine
reine Testabhängigkeit für den aktuellen Geometry-Snapshot, keine ZIP-Abhängigkeit.

Das erzeugte ZIP liegt unter
`assemblies/assemblies-hop-interlis/target/hop-interlis-plugin-<version>.zip`:

```text
plugins/transforms/interlis/
├── hop-interlis-transforms-<version>.jar
└── lib/
    ├── hop-interlis-core-<version>.jar
    ├── ili2c-core-5.6.8.jar
    ├── ili2c-tool-5.6.8.jar
    ├── iox-ili-1.24.4.jar
    ├── iox-api-1.0.3.jar
    ├── ehibasics-1.4.1.jar
    └── weitere benötigte Runtime-JARs
```

Nur das Transform-JAR liegt direkt an der Plugin-Wurzel und enthält den
Jandex-Index zur Plugin-Erkennung. Core und Runtime-Abhängigkeiten liegen in
`lib/`. `ValueMetaInterlisObject` ist im Transform-JAR registriert. Geometry wird
separat unter `plugins/misc/hop-geometry-type`, Vector/Raster unter
`plugins/transforms/vector-raster` installiert.

`python3 scripts/check-distribution.py` verlangt genau ein ZIP, prüft Layout,
Jandex, benötigte INTERLIS-JARs und Duplikate. Es verbietet zusätzliche Hop-,
Geometry-, aktuelle JTS-, H2-, ImageN-, json-simple- und Test-JARs sowie lose
Klassen, Sources und sonstige Fremddateien.

## 4. Lokaler Feedback-Workflow

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

Der Einstieg delegiert an `scripts/dev-install-and-run.sh`. Dieses Skript:

1. wählt einen geeigneten JDK;
2. baut und testet das benachbarte Geometry-Plugin (`clean install`);
3. installiert dessen ZIP;
4. baut und testet INTERLIS mit `clean verify` und prüft die Distribution;
5. installiert das INTERLIS-ZIP;
6. beendet eine laufende Hop-GUI und startet sie neu;
7. nennt den Startup-Logpfad (`${TMPDIR:-/tmp}/hop-interlis-dev-hop.log`).

`HOP_HOME` bleibt ein Argument. Der Geometry-Checkout kann als zweites Argument
oder über `HOP_GEOMETRY_TYPE_REPO` gewählt werden. Maven Wrapper werden verwendet,
wo sie vorhanden sind. Das Skript startet die GUI tatsächlich neu; Änderungen in
der GUI vor dem Aufruf speichern. Es gibt derzeit keinen eigenen
`--no-restart`-Schalter und kein PowerShell-Pendant.

Für schnelle Iteration:

```bash
./mvnw -B -ntp -pl hop-interlis-core -am test
./mvnw -B -ntp -pl hop-interlis-transforms -am \
  -Dtest=DocsExamplesTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Vor Abschluss eines Laufs:

```bash
./mvnw -B -ntp clean verify
python3 scripts/check-distribution.py
```

Tests werden nicht übersprungen. `scripts/check-env.sh` zeigt Java, Maven,
Hop-Verzeichnis und den Geometry-Checkout als Diagnose, ersetzt jedoch keine
vollständige Abnahme. Weitere manuelle Copy-/Installationsskripte sind kein
zweiter Standardweg.

## 5. Installierte Hop-E2E

Wichtige Pfade werden mit dem verpackten Plugin in einer disponiblen Hop-2.19.0-
Installation geprüft, einschliesslich der Handbuchpipelines direkt aus `examples/`.

```bash
export HOP_HOME=/absolute/path/to/disposable-hop
export HOP_GEOMETRY_TYPE_ZIP=/absolute/path/to/geometry.zip
export HOP_VECTOR_RASTER_ZIP=/absolute/path/to/vector-raster.zip
export REQUIRE_VECTOR_RASTER_E2E=true
bash scripts/run-e2e.sh "$HOP_HOME"
```

Der Runner installiert in das übergebene Hop-Verzeichnis. Das INTERLIS-ZIP kommt
aus dem Assembly-Target. Vorbereitete Geometry-/Vector/Raster-ZIPs verhindern den
lokalen Checkout-Fallback. `KEEP_E2E_WORKDIR=true` bewahrt Diagnoseausgaben.

Ein eigener temporärer Config-/Audit-Bereich hält Benutzerprojekte unberührt. Die
Run-Konfiguration `local` verwendet Safe Mode und Rowset-Grösse 2. Der Runner
kopiert minimale Fixtures, ruft `hop-run.sh` auf und prüft Werte mit
`scripts/check-e2e-output.py` sowie `scripts/check-doc-examples-output.py`.
Erwartete technische Fehlerfälle müssen fehlschlagen; das ist kein Überspringen
von Tests. Bei `REQUIRE_VECTOR_RASTER_E2E=true` sind beide GeoPackage-Pfade Pflicht.

Für einen CI-Publikationskandidaten wird das heruntergeladene kanonische ZIP im
Assembly-Target geprüft. Der Kandidat wird im E2E-Job nicht neu gebaut.

## 6. CI und Veröffentlichung

Vor Änderungen an Tests oder Workflows gilt der
[gemeinsame CI-Vertrag](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/ci-contract.md).
Die tatsächlich verwendeten Schnittstellen stehen in `.github/workflows/verify.yml`.
Workflow-Revision und Helper-`ci-ref` bleiben gemeinsam auf
`990af4813013fa8d21f6447e7545ed5499f2f202`.

Die Verify-Matrix prüft Java 21/25 auf Ubuntu, macOS und Windows. Ubuntu/JDK 21
führt `clean verify` und Distributionsprüfung aus und liefert das kanonische
Artefakt. Die anderen Zellen führen `test` aus. Auf headless Linux brauchen
SWT-Tests einen virtuellen Bildschirm (`xvfb-run -a`).

Gemeinsame Maven-Repositories werden mit dem gepinnten Helper vorbereitet:

```bash
CI_TEST_TMP="$(mktemp -d)"
export MAVEN_SETTINGS="$CI_TEST_TMP/maven-settings.xml"
python3 "$HOP_CI_DIR/scripts/write_maven_settings.py" --output "$MAVEN_SETTINGS"
./mvnw -s "$MAVEN_SETTINGS" -U -B -ntp \
  -Dhop.geometry.type.version=0.2.0-SNAPSHOT clean verify
python3 scripts/check-distribution.py
```

`HOP_CI_DIR` bezeichnet einen absoluten Checkout der oben genannten Helper-Revision.
Der separate Installed-E2E-Job lädt das kanonische INTERLIS-ZIP. Geometry
0.2.0-SNAPSHOT und Vector/Raster 0.1.0-SNAPSHOT werden mit
`download_maven_artifact.py` und denselben Settings aus dem Repository aufgelöst.
Es gibt im aktuellen Workflow keinen Geometry-Quellcode-Commit-Pin oder
GeoTools-Quellcode-Build.

Hop 2.19.0 wird zuerst von `downloads.apache.org`, bei Fehlern vom Apache-Archiv
bezogen. Timeouts und die im Workflow festgehaltene SHA-512-Prüfsumme sichern den
Download. Publikation benötigt die vollständige Verify-/Paket-E2E-Abnahme.

`publish-snapshot.yml` reagiert auf Main-Push und manuellen Aufruf;
`release.yml` auf `v*`-Tags. Beide verwenden denselben gepinnten Publish-Workflow
und das bereits geprüfte kanonische Artefakt. ZIP und Parent-POM werden im Maven-
Repository veröffentlicht. Zugangsdaten kommen ausschliesslich aus Actions-
Secrets, nie aus Quelltext, Handbuch oder Logausgaben.

## 7. Diagnosen und Betriebsgrenzen

- Fehlende Geometry-Klassen: Geometry-ZIP und Startup-Log prüfen.
- Zwei inkompatible Geometry-Klassen: zusätzliche JTS-/Geometry-JARs und
  Classloader-Gruppe prüfen; die Distributionsprüfung ist ein Pflichtschritt.
- Modellprobe scheitert: Variablen, lokale `.ili`-Dateien, Imports und Repository-
  Erreichbarkeit prüfen. Offline-Tests verwenden lokale Modelle. Die GUI bleibt
  bei Probe-Fehlern bedienbar; Runtime bleibt strikt.
- Ungültige Ausgabe: Writer-Validierung vor Veröffentlichung aktivieren oder
  veröffentlichten Transfer in einem getrennten Validate-Lauf prüfen.
- Hoher Speicher-/Diskbedarf: Budgets pro Transform, gemeinsame Spill-Grenze,
  grösstes Einzelobjekt und Wartezeit auf Basket-/Lookup-Ende berücksichtigen.
- Neue XTF-Datei noch nicht sichtbar: Veröffentlichung erfolgt am erfolgreichen
  Pipeline-Ende; eine Folgepipeline liest erst danach. Atomarität gilt pro Datei.

Produktionscode verwendet Hop-Logging, keine Konsolenausgabe pro Zeile. Modell-
Kompilierung bleibt unter `MODEL_LOCK`; Reader, Writer und Validator werden nicht
zwischen Threads geteilt. File-Transforms sind auf eine Kopie begrenzt.

## 8. Handbuch und Abschluss

Fixtures liegen einmalig unter `docs/biblios/user/examples/`. Ausführbare neue
Tutorials liegen in `examples/`; alte Regressionspipelines bleiben unter `e2e/`.
Die zugehörigen Fachabschnitte und GUI-Controls werden gemeinsam aktualisiert.

Rendering erfolgt mit einer einmal aufgelösten thoth-biblios-JAR aus dem aktuellen
Working Tree; Build und Browserprüfung verwenden dieselbe Version. Der
[Handbuch-README](../biblios/user/README.adoc) nennt die Befehle.
`scripts/check-user-manual.py` prüft die erzeugten Kapitel, Anker, Includes,
Code-Literale, Assets und Beispielziele. Breites und schmales Browserfenster prüfen
zusätzlich die Lesbarkeit.

Vor Abschluss: Diff und Git-Status prüfen, fokussierte Tests, vollständiges
`clean verify`, Distributionsprüfung und verpflichtende installierte E2E ausführen.
Generierte Artefakte, lokale Config und Zugangsdaten werden nicht eingecheckt.
Abnahmeberichte nennen tatsächlich ausgeführte Prüfungen, Artefaktstände und
verbleibende Grenzen; historische Berichte behalten ihre damaligen Ergebnisse.
