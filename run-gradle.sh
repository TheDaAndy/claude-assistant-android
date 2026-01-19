#!/data/data/com.termux/files/usr/bin/bash
export TMPDIR="$HOME/.tmp-gradle"
mkdir -p "$TMPDIR"
exec gradle "$@"
