./gradlew assembleDebug
./gradlew installDebug
adb shell am force-stop com.example.cloudrive
adb shell am start -n com.example.cloudrive/.MainActivity

