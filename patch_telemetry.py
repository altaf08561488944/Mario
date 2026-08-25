import sys

content = open("app/src/main/java/com/example/emulator/telemetry/TelemetryLogger.kt").read()
content = content.replace("class TelemetryLogger(private val context: Context)", "class TelemetryLogger")
content = content.replace("import android.content.Context", "")
content = content.replace("val file = File(context.filesDir, fileName)", "val file = File(System.getProperty(\"java.io.tmpdir\"), fileName)")

with open("app/src/main/java/com/example/emulator/telemetry/TelemetryLogger.kt", "w") as f:
    f.write(content)
