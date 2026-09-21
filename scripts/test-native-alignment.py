#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Offline positive/negative cases for the 16 KB release gate."""
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zipfile

sys.dont_write_bytecode = True
from verify_native_alignment import MACHINES, verify_archive, verify_elf


def elf(machine=183, alignment=16384, address=0, count=1):
    data = bytearray(256)
    struct.pack_into("<16sHHIQQQIHHHHHH", data, 0,
                     b"\x7fELF\x02\x01\x01", 3, machine, 1, 0, 64, 0, 0, 64, 56, count, 0, 0, 0)
    struct.pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, address, 0, 256, 256, alignment)
    return data


class NativeAlignmentTest(unittest.TestCase):
    def test_valid_64_bit_abis(self):
        for machine in MACHINES.values():
            verify_elf(elf(machine), machine)

    def test_bad_elfs_fail(self):
        for data in (elf(alignment=4096), elf(alignment=24576), elf(address=4096),
                     elf(machine=62), elf(count=0), elf()[:100], b"\x7fELF"):
            with self.subTest(data=data[:20]), self.assertRaises(ValueError):
                verify_elf(data, 183)

    def archive(self, path, compressed=False, aligned=False, missing=False):
        prefix = "lib" if path.suffix == ".apk" else "base/lib"
        with zipfile.ZipFile(path, "w") as archive:
            for abi, machine in MACHINES.items():
                if missing and abi == "x86_64":
                    continue
                info = zipfile.ZipInfo(f"{prefix}/{abi}/libtest.so")
                info.compress_type = zipfile.ZIP_DEFLATED if compressed else zipfile.ZIP_STORED
                if aligned:
                    needed = (-archive.fp.tell() - 30 - len(info.filename)) % 16384
                    if needed < 4:
                        needed += 16384
                    info.extra = struct.pack("<HH", 0xffff, needed - 4) + bytes(needed - 4)
                archive.writestr(info, elf(machine))

    def test_valid_bundle_compressed_and_aligned_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            for name, options in (("test.aab", {}), ("aligned.apk", {"aligned": True}),
                                  ("compressed.apk", {"compressed": True})):
                path = Path(directory) / name
                self.archive(path, **options)
                verify_archive(path)

    def test_unaligned_apk_and_missing_abi_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            for name, options in (("unaligned.apk", {}), ("missing.aab", {"missing": True})):
                path = Path(directory) / name
                self.archive(path, **options)
                with self.assertRaises(ValueError):
                    verify_archive(path)


if __name__ == "__main__":
    unittest.main()
