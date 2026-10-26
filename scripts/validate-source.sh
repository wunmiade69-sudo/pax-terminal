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
if 'versionName = "0.4.2"' not in (root / 'app/build.gradle.kts').read_text():
    raise SystemExit('App version does not match v0.4.2')
workflow = (root / '.github/workflows/build-apk.yml').read_text()
if '2.4.3' not in workflow or 'Could not pin the talloc build dependency' not in workflow:
    raise SystemExit('Missing talloc/Waf Python 3 compatibility fix')
if "sed -i 's/else API=21/else API=26/' config" not in workflow:
    raise SystemExit('Missing native API 26 fix for telldir/seekdir')
if 'Could not set the PRoot native build API to 26' not in workflow:
    raise SystemExit('Missing native API baseline guard')
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
