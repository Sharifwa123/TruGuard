#!/usr/bin/env bash
# Issues an ad-free supporter code:   tools/license/issue.sh "Ama Mensah"
# The name is just a label so you can remember who the code was for; it is not shown to anyone else.
set -euo pipefail
cd "$(dirname "$0")/../.."
[ -e supporter_private.pem ] || { echo "No supporter_private.pem: run tools/license/keygen.sh first." >&2; exit 1; }
holder="${1:?usage: issue.sh \"Holder name\"}"
b64url() { base64 -w0 | tr '+/' '-_' | tr -d '='; }
payload="TG1|${holder//|/ }|$(date +%F)"
sig=$(printf %s "$payload" | openssl dgst -sha256 -sign supporter_private.pem | b64url)
echo "TG1.$(printf %s "$payload" | b64url).${sig}"
