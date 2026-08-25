import sys

content = open("app/src/main/java/com/example/ui/screens/ActiveEmulationScreen.kt").read()

old_touch_layout = """            // Touch Gamepad Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Joy-Con Controls
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TouchPill("L", NeonBlue)
                    TouchPill("ZL", NeonBlue)
                    TouchPill("D-PAD", NeonBlue)
                }

                // Right Joy-Con Controls
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TouchPill("ABXY", NeonRed)
                    TouchPill("R", NeonRed)
                    TouchPill("ZR", NeonRed)
                }
            }"""

new_touch_layout = """            // Advanced Virtual Gamepad Overlay (Comfortable Play)
            VirtualGamepadOverlay()"""

old_touch_pill = """@Composable
private fun TouchPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(54.dp, 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .border(1.dp, color, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}"""

new_touch_pill = """@Composable
private fun VirtualGamepadOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Left Joy-Con Area (D-Pad & Analog)
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GamepadButton("L", NeonBlue)
                GamepadButton("ZL", NeonBlue)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                GamepadAnalogStick(NeonBlue)
            }
        }
        
        // Right Joy-Con Area (ABXY & Analog)
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GamepadButton("R", NeonRed)
                GamepadButton("ZR", NeonRed)
            }
            Spacer(modifier = Modifier.height(16.dp))
            // ABXY Diamond
            Box(modifier = Modifier.size(100.dp)) {
                Box(modifier = Modifier.align(Alignment.TopCenter)) { GamepadRoundButton("X", NeonRed) }
                Box(modifier = Modifier.align(Alignment.BottomCenter)) { GamepadRoundButton("B", NeonRed) }
                Box(modifier = Modifier.align(Alignment.CenterStart)) { GamepadRoundButton("Y", NeonRed) }
                Box(modifier = Modifier.align(Alignment.CenterEnd)) { GamepadRoundButton("A", NeonRed) }
            }
        }
    }
}

@Composable
private fun GamepadButton(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(48.dp, 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun GamepadRoundButton(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.Black.copy(alpha = 0.7f))
            .border(1.dp, color.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun GamepadAnalogStick(color: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, color.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.DarkGray)
                .border(1.dp, color.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape)
        )
    }
}"""

content = content.replace(old_touch_layout, new_touch_layout)
content = content.replace(old_touch_pill, new_touch_pill)

with open("app/src/main/java/com/example/ui/screens/ActiveEmulationScreen.kt", "w") as f:
    f.write(content)

