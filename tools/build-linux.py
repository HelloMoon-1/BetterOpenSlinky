#!/usr/bin/env python3
"""Offline by default: verify pinned dependencies, build shared Java, then Linux native glue."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def call(*args):
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True)


def quote_arg(value):
    return '"' + str(value).replace('\\', '\\\\').replace('"', '\\"') + '"'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--restore-dependencies', action='store_true',
                        help='Download only missing/invalid pinned dependencies using HTTPS')
    parser.add_argument('--java-only', action='store_true', help='Build Java payloads and launcher only')
    options = parser.parse_args()
    if platform.system() != 'Linux':
        parser.error('Run this build on Linux (WSL also works).')
    javac_path = Path(os.environ['JAVA_HOME']) / 'bin/javac' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or '')
    if not javac_path.is_file():
        parser.error('A full JDK 11 or newer is required; set JAVA_HOME or put javac on PATH.')
    java_home = javac_path.resolve().parent.parent
    javac, java, jar = [java_home / 'bin' / name for name in ('javac', 'java', 'jar')]
    os.environ['JAVA_HOME'] = str(java_home)
    artifacts = json.loads((ROOT / 'deps/download-manifest.json').read_text())
    for artifact in artifacts:
        destination = (ROOT / 'deps' / artifact['path']).resolve()
        if ROOT / 'deps' not in destination.parents:
            raise RuntimeError('Dependency path escapes deps directory')
        if destination.is_file() and hashlib.sha256(destination.read_bytes()).hexdigest() == artifact['sha256']:
            continue
        if not options.restore_dependencies:
            raise RuntimeError('Missing or invalid pinned dependency: ' + artifact['path'] +
                               '; use --restore-dependencies to download it.')
        if not artifact['url'].startswith('https://'):
            raise RuntimeError('Dependency URL must use HTTPS')
        destination.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as temporary:
            temporary_path = Path(temporary.name)
        try:
            urllib.request.urlretrieve(artifact['url'], temporary_path)
            if hashlib.sha256(temporary_path.read_bytes()).hexdigest() != artifact['sha256']:
                raise RuntimeError('SHA-256 mismatch: ' + artifact['path'])
            temporary_path.replace(destination)
        finally:
            temporary_path.unlink(missing_ok=True)
    print('Verified {} pinned dependency files.'.format(len(artifacts)), flush=True)
    build = ROOT / 'build/linux'
    build.mkdir(parents=True, exist_ok=True)
    tooling = build / 'tools'
    tooling.mkdir(exist_ok=True)
    tool_classpath = str(tooling) + ':' + str(ROOT / 'deps/tooling/*')
    call(javac, '--release', '8', '-encoding', 'UTF-8', '-cp', ROOT / 'deps/tooling/*',
         '-d', tooling, ROOT / 'tools/RemapJar.java')
    named = build / 'minecraft-1.8.9-named.jar'
    call(java, '-cp', tool_classpath, 'RemapJar', ROOT / 'mappings/named-to-official.txt',
         'reverse', ROOT / 'deps/minecraft-1.8.9-official.jar', named, '--mcp-exceptions')
    classes = build / 'classes'
    if classes.exists():
        # The exact fixed output path is resolved inside this project before removal.
        if build.resolve() not in classes.resolve().parents:
            raise RuntimeError('Unexpected classes output path')
        shutil.rmtree(classes)
    classes.mkdir()
    sources = sorted((ROOT / 'java/src/main/java').rglob('*.java'))
    if not sources:
        raise RuntimeError('No Java sources found')
    classpath = ':'.join(str(path) for path in [named] + sorted((ROOT / 'deps/libraries').glob('*.jar')))
    arguments = ['--release', '8', '-encoding', 'UTF-8', '-classpath', quote_arg(classpath),
                 '-d', quote_arg(classes)] + [quote_arg(source) for source in sources]
    argfile = build / 'javac-sources.args'
    argfile.write_text('\n'.join(arguments) + '\n', encoding='utf-8')
    print('Compiling the shared Java payload (Java 8 format).', flush=True)
    call(javac, '@' + str(argfile))
    manifest = build / 'MANIFEST.MF'
    manifest.write_text('Manifest-Version: 1.0\nImplementation-Title: Slinky injectable payload\n'
                        'Implementation-Version: 0.5.0-settings\n\n', encoding='utf-8')
    call(jar, 'cfm', build / 'payload-101.jar', manifest, '-C', classes, '.',
         '-C', ROOT / 'java/src/main/resources', '.')
    for identifier, mapping in ((102, 'srg'), (103, 'official')):
        payload = build / ('payload-{}.jar'.format(identifier))
        call(java, '-cp', tool_classpath, 'RemapJar', ROOT / ('mappings/named-to-' + mapping + '.txt'),
             'forward', build / 'payload-101.jar', payload, named)
        mapping_root = build / ('mapping-{}'.format(identifier))
        destination = mapping_root / 'dev/slinky/inject/mappings.txt'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / ('java/mappings/' + mapping + '.txt'), destination)
        # RemapJar removes the input manifest, so restore it together with runtime mappings.
        call(jar, 'ufm', payload, manifest, '-C', mapping_root, 'dev/slinky/inject/mappings.txt')
    launcher_classes = build / 'launcher-classes'
    launcher_classes.mkdir(exist_ok=True)
    call(javac, '--release', '11', '--add-modules', 'jdk.attach', '-encoding', 'UTF-8',
         '-d', launcher_classes, ROOT / 'linux/SlinkyLauncher.java')
    launcher_manifest = build / 'LAUNCHER.MF'
    launcher_manifest.write_text('Manifest-Version: 1.0\nMain-Class: SlinkyLauncher\n\n', encoding='utf-8')
    call(jar, 'cfm', build / 'slinky-launcher.jar', launcher_manifest, '-C', launcher_classes, '.')
    machine = platform.machine()
    if machine not in ('x86_64', 'aarch64'):
        raise RuntimeError('Unsupported Linux architecture: ' + machine)
    distribution = ROOT / 'dist' / ('linux-' + machine)
    distribution.mkdir(parents=True, exist_ok=True)
    for name in ('payload-101.jar', 'payload-102.jar', 'payload-103.jar', 'slinky-launcher.jar'):
        shutil.copyfile(build / name, distribution / name)
    shutil.copyfile(ROOT / 'linux/slinky', distribution / 'slinky')
    (distribution / 'slinky').chmod(0o755)
    if not options.java_only:
        call('cmake', '-S', ROOT / 'native', '-B', build / 'native', '-DCMAKE_BUILD_TYPE=Release',
             '-DPAYLOAD_DIR=' + str(build), '-DJAVA_HOME=' + str(java_home))
        call('cmake', '--build', build / 'native', '--parallel', '2')
        shutil.copyfile(build / 'native/libSlinkyNative.so', distribution / 'libSlinkyNative.so')
    print('Build complete: ' + str(distribution), flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit('Build failed: ' + str(error))
