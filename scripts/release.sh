#!/usr/bin/env bash
# Builds a release APK exactly the way F-Droid does (same buildserver container, same fdroid
# build command), then signs it with your release key. Because Toto uses reproducible builds,
# F-Droid rebuilds from source, checks its APK matches this one byte for byte (minus the
# signature), and then ships your signed APK.
#
# Usage: scripts/release.sh <versionCode>
#
# Environment (all optional):
#   FDROIDDATA    fdroiddata checkout with this app's metadata   (default: ~/fdroiddata)
#   FDROIDSERVER  fdroidserver checkout                           (default: ~/fdroidserver)
#   KEYSTORE      release keystore                                (default: ~/keys/toto-release.jks)
#   KEY_ALIAS     key alias in the keystore                       (default: toto)
#   BUILD_TOOLS   Android build-tools dir with apksigner          (default: newest in ~/Library/Android/sdk)
#
# The metadata's Builds entry for <versionCode> must point at the release commit, and must be
# committed in fdroiddata (fdroidserver derives SOURCE_DATE_EPOCH from git history).
set -euo pipefail

APP_ID=codes.shandon.toto
IMAGE=registry.gitlab.com/fdroid/fdroidserver:buildserver

VERCODE=${1:?usage: scripts/release.sh <versionCode>}
FDROIDDATA=${FDROIDDATA:-$HOME/fdroiddata}
FDROIDSERVER=${FDROIDSERVER:-$HOME/fdroidserver}
KEYSTORE=${KEYSTORE:-$HOME/keys/toto-release.jks}
KEY_ALIAS=${KEY_ALIAS:-toto}
BUILD_TOOLS=${BUILD_TOOLS:-$(ls -d "$HOME"/Library/Android/sdk/build-tools/* | sort -V | tail -1)}
REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)

[ -f "$FDROIDDATA/metadata/$APP_ID.yml" ] || { echo "No metadata at $FDROIDDATA/metadata/$APP_ID.yml" >&2; exit 1; }
[ -f "$KEYSTORE" ] || { echo "No keystore at $KEYSTORE" >&2; exit 1; }
if [ -n "$(git -C "$FDROIDDATA" status --porcelain -- "metadata/$APP_ID.yml")" ]; then
    echo "Commit metadata/$APP_ID.yml in $FDROIDDATA first (fdroid build needs it in git history)." >&2
    exit 1
fi

# versionName comes right before versionCode in each Builds entry
VERSION=$(grep -B1 -E "versionCode: $VERCODE\$" "$FDROIDDATA/metadata/$APP_ID.yml" \
    | sed -nE "s/.*versionName: '?([^']*)'?\$/\\1/p" || true)
[ -n "$VERSION" ] || { echo "No Builds entry with versionCode $VERCODE in the metadata" >&2; exit 1; }

echo ">> Building $APP_ID $VERSION ($VERCODE) in the F-Droid buildserver container"
rm -f "$FDROIDDATA/unsigned/${APP_ID}_${VERCODE}.apk"
docker run --rm -u vagrant --entrypoint /bin/bash \
    -v "$FDROIDDATA":/build:z \
    -v "$FDROIDSERVER":/home/vagrant/fdroidserver:Z \
    "$IMAGE" -c "
        set -e
        . /etc/profile
        export PATH=\"\$HOME/fdroidserver:\$PATH\" PYTHONPATH=\"\$HOME/fdroidserver\"
        export JAVA_HOME=\$(java -XshowSettings:properties -version 2>&1 >/dev/null | grep java.home | awk -F= '{print \$2}' | tr -d ' ')
        cd /build
        fdroid build --verbose $APP_ID:$VERCODE"

UNSIGNED="$FDROIDDATA/unsigned/${APP_ID}_${VERCODE}.apk"
[ -f "$UNSIGNED" ] || { echo "Build produced no APK at $UNSIGNED" >&2; exit 1; }

OUT_DIR="$REPO_ROOT/release"
OUT="$OUT_DIR/Toto-$VERSION.apk"
mkdir -p "$OUT_DIR"
echo ">> Signing with $KEYSTORE ($KEY_ALIAS). apksigner will ask for the password."
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --out "$OUT" "$UNSIGNED"
"$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" | grep -E 'SHA-256'

echo
echo ">> Done: $OUT"
echo "   Attach it to the GitHub release for tag v$VERSION, named exactly Toto-$VERSION.apk"
