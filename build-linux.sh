#!/usr/bin/env bash
set -euo pipefail
script_path=$(readlink -f -- "${BASH_SOURCE[0]}")
root=$(dirname -- "$script_path")
exec python3 "$root/tools/build-linux.py" "$@"
