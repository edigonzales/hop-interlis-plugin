"""Offline regression tests with a local Maven repository and HTTP server."""

import functools
import http.server
import importlib.util
import pathlib
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "resolve-geometry-snapshot.py"
SPEC = importlib.util.spec_from_file_location("snapshot", SCRIPT)
SNAPSHOT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SNAPSHOT)
FIXTURE = (pathlib.Path(__file__).parent / "fixtures/geometry-metadata.xml").read_text()
VERSION = "0.2.0-SNAPSHOT"
RESOLVED = "0.2.0-20260910.120000-1"


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


class SnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.output = self.root / "output"
        self.pom = self.root / "pom.xml"
        self.pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><properties>'
                            '<hop.geometry.type.version>0.2.0-SNAPSHOT</hop.geometry.type.version>'
                            '</properties></project>')
        for artifact in (SNAPSHOT.PARENT, SNAPSHOT.CORE, SNAPSHOT.PLUGIN):
            directory = self.directory(artifact)
            directory.mkdir(parents=True)
            metadata = FIXTURE.replace("<artifactId>hop-geometry-type</artifactId>",
                                       f"<artifactId>{artifact}</artifactId>")
            if artifact == SNAPSHOT.PLUGIN:
                metadata = metadata.replace("<extension>jar</extension>", "<extension>zip</extension>")
            (directory / "maven-metadata.xml").write_text(metadata)
            (directory / f"{artifact}-{RESOLVED}.pom").write_text("<project/>")
        self.jar = self.directory(SNAPSHOT.CORE) / f"{SNAPSHOT.CORE}-{RESOLVED}.jar"
        self.jar.write_bytes(b"jar fixture")
        self.zip = self.directory(SNAPSHOT.PLUGIN) / f"{SNAPSHOT.PLUGIN}-{RESOLVED}.zip"
        with zipfile.ZipFile(self.zip, "w") as archive:
            archive.writestr("plugins/misc/hop-geometry-type/geometry.jar", b"fixture")
        handler = functools.partial(QuietHandler, directory=str(self.root))
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_server)
        self.url = f"http://127.0.0.1:{self.server.server_port}"

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def directory(self, artifact):
        return self.root / "ch/so/agi" / artifact / VERSION

    def resolve(self):
        return SNAPSHOT.resolve(self.url, VERSION, self.output)

    def test_valid_pair_and_cli_outputs(self):
        output_file = self.root / "github-output"
        result = subprocess.run([sys.executable, str(SCRIPT), "--repository", self.url,
                                 "--pom", str(self.pom), "--output-dir", str(self.output),
                                 "--github-output", str(output_file)], text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"version={RESOLVED}\n", output_file.read_text())
        self.assertIn(RESOLVED, result.stdout)
        self.assertEqual((self.output / f"{SNAPSHOT.PLUGIN}-{VERSION}.zip").read_bytes(), self.zip.read_bytes())

    def test_missing_artifacts_fail_before_output(self):
        for path in (self.jar, self.zip, self.directory(SNAPSHOT.PARENT) / f"{SNAPSHOT.PARENT}-{RESOLVED}.pom"):
            with self.subTest(path=path):
                data = path.read_bytes()
                path.unlink()
                with self.assertRaisesRegex(ValueError, "Cannot download published Geometry artifact"):
                    self.resolve()
                self.assertFalse(list(self.output.glob("*.zip")))
                path.write_bytes(data)

    def test_missing_jar_or_zip_metadata(self):
        for artifact, extension in ((SNAPSHOT.CORE, "jar"), (SNAPSHOT.PLUGIN, "zip")):
            path = self.directory(artifact) / "maven-metadata.xml"
            original = path.read_text()
            with self.subTest(artifact=artifact):
                path.write_text(original.replace(f"<extension>{extension}</extension>", "<extension>txt</extension>"))
                with self.assertRaisesRegex(ValueError, "Missing or inconsistent"):
                    self.resolve()
                path.write_text(original)

    def test_different_publications_fail(self):
        path = self.directory(SNAPSHOT.PLUGIN) / "maven-metadata.xml"
        path.write_text(path.read_text().replace("20260910.120000", "20260910.130000"))
        with self.assertRaisesRegex(ValueError, "different snapshot publications"):
            self.resolve()

    def test_invalid_metadata_and_wrong_coordinates(self):
        for xml in ("<broken", FIXTURE.replace("ch.so.agi", "wrong.group"),
                    FIXTURE.replace("<buildNumber>1", "<buildNumber>invalid")):
            with self.subTest(xml=xml):
                with self.assertRaises(ValueError):
                    SNAPSHOT.snapshot_version(xml, SNAPSHOT.CORE, VERSION, ("jar",))

    def test_classified_artifact_does_not_replace_main_jar(self):
        xml = FIXTURE.replace("<extension>jar</extension>", "<extension>jar</extension><classifier>sources</classifier>")
        with self.assertRaisesRegex(ValueError, "Missing or inconsistent"):
            SNAPSHOT.snapshot_version(xml, SNAPSHOT.CORE, VERSION, ("jar",))

    def test_corrupt_zip_leaves_no_runtime_artifact(self):
        self.zip.write_bytes(b"incomplete download")
        with self.assertRaises(zipfile.BadZipFile):
            self.resolve()
        self.assertFalse(list(self.output.glob("*.zip")))

    def test_version_comes_from_pom(self):
        self.assertEqual(SNAPSHOT.project_version(self.pom), VERSION)
        self.pom.write_text(self.pom.read_text().replace(VERSION, "${unresolved}"))
        with self.assertRaisesRegex(ValueError, "literal Geometry SNAPSHOT"):
            SNAPSHOT.project_version(self.pom)


if __name__ == "__main__":
    unittest.main()
