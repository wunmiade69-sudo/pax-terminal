#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path('.')
ET.parse(root / 'app/src/main/AndroidManifest.xml')
for p in (root / 'app/src/main/res').rglob('*.xml'):
    ET.parse(p)
main = (root / 'app/src/main/java/com/paxterminal/app/MainActivity.java').read_text()
required = [
    'ROOTFS_ASSET', 'bootstrapLinuxEnvironment', 'pkgWrapperScript',
    'libproot-userland.so', 'npm run dev', 'pnpm dev', 'deno task dev', 'bun run dev', 'npx tsc --noEmit', 'pax-bg', 'pax-jobs', 'pax-logs', 'pax-stop',
    'ACCESS_NETWORK_STATE',
]
for token in required:
    if token == 'ACCESS_NETWORK_STATE':
        manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()
        if token not in manifest:
            raise SystemExit(f'Missing manifest permission: {token}')
    elif token not in main:
        raise SystemExit(f'Missing expected terminal implementation token: {token}')
if 'versionName = "0.4.0"' not in (root / 'app/build.gradle.kts').read_text():
    raise SystemExit('App version does not match v0.4.0')
dockerfile = (root / 'runtime/Dockerfile.debian').read_text()
for token in ('pnpm', 'typescript', 'tsx', 'deno', 'bun', 'python-is-python3'):
    if token not in dockerfile:
        raise SystemExit(f'Missing runtime package or setup: {token}')
print('Source validation passed: manifest/resources, terminal commands, runtime setup, version.')
PY

for file in \
  app/src/main/java/com/paxterminal/app/MainActivity.java \
  app/src/main/AndroidManifest.xml \
  app/build.gradle.kts \
  .github/workflows/build-apk.yml \
  runtime/Dockerfile.debian; do
  test -s "$file"
done

bash -n "$0"
echo "PAX Terminal source checks passed."
