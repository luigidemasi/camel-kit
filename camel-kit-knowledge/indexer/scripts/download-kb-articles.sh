#!/usr/bin/env bash
# download-kb-articles.sh — Discover and download Red Hat KB articles
# for Camel and Fuse products via the Hydra API.
#
# Usage:
#   RH_OFFLINE_TOKEN=<token> ./scripts/download-kb-articles.sh
#
# To generate an offline token:
#   1. Go to https://access.redhat.com/management/api
#   2. Click "Generate Token"
#   3. Copy the token and set it as RH_OFFLINE_TOKEN
#
# The script is idempotent: already-downloaded articles are skipped.
# Use --force to re-download all articles.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
KB_DIR="$MODULE_ROOT/src/main/resources/rh-build-camel/kb-articles"

SSO_TOKEN_URL="https://sso.redhat.com/auth/realms/redhat-external/protocol/openid-connect/token"
SSO_CLIENT_ID="rhsm-api"
HYDRA_URL="https://access.redhat.com/hydra/rest/search/kcs"
PORTAL_BASE_URL="https://access.redhat.com"

# ──────────────────────────────────────────────────────────────
# Hydra search products — used to discover KB articles
# ──────────────────────────────────────────────────────────────

SEARCH_PRODUCTS=(
  "Red Hat Build of Apache Camel"
  "Red Hat Fuse"
)

# Document kinds to search for
DOC_KINDS=("Article" "Solution")

# ──────────────────────────────────────────────────────────────
# Product filter — only keep articles whose "product" field
# contains at least one of these substrings
# ──────────────────────────────────────────────────────────────

PRODUCT_FILTERS=("Camel" "Fuse")

# ──────────────────────────────────────────────────────────────
# Parse arguments
# ──────────────────────────────────────────────────────────────

FORCE=false
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    --help|-h)
      echo "Usage: RH_OFFLINE_TOKEN=<token> $0 [--force]"
      echo ""
      echo "Options:"
      echo "  --force   Re-download all articles (ignore cache)"
      echo ""
      echo "Environment:"
      echo "  RH_OFFLINE_TOKEN   Red Hat SSO offline token (required)"
      echo "                     Generate at: https://access.redhat.com/management/api"
      exit 0
      ;;
  esac
done

# ──────────────────────────────────────────────────────────────
# Authenticate
# ──────────────────────────────────────────────────────────────

if [[ -z "${RH_OFFLINE_TOKEN:-}" ]]; then
  echo "ERROR: RH_OFFLINE_TOKEN environment variable is required."
  echo ""
  echo "To generate an offline token:"
  echo "  1. Go to https://access.redhat.com/management/api"
  echo "  2. Click 'Generate Token'"
  echo "  3. Export it:  export RH_OFFLINE_TOKEN=<your-token>"
  echo ""
  echo "Then re-run this script."
  exit 1
fi

echo "=== Red Hat KB Article Downloader ==="
echo "    Target directory: $KB_DIR"
echo ""

echo "Exchanging offline token for access token..."
ACCESS_TOKEN=$(curl -sf -X POST "$SSO_TOKEN_URL" \
  -d "grant_type=refresh_token" \
  -d "client_id=$SSO_CLIENT_ID" \
  -d "refresh_token=$RH_OFFLINE_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null) || {
  echo "ERROR: Failed to exchange offline token for access token."
  echo "       Your offline token may have expired. Generate a new one at:"
  echo "       https://access.redhat.com/management/api"
  exit 1
}

echo "Authentication successful."
echo ""

# ──────────────────────────────────────────────────────────────
# Phase 1: Discover articles via Hydra API
# ──────────────────────────────────────────────────────────────

echo "Discovering KB articles from Hydra API..."

MANIFEST_FILE=$(mktemp)
trap "rm -f $MANIFEST_FILE" EXIT

python3 - "$MANIFEST_FILE" << 'PYEOF'
import subprocess, json, urllib.parse, sys

manifest_file = sys.argv[1]
HYDRA_URL = "https://access.redhat.com/hydra/rest/search/kcs"
SEARCH_PRODUCTS = ["Red Hat Build of Apache Camel", "Red Hat Fuse"]
DOC_KINDS = ["Article", "Solution"]
PRODUCT_FILTERS = ["Camel", "Fuse"]

def fetch(url):
    r = subprocess.run(["curl", "-sf", url], capture_output=True, text=True)
    return json.loads(r.stdout)

seen = {}

