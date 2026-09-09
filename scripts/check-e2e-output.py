#!/usr/bin/env python3
"""Asserts the outputs produced by the hop-run E2E pipelines.

Usage: check-e2e-output.py <output-dir> [--with-gpkg]

Hop's Text file output pads string fields to their value-meta length, so string
values are compared after stripping. With --with-gpkg the GeoPackage produced by
the INTERLIS -> Vector Writer pipeline is verified as well.
"""

import csv
import sqlite3
import sys
from pathlib import Path

output_dir = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
with_gpkg = "--with-gpkg" in sys.argv

geometry_csv = output_dir / "interlis-input.csv"
if not geometry_csv.exists():
    raise SystemExit(f"missing output {geometry_csv}")

with geometry_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

def val(row, index):
    return row[index].strip()

header = rows[0]
assert header[:3] == ["_ili_tid", "_ili_bid", "Name"], f"unexpected header: {header}"
assert len(rows) == 3, f"expected 1 header + 2 data rows, got {len(rows)}"

o1 = rows[1]
o2 = rows[2]
assert val(o1, 0) == "o1" and val(o1, 1) == "b1" and val(o1, 2) == "A", f"unexpected o1 row: {o1}"
assert val(o2, 0) == "o2" and val(o2, 1) == "b1" and val(o2, 2) == "B", f"unexpected o2 row: {o2}"

# o1: straight geometries; center point, straight axis line, boundary polygon.
assert "POINT (2600000 1200000)" in o1[3], f"center point missing in {o1}"
assert "LINESTRING" in o1[5], f"straight axis missing in {o1}"
assert "POLYGON" in o1[7], f"boundary polygon missing in {o1}"

# o2: the axis must survive as a SQL/MM curve with an exact CIRCULARSTRING control point.
assert "COMPOUNDCURVE" in o2[5], f"axis is not a compound curve: {o2}"
assert "CIRCULARSTRING" in o2[5], f"arc was linearized: {o2}"
assert "2600050 1200050" in o2[5], f"arc control point missing: {o2}"
assert "2600000 1200000" in o2[5], f"arc start point missing: {o2}"
assert "2600100 1200000" in o2[5], f"arc end point missing: {o2}"

structures_csv = output_dir / "interlis-input-structures.csv"
if not structures_csv.exists():
    raise SystemExit(f"missing output {structures_csv}")

with structures_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

assert len(rows) == 2, f"expected 1 header + 1 data row, got {len(rows)}"
building = rows[1]
# _ili_tid, _ili_bid, Code, Location, Address_Street, Address_Number, Municipality_ref, Note
assert val(building, 0) == "g1", f"unexpected TID: {building}"
assert val(building, 1) == "b1", f"unexpected BID: {building}"
assert val(building, 2) == "42", f"unexpected Code: {building}"
assert "POINT (2600000 1200000)" in building[3], f"location missing: {building}"
assert val(building, 4) == "Main Street", f"flattened street missing: {building}"
assert val(building, 5) == "10", f"flattened number missing: {building}"
assert val(building, 6) == "m1", f"role reference missing: {building}"
assert val(building, 7) == "inherited note", f"inherited attribute missing: {building}"

roundtrip_csv = output_dir / "interlis-roundtrip.csv"
if not roundtrip_csv.exists():
    raise SystemExit(f"missing output {roundtrip_csv}")

with roundtrip_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

assert len(rows) == 3, f"expected 1 header + 2 data rows in roundtrip, got {len(rows)}"
r1, r2 = rows[1], rows[2]
assert val(r1, 0) == "o1" and val(r1, 2) == "A", f"unexpected roundtrip o1 row: {r1}"
assert val(r2, 0) == "o2" and val(r2, 2) == "B", f"unexpected roundtrip o2 row: {r2}"
assert "POINT (2600000 1200000)" in r1[3], f"roundtrip center missing: {r1}"
assert "POLYGON" in r1[7], f"roundtrip boundary missing: {r1}"
# The arc must survive the full XTF write/read roundtrip.
assert "COMPOUNDCURVE" in r2[5], f"roundtrip axis is not a compound curve: {r2}"
assert "CIRCULARSTRING" in r2[5], f"roundtrip arc was linearized: {r2}"
assert "2600050 1200050" in r2[5], f"roundtrip arc control point missing: {r2}"

