"""Finalize Expo's generated Android release project before invoking Gradle."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parent
app = root / "app"
android = app / "android/app"
gradle = android / "build.gradle"
text = gradle.read_text()
start = text.index("        release {")
before, release = text[:start], text[start:]
template = "signingConfig signingConfigs.debug"
if release.count(template) != 1:
    raise RuntimeError("Unexpected Expo release signing configuration")
gradle.write_text(before + release.replace(template, "signingConfig null"))

main = android / "src/main"
(main / "assets").mkdir(exist_ok=True)
shutil.copyfile(app / "LICENSE", main / "assets/Chatwoot-LICENSE.txt")
shutil.copyfile(root.parent / "chatwoot-native/app/src/main/res/drawable/ic_stat_chat.xml",
                main / "res/drawable/ic_stat_chat.xml")
manifest = main / "AndroidManifest.xml"
text = manifest.read_text()
anchor = '    <meta-data android:name="expo.modules.updates.ENABLED"'
if text.count(anchor) != 1:
    raise RuntimeError("Missing application metadata in the generated Android manifest")
manifest.write_text(text.replace(anchor, '    <meta-data android:name="com.google.firebase.messaging.default_notification_icon" android:resource="@drawable/ic_stat_chat"/>\n' + anchor))
