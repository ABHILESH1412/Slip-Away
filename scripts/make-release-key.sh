#!/usr/bin/env bash
# Creates Slip Away's release signing key. Run it ONCE; every future release must use the same key,
# or phones refuse to install the update over the old version.
#
#   bash scripts/make-release-key.sh            # creates ~/slipaway-release.jks
#   bash scripts/make-release-key.sh other.jks
#
# keytool asks for a password; you type it, nothing here stores it.
set -euo pipefail
KEY="${1:-$HOME/slipaway-release.jks}"
if [ -e "$KEY" ]; then
    echo "$KEY already exists. Keep using it (and keep a backup)."
    exit 1
fi
KEYTOOL="${JAVA_HOME:-/opt/android-studio/jbr}/bin/keytool"
[ -x "$KEYTOOL" ] || KEYTOOL=keytool

"$KEYTOOL" -genkeypair -keystore "$KEY" -storetype PKCS12 -alias slipaway \
    -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Slip Away"

cat <<MSG

Created $KEY
  1. Back it up (password manager / cloud drive). Lose it and you can never update Slip Away on phones.
  2. Add two secrets to the GitHub repo (Settings -> Secrets and variables -> Actions -> New secret):
       SLIPAWAY_KEYSTORE_BASE64   the output of:  base64 -w0 "$KEY"
       SLIPAWAY_STORE_PASSWORD    the password you just typed
     Or with the GitHub CLI (it asks for the password):
       base64 -w0 "$KEY" | gh secret set SLIPAWAY_KEYSTORE_BASE64
       gh secret set SLIPAWAY_STORE_PASSWORD
MSG
