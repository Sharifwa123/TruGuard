#!/usr/bin/env bash
# One-time: creates your signing key pair for ad-free supporter codes.
#   supporter_private.pem   KEEP SECRET, NEVER COMMIT. Whoever has it can issue codes. Back it up somewhere safe.
#   app/src/main/assets/supporter_public_key.txt   safe to commit; the app uses it to check codes.
# Needs only openssl.
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ -e supporter_private.pem ]; then echo "supporter_private.pem already exists; refusing to overwrite it." >&2; exit 1; fi
openssl ecparam -name prime256v1 -genkey -noout -out supporter_private.pem
chmod 600 supporter_private.pem
openssl ec -in supporter_private.pem -pubout -outform DER 2>/dev/null | base64 -w0 > app/src/main/assets/supporter_public_key.txt
echo "Created supporter_private.pem (keep it secret) and app/src/main/assets/supporter_public_key.txt."
echo "Commit the public key, rebuild and release. Codes issued with issue.sh will then unlock ad-free mode."
