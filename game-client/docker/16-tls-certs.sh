#!/bin/sh
# Certificado TLS del cliente: Chrome exige secure context para WebRTC
# (no arranca en http://IP).
#   TLS_CERT=self  autofirmado (default). El browser va a pedir aceptar el riesgo.
#   TLS_CERT=acme  Let's Encrypt para PUBLIC_HOST. Con IP pública solo existe el
#                  perfil shortlived (6 días): lego valida por HTTP-01 en :80 y
#                  renueva a mitad de vida; acá se instala y se recarga nginx.
set -e
CERT_DIR=/etc/nginx/certs
ACME_DIR=/etc/nginx/acme
CN="${PUBLIC_HOST:-localhost}"
mkdir -p "$CERT_DIR"

# Copia el cert de lego a tls.* si cambió. 0 = instalado uno nuevo.
install_acme() {
  src="$ACME_DIR/data/certificates/$CN"
  [ -s "$src.crt" ] && [ -s "$src.key" ] || return 1
  cmp -s "$src.crt" "$CERT_DIR/tls.crt" && return 1
  cp "$src.key" "$CERT_DIR/tls.key.new"
  cp "$src.crt" "$CERT_DIR/tls.crt.new"
  mv "$CERT_DIR/tls.key.new" "$CERT_DIR/tls.key"
  mv "$CERT_DIR/tls.crt.new" "$CERT_DIR/tls.crt"
  echo "nginx: certificado ACME instalado para $CN" >&2
}

self_signed() {
  if [ -f "$CERT_DIR/self.crt" ] && [ -f "$CERT_DIR/self.key" ]; then
    OLD_CN=$(openssl x509 -noout -subject -in "$CERT_DIR/self.crt" 2>/dev/null \
      | sed -n 's/.*CN=\([^,/]*\).*/\1/p')
    if [ "$OLD_CN" != "$CN" ]; then
      echo "nginx: regenerando certificado (CN ${OLD_CN:-?} → ${CN})" >&2
      rm -f "$CERT_DIR/self.crt" "$CERT_DIR/self.key"
    fi
  fi
  if [ ! -f "$CERT_DIR/self.crt" ]; then
    openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
      -keyout "$CERT_DIR/self.key" \
      -out "$CERT_DIR/self.crt" \
      -subj "/CN=${CN}"
  fi
  cp "$CERT_DIR/self.key" "$CERT_DIR/tls.key"
  cp "$CERT_DIR/self.crt" "$CERT_DIR/tls.crt"
}

acme_loop() {
  profile="${ACME_PROFILE:-}"
  if [ -z "$profile" ] && echo "$CN" | grep -Eq '^[0-9.]+$|:'; then
    profile=shortlived
  fi
  sleep 5 # el challenge necesita a nginx sirviendo :80
  while :; do
    # run obtiene o renueva solo si hace falta (ARI / mitad de vida).
    if lego run --log.format text --accept-tos \
        --server "${ACME_SERVER:-letsencrypt}" \
        ${ACME_EMAIL:+--email "$ACME_EMAIL"} \
        ${profile:+--profile "$profile"} \
        --domains "$CN" \
        --http --http.webroot "$ACME_DIR/webroot" \
        --path "$ACME_DIR/data"; then
      if install_acme; then
        nginx -s reload
      fi
    else
      echo "nginx: lego falló; reintento en ${ACME_INTERVAL_S:-21600}s" >&2
    fi
    sleep "${ACME_INTERVAL_S:-21600}"
  done
}

if [ "${TLS_CERT:-self}" = "acme" ]; then
  mkdir -p "$ACME_DIR/webroot" "$ACME_DIR/data"
  # Hasta que lego emita el primero, arranca con el autofirmado.
  if ! install_acme && ! cmp -s "$ACME_DIR/data/certificates/$CN.crt" "$CERT_DIR/tls.crt"; then
    self_signed
  fi
  acme_loop &
else
  self_signed
fi
