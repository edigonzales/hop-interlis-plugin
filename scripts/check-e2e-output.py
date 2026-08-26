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
if with_gpkg:
    print("  interlis-arcs.gpkg: 2 curve features, axis stored as COMPOUNDCURVE")
