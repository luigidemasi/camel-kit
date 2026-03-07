#!/usr/bin/env bash
# download-rh-docs.sh — Download public PDF guides from docs.redhat.com
# for Red Hat Build of Apache Camel.
#
# Usage:  ./scripts/download-rh-docs.sh
#
# The script is idempotent: already-downloaded files are skipped.
# Downloaded files are verified to be valid PDFs.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE_DIR="$MODULE_ROOT/src/main/resources/rh-build-camel"

DOCS_BASE_URL="https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel"

# ──────────────────────────────────────────────────────────────
# Guide matrix
#
# Format:  "version|guide_slug|short_name"
#
# Guides common to ALL versions (4.0, 4.4, 4.8, 4.10, 4.14)
# ──────────────────────────────────────────────────────────────

COMMON_GUIDES=(
  "getting_started_with_red_hat_build_of_apache_camel_for_quarkus|getting-started-quarkus"
  "getting_started_with_red_hat_build_of_apache_camel_for_spring_boot|getting-started-spring-boot"
  "developing_applications_with_red_hat_build_of_apache_camel_for_quarkus|developing-quarkus"
  "red_hat_build_of_apache_camel_for_quarkus_reference|quarkus-reference"
  "red_hat_build_of_apache_camel_for_spring_boot_reference|spring-boot-reference"
  "hawtio_diagnostic_console_guide|hawtio"
  "migrating_fuse_7_applications_to_red_hat_build_of_apache_camel_for_quarkus|fuse7-migration"
  "migrating_to_red_hat_build_of_apache_camel_for_spring_boot|spring-boot-migration"
  "release_notes_for_red_hat_build_of_apache_camel_for_quarkus|release-notes-quarkus"
  "release_notes_for_red_hat_build_of_apache_camel_for_spring_boot|release-notes-spring-boot"
)

# Build the full guide list: version|slug|short_name
GUIDES=()

add_common() {
  local ver="$1"
  for entry in "${COMMON_GUIDES[@]}"; do
    GUIDES+=("${ver}|${entry}")
  done
}

# --- 4.0 ---
add_common "4.0"
GUIDES+=("4.0|tooling_guide|tooling")
GUIDES+=("4.0|release_notes_for_hawtio_diagnostic_console_guide|release-notes-hawtio")

# --- 4.4 ---
add_common "4.4"
GUIDES+=("4.4|tooling_guide_for_red_hat_build_of_apache_camel|tooling")
GUIDES+=("4.4|release_notes_for_hawtio_diagnostic_console_guide|release-notes-hawtio")
GUIDES+=("4.4|kaoto|kaoto")
GUIDES+=("4.4|migration_guide_camel_k_to_camel_extensions_for_quarkus|camel-k-migration")

# --- 4.8 ---
add_common "4.8"
GUIDES+=("4.8|tooling_guide_for_red_hat_build_of_apache_camel|tooling")
GUIDES+=("4.8|release_notes_for_hawtio_diagnostic_console|release-notes-hawtio")
GUIDES+=("4.8|kaoto|kaoto")
GUIDES+=("4.8|migrating_from_camel_k_to_red_hat_build_of_apache_camel_for_quarkus|camel-k-migration")
GUIDES+=("4.8|migrating_apache_camel|camel-migration")
GUIDES+=("4.8|migrating_camel_quarkus_projects|quarkus-migration")
GUIDES+=("4.8|kamelets_reference_for_red_hat_build_of_apache_camel_for_quarkus|kamelets-reference")
GUIDES+=("4.8|quarkus_cxf_security_guide_for_red_hat_build_of_apache_camel|cxf-security")

# --- 4.10 ---
add_common "4.10"
GUIDES+=("4.10|tooling_guide_for_red_hat_build_of_apache_camel|tooling")
GUIDES+=("4.10|release_notes_for_hawtio_diagnostic_console|release-notes-hawtio")
GUIDES+=("4.10|kaoto_camel_designer|kaoto")
GUIDES+=("4.10|migrating_from_camel_k_to_red_hat_build_of_apache_camel_for_quarkus|camel-k-migration")
GUIDES+=("4.10|migrating_apache_camel|camel-migration")
GUIDES+=("4.10|migrating_camel_quarkus_projects|quarkus-migration")
GUIDES+=("4.10|kamelets_reference_for_red_hat_build_of_apache_camel_for_quarkus|kamelets-reference")
GUIDES+=("4.10|quarkus_cxf_security_guide_for_red_hat_build_of_apache_camel|cxf-security")

