#!/usr/bin/env bash
# Writes local.properties (API keys + signing) for a Nuvio checkout in the current directory.
# Usage: write-properties.sh <keystore-out-path>. Exports KEYSTORE/KEYPASS/KEYALIAS to $GITHUB_ENV.
set -euo pipefail
keystore="$1"
: > local.properties
for name in NUVIO_SUPABASE_URL NUVIO_SUPABASE_ANON_KEY NUVIO_SUPABASE_FALLBACK_URL TMDB_API_KEY \
  TRAKT_CLIENT_ID TRAKT_CLIENT_SECRET SIMKL_CLIENT_ID MDBLIST_CLIENT_ID PREMIUMIZE_CLIENT_ID \
  INTRODB_API_URL IMDB_RATINGS_API_BASE_URL IMDB_TAPFRAME_API_BASE_URL PARENTAL_GUIDE_API_URL TRAILER_API_URL; do
  value="${!name:-}"
  if [ -n "$value" ]; then
    printf '%s=%s\n' "$name" "$value" >> local.properties
    echo "$name: set"
  else
    echo "::notice::$name is not set (the matching feature stays off in this build)"
  fi
done
if [ -z "${NUVIO_SUPABASE_URL:-}" ] || [ -z "${NUVIO_SUPABASE_ANON_KEY:-}" ]; then
  sed -i '/^NUVIO_SUPABASE_URL=/d;/^NUVIO_SUPABASE_ANON_KEY=/d' local.properties
  printf 'NUVIO_SUPABASE_URL=%s\n' "https://api.nuvio.tv" >> local.properties
  printf 'NUVIO_SUPABASE_ANON_KEY=%s\n' "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiwiaWF0IjoxNzgxNTIxMzQ2LCJleHAiOjE5MzkyMDEzNDZ9.tmQaj682pwzehpqlgCDMnySOqiUvpgRbrE43T4VJpDI" >> local.properties
fi
if [ -n "${NUVIO_FACE_KEYSTORE_B64:-}" ]; then
  printf '%s' "$NUVIO_FACE_KEYSTORE_B64" | base64 --decode > "$keystore"; pass=nuvioface; alias=nuvioface
elif [ -n "${NUVIO_AUTO_KEYSTORE_B64:-}" ]; then
  printf '%s' "$NUVIO_AUTO_KEYSTORE_B64" | base64 --decode > "$keystore"; pass=nuvioauto; alias=nuvioauto
else
  echo "::warning::No signing key secret: this build has a one-off key and won't update over the last one."
  pass=nuvioface; alias=nuvioface
  keytool -genkeypair -keystore "$keystore" -storetype PKCS12 -storepass $pass -keypass $pass \
    -alias $alias -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Nuvio Face"
fi
{
  printf 'NUVIO_RELEASE_STORE_FILE=%s\n' "$keystore"
  printf 'NUVIO_RELEASE_STORE_PASSWORD=%s\n' "$pass"
  printf 'NUVIO_RELEASE_KEY_ALIAS=%s\n' "$alias"
  printf 'NUVIO_RELEASE_KEY_PASSWORD=%s\n' "$pass"
} >> local.properties
if [ -n "${GITHUB_ENV:-}" ]; then
  { echo "KEYSTORE=$keystore"; echo "KEYPASS=$pass"; echo "KEYALIAS=$alias"; } >> "$GITHUB_ENV"
fi