structures_roundtrip_csv = output_dir / "interlis-structures-roundtrip.csv"
if not structures_roundtrip_csv.exists():
    raise SystemExit(f"missing output {structures_roundtrip_csv}")

with structures_roundtrip_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

header = rows[0]
assert header[:8] == [
    "_ili_parent_tid",
    "_ili_parent_bid",
    "_ili_index",
    "Street",
    "Number",
    "Location",
    "PostCode_Code",
    "PostCode_Town",
], f"unexpected structures header: {header}"
assert len(rows) == 5, f"expected 1 header + 4 address rows, got {len(rows)}"

# LIST order must survive the full Explode -> Collect -> Output -> Input cycle.
first, second, third, fourth = rows[1], rows[2], rows[3], rows[4]
assert val(first, 0) == "p1" and val(first, 1) == "b1" and val(first, 2) == "0", f"unexpected first child: {first}"
assert val(first, 3) == "Main Street" and val(first, 4) == "10", f"unexpected first address: {first}"
assert "POINT (2600000 1200000)" in first[5], f"child geometry missing: {first}"
assert val(first, 6) == "4600" and val(first, 7) == "Olten", f"nested PostCode missing: {first}"

assert val(second, 0) == "p1" and val(second, 2) == "1", f"unexpected second child: {second}"
assert val(second, 3) == "Station Street", f"LIST order broken: {second}"
assert val(third, 0) == "p1" and val(third, 2) == "2", f"unexpected third child: {third}"
assert val(third, 3) == "Village Road", f"LIST order broken: {third}"
assert val(fourth, 0) == "p2" and val(fourth, 2) == "0", f"unexpected fourth child: {fourth}"
assert val(fourth, 3) == "Village Road", f"p2 address missing: {fourth}"

associations_csv = output_dir / "interlis-associations-roundtrip.csv"
if not associations_csv.exists():
    raise SystemExit(f"missing output {associations_csv}")

with associations_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

header = rows[0]
assert header[:5] == [
    "_ili_tid",
    "_ili_bid",
    "Name",
    "Address_ref",
    "Address_Share",
], f"unexpected associations header: {header}"
assert len(rows) == 4, f"expected 1 header + 3 person rows, got {len(rows)}"

p1, p2, p3 = rows[1], rows[2], rows[3]
assert val(p1, 0) == "p1" and val(p1, 3) == "a1", f"flattened role ref missing: {p1}"
assert val(p1, 4) == "0.5", f"flattened association attribute missing: {p1}"
assert val(p2, 0) == "p2" and val(p2, 3) == "", f"unexpected p2 row: {p2}"
assert val(p3, 0) == "p3" and val(p3, 3) == "", f"unexpected p3 row: {p3}"

association_rows_csv = output_dir / "interlis-association-rows-roundtrip.csv"
if not association_rows_csv.exists():
    raise SystemExit(f"missing output {association_rows_csv}")

with association_rows_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

header = rows[0]
assert header[:4] == [
    "_ili_bid",
    "Person_ref",
    "Task_ref",
    "Task_order_pos",
], f"unexpected association rows header: {header}"
assert len(rows) == 3, f"expected 1 header + 2 link rows, got {len(rows)}"

link1, link2 = rows[1], rows[2]
assert val(link1, 1) == "p1" and val(link1, 2) == "t1", f"unexpected link row: {link1}"
assert val(link1, 3) == "0", f"order position missing: {link1}"
assert val(link2, 1) == "p2" and val(link2, 2) == "t2", f"unexpected link row: {link2}"
assert val(link2, 3) == "1", f"order position missing: {link2}"

generic_csv = output_dir / "interlis-generic-transfer.csv"
if not generic_csv.exists():
    raise SystemExit(f"missing output {generic_csv}")

with generic_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