# --- 4.14 ---
add_common "4.14"
GUIDES+=("4.14|tooling_guide_for_red_hat_build_of_apache_camel|tooling")
GUIDES+=("4.14|release_notes_for_hawtio_diagnostic_console|release-notes-hawtio")
GUIDES+=("4.14|kaoto_camel_designer|kaoto")
GUIDES+=("4.14|migrating_from_camel_k_to_red_hat_build_of_apache_camel_for_quarkus|camel-k-migration")
GUIDES+=("4.14|migrating_apache_camel|camel-migration")
GUIDES+=("4.14|migrating_camel_quarkus_projects|quarkus-migration")
GUIDES+=("4.14|kamelets_reference_for_red_hat_build_of_apache_camel_for_quarkus|kamelets-reference")
GUIDES+=("4.14|quarkus_cxf_for_red_hat_build_of_apache_camel|cxf")
GUIDES+=("4.14|camel_development_guide_for_red_hat_build_of_apache_camel_for_spring_boot|development-spring-boot")

# ──────────────────────────────────────────────────────────────
# Download logic
# ──────────────────────────────────────────────────────────────

total=${#GUIDES[@]}
ok=0
skipped=0
failed=0
missing=0

echo "=== Red Hat Build of Apache Camel — PDF guide downloader ==="
echo "    Target directory: $BASE_DIR"
echo "    Guides to process: $total"
echo ""

for entry in "${GUIDES[@]}"; do
  IFS='|' read -r version slug short_name <<< "$entry"

  dest_dir="$BASE_DIR/$version"
  mkdir -p "$dest_dir"
  dest_file="$dest_dir/${short_name}.pdf"

  # Idempotent: skip if already downloaded and valid
  if [[ -f "$dest_file" ]]; then
    if file "$dest_file" | grep -q "PDF"; then
      printf "  SKIP  %s/%s (already exists)\n" "$version" "$short_name"
      skipped=$((skipped + 1))
      continue
    else
      echo "  WARN  $version/$short_name exists but is not a valid PDF — re-downloading"
      rm -f "$dest_file"
    fi
  fi

  # docs.redhat.com serves PDFs directly at the slug URL (with a 301 redirect).
  # No HTML parsing needed — just download the URL directly.
  pdf_url="${DOCS_BASE_URL}/${version}/pdf/${slug}/"
  printf "  GET   %s/%s ... " "$version" "$short_name"

  # Download the PDF directly
  if curl -fsSL --max-time 120 -o "$dest_file" "$pdf_url" 2>/dev/null; then
    # Verify it is a real PDF
    if file "$dest_file" | grep -q "PDF"; then
      printf "OK\n"
      ok=$((ok + 1))
    else
      printf "FAIL (downloaded file is not a valid PDF)\n"
      rm -f "$dest_file"
      failed=$((failed + 1))
    fi
  else
    printf "FAIL (download error)\n"
    rm -f "$dest_file"
    failed=$((failed + 1))
  fi
done

echo ""
echo "=== Summary ==="
echo "  OK:      $ok"
echo "  SKIPPED: $skipped"
echo "  FAILED:  $failed"
echo "  MISSING: $missing"
echo "  TOTAL:   $total"
echo ""

if (( failed > 0 || missing > 0 )); then
  echo "WARNING: Some guides could not be downloaded."
  echo "         Re-run this script to retry failed downloads."
  echo ""
fi

echo "=== KB Articles (manual download required) ==="
echo ""
echo "The following KB articles must be downloaded manually because they"
echo "require a Red Hat account login:"
echo ""
echo "  1. Open each article URL in a browser while logged in to access.redhat.com"
echo "  2. Save the page as HTML (Ctrl+S / Cmd+S → 'Webpage, HTML Only')"
echo "  3. Place the saved .html files into:"
echo "       $BASE_DIR/kb-articles/"
echo ""
echo "See $BASE_DIR/kb-articles/README.md for the list of articles."
