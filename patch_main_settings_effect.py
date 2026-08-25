import sys

content = open("app/src/main/java/com/example/MainActivity.kt").read()

if "LaunchedEffect(currentSettings.targetFps)" not in content:
    old_effect = """                LaunchedEffect(userMessage) {
                    userMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearUserMessage()
                    }
                }"""
    new_effect = """                LaunchedEffect(userMessage) {
                    userMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearUserMessage()
                    }
                }
                
                LaunchedEffect(currentSettings.targetFps) {
                    viewModel.updateCoreSettings(currentSettings.targetFps)
                }"""
    content = content.replace(old_effect, new_effect)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

