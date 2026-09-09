#!/usr/bin/env python3
"""Release-relevant checks for the hop-interlis-plugin distribution ZIP.

Verifies the plugin layout under plugins/transforms/interlis/, requires the
INTERLIS runtime (iox-ili, ili2c-core, ili2c-tool), forbids re-bundling of
shared or Hop-provided artifacts and checks for accidental duplicates.
"""

from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
target = root / "assemblies" / "assemblies-hop-interlis" / "target"
zips = sorted(target.glob("hop-interlis-plugin-*.zip"))
if len(zips) != 1:
    raise SystemExit(f"Expected exactly one plugin ZIP in {target}, found {len(zips)}")

zip_path = zips[0]
with ZipFile(zip_path) as archive:
    entries = [name for name in archive.namelist() if not name.endswith("/")]
    names = [Path(name).name.lower() for name in entries]

    # Required runtime content.
    required = [
        "hop-interlis-core-",
        "hop-interlis-transforms-",
        "iox-ili-",
        "iox-api-",
        "ili2c-core-",
        "ili2c-tool-",
        "ehibasics-",
        "antlr-",
    ]
    for fragment in required:
        if not any(fragment in name for name in names):
            raise SystemExit(f"{zip_path.name}: required dependency matching {fragment!r} is missing")

    # Shared via classLoaderGroup=sogeo-geometry / provided by Hop itself.
    forbidden = [
        "hop-geometry-type",
        "jts-core-1.20",  # org.locationtech.jts shared Hop geometry
        "hop-core-",
        "json-simple-",
        "hop-engine-",
        "hop-ui-",
        "hop-transform-",
        "hop-action-",
    ]
    for fragment in forbidden:
        matches = [name for name in names if fragment in name]
        if matches:
            raise SystemExit(
                f"{zip_path.name}: dependency {fragment!r} must not be bundled in the INTERLIS plugin: {matches}"
            )

    # iox-ili internally uses the legacy com.vividsolutions.jts namespace
    # (different packages, never visible as Hop geometry). Exactly one such
    # jar is expected; org.locationtech.jts must not appear twice anywhere.
    legacy = [name for name in names if "jts-core-1.14" in name or "vividsolutions" in name]
    if not any("jts-core" in name for name in legacy):
        raise SystemExit(
            f"{zip_path.name}: iox-ili requires the legacy com.vividsolutions jts-core jar, which is missing"
        )
    locationtech = [name for name in names if "locationtech" in name]
    if locationtech:
        raise SystemExit(
            f"{zip_path.name}: org.locationtech JTS artifacts must not be bundled: {locationtech}"
        )

    # Layout checks: only jars, no tests/sources jars, no loose classes, no junk.
    for entry in entries:
        name = Path(entry).name
        if not entry.endswith(".jar"):
            raise SystemExit(f"{zip_path.name}: unexpected non-jar entry in ZIP: {entry}")
        if name.endswith("-tests.jar") or name.endswith("-sources.jar") or name.endswith("-javadoc.jar"):
            raise SystemExit(f"{zip_path.name}: forbidden artifact in ZIP: {entry}")
        if name == ".DS_Store":
            raise SystemExit(f"{zip_path.name}: .DS_Store must not be bundled")

    duplicates = {name: names.count(name) for name in set(names) if names.count(name) > 1}
    if duplicates:
        raise SystemExit(f"{zip_path.name}: duplicate jar file names: {duplicates}")

    # Expected layout: only the transform plugin jar (with Jandex index) at the plugin root;
    # all other jars, including hop-interlis-core, belong to lib/.
    plugin_root = "plugins/transforms/interlis/"
    root_jars = [
        name
        for name in entries
        if name.startswith(plugin_root) and "/lib/" not in name and name.endswith(".jar")
    ]
    if len(root_jars) != 1 or "hop-interlis-transforms-" not in root_jars[0]:
        raise SystemExit(
            f"{zip_path.name}: expected exactly the hop-interlis-transforms jar at {plugin_root}, found {root_jars}"
        )
    lib_jars = [name for name in entries if name.startswith(plugin_root + "lib/")]
    if not lib_jars:
        raise SystemExit(f"{zip_path.name}: missing {plugin_root}lib/ runtime dependencies")
    if not any("hop-interlis-core-" in name for name in lib_jars):
        raise SystemExit(
            f"{zip_path.name}: hop-interlis-core jar must be in {plugin_root}lib/ "
            "(Hop only puts one jar per plugin folder on the plugin classloader)"
        )

    # Transform plugin jar must carry a Jandex index for Hop plugin discovery.
    transform_jar = root_jars[0]
    with archive.open(transform_jar) as nested_file:
        with ZipFile(nested_file) as nested:
            if "META-INF/jandex.idx" not in nested.namelist():
                raise SystemExit(
                    f"{zip_path.name}: {transform_jar} is missing META-INF/jandex.idx; "
                    "Hop plugin discovery will fail"
                )

size_mib = zip_path.stat().st_size / (1024 * 1024)
print(f"Distribution OK: {zip_path} ({size_mib:.1f} MiB)")
print("  hop-interlis-transforms jar (with Jandex index) at plugins/transforms/interlis/")
print("  hop-interlis-core and INTERLIS runtime (iox-ili, ili2c-core/tool, ehibasics, antlr) in lib/")
print("  hop-geometry-type, org.locationtech.jts and Apache Hop artifacts stay shared/provided")
print("  legacy com.vividsolutions jts-core (iox-ili internal) is isolated in lib/")
