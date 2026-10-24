#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Prepare, and with --publish publish, a ribbitOS release that R1s update themselves from.

Takes the release build in app/build/outputs/apk/release and writes dist/<tag>/: the APK as
ribbitos-lite-<tag>.apk, update.json (version code, APK name, SHA-256), update.json.sig (the
release key's signature, docs/UPDATES.md) and SHA256SUMS. Device-test the build first
(docs/RULES.md rule 5):

    ./gradlew check assembleRelease
    tools/release.py v0.01                      # prepare and check dist/v0.01
    tools/release.py v0.01 --publish notes.md   # and publish it as the latest GitHub release

The release key's private half lives outside the repository: $RIBBITOS_UPDATE_KEY, by default
~/.config/ribbitos/update-key.pem.
"""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "app" / "build" / "outputs" / "apk" / "release"
PUBLIC_KEY = ROOT / "app" / "src" / "main" / "res" / "raw" / "update_key.pem"
DEFAULT_KEY = Path.home() / ".config" / "ribbitos" / "update-key.pem"
MANIFEST = "update.json"
SIGNATURE = "update.json.sig"
SUMS = "SHA256SUMS"


def tool(name: str) -> str:
    """Return the full path of a program, or stop if it isn't installed."""
    path = shutil.which(name)
    if path is None:
        sys.exit(f"{name} not found")
    return path


def run(*args: str) -> None:
    """Run a program with fixed arguments (no shell), stopping on failure."""
    subprocess.run(args, check=True, capture_output=True)  # noqa: S603 # Arguments are built here, not taken from input.


def build() -> tuple[int, str, Path]:
    """Return the release build's version code, version name and APK."""
    meta = json.loads((BUILD / "output-metadata.json").read_text())
    (element,) = meta["elements"]
    return int(element["versionCode"]), str(element["versionName"]), BUILD / element["outputFile"]


def sha256(path: Path) -> str:
    """Return a file's SHA-256 as hex."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(tag: str, key: Path) -> list[Path]:
    """Write dist/<tag>/ and return its files, APK first."""
    code, name, apk = build()
    if tag != f"v{name}":
        sys.exit(f"Tag {tag} doesn't match the build's version name {name}")
    out = ROOT / "dist" / tag
    out.mkdir(parents=True, exist_ok=True)
    apk_copy = out / f"ribbitos-lite-{tag}.apk"
    shutil.copyfile(apk, apk_copy)
    manifest = {"versionCode": code, "versionName": name, "apk": apk_copy.name, "sha256": sha256(apk_copy)}
    (out / MANIFEST).write_text(json.dumps(manifest, separators=(",", ":")))
    openssl = tool("openssl")
    run(openssl, "dgst", "-sha256", "-sign", str(key), "-out", str(out / SIGNATURE), str(out / MANIFEST))
    # The same check the app makes, against the public key it ships with.
    run(openssl, "dgst", "-sha256", "-verify", str(PUBLIC_KEY), "-signature", str(out / SIGNATURE), str(out / MANIFEST))
    files = [apk_copy, out / MANIFEST, out / SIGNATURE]
    (out / SUMS).write_text("".join(f"{sha256(f)}  {f.name}\n" for f in files))
    return [*files, out / SUMS]


def main() -> None:
    """Prepare the release, then publish it if asked."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("tag", help="the release tag: v + the build's version name, e.g. v0.01")
    parser.add_argument("--publish", metavar="NOTES", type=Path, help="publish with these release notes (Markdown)")
    args = parser.parse_args()
    key = Path(os.environ.get("RIBBITOS_UPDATE_KEY", str(DEFAULT_KEY)))
    if not key.is_file():
        sys.exit(f"No release key at {key} (docs/UPDATES.md)")
    files = prepare(args.tag, key)
    print(f"Prepared {files[0].parent}, signature checked")
    if args.publish is not None:
        gh = tool("gh")
        title, notes = f"ribbitOS {args.tag}", str(args.publish)
        run(gh, "release", "create", args.tag, *map(str, files), "--title", title, "--notes-file", notes)
        print(f"Published {args.tag}: R1s pick it up at their next idle check")


if __name__ == "__main__":
    main()