# 14 object rows of mixed classes through the lossless generic event roundtrip.
assert len(rows) == 15, f"expected 1 header + 14 object rows, got {len(rows)}"
header = rows[0]
assert header[0] == "_ili_event_type" and header[4] == "_ili_class", f"unexpected header: {header}"
classes = {val(row, 4) for row in rows[1:]}
assert "HopIli_Associations_V1.Data.Membership" in classes, f"membership link missing: {classes}"
assert "HopIli_Associations_V1.Data.Person" in classes, f"person missing: {classes}"
assert all(val(row, 3) == "b1" for row in rows[1:]), "basket id not preserved"
assert all(val(row, 0) == "OBJECT" for row in rows[1:]), "unexpected event rows in OBJECTS mode"

generic_delete_csv = output_dir / "interlis-generic-delete.csv"
if not generic_delete_csv.exists():
    raise SystemExit(f"missing output {generic_delete_csv}")

with generic_delete_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

assert len(rows) == 2, f"expected 1 header + 1 delete row, got {len(rows)}"
delete_row = rows[1]
assert val(delete_row, 0) == "OBJECT", f"unexpected event: {delete_row}"
assert val(delete_row, 6) == "DELETE", f"delete operation lost: {delete_row}"
assert val(delete_row, 5) == "p1", f"unexpected TID: {delete_row}"

validate_csv = output_dir / "interlis-validate.csv"
if not validate_csv.exists():
    raise SystemExit(f"missing output {validate_csv}")

with validate_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

header = rows[0]
assert header[0] == "_ili_severity" and header[1] == "_ili_message", f"unexpected header: {header}"
assert len(rows) > 1, "expected validation error rows"
assert any(val(row, 0) == "ERROR" for row in rows[1:]), "no ERROR severity rows"
assert any("Sample" in val(row, 8) for row in rows[1:]), "class context missing"

enumerations_csv = output_dir / "interlis-enumerations.csv"
if not enumerations_csv.exists():
    raise SystemExit(f"missing output {enumerations_csv}")

with enumerations_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

header = rows[0]
assert header[:6] == [
    "enum_definition",
    "enum_value",
    "enum_path",
    "parent_value",
    "depth",
    "is_leaf",
], f"unexpected header: {header}"
values = {val(row, 1) for row in rows[1:]}
assert "beta" in values and "beta_1" in values, f"sub-enumeration values missing: {values}"
beta1 = next(row for row in rows[1:] if val(row, 1) == "beta_1")
assert val(beta1, 3) == "beta" and val(beta1, 4) == "1", f"hierarchy broken: {beta1}"

delete_roundtrip_csv = output_dir / "interlis-delete-roundtrip.csv"
if not delete_roundtrip_csv.exists():
    raise SystemExit(f"missing output {delete_roundtrip_csv}")

with delete_roundtrip_csv.open(newline="", encoding="utf-8") as f:
    rows = list(csv.reader(f, delimiter=";"))

assert len(rows) == 2, f"expected 1 header + 1 delete row, got {len(rows)}"
header = rows[0]
assert "_ili_operation" in header, f"operation field missing: {header}"
delete_row = rows[1]
assert val(delete_row, 0) == "p1", f"unexpected TID: {delete_row}"
operation = val(delete_row, header.index("_ili_operation"))
assert operation == "DELETE", f"delete operation lost through the typed roundtrip: {delete_row}"

if with_gpkg:
    gpkg = output_dir / "interlis-arcs.gpkg"
    if not gpkg.exists():
        raise SystemExit(f"missing output {gpkg}")
    with sqlite3.connect(gpkg) as conn:
        count = conn.execute(
            "SELECT COUNT(*) FROM buildings"
        ).fetchone()[0]
        assert count == 2, f"expected 2 features in buildings, got {count}"

        names = sorted(
            row[0]
            for row in conn.execute("SELECT name FROM buildings")
        )
        assert names == ["arc-one", "arc-two"], f"unexpected feature names: {names}"

        geometry_type = conn.execute(
            "SELECT geometry_type_name FROM gpkg_geometry_columns "
            "WHERE table_name = 'buildings' AND column_name = 'Axis'"
        ).fetchone()
        assert geometry_type is not None, "geometry column Axis missing in gpkg_geometry_columns"
        # The axis must survive as an SQL/MM curve in the GeoPackage.
        assert geometry_type[0] == "COMPOUNDCURVE", (
            f"axis was not stored as a curve: {geometry_type[0]}"
        )

        extension = conn.execute(
            "SELECT scope FROM gpkg_extensions "
            "WHERE table_name = 'buildings' AND column_name = 'Axis' "
            "AND extension_name = 'gpkg_geom_COMPOUNDCURVE'"
        ).fetchone()
        assert extension is not None, "COMPOUNDCURVE extension not registered for Axis"

