import re

with open("app/src/main/java/com/example/ui/screens/CartridgeLibraryScreen.kt", "r") as f:
    code = f.read()

# Add a prop to CartridgeLibraryScreen
find_header = "    onGoToSaveStates: () -> Unit\n) {"
replace_header = "    onGoToSaveStates: () -> Unit,\n    onScanDevice: () -> Unit\n) {"
code = code.replace(find_header, replace_header)

# Add the SCAN button next to SAVE STATES
find_btn = """                        Icon(Icons.Default.Save, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save States", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }"""

replace_btn = """                        Icon(Icons.Default.Save, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save States", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = onScanDevice,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(32.dp).border(
                            1.dp,
                            Brush.horizontalGradient(listOf(NeonBlue, NeonGreen)),
                            RoundedCornerShape(12.dp)
                        )
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan Device", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }"""
code = code.replace(find_btn, replace_btn)

with open("app/src/main/java/com/example/ui/screens/CartridgeLibraryScreen.kt", "w") as f:
    f.write(code)
