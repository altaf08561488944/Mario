import sys

content = open("app/src/main/java/com/example/ui/screens/CartridgeLibraryScreen.kt").read()

old_text = "Go to 'My Folder', select a game file (.nsp, .xci, or .sup) and choose 'CONVERT TO CARTRIDGE' to build a virtual cartridge for the emulator."
new_text = "Go to 'My Folder', select a game file (.nsp or .nro) and choose 'CONVERT TO CARTRIDGE' to build a virtual cartridge.\\n\\nNote: .xci commercial format support is Coming Soon!"

content = content.replace(old_text, new_text)

# Also let's update the format badge for XCI if any exist.
old_badge = """                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NeonYellow.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "VIRTUAL CARTRIDGE (${cartridge.sourceFormat})",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonYellow,"""

new_badge = """                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (cartridge.sourceFormat.contains("XCI", ignoreCase = true)) NeonRed.copy(alpha = 0.2f) else NeonYellow.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (cartridge.sourceFormat.contains("XCI", ignoreCase = true)) "XCI (COMING SOON)" else "VIRTUAL CARTRIDGE (${cartridge.sourceFormat})",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (cartridge.sourceFormat.contains("XCI", ignoreCase = true)) NeonRed else NeonYellow,"""

old_button = """                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("launch_cartridge_${cartridge.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PLAY / LAUNCH", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }"""

new_button = """                if (cartridge.sourceFormat.contains("XCI", ignoreCase = true)) {
                    Button(
                        onClick = { /* Disabled for now */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray, contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        enabled = false
                    ) {
                        Text("COMING SOON", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onLaunch,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("launch_cartridge_${cartridge.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PLAY / LAUNCH", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }"""

content = content.replace(old_badge, new_badge)
content = content.replace(old_button, new_button)

with open("app/src/main/java/com/example/ui/screens/CartridgeLibraryScreen.kt", "w") as f:
    f.write(content)

