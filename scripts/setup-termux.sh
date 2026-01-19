#!/data/data/com.termux/files/usr/bin/bash

echo "=== Claude Assistant Termux Setup ==="

# 1. Configure termux.properties
PROPS_FILE="$HOME/.termux/termux.properties"
mkdir -p "$HOME/.termux"

if ! grep -q "allow-external-apps" "$PROPS_FILE" 2>/dev/null; then
    echo "allow-external-apps = true" >> "$PROPS_FILE"
    echo "[OK] allow-external-apps enabled"
else
    sed -i 's/allow-external-apps.*/allow-external-apps = true/' "$PROPS_FILE"
    echo "[OK] allow-external-apps updated"
fi

# 2. Check Termux:API
if ! command -v termux-tts-speak &>/dev/null; then
    echo "[INFO] Installing termux-api..."
    pkg install termux-api -y
else
    echo "[OK] termux-api found"
fi

# 3. Check Claude Code
if ! command -v claude &>/dev/null; then
    echo "[WARNING] Claude Code not found!"
    echo "Please install Claude Code with: npm install -g @anthropic-ai/claude-code"
else
    echo "[OK] Claude Code found: $(which claude)"
fi

# 4. Reload Termux settings
termux-reload-settings

echo ""
echo "=== Setup complete ==="
echo "Please grant the Claude Assistant App the permission"
echo "'com.termux.permission.RUN_COMMAND' in Android Settings."
echo ""
echo "To set as default assistant:"
echo "Settings > Apps > Default Apps > Digital Assistant > Claude Assistant"
