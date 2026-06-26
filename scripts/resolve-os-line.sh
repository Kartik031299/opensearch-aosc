#!/usr/bin/env bash
set -euo pipefail

# Map a git ref / branch name to an OpenSearch release line (os2, os3, ...).
#
# Branch topology (see release/<line>.properties):
#   develop        -> latest major mainline (currently os3 / OpenSearch 3.x)
#   releases/2.x   -> os2
#   releases/3.x   -> os3
#
# Used by the CI workflows to pick the right line (and therefore the right
# OpenSearch versions + Java version) for the branch under test. For
# pull_request events pass the BASE branch (github.base_ref); for push events
# pass the pushed branch (github.ref_name).

REF="${1:?usage: resolve-os-line.sh <ref>}"

# Strip a leading refs/heads/ if present.
REF="${REF#refs/heads/}"

case "${REF}" in
  releases/2.*|2.x|2.*)  echo "os2" ;;
  releases/3.*|3.x|3.*)  echo "os3" ;;
  develop)               echo "os3" ;;  # develop tracks the latest major (3.x)
  *)                     echo "os3" ;;  # default to the current mainline major
esac
