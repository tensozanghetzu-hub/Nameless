import sys
from pathlib import Path
import tempfile
import unittest
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from nameless_release import release_version, source_archive, sha256


class ReleaseToolTests(unittest.TestCase):
    def test_bootstrap_version(self):
        self.assertEqual((54, "2.5.3-nameless.5"), release_version("v54"))

    def test_future_version_increases(self):
        self.assertEqual((55, "2.5.3-nameless.6"), release_version("v55"))
        self.assertEqual((56, "2.5.3-nameless.7"), release_version("v56"))

    def test_bad_and_shell_injection_tags_rejected(self):
        for tag in ["v53", "v055", "v1.2.3", "v55;echo BAD", "../v55", "v-1", "v99999999999"]:
            with self.assertRaises(ValueError):
                release_version(tag)

    def populate(self, root):
        for name in ["LICENSE", "android/build.gradle.kts", "gradle/wrapper/gradle-wrapper.jar", "gradle/libs.versions.toml", "third_party/shosetsu-kotlin-lib/LICENSE", ".github/workflows/release.yml", "android/src/main/App.kt"]:
            p = root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("fixture")

    def test_archive_keeps_required_source_and_workflows(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t) / "source"
            self.populate(root)
            archive = source_archive(root, Path(t) / "source.zip")
            with zipfile.ZipFile(archive) as z:
                self.assertIsNone(z.testzip())
                self.assertIn("Nameless-source/.github/workflows/release.yml", z.namelist())
                self.assertIn("Nameless-source/gradle/wrapper/gradle-wrapper.jar", z.namelist())

    def test_signing_secrets_and_builds_never_enter_public_archive(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t) / "source"
            self.populate(root)
            for name in ["secret.p12", "password.txt", "NAMELESS_KEYSTORE_BASE64.txt", "NAMELESS_KEYSTORE_PASSWORD.txt", "android/build/app.apk", "release-assets/nameless-update.json", "nameless-signing/readme.txt", "private-signing/secret.txt", ".git/config", "local.properties"]:
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text("PRIVATE-MARKER")
            archive = source_archive(root, Path(t) / "source.zip")
            with zipfile.ZipFile(archive) as z:
                for name in z.namelist():
                    self.assertNotIn(b"PRIVATE-MARKER", z.read(name))

    def test_symlink_to_private_material_is_not_archived(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t) / "source"
            self.populate(root)
            secret = Path(t) / "private.txt"
            secret.write_text("PRIVATE-MARKER")
            (root / "innocent.txt").symlink_to(secret)
            archive = source_archive(root, Path(t) / "source.zip")
            with zipfile.ZipFile(archive) as z:
                self.assertNotIn("Nameless-source/innocent.txt", z.namelist())

    def test_missing_corresponding_source_fails(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t) / "empty"
            root.mkdir()
            with self.assertRaises(ValueError):
                source_archive(root, Path(t) / "broken.zip")

    def test_hash_is_reproducible(self):
        with tempfile.TemporaryDirectory() as t:
            p = Path(t) / "data"
            p.write_bytes(b"abc")
            self.assertEqual("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256(p))


if __name__ == "__main__":
    unittest.main()
