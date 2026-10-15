BetterOpenSlink
Minecraft 1.8.9 client source for Windows x64 and Linux x86_64.

GitHub Actions: Build Slinky builds both platforms on pushes to this repository.
Download the platform artifact from the corresponding workflow run.
Windows artifacts contain Slinky.exe, README.txt and BUILD-INFO.json.
Linux artifacts retain their launcher, native library and required payload JARs.

Build requirements: JDK 17, Python 3.12, CMake 3.24+, and a C++ toolchain.
Windows: ./build.ps1 -RestoreDependencies -Generator 'Visual Studio 17 2022'
Linux: python3 -I tools/build-linux.py --restore-dependencies

Pinned dependencies are restored from deps/download-manifest.json.
Payloads target Java 8. The Linux launcher requires JDK 11 or newer.
Compilation does not establish game or injection compatibility.
