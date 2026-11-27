#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Guard removal of the legacy cross-editor deferred-result bridge."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class VoicePrivacyContractTest(unittest.TestCase):
    def test_no_legacy_trigger_or_artifact_can_schedule_results(self):
        self.assertFalse((ROOT / 'app/libs/voiceimeutils.jar').exists())
        for path in (ROOT / 'app/src/main').rglob('*.java'):
            source = path.read_text()
            self.assertNotIn('com.google.android.voiceime', source, str(path))
            self.assertNotIn('VoiceRecognitionTrigger', source, str(path))
        self.assertNotIn('voiceimeutils', (ROOT / 'app/build.gradle').read_text())


if __name__ == '__main__':
    unittest.main()
