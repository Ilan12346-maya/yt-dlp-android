#!/bin/bash
# collect_libs.sh - Helper to update libraries from your local Termux environment
# Run this inside Termux if you want to update the bundled libs.

TARGET_DIR="lib/arm64-v8a"
mkdir -p $TARGET_DIR

echo "[*] Collecting libraries from Termux system..."

# List of essential libs for FFmpeg and Python
LIBS=(
    "libandroid-support.so"
    "libbrotlicommon.so"
    "libbrotlidec.so"
    "libbz2.so"
    "libc++_shared.so"
    "libcrypto.so"
    "libexpat.so"
    "libiconv.so"
    "liblzma.so"
    "libpython3.12.so"
    "libssl.so"
    "libx264.so"
    "libz.so"
)

for lib in "${LIBS[@]}"; do
    SRC="/data/data/com.termux/files/usr/lib/$lib"
    if [ -f "$SRC" ]; then
        echo "Copying $lib..."
        # Rename some libs to match the app's internal naming if needed
        # (The app uses .so names that Android's APK installer accepts)
        cp "$SRC" "$TARGET_DIR/"
    else
        echo "Warning: $lib not found in Termux /usr/lib/"
    fi
done

# Special handling for the executables (renamed to .so to be allowed in lib/ folder)
cp /data/data/com.termux/files/usr/bin/ffmpeg "$TARGET_DIR/libffmpeg_exe.so"
cp /data/data/com.termux/files/usr/bin/python3.12 "$TARGET_DIR/libpython_exe.so"

echo "[!] Done"
