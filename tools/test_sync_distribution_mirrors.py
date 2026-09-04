import contextlib
import io
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_distribution_mirrors as mirrors


class SyncDistributionMirrorsTest(unittest.TestCase):

    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        (self.root / "distribution.properties").write_text(
            "camel.main.version=4.22.0\nresolver.version=2.0.0\n", encoding="utf-8"
        )
        self.original_pom = """<project>
  <properties>
    <!-- BEGIN distribution property mirrors
         distribution.properties is authoritative. -->
    <camel.main.version>4.21.0</camel.main.version>
    <resolver.version>2.0.0</resolver.version>
    <!-- END distribution property mirrors -->
  </properties>
</project>
"""
        (self.root / "pom.xml").write_text(self.original_pom, encoding="utf-8")

    def tearDown(self):
        self.temporary_directory.cleanup()

    def test_synchronizes_all_marked_properties_idempotently(self):
        self.assertEqual(["camel.main.version"], mirrors.synchronize(self.root))
        updated = (self.root / "pom.xml").read_text(encoding="utf-8")
        self.assertIn("<camel.main.version>4.22.0</camel.main.version>", updated)
        self.assertEqual([], mirrors.synchronize(self.root))
        self.assertEqual(updated, (self.root / "pom.xml").read_text(encoding="utf-8"))

    def test_check_reports_drift_without_writing(self):
        errors = io.StringIO()
        with contextlib.redirect_stderr(errors):
            result = mirrors.main(["--check"], self.root)
        self.assertEqual(1, result)
        self.assertIn("camel.main.version", errors.getvalue())
        self.assertEqual(self.original_pom, (self.root / "pom.xml").read_text(encoding="utf-8"))

    def test_rejects_a_mirror_without_an_authoritative_property(self):
        (self.root / "distribution.properties").write_text(
            "camel.main.version=4.22.0\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "missing mirrored property resolver.version"):
            mirrors.synchronize(self.root)


if __name__ == "__main__":
    unittest.main()
