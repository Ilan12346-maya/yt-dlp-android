#!/bin/bash
set -e

# --- Configuration ---
SDK_PLATFORM="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/debian/usr/lib/android-sdk/platforms/android-33"
ANDROID_JAR="$SDK_PLATFORM/android.jar"
AAPT2="/data/data/com.termux/files/usr/bin/aapt2"
JAVAC="javac"
BUILD_TOOLS_LIB="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/debian/usr/lib/android-sdk/build-tools/35.0.0/lib"
D8_JAR="$BUILD_TOOLS_LIB/d8.jar"
APKSIGNER_JAR="$BUILD_TOOLS_LIB/apksigner.jar"
ADB="/data/data/com.termux/files/usr/bin/adb"

# Project structure
PACKAGE_NAME="com.example.ytdlp"
SRC_DIR="src"
RES_DIR="res"
MANIFEST="AndroidManifest.xml"
BIN_DIR="bin"
OBJ_DIR="obj"
KEYSTORE="debug.keystore"
KEY_ALIAS="androiddebugkey"
KEY_PASS="android"

# --- Build Process ---


echo "[0/8] Backing up project..."

python3 backup.py



echo "[1/8] Cleaning old build artifacts..."

rm -rf $BIN_DIR $OBJ_DIR *.zip

mkdir -p $BIN_DIR $OBJ_DIR



echo "[2/8] Compiling resources with aapt2..."

# Create python.tar.gz in assets
mkdir -p assets
if [ -d "python_bundle" ]; then
    echo "Creating python.tar.gz from python_bundle..."
    tar -czf assets/python.tar.gz -C python_bundle .
fi

$AAPT2 compile --dir $RES_DIR -o resources.zip



echo "[3/8] Linking resources with aapt2..."

# Ensure assets dir exists to prevent aapt2 error
mkdir -p assets
$AAPT2 link -I $ANDROID_JAR \
    --manifest $MANIFEST \
    --java $SRC_DIR \
    -A assets \
    -o $BIN_DIR/ytdlp.unsigned.apk \
    --auto-add-overlay \
    resources.zip


echo "[4/8] Compiling Java source files with javac..."

$JAVAC -encoding UTF-8 -d $OBJ_DIR -classpath $ANDROID_JAR -sourcepath $SRC_DIR $(find $SRC_DIR -name "*.java")


echo "[5/8] Converting class files to DEX format..."

java -cp $D8_JAR com.android.tools.r8.D8 --output $BIN_DIR --lib $ANDROID_JAR $(find $OBJ_DIR -name "*.class")


echo "[6/8] Adding classes.dex and native libs to the APK..."

  (cd $BIN_DIR && zip -u ytdlp.unsigned.apk classes.dex)

  # Add lib directory from project root to the APK

  zip -u $BIN_DIR/ytdlp.unsigned.apk -r lib



echo "[7/8] Signing the APK..."


if [ ! -f "$KEYSTORE" ]; then
    echo "Generating debug keystore..."
    keytool -genkey -v -keystore $KEYSTORE -alias $KEY_ALIAS -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" -storepass $KEY_PASS -keypass $KEY_PASS
fi

java -jar $APKSIGNER_JAR sign --ks $KEYSTORE --ks-pass pass:$KEY_PASS --out $BIN_DIR/ytdlp.apk $BIN_DIR/ytdlp.unsigned.apk

echo "--- Build Successful ---"
echo "APK location: $BIN_DIR/ytdlp.apk"

# Cleanup
rm resources.zip

# Install if adb is available
if command -v $ADB &> /dev/null; then
    echo "Attempting install..."
    $ADB install -r $BIN_DIR/ytdlp.apk || echo "Install failed."
    echo "Starting application..."
    $ADB shell am start -n $PACKAGE_NAME/.MainActivity || echo "Start failed."
else
    echo "adb not found, skipping install."
fi