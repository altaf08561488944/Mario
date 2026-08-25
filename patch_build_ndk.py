import sys

content = open("app/build.gradle.kts").read()

if "externalNativeBuild" not in content:
    # Find defaultConfig block to add ndk flags if needed, or just add externalNativeBuild block in android
    
    # Let's add externalNativeBuild right before buildTypes
    old_build_types = "buildTypes {"
    new_external_build = """externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    buildTypes {"""
    
    content = content.replace(old_build_types, new_external_build)
    
    with open("app/build.gradle.kts", "w") as f:
        f.write(content)

