set -eu
TTS_ROOT="${TTS_LAB_ROOT:?Set TTS_LAB_ROOT to the lab workspace}"
TTS_REPO=$TTS_ROOT/mitsukotoba-validation
TTS_SDK=$TTS_ROOT/tts-android-sdk
TTS_BUILD=$TTS_ROOT/tts-android-build
mkdir -p "$TTS_BUILD/assets" "$TTS_BUILD/dex" "$TTS_BUILD/lib/x86_64"
cd "$TTS_REPO"
"$TTS_ROOT/tts-kotlin-runtime/kotlinc/bin/kotlinc" tts-validation/android-isolated/Runtime.kt tts-validation/android-isolated/SpeakerCore.kt tts-validation/android-isolated/AndroidSink.kt tts-validation/android-isolated/NativeLitePlanner.kt tts-validation/android-lab/LabInstrumentation.kt -cp "$TTS_ROOT/tts-kotlin-runtime/android.jar:$TTS_ROOT/tts-kotlin-runtime/onnxruntime-android-classes.jar" -language-version 2.0 -api-version 2.0 -d "$TTS_BUILD/lab.jar"
"$TTS_SDK/android-15/d8" --min-api 28 --lib "$TTS_ROOT/tts-kotlin-runtime/android.jar" --output "$TTS_BUILD/dex" "$TTS_BUILD/lab.jar" "$TTS_ROOT/tts-kotlin-runtime/kotlin-stdlib-2.0.21.jar" "$TTS_ROOT/tts-kotlin-runtime/onnxruntime-android-classes.jar"
cp "$TTS_ROOT"/tts-model-audit/assets/* "$TTS_BUILD/assets/"
cp "$TTS_ROOT/cmudict-audit-source/cmudict.dict" "$TTS_BUILD/assets/"
cp tts-validation/fixtures/native-lite-catalogue.txt "$TTS_BUILD/assets/catalogue.txt"
"$TTS_SDK/android-15/aapt2" link -o "$TTS_BUILD/lab-unsigned.apk" --manifest tts-validation/android-lab/AndroidManifest.xml -I "$TTS_ROOT/tts-kotlin-runtime/android.jar" -A "$TTS_BUILD/assets"
python - "$TTS_BUILD" "$TTS_ROOT/tts-kotlin-runtime/onnxruntime-android-1.22.0.aar" <<'PY'
import zipfile,sys,pathlib
b=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(sys.argv[2]) as aar,zipfile.ZipFile(b/'lab-unsigned.apk','a') as apk:
 for n in aar.namelist():
  if n.startswith('jni/x86_64/') and n.endswith('.so'):apk.writestr(n.replace('jni/','lib/',1),aar.read(n))
 for n in (b/'dex').glob('*.dex'):apk.write(n,n.name)
PY
test -e "$TTS_BUILD/lab.keystore" || keytool -genkeypair -keystore "$TTS_BUILD/lab.keystore" -storepass android -keypass android -alias lab -keyalg RSA -validity 3650 -dname 'CN=Isolated validation' -noprompt
"$TTS_SDK/android-15/apksigner" sign --ks "$TTS_BUILD/lab.keystore" --ks-pass pass:android --out "$TTS_BUILD/lab.apk" "$TTS_BUILD/lab-unsigned.apk"