print("E2E outputs OK:")
print(f"  {geometry_csv}: 2 geometry rows, arc preserved as CIRCULARSTRING")
print(f"  {structures_csv}: structure flattening, role reference and inheritance verified")
print(f"  {roundtrip_csv}: XTF write/read roundtrip, arc still a CIRCULARSTRING")
print(f"  {structures_roundtrip_csv}: LIST explode/collect roundtrip with order, geometry and nested structure")
print(f"  {associations_csv}: association attributes flattened from link objects and written back")
print(f"  {association_rows_csv}: association rows roundtrip with ORDERED role order positions")
print(f"  {generic_csv}: lossless generic event roundtrip with mixed classes and baskets")
print(f"  {generic_delete_csv}: delete operation preserved through the generic roundtrip")
print(f"  {validate_csv}: validation error rows with severity, class and line context")
print(f"  {enumerations_csv}: enumeration values incl. sub-enumeration hierarchy")
print(f"  {delete_roundtrip_csv}: DELETE operation preserved through the typed roundtrip")
if with_gpkg:
    print("  interlis-arcs.gpkg: 2 curve features, axis stored as COMPOUNDCURVE")

# P1: inspect the actual XML to compare every XYZ ordinate (WKT rendering may omit Z).
import xml.etree.ElementTree as ET
ns = {"m": "http://www.interlis.ch/xtf/2.4/HopIli_P1_V1",
      "ili": "http://www.interlis.ch/xtf/2.4/INTERLIS", "g": "http://www.interlis.ch/geometry/1.0"}
xml = ET.parse(output_dir / "p1-roundtrip.xtf")
item = xml.find(".//m:Item", ns)
assert item is not None
assert item.attrib[f"{{{ns['ili']}}}tid"] == "new-id", "configured TID did not override _ili_tid"
assert xml.find(".//m:Data", ns).attrib[f"{{{ns['ili']}}}bid"] == "constant"
assert item.findtext("m:Name", namespaces=ns) == "updated", "source carrier overrode the edited row"
assert item.findtext("m:Children/m:Detail/m:Code", namespaces=ns) == "keep-child"
line = [(1., 2., 3.), (4., 5., 6.)]
polygon = [(1., 1., 3.), (4., 1., 3.), (4., 4., 3.), (1., 1., 3.)]
for attribute, expected in {"Location": [line[0]], "Axis": line, "Face": polygon, "Axes": line, "Faces": polygon}.items():
    attributes = item.findall(f"m:{attribute}", ns)
    assert len(attributes) == 1, f"duplicate {attribute} after source overlay"
    coords = [tuple(float(c.findtext(f"g:c{i}", namespaces=ns)) for i in (1, 2, 3))
              for c in attributes[0].findall(".//g:coord", ns)]
    assert coords == expected, f"XYZ lost in {attribute}: {coords}"

def read_p1_csv(name):
    with (output_dir / f"{name}.csv").open(newline="", encoding="utf-8") as f:
        return [[v.strip() for v in row] for row in csv.reader(f, delimiter=";")]

append = read_p1_csv("p1-append")
assert append == [["_ili_event_type", "_ili_tid", "_ili_bid", "Name"], ["OBJECT", "i0", "b1", "before0"]], append
for name, count, incomplete in [("23-p1-validation-failure", 10, False), ("24-p1-validation-limit", 2, True)]:
    findings = read_p1_csv(name)
    assert len(findings[0]) == 13
    assert sum(row[0] == "ERROR" for row in findings[1:]) == count, findings
    assert any("Validation incomplete" in row[1] for row in findings[1:]) == incomplete, findings
    assert len(findings) == 1 + count + int(incomplete), findings
print("  P1: source overlay, configured identities, XYZ, append and complete failure diagnostics verified")
