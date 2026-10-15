"""Validate and package fresh CI outputs. Never load or execute a payload or launcher."""
import argparse
import hashlib
import json
import os
import shutil
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=("windows-x64", "linux-x86_64"))
    options = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    windows = options.platform == "windows-x64"
    build = root / ("build" if windows else "build/linux")
    files = [(build / ("payload-%s.jar" % identifier), "payload-%s.jar" % identifier) for identifier in (101, 102, 103)]
    if windows:
        files += [(build / "native/Release/Slinky.exe", "Slinky.exe"), (build / "native/Release/SlinkyNative.dll", "SlinkyNative.dll")]
    else:
        files += [(build / "slinky-launcher.jar", "slinky-launcher.jar"),
                  (build / "native/libSlinkyNative.so", "libSlinkyNative.so"), (root / "linux/slinky", "slinky")]
    for source, name in files:
        if not source.is_file() or source.stat().st_size == 0:
            raise SystemExit("Missing or empty build output: " + str(source))
        if source.suffix == ".jar":
            with zipfile.ZipFile(source) as archive:
                invalid = archive.testzip()
                if invalid:
                    raise SystemExit("Invalid JAR entry: " + invalid)
                if "META-INF/MANIFEST.MF" not in archive.namelist():
                    raise SystemExit("JAR is missing its manifest: " + name)
        elif source.suffix.lower() in (".exe", ".dll", ".so"):
            expected = b"MZ" if windows else b"\x7fELF"
            with source.open("rb") as stream:
                if stream.read(len(expected)) != expected:
                    raise SystemExit("Unexpected native file format: " + name)
    output = root / "artifacts" / options.platform
    if output.exists():
        raise SystemExit("Refusing a non-fresh artifact directory: " + str(output))
    output.mkdir(parents=True)
    manifest = []
    # Windows embeds the DLL in the EXE and all payload JARs in the DLL.
    # Validate those fresh build inputs above, but do not distribute duplicates.
    packaged = [(source, name) for source, name in files if not windows or name == "Slinky.exe"]
    for source, name in packaged:
        destination = output / name
        shutil.copy2(source, destination)
        if name == "slinky":
            destination.chmod(0o755)
        manifest.append({"file": name, "bytes": destination.stat().st_size,
                         "sha256": hashlib.sha256(destination.read_bytes()).hexdigest()})
    info = {"platform": options.platform, "commit": os.environ.get("GITHUB_SHA", "unknown"),
            "run": os.environ.get("GITHUB_SERVER_URL", "https://github.com") + "/" + os.environ.get("GITHUB_REPOSITORY", "")
                   + "/actions/runs/" + os.environ.get("GITHUB_RUN_ID", ""),
            "validation": "Compiled and archive/header checked only; no game, payload, launcher or injection executed.",
            "files": manifest}
    (output / "BUILD-INFO.json").write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    (output / "README.txt").write_text(
        "Slinky build artifacts for " + options.platform + "\n"
        "See BUILD-INFO.json for the exact commit, run and SHA-256 hashes.\n"
        "Only freshly compiled outputs are included; no historical dist files.\n"
        + ("Windows: Slinky.exe embeds the native DLL and all Java payloads.\n"
           "No separate DLL or JAR files are needed alongside the EXE.\n" if windows else
           "Linux: keep slinky, slinky-launcher.jar, libSlinkyNative.so and all three payload JARs together.\n"
           "These JARs are runtime dependencies, not optional build intermediates.\n"
           "The Linux launcher requires JDK 11 or newer. Run: bash ./slinky\n")
        + "Java payloads target Java 8.\n"
        "Compilation is not a runtime/game compatibility test.\n", encoding="utf-8")
    print(json.dumps(info, indent=2))


if __name__ == "__main__":
    main()
