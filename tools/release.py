#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Prepare, and with --publish publish, a ribbitOS release that R1s update themselves from.

A release is CI's build of main, never a local one. Preparing signs it into dist/<tag>/; publishing
uploads that folder unchanged, tagged at the commit CI built (docs/UPDATES.md):

    tools/release.py v0.02                      # then test dist/v0.02's APK on an R1
    tools/release.py v0.02 --publish notes.md

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
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PUBLIC_KEY = ROOT / "app" / "src" / "main" / "res" / "raw" / "update_key.pem"
DEFAULT_KEY = Path.home() / ".config" / "ribbitos" / "update-key.pem"
BRANCH = "main"
ARTIFACT = "release-apk"  # .github/workflows/ci.yml uploads it from every push to main.
MANIFEST = "update.json"
SIGNATURE = "update.json.sig"
SUMS = "SHA256SUMS"


def tool(name: str) -> str:
    """Return the full path of a program, or stop if it isn't installed."""
    path = shutil.which(name)
    if path is None:
        sys.exit(f"{name} not found")
    return path


def run(*args: str) -> str:
    """Run a program with fixed arguments (no shell) and return its output, stopping on failure."""
    done = subprocess.run(args, capture_output=True, text=True, check=False)  # noqa: S603 # Arguments are built here, not taken from input.
    if done.returncode != 0:
        sys.exit(f"{Path(args[0]).name} {args[1]}: {(done.stderr or done.stdout).strip()}")
    return done.stdout.strip()


def gh(*args: str) -> str:
    """Run gh against this repository on GitHub and return its output."""
    return run(tool("gh"), *args)


def sha256(path: Path) -> str:
    """Return a file's SHA-256 as hex."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sums(files: list[Path]) -> str:
    """Return SHA256SUMS for these files."""
    return "".join(f"{sha256(f)}  {f.name}\n" for f in files)


def ci_build(into: Path) -> tuple[str, int, str, Path]:
    """Download CI's build of main's tip; return its commit, version code, version name and APK."""
    commit = gh("api", f"repos/{{owner}}/{{repo}}/commits/{BRANCH}", "--jq", ".sha")
    runs = gh(
        *("run", "list", "--workflow", "ci.yml", "--branch", BRANCH, "--commit", commit, "--status", "success"),
        *("--json", "databaseId,event", "--jq", '.[] | select(.event != "pull_request") | .databaseId'),
    )
    if not runs:
        sys.exit(f"CI hasn't passed on {BRANCH}'s tip ({commit}) yet; for a docs-only commit: gh workflow run ci.yml")
    gh("run", "download", runs.split()[0], "--name", ARTIFACT, "--dir", str(into))
    (element,) = json.loads((into / "output-metadata.json").read_text())["elements"]
    return commit, int(element["versionCode"]), str(element["versionName"]), into / element["outputFile"]


def check_new(tag: str, code: int) -> None:
    """Stop unless the tag is unused and R1s on the latest release would update to this versionCode."""
    refs = gh("api", f"repos/{{owner}}/{{repo}}/git/matching-refs/tags/{tag}", "--jq", ".[].ref")
    if f"refs/tags/{tag}" in refs.splitlines():
        sys.exit(f"Tag {tag} already exists: every release gets a new one")
    latest = gh("release", "list", "--json", "tagName,isLatest", "--jq", ".[] | select(.isLatest) | .tagName")
    if latest:
        with tempfile.TemporaryDirectory() as tmp:
            gh("release", "download", latest, "--pattern", MANIFEST, "--dir", tmp)
            latest_code = int(json.loads((Path(tmp) / MANIFEST).read_text())["versionCode"])
        if code <= latest_code:
            sys.exit(f"versionCode {code} isn't above {latest}'s {latest_code}: R1s would ignore it")


def verify(tag: str, out: Path) -> tuple[int, str, list[Path]]:
    """Check a release folder against its signed update.json; return its versionCode, commit and files."""
    openssl = tool("openssl")
    run(openssl, "dgst", "-sha256", "-verify", str(PUBLIC_KEY), "-signature", str(out / SIGNATURE), str(out / MANIFEST))
    manifest = json.loads((out / MANIFEST).read_text())
    files = [out / manifest["apk"], out / MANIFEST, out / SIGNATURE]
    if (
        tag != f"v{manifest['versionName']}"
        or sha256(files[0]) != manifest["sha256"]
        or (out / SUMS).read_text() != sums(files)
    ):
        sys.exit(f"{out} doesn't match its signed {MANIFEST}")
    return int(manifest["versionCode"]), str(manifest["commit"]), [*files, out / SUMS]


def prepare(tag: str, key: Path, dest: Path) -> str:
    """Write CI's build of main's tip, signed, to dest; return the commit it was built from."""
    if dest.exists():
        sys.exit(f"{dest} exists: publish it, or delete it to prepare again")
    with tempfile.TemporaryDirectory() as tmp:
        commit, code, name, apk = ci_build(Path(tmp) / "ci")
        if tag != f"v{name}":
            sys.exit(f"Tag {tag} doesn't match the build's version name {name}")
        check_new(tag, code)
        out = Path(tmp) / tag
        out.mkdir()
        apk_copy = out / f"ribbitos-lite-{tag}.apk"
        shutil.copyfile(apk, apk_copy)
        manifest = {"versionCode": code, "versionName": name, "apk": apk_copy.name, "sha256": sha256(apk_copy)}
        (out / MANIFEST).write_text(json.dumps({**manifest, "commit": commit}, separators=(",", ":")))
        openssl = tool("openssl")
        run(openssl, "dgst", "-sha256", "-sign", str(key), "-out", str(out / SIGNATURE), str(out / MANIFEST))
        (out / SUMS).write_text(sums([apk_copy, out / MANIFEST, out / SIGNATURE]))
        verify(tag, out)  # The app's own check, against the public key it ships with.
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(out, dest)
    return commit


def publish(tag: str, folder: Path, notes: Path) -> None:
    """Publish a prepared release folder unchanged, tagged at the commit CI built it from."""
    if not folder.is_dir():
        sys.exit(f"Prepare {tag} first: tools/release.py {tag}")
    code, commit, files = verify(tag, folder)
    check_new(tag, code)
    title = f"ribbitOS {tag}"
    gh("release", "create", tag, *map(str, files), "--target", commit, "--title", title, "--notes-file", str(notes))


def main() -> None:
    """Prepare the release, or publish a prepared one."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("tag", help="the release tag: v + the build's version name, e.g. v0.02")
    parser.add_argument("--publish", metavar="NOTES", type=Path, help="publish with these release notes (Markdown)")
    args = parser.parse_args()
    folder = ROOT / "dist" / args.tag
    if args.publish is not None:
        publish(args.tag, folder, args.publish)
        print(f"Published {args.tag}: R1s pick it up at their next idle check")
        return
    key = Path(os.environ.get("RIBBITOS_UPDATE_KEY", str(DEFAULT_KEY)))
    if not key.is_file():
        sys.exit(f"No release key at {key} (docs/UPDATES.md)")
    commit = prepare(args.tag, key, folder)
    print(f"Prepared {folder} from CI's build of {commit}. Test its APK on an R1, then publish it with --publish")


if __name__ == "__main__":
    main()
