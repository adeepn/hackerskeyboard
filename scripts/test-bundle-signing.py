#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the actual release workflow's AAB signing block with disposable keys."""

import base64
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]


def signing_script():
    workflow = (ROOT / ".github/workflows/release.yml").read_text()
    step = workflow.split("      - name: Sign AAB and verify upload certificate\n", 1)[1]
    step = step.split("\n      - name:", 1)[0]
    return textwrap.dedent(step.split("        run: |\n", 1)[1])


class BundleSigningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture_dir = tempfile.TemporaryDirectory(prefix="hk-signing-key-")
        cls.addClassCleanup(cls.fixture_dir.cleanup)
        cls.keystore = Path(cls.fixture_dir.name) / "test.p12"
        cls.password = "disposable-test-password"
        cls.key_env = dict(os.environ, TEST_STORE_PASSWORD=cls.password)
        subprocess.run(
            ["keytool", "-genkeypair", "-alias", "test-upload", "-keyalg", "RSA",
             "-keysize", "2048", "-validity", "3650", "-dname", "CN=Disposable test",
             "-storetype", "PKCS12", "-keystore", str(cls.keystore),
             "-storepass:env", "TEST_STORE_PASSWORD", "-keypass:env", "TEST_STORE_PASSWORD"],
            env=cls.key_env, check=True, capture_output=True,
        )
        cert = subprocess.run(
            ["keytool", "-exportcert", "-keystore", str(cls.keystore),
             "-storepass:env", "TEST_STORE_PASSWORD", "-alias", "test-upload"],
            env=cls.key_env, check=True, capture_output=True,
        ).stdout
        cls.fingerprint = hashlib.sha256(cert).hexdigest().upper()
        cls.script = signing_script()

    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="hk-signing-run-")
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name)
        (self.directory / "runner-temp").mkdir()
        self.unsigned = self.directory / "unsigned-bundle/app-release.aab"
        self.unsigned.parent.mkdir()
        with zipfile.ZipFile(self.unsigned, "w") as bundle:
            bundle.writestr("BundleConfig.pb", b"test config")
            bundle.writestr("base/manifest/AndroidManifest.xml", b"test manifest")
            bundle.writestr("base/dex/classes.dex", b"test payload")
        self.env = dict(
            os.environ,
            KEYSTORE_BASE64=base64.b64encode(self.keystore.read_bytes()).decode(),
            KEY_ALIAS="test-upload", STORE_PASSWORD=self.password, STORE_TYPE="PKCS12",
            EXPECTED_CERT_SHA256=self.fingerprint,
            VERSION_NAME="2.0.0-alpha04", VERSION_CODE="2000004",
            RUNNER_TEMP=str(self.directory / "runner-temp"), GITHUB_SHA="test-commit",
        )
        self.signed = self.directory / "signed/hackers-keyboard-v2-2.0.0-alpha04.aab"
        self.evidence = self.directory / "signed/bundle-evidence.txt"

    def run_sign(self):
        result = subprocess.run(
            ["bash", "-c", self.script], cwd=self.directory, env=self.env,
            text=True, capture_output=True,
        )
        self.assertFalse((self.directory / "runner-temp/hackers-keyboard-v2-bundle.p12").exists())
        return result

    def verify(self, alias="test-upload"):
        return subprocess.run(
            ["jarsigner", "-verify", "-strict", "-keystore", str(self.keystore),
             "-storetype", "PKCS12", "-storepass:env", "TEST_STORE_PASSWORD",
             str(self.signed), alias],
            env=self.key_env, capture_output=True,
        )

    def test_signed_payload_and_evidence(self):
        result = self.run_sign()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.verify().returncode, 0)
        with zipfile.ZipFile(self.unsigned) as original, zipfile.ZipFile(self.signed) as signed:
            for name in original.namelist():
                self.assertEqual(original.read(name), signed.read(name))
        evidence = dict(line.split("=", 1) for line in self.evidence.read_text().splitlines())
        self.assertEqual(evidence["upload_certificate_sha256"], self.fingerprint)
        self.assertEqual(evidence["version_code"], "2000004")
        self.assertEqual(evidence["version_name"], "2.0.0-alpha04")
        self.assertEqual(evidence["commit"], "test-commit")
        self.assertEqual(evidence["aab_sha256"], hashlib.sha256(self.signed.read_bytes()).hexdigest())
        self.assertEqual(evidence["unsigned_aab_sha256"], hashlib.sha256(self.unsigned.read_bytes()).hexdigest())

    def test_wrong_certificate_rejected(self):
        self.env["EXPECTED_CERT_SHA256"] = "0" * 64
        self.assertNotEqual(self.run_sign().returncode, 0)
        self.assertFalse(self.signed.exists())
        self.assertFalse(self.evidence.exists())

    def test_missing_secret_rejected(self):
        self.env["STORE_PASSWORD"] = ""
        self.assertNotEqual(self.run_sign().returncode, 0)
        self.assertFalse(self.evidence.exists())

    def test_invalid_versions_rejected(self):
        for code in ("2000003", "garbage", "", "02000004"):
            with self.subTest(code=code):
                self.env["VERSION_CODE"] = code
                self.assertNotEqual(self.run_sign().returncode, 0)
                self.assertFalse(self.evidence.exists())
        self.env["VERSION_CODE"] = "2000004"
        self.env["VERSION_NAME"] = "../../invalid"
        self.assertNotEqual(self.run_sign().returncode, 0)

    def test_tampered_payload_rejected(self):
        self.assertEqual(self.run_sign().returncode, 0)
        with zipfile.ZipFile(self.signed) as signed:
            entries = [(entry, signed.read(entry)) for entry in signed.infolist()]
        with zipfile.ZipFile(self.signed, "w") as signed:
            for entry, content in entries:
                signed.writestr(entry, b"tampered" if entry.filename.endswith("classes.dex") else content)
        self.assertNotEqual(self.verify().returncode, 0)

    def test_unsigned_entry_rejected(self):
        self.assertEqual(self.run_sign().returncode, 0)
        with zipfile.ZipFile(self.signed, "a") as signed:
            signed.writestr("base/assets/unsigned.txt", b"not signed")
        self.assertNotEqual(self.verify().returncode, 0)

    def test_wrong_signer_alias_rejected(self):
        self.assertEqual(self.run_sign().returncode, 0)
        self.assertNotEqual(self.verify(alias="not-the-upload-key").returncode, 0)


if __name__ == "__main__":
    unittest.main()
