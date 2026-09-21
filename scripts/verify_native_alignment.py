#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check 64-bit ELF load alignment and uncompressed APK library ZIP alignment."""
from pathlib import Path
import struct
import sys
import zipfile

PAGE_SIZE = 16384
MACHINES = {"arm64-v8a": 183, "x86_64": 62}


def verify_elf(data: bytes, machine: int) -> None:
    if len(data) < 64 or data[:7] != b"\x7fELF\x02\x01\x01":
        raise ValueError("Expected little-endian ELF64")
    header = struct.unpack_from("<16sHHIQQQIHHHHHH", data)
    if header[1] != 3 or header[2] != machine:
        raise ValueError("Wrong ELF type or ABI")
    offset, size, count = header[5], header[9], header[10]
    if offset < 64 or size != 56 or count == 0 or offset + count * size > len(data):
        raise ValueError("Invalid ELF program header table")
    loads = 0
    for index in range(count):
        kind, _, file_offset, address, _, file_size, memory_size, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, offset + index * size)
        if kind != 1:
            continue
        loads += 1
        if (alignment < PAGE_SIZE or alignment & (alignment - 1)
                or file_offset % PAGE_SIZE != address % PAGE_SIZE):
            raise ValueError("ELF PT_LOAD is not 16 KB aligned")
        if file_size > memory_size or file_offset + file_size > len(data):
            raise ValueError("ELF PT_LOAD exceeds library bounds")
    if loads == 0:
        raise ValueError("ELF has no loadable segments")


def verify_archive(path: Path) -> None:
    is_apk = path.suffix == ".apk"
    prefix = "lib/" if is_apk else "base/lib/"
    found = set()
    with zipfile.ZipFile(path) as archive, path.open("rb") as raw:
        for entry in archive.infolist():
            if not entry.filename.startswith(prefix) or not entry.filename.endswith(".so"):
                continue
            abi = entry.filename[len(prefix):].split("/")[0]
            if abi not in MACHINES:
                continue
            found.add(abi)
            verify_elf(archive.read(entry), MACHINES[abi])
            if is_apk and entry.compress_type == zipfile.ZIP_STORED:
                raw.seek(entry.header_offset)
                header = raw.read(30)
                if len(header) != 30 or header[:4] != b"PK\x03\x04":
                    raise ValueError("Invalid ZIP local header")
                name_size, extra_size = struct.unpack_from("<HH", header, 26)
                if (entry.header_offset + 30 + name_size + extra_size) % PAGE_SIZE:
                    raise ValueError("Uncompressed APK library is not ZIP-aligned to 16 KB")
    if found != set(MACHINES):
        raise ValueError("Missing arm64-v8a or x86_64 libraries")


if __name__ == "__main__":
    try:
        if len(sys.argv) < 2:
            raise ValueError("Usage: verify_native_alignment.py APK_OR_AAB [...]")
        for argument in sys.argv[1:]:
            verify_archive(Path(argument))
            print(f"Verified 16 KB ELF/APK alignment: {argument}")
    except (ValueError, OSError, struct.error, zipfile.BadZipFile) as error:
        sys.exit(f"Native alignment verification failed: {error}")
