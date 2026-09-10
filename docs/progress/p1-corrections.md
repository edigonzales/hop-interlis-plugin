# P1-Korrekturpaket

Status: **abgeschlossen und vollständig verifiziert** (2026-09-09).

## Umgesetzt

- Vollvalidierung mit genau einem expliziten zweiten Durchlauf; explizite
  Validator-Konfiguration bleibt wirksam. Reader/Validator werden auf allen
  Ausgangspfaden geschlossen.
- Fehlerzählung im Meldungsadapter, unmittelbarer Abbruch am Fehlerlimit in beiden
  Durchläufen und zusätzliche Abschlussdiagnose bei unvollständiger Prüfung.
  Warnungen/Informationen zählen nicht gegen das Limit; 0 ist unbegrenzt.
- Diagnoseausgabe und Fehlerstatus sind getrennt. `failOnErrors` setzt den
  Hop-Fehlerzähler beim Pipeline-Abschluss, nachdem auch langsame Verbraucher
  fertig sind. Grund: Hop 2.18.1 stoppt beim Transform-Abschluss mit positivem
  Fehlerzähler sonst selbst die übrigen Transforms. Technische Fehler bleiben sofortige Fehler.
- Quellobjekt-Overlay ersetzt/löscht projizierte Werte auf einer tiefen Kopie,
  erhält nicht projizierte Strukturen/BAG/LIST und dupliziert keine Geometrien
  oder Referenzen. Referenz-TID/BID/Reihenfolge werden gemeinsam erneuert.
  Pflichtfelder werden am fertigen Objekt geprüft; DELETE hat keine Nutzdaten/Links.
- Output bindet TID/BID gemäss Konfiguration. Konfigurierter TID gewinnt vor
  `_ili_tid`; ein leerer BID-Feldname verwendet den konstanten Basket. Fehlende
  konfigurierte Felder werden abgelehnt. Nicht identifizierbare Assoziationen
  benötigen kein TID-Feld. Prüfung, Anzeige und Runtime teilen die Bindungsregeln.
- Modellbasierte Dimension für Linien/Flächen/Multi-Geometrien und CHLV95-Wrapper;
  Alias-/Vererbungsketten behalten ihre Dimension. Gerade 3D-Geometrien behalten
  XYZ, bestehende 2D-Kurven bleiben erhalten. Unbekannte Dimensionen und echte
  3D-Kurven werden explizit abgelehnt.
- Gemeinsamer Append-Ausgabeplan für Designzeit und Runtime: Eingabefeldreihenfolge
  bleibt erhalten; technische Identitäten werden einmalig typkompatibel übernommen,
  fachliche Kollisionen abgelehnt. Derselbe Aufbau gilt für gepufferte Projektionen.
- Kopienzahl wird auch in Kopie 0 geprüft. Object to Row bleibt ohne
  Assoziationsauflösung parallel nutzbar; gepufferte Projektionen, Structure Collect
  und Role Join benötigen eine Kopie.

## Regressionen und Fixtures

Neue Core-, Adapter-, Binding- und Pipeline-Regressionen decken die P1-Verträge ab,
inklusive langsamen Verbrauchers mit Queuegrösse 2, Abbruch im zweiten Durchlauf,
Vorwärtsreferenz, fehlendem Ziel, UNIQUE und tatsächlich angewandter TOML-Konfiguration.

Die vorher als gültig benannte Assoziationsfixture enthielt unzulässige eigenständige
Linkobjekte, einen OID auf einer nicht identifizierbaren Assoziation und fehlende
Pflichtverknüpfungen. `HopIli_Associations_V1_valid.xtf` ist korrigiert und wird jetzt
vollständig validiert. Der bisherige Inhalt bleibt für die gezielten Mappingtests
unter `HopIli_Associations_V1_mapping.xtf` erhalten. Es wurden keine Prüfungen
abgeschaltet, um die gültige Fixture durchzubringen.

Paket-E2E 21–24 ergänzt geänderte Quellobjekte, TID-/BID-Konfiguration, XYZ-Vergleich
aller Koordinaten, Append und Validierungsfehler mit erwarteten Exit-Codes. Der
Runner isoliert seine Hop-Konfiguration und prüft auch erwartete Fehlerläufe.

## Abnahme

- `./mvnw -B -ntp clean verify`: **erfolgreich**, 348 Tests (163 Core + 185 Transforms),
  keine Fehler und keine übersprungenen Tests; Temurin Java 21.0.7.
- `python3 scripts/check-distribution.py`: **erfolgreich**, Plugin-ZIP 4.8 MiB,
  korrekte gemeinsame/provided Geometry-/Hop-Abhängigkeiten und gepinnte INTERLIS-Libraries.
- `bash scripts/run-e2e.sh "$HOP_HOME"`: **erfolgreich**, 23 Paketpipelines auf einer
  frisch entpackten isolierten Hop-2.18.1-Installation. Für den optionalen
  GeoPackage-Test wurde über `HOP_VECTOR_RASTER_ZIP` ein vorgebautes kompatibles
  GeoTools-Paket eingesetzt; das aktuelle benachbarte Repository verwendet bereits
  andere Plugin-IDs.
- Beide erwarteten Fehlerläufe liefern Exit-Code 1. Alle 10 Fehlerdiagnosen des
  unbegrenzten Laufs sowie genau 2 Fehler und die Abschlussdiagnose des limitierten
  Laufs sind in den CSV-Ausgaben angekommen.
- XYZ-Vergleich jeder Koordinate, geändertes Quellobjekt, konfigurierte Identitäten,
  erhaltene BAG-Werte, Append, bestehende 2D-ARC-Roundtrips und GeoPackage geprüft.
- `git diff --check`, Shell-/Python-Syntaxprüfung sowie Diff-/Statuskontrolle:
  **erfolgreich**. Keine Buildartefakte oder festen Entwicklerpfade in den Änderungen.

## Grenzen

- Keine Plugin-ID-, `.hpl`-Metadaten- oder Bibliotheksversionsänderungen; das
  Validierungsfehlerschema bleibt bei 13 Feldern.
- Keine neue Partitionierungsarchitektur und kein frei editierbares Output-Mapping.
- 3D-Kurven sind durch die gemeinsame Geometry-Bibliothek begrenzt; es erfolgt
  keine stille Linearisierung. Gerade 3D-Geometrien sind abgedeckt.
- Bei Benutzerabbruch werden Ressourcen geschlossen und eine Abschlussdiagnose
  vorbereitet. Eine bereits gestoppte Hop-Pipeline garantiert keine weitere Zustellung.
- Die historischen Mapping-Fixtures testen bewusst auch unvollständige Transfers;
  ein erfolgreicher Mapping-Roundtrip ist kein Nachweis der fachlichen Vollgültigkeit.
