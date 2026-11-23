#!/usr/bin/env bash
# Builds a signed APK and puts it in dist/ together with dist/update.json.
# After this is committed and merged into main, every installed app sees the
# new version (it reads dist/update.json from main) and updates in place.
#
# Usage:
#   MARKET_KEYSTORE_FILE=/path/market-release.jks \
#   MARKET_KEYSTORE_PASSWORD=... MARKET_KEY_ALIAS=market MARKET_KEY_PASSWORD=... \
#   scripts/publish.sh 1.0.2 "ما الجديد في هذا الإصدار" [--mandatory]
# --mandatory: installed apps (1.1.0 and later) cannot be used until they update.
set -euo pipefail

VERSION="${1:?version required, e.g. 1.0.2}"
NOTES="${2:-}"
MANDATORY=false
[ "${3:-}" = "--mandatory" ] && MANDATORY=true
: "${MARKET_KEYSTORE_FILE:?MARKET_KEYSTORE_FILE not set}"

IFS='.' read -r MAJ MIN PAT <<< "$VERSION"
CODE=$(( MAJ * 10000 + MIN * 100 + PAT ))

cd "$(dirname "$0")/.."
./gradlew --no-daemon -q testDebugUnitTest assembleRelease -PversionName="$VERSION" -PversionCode="$CODE"

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/market.apk
SIZE=$(stat -c %s dist/market.apk)

python3 - "$VERSION" "$CODE" "$SIZE" "$NOTES" "$MANDATORY" > dist/update.json <<'EOF'
import json, sys
v, code, size, notes, mandatory = sys.argv[1:6]
print(json.dumps({
    "version": v,
    "versionCode": int(code),
    "mandatory": mandatory == "true",
    "apkUrl": "https://raw.githubusercontent.com/tareqsoft2025/market/main/dist/market.apk",
    "size": int(size),
    "notes": notes,
}, ensure_ascii=False, indent=2))
EOF

echo "Built version $VERSION (code $CODE). Commit dist/ and merge into main to publish."
