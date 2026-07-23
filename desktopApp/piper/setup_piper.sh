#!/bin/bash
set -e

PIPER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$PIPER_DIR/bin"
MODELS_DIR="$PIPER_DIR/models"

mkdir -p "$BIN_DIR"
mkdir -p "$MODELS_DIR"

echo "Downloading Piper binary..."
if [ ! -f "$BIN_DIR/piper" ]; then
    TMP_TAR=$(mktemp)
    curl -C - --retry 5 --retry-delay 2 -L "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz" -o "$TMP_TAR"
    tar -xzf "$TMP_TAR" -C "$BIN_DIR" --strip-components=1
    rm "$TMP_TAR"
    chmod +x "$BIN_DIR/piper"
    echo "Piper binary downloaded successfully to $BIN_DIR/piper"
else
    echo "Piper binary already exists."
fi

# Base HF URL
HF_BASE="https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0"

download_model() {
    local lang_path="$1" # e.g. en/en_US/lessac/medium
    local model_name="$2" # e.g. en_US-lessac-medium

    echo "Checking model $model_name..."
    if [ ! -f "$MODELS_DIR/$model_name.onnx" ]; then
        echo "Downloading $model_name.onnx..."
        curl -C - --retry 5 --retry-delay 2 -L "$HF_BASE/$lang_path/$model_name.onnx" -o "$MODELS_DIR/$model_name.onnx"
    fi
    if [ ! -f "$MODELS_DIR/$model_name.onnx.json" ]; then
        echo "Downloading $model_name.onnx.json..."
        curl -C - --retry 5 --retry-delay 2 -L "$HF_BASE/$lang_path/$model_name.onnx.json" -o "$MODELS_DIR/$model_name.onnx.json"
    fi
}

echo "Downloading voice models..."
download_model "en/en_US/lessac/medium" "en_US-lessac-medium"
download_model "en/en_US/ryan/high" "en_US-ryan-high"
download_model "en/en_US/bryce/medium" "en_US-bryce-medium"

download_model "es/es_MX/ald/medium" "es_MX-ald-medium"
download_model "es/es_MX/claude/high" "es_MX-claude-high"
download_model "es/es_ES/sharvard/medium" "es_ES-sharvard-medium"

echo "All Piper assets successfully set up in $PIPER_DIR!"
