#!/usr/bin/env bash
# Fetch the OpenCV test fixtures (opencv_extra) matching this source tree.
#
# The accuracy/regression tests require OPENCV_TEST_DATA_PATH to point at a
# checkout of https://github.com/opencv/opencv_extra on the branch that matches
# CV_VERSION (3.0 for this 3.0-dev tree). Without it, data-driven tests report
# FAIL_INVALID_TEST_DATA.
#
# Usage:
#   scripts/fetch-test-data.sh [destination]
#
# Arguments:
#   destination   Directory to clone/update (default: .cache/opencv_extra)
#
# Environment:
#   OPENCV_EXTRA_REPO    Override source repository URL
#   OPENCV_EXTRA_BRANCH  Override branch/tag (default: 3.0)
#
# The script is idempotent: if the destination is already a git checkout of the
# requested branch, it performs a shallow fetch/reset to update fixtures.
set -euo pipefail

DEST="${1:-${OPENCV_TEST_DATA_PATH:-.cache/opencv_extra}}"
REPO="${OPENCV_EXTRA_REPO:-https://github.com/opencv/opencv_extra.git}"
BRANCH="${OPENCV_EXTRA_BRANCH:-3.0}"

if ! command -v git >/dev/null 2>&1; then
  echo "error: git is required to fetch opencv_extra test data" >&2
  exit 1
fi

mkdir -p "$(dirname "$DEST")"

if [ -d "$DEST/.git" ]; then
  echo "Updating existing opencv_extra checkout in $DEST (branch $BRANCH)"
  git -C "$DEST" remote set-url origin "$REPO" >/dev/null 2>&1 || true
  git -C "$DEST" fetch --depth 1 origin "$BRANCH"
  git -C "$DEST" checkout --detach "FETCH_HEAD"
else
  echo "Cloning opencv_extra ($BRANCH) into $DEST"
  rm -rf "$DEST"
  git clone --depth 1 --branch "$BRANCH" "$REPO" "$DEST"
fi

echo "OPENCV_TEST_DATA_PATH=$DEST"