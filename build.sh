#!/bin/bash
cd ~/buildAPKs/sources/com/ncmconverter/app
rm -rf build compiled_res classes.dex app-unsigned.apk app-signed.apk
mkdir -p compiled_res build
aapt2 compile res/values/strings.xml -o compiled_res/
aapt2 link compiled_res/values_strings.arsc.flat --manifest AndroidManifest.xml -I ./android.jar -o app-unsigned.apk --min-sdk-version 21 --target-sdk-version 35
ecj -cp ./android.jar -source 1.7 -target 1.7 src/com/ncmconverter/app/MainActivity.java -d build
dx --dex --output=classes.dex build/
zip -u app-unsigned.apk classes.dex
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --v1-signing-enabled true --v2-signing-enabled true --out app-signed.apk app-unsigned.apk
cp app-signed.apk ~/storage/downloads/
echo "✅ 打包完成！APK已保存到：/storage/emulated/0/Download/app-signed.apk"
