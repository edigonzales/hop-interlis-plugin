#!/usr/bin/env python3
"""Verify the packaged Hop pipelines for the six documentation examples."""

import csv
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


if len(sys.argv) != 2:
    raise SystemExit("Usage: check-doc-examples-output.py <output-dir>")

output_dir = Path(sys.argv[1])
XTF24 = "http://www.interlis.ch/xtf/2.4/INTERLIS"
GEOM = "http://www.interlis.ch/geometry/1.0"


def csv_rows(name):
    path = output_dir / name
    if not path.exists():
        raise SystemExit(f"missing documentation output {path}")
    with path.open(newline="", encoding="utf-8") as stream:
        return [[value.strip() for value in row] for row in csv.reader(stream, delimiter=";")]


def require(condition, message):
    if not condition:
        raise SystemExit(message)


demo = csv_rows("doc-demo.csv")
require(len(demo) == 3, f"doc-demo: expected two buildings, got {len(demo) - 1}")
require(demo[0][:5] == ["_ili_tid", "_ili_bid", "Name", "Art", "Geometrie"], f"doc-demo header: {demo[0]}")
require([row[0] for row in demo[1:]] == ["g1", "g2"], f"doc-demo TIDs: {demo[1:]}")
require([row[2] for row in demo[1:]] == ["Rathaus", "Migros"], f"doc-demo names: {demo[1:]}")
require(all("POLYGON" in row[4] for row in demo[1:]), f"doc-demo geometry missing: {demo[1:]}")

structure = csv_rows("doc-struktur.csv")
require(len(structure) == 3, f"doc-struktur: expected two persons, got {len(structure) - 1}")
require(
    structure[0][:8]
    == [
        "_ili_tid",
        "_ili_bid",
        "Name",
        "Vorname",
        "Adresse_Strasse",
        "Adresse_Hausnummer",
        "Adresse_PLZ",
        "Adresse_Ort",
    ],
    f"doc-struktur header: {structure[0]}",
)
require(structure[1][4:8] == ["Hauptstrasse", "42", "4500", "Solothurn"], f"doc-struktur p1: {structure[1]}")
require(structure[2][4:8] == ["Bahnhofstrasse", "1", "4500", "Solothurn"], f"doc-struktur p2: {structure[2]}")

liste = csv_rows("doc-liste.csv")
require(len(liste) == 2, f"doc-liste: expected one parent, got {len(liste) - 1}")
require(liste[0][:3] == ["_ili_tid", "_ili_bid", "Name"], f"doc-liste header: {liste[0]}")
require(liste[1][:3] == ["g1", "b1", "Rathaus"], f"doc-liste parent: {liste[1]}")
liste_transfer = ET.parse(output_dir / "doc-liste-roundtrip.xtf").getroot()
require(liste_transfer.tag == f"{{{XTF24}}}transfer", "doc-liste roundtrip is not XTF 2.4")
codes = [element.text for element in liste_transfer.iter() if element.tag.endswith("}Code")]
require(codes == ["Q100", "Q200"], f"doc-liste order/content changed: {codes}")

role = csv_rows("doc-rolle.csv")
header = role[0]
require(len(role) == 2, f"doc-rolle: expected one building, got {len(role) - 1}")
require("Gemeinde_ref" in header, f"doc-rolle reference field missing: {header}")
require("Gemeinde_Name" in header and "Gemeinde_BFS_Nummer" in header, f"doc-rolle joined fields missing: {header}")
building = role[1]
require(building[header.index("Gemeinde_ref")] == "gem1", f"doc-rolle reference: {building}")
require(building[header.index("Gemeinde_Name")] == "Solothurn", f"doc-rolle joined name: {building}")
require(building[header.index("Gemeinde_BFS_Nummer")] == "2549", f"doc-rolle joined BFS number: {building}")

arc = csv_rows("doc-bogen.csv")
require(len(arc) == 2, f"doc-bogen: expected one axis, got {len(arc) - 1}")
require("COMPOUNDCURVE" in arc[1][3] and "CIRCULARSTRING" in arc[1][3], f"doc-bogen lost curve: {arc[1]}")
require("2600050 1200050" in arc[1][3], f"doc-bogen midpoint missing: {arc[1]}")
arc_transfer = ET.parse(output_dir / "doc-bogen-roundtrip.xtf").getroot()
require(arc_transfer.tag == f"{{{XTF24}}}transfer", "doc-bogen roundtrip is not XTF 2.4")
require(sum(1 for element in arc_transfer.iter() if element.tag == f"{{{GEOM}}}arc") == 1, "doc-bogen ARC was not written")

validation = csv_rows("doc-validierung.csv")
require(validation[0][0:2] == ["_ili_severity", "_ili_message"], f"doc-validierung header: {validation[0]}")
errors = [row for row in validation[1:] if row[0] == "ERROR"]
require(len(errors) == 1, f"doc-validierung: expected one ERROR, got {len(errors)}")
error = errors[0]
require("Temperaturbereich" in error[1], f"doc-validierung constraint message missing: {error}")
require(error[8:10] == ["DemoValidierung.Daten.Messung", "m2"], f"doc-validierung context missing: {error}")
require(error[10] == "", f"doc-validierung class constraint must not have attribute path: {error}")

print("Documentation example E2E outputs are valid")