for product in SEARCH_PRODUCTS:
    for kind in DOC_KINDS:
        start = 0
        while True:
            url = (f"{HYDRA_URL}?q={urllib.parse.quote(product)}"
                   f"&rows=100&start={start}&fq=documentKind:{kind}")
            data = fetch(url)
            docs = data["response"]["docs"]
            num_found = data["response"]["numFound"]
            if not docs:
                break
            for doc in docs:
                doc_id = str(doc["id"])
                if doc_id not in seen:
                    seen[doc_id] = doc
            start += len(docs)
            if start >= num_found:
                break
        print(f"  {product} | {kind}: {num_found} total", file=sys.stderr)

# Filter: English only + product contains Camel or Fuse
results = []
for doc_id, doc in seen.items():
    lang = doc.get("language", "en")
    if lang != "en":
        continue
    products = doc.get("product", [])
    if isinstance(products, str):
        products = [products]
    if not any(f in p for p in products for f in PRODUCT_FILTERS):
        continue

    view_uri = doc.get("view_uri", "")
    # Normalize URI: /node/XXXX -> /articles/XXXX or /solutions/XXXX
    kind = doc.get("documentKind", "Article")
    if "/node/" in view_uri:
        path = "articles" if kind == "Article" else "solutions"
        view_uri = f"https://access.redhat.com/{path}/{doc_id}"

    title = doc.get("publishedTitle", doc.get("title", doc.get("allTitle", "")))
    results.append({"id": doc_id, "kind": kind, "title": title, "uri": view_uri})

# Write manifest (id|kind|title|uri)
with open(manifest_file, "w") as f:
    for r in results:
        f.write(f"{r['id']}|{r['kind']}|{r['title']}|{r['uri']}\n")

articles = sum(1 for r in results if r["kind"] == "Article")
solutions = sum(1 for r in results if r["kind"] == "Solution")
print(f"\n  Total unique: {len(seen)}", file=sys.stderr)
print(f"  English + Camel/Fuse: {len(results)} ({articles} articles, {solutions} solutions)", file=sys.stderr)
PYEOF

TOTAL=$(wc -l < "$MANIFEST_FILE")
echo ""
echo "Found $TOTAL articles to download."
echo ""

# ──────────────────────────────────────────────────────────────
# Phase 2: Download HTML for each article
# ──────────────────────────────────────────────────────────────

mkdir -p "$KB_DIR"

ok=0
skipped=0
failed=0
current=0

while IFS='|' read -r article_id kind title uri; do
  current=$((current + 1))
  dest_file="$KB_DIR/${article_id}.html"

  # Idempotent: skip if already downloaded (unless --force)
  if [[ -f "$dest_file" && "$FORCE" == "false" ]]; then
    size=$(stat --printf="%s" "$dest_file" 2>/dev/null || stat -f%z "$dest_file" 2>/dev/null)
    if (( size > 1000 )); then
      skipped=$((skipped + 1))
      continue
    fi
  fi

  printf "  [%d/%d] GET %s — %s ... " "$current" "$TOTAL" "$article_id" "${title:0:60}"

  http_code=$(curl -sS -w "%{http_code}" -o "$dest_file" \
    --max-time 60 \
    -L \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Accept: text/html" \
    "$uri" 2>/dev/null) || http_code="000"

  if [[ "$http_code" == "200" ]]; then
    size=$(stat --printf="%s" "$dest_file" 2>/dev/null || stat -f%z "$dest_file" 2>/dev/null)
    if (( size > 1000 )); then
      printf "OK (%s bytes)\n" "$size"
      ok=$((ok + 1))
    else
      printf "FAIL (too small: %s bytes)\n" "$size"
      rm -f "$dest_file"
      failed=$((failed + 1))
    fi
  elif [[ "$http_code" == "401" || "$http_code" == "403" ]]; then
    printf "FAIL (HTTP %s — access denied)\n" "$http_code"
    rm -f "$dest_file"
    failed=$((failed + 1))
  elif [[ "$http_code" == "404" ]]; then
    printf "SKIP (HTTP 404 — not found)\n"
    rm -f "$dest_file"
    skipped=$((skipped + 1))
  else
    printf "FAIL (HTTP %s)\n" "$http_code"
    rm -f "$dest_file"
    failed=$((failed + 1))
  fi
done < "$MANIFEST_FILE"

echo ""
echo "=== Summary ==="
echo "  Downloaded: $ok"
echo "  Skipped:    $skipped"
echo "  Failed:     $failed"
echo "  Total:      $TOTAL"
echo ""

if (( failed > 0 )); then
  echo "WARNING: $failed articles could not be downloaded."
  echo "         Check your RH_OFFLINE_TOKEN and subscription access."
  exit 1
fi

echo "All articles downloaded to: $KB_DIR"
echo "Run the indexer to process them into the knowledge index."
