#!/usr/bin/env bash
set -euo pipefail

# The workflow verifies the prepared tag and commit before invoking this script.
exec ./mvnw -B -Psourcecheck,release,linux-ship-certification \
  -DdeployAtEnd=true -Dgpg.keyname="${GPG_ID:?}" -Dgpg.passphrase= clean deploy
