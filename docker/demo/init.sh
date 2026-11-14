#!/bin/sh
# Generates throwaway DSC / mTLS key material and a matching mock WHO trust list for the compose demo.
# NOT for production: the key is created on first start and stored in a Docker volume.
set -eu
KID="${DEMO_KID:-demo-kid}"
COUNTRY="${DEMO_COUNTRY:-XM}"

if [ -f /secrets/key.pem ]; then
  echo "demo key material already present, keeping it"
  exit 0
fi

openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out /secrets/key.pem
openssl req -new -x509 -key /secrets/key.pem -subj "/CN=GDHCN demo DSC/C=${COUNTRY}" -days 365 -out /secrets/cert.pem
CERT=$(openssl x509 -in /secrets/cert.pem -outform DER | base64 -w0)

mkdir -p /tng/trustList
printf '[{"uuid":"demo","timestamp":"2024-01-01T00:00:00Z","country":"%s","kid":"%s","certificateType":"DSC","version":1,"domain":"DCC","certificate":"%s","group":"DSC"}]' \
  "$COUNTRY" "$KID" "$CERT" > /tng/trustList/certificate

# the app runs as a non-root user and must be able to read the mounted files
chmod 755 /secrets /tng /tng/trustList
chmod 644 /secrets/* /tng/trustList/certificate
echo "generated demo key material (kid=${KID}, country=${COUNTRY})"
