Dependency preparation
======================
download-manifest.json pins the exact downloaded files by HTTPS URL and SHA-256.
Mojang artifacts additionally retain SHA-1 values from the installed official
Minecraft 1.8.9 version manifest. tools/download-deps.ps1 can restore all inputs.
No Minecraft, payload, launcher, DLL, or native library is run during preparation.

minecraft-1.8.9-official.jar is Mojang's original client, used as compile metadata.
minecraft-1.8.9-named.jar is derived statically using the inverse embedded
named-to-official mapping. It contains the same game implementations with renamed
classes and members. It is not distributed as part of the reconstructed payload.
libraries/*.jar are the original 1.8.9 Java dependencies. The runtime normally
provides these libraries; they must not be repackaged into Slinky's payload JARs.
tooling/asm{,-commons,-tree}-9.6.jar are independent build tools from Maven Central.
The build tools use those ASM classes, never the sample's own shaded ASM classes.

Mapping coverage: 2,516 class mappings, 8,843 field mappings, 9,370 method mappings.
All 2,507 classes present in the official client were renamed to net/minecraft;
the remaining mapping classes cover server-only material absent from this JAR.
RemapJar reads inheritance from class-file metadata and resolves member renaming
through superclasses and interfaces. No Class.forName or class loading is used.
Literal strings remain unchanged, as Slinky's runtime Mappings helper is designed
to map class/member/descriptor strings when hooks and reflection need them.

CLI:
javac --release 8 -cp "deps/tooling/*" -d build/tools tools/RemapJar.java
java -cp "build/tools;deps/tooling/*" RemapJar mappings/named-to-official.txt reverse deps/minecraft-1.8.9-official.jar deps/minecraft-1.8.9-named.jar --mcp-exceptions
java -cp "build/tools;deps/tooling/*" RemapJar mappings/named-to-srg.txt forward build/payload101.jar build/payload102.jar deps/minecraft-1.8.9-named.jar
java -cp "build/tools;deps/tooling/*" RemapJar mappings/named-to-official.txt forward build/payload101.jar build/payload103.jar deps/minecraft-1.8.9-named.jar

Each output variant must receive its matching dev/slinky/inject/mappings.txt
resource after remapping. The named variant retains its original resource state.

--mcp-exceptions adds IOException declaration metadata to five GuiScreen methods
to match MCP's development compile API. Mojang omits those Exceptions attributes
in its shipping class files. This changes only compilation metadata in the
dependency JAR; game implementations remain unchanged, and this JAR is never
packaged as a payload or launched. Do not pass the flag on the payload remap.
