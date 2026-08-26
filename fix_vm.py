with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "r") as f:
    lines = f.readlines()

out = []
skip = False
for line in lines:
    if "fun setVulkanSurface" in line:
        skip = True
        continue
    if skip:
        if "}" in line:
            skip = False
        continue
    out.append(line)

with open("app/src/main/java/com/example/viewmodel/SwtcViewModel.kt", "w") as f:
    f.writelines(out)

