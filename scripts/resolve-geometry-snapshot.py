#!/usr/bin/env python3
"""Resolve one published Geometry snapshot for the whole CI run (stdlib only)."""

import argparse
import http.client
import re
import shutil
import sys
import tempfile
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

REPOSITORY = "https://jars.interlis.guru/snapshots"
GROUP = "ch.so.agi"
CORE = "hop-geometry-type"
PLUGIN = "hop-geometry-type-plugin"
PARENT = "hop-geometry-type-parent"


def project_version(pom):
    root = ET.parse(pom).getroot()
    version = root.findtext("{*}properties/{*}hop.geometry.type.version", "").strip()
    if not re.fullmatch(r"[0-9][A-Za-z0-9_.-]*-SNAPSHOT", version):
        raise ValueError(f"Expected a literal Geometry SNAPSHOT version in {pom}, got {version!r}")
    return version


def snapshot_version(xml, artifact, version, extensions):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError as error:
        raise ValueError(f"Invalid metadata for {artifact}: {error}") from error
    if root.findtext("groupId") != GROUP or root.findtext("artifactId") != artifact:
        raise ValueError(f"Unexpected Maven coordinates in metadata for {artifact}")
    timestamp = root.findtext("versioning/snapshot/timestamp", "")
    number = root.findtext("versioning/snapshot/buildNumber", "")
    if not re.fullmatch(r"\d{8}\.\d{6}", timestamp) or not re.fullmatch(r"[1-9]\d*", number):
        raise ValueError(f"Invalid snapshot timestamp/build number for {artifact}")
    resolved = version.removesuffix("SNAPSHOT") + timestamp + "-" + number
    for extension in extensions:
        values = [entry.findtext("value")
                  for entry in root.findall("versioning/snapshotVersions/snapshotVersion")
                  if entry.findtext("extension") == extension and not entry.findtext("classifier")]
        if values != [resolved]:
            raise ValueError(f"Missing or inconsistent {artifact}:{extension} snapshot: expected {resolved}, got {values}")
    return resolved


def fetch(url):
    try:
        return urllib.request.urlopen(url, timeout=60)
    except (urllib.error.URLError, TimeoutError) as error:
        raise ValueError(f"Cannot download published Geometry artifact {url}: {error}") from error


def resolve(repository, version, output_dir):
    base = repository.rstrip("/") + "/" + GROUP.replace(".", "/")
    versions = {}
    for artifact, extensions in ((PARENT, ("pom",)), (CORE, ("pom", "jar")), (PLUGIN, ("pom", "zip"))):
        url = f"{base}/{artifact}/{version}/maven-metadata.xml"
        with fetch(url) as response:
            versions[artifact] = snapshot_version(response.read(), artifact, version, extensions)
    if len(set(versions.values())) != 1:
        raise ValueError(f"Geometry artifacts belong to different snapshot publications: {versions}. Retry after publishing completes.")
    resolved = versions[CORE]
    # Verify POMs and the actual JAR, not only the metadata, before downstream jobs start.
    for artifact, extension in ((PARENT, "pom"), (CORE, "pom"), (CORE, "jar"), (PLUGIN, "pom")):
        url = f"{base}/{artifact}/{version}/{artifact}-{resolved}.{extension}"
        with fetch(url) as response:
            while response.read(65536):
                pass
    output_dir.mkdir(parents=True, exist_ok=True)
    destination = output_dir / f"{PLUGIN}-{version}.zip"
    url = f"{base}/{PLUGIN}/{version}/{PLUGIN}-{resolved}.zip"
    # A failed or truncated download must never become the E2E runtime artifact.
    with tempfile.TemporaryDirectory(dir=output_dir) as temporary:
        downloaded = Path(temporary) / "plugin.zip"
        with fetch(url) as response, downloaded.open("wb") as target:
            shutil.copyfileobj(response, target)
        with zipfile.ZipFile(downloaded) as archive:
            bad_entry = archive.testzip()
            if bad_entry:
                raise ValueError(f"Corrupt Geometry ZIP entry: {bad_entry}")
        downloaded.replace(destination)
    return resolved, destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pom", type=Path, default=Path(__file__).resolve().parents[1] / "pom.xml")
    parser.add_argument("--repository", default=REPOSITORY)
    parser.add_argument("--output-dir", type=Path, default=Path(".ci/geometry-dist"))
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    try:
        version = project_version(args.pom)
        resolved, destination = resolve(args.repository, version, args.output_dir)
        print(f"Geometry snapshot: {resolved} ({args.repository})")
        print(f"Geometry runtime ZIP: {destination}")
        if args.github_output:
            with args.github_output.open("a", encoding="utf-8") as output:
                output.write(f"version={resolved}\nzip_name={destination.name}\n")
    except (ValueError, OSError, http.client.HTTPException, ET.ParseError, zipfile.BadZipFile) as error:
        print(f"Geometry snapshot resolution failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
