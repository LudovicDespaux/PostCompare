#!/usr/bin/env bash
set -euo pipefail
exec sudo -n /usr/local/sbin/postcompare-ci-receive "${SSH_ORIGINAL_COMMAND:-}"

