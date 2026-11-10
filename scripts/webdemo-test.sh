#!/bin/sh
set -e
cd "$(dirname "$0")/.."
env -u LD_LIBRARY_PATH node scripts/webdemo-test.js
