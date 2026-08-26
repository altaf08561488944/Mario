import re

with open("app/src/main/cpp/vulkan_backend.cpp", "r") as f:
    code = f.read()

# Fix the duplicate deviceExtensions
fix_pattern = r'    std::vector<const char\*> deviceExtensions = \{VK_KHR_SWAPCHAIN_EXTENSION_NAME\};\n    VkDeviceCreateInfo createInfo\{\};\n    createInfo\.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;\n    createInfo\.enabledExtensionCount = static_cast<uint32_t>\(deviceExtensions\.size\(\)\);\n    createInfo\.ppEnabledExtensionNames = deviceExtensions\.data\(\);\n    createInfo\.pQueueCreateInfos = &queueCreateInfo;\n    createInfo\.queueCreateInfoCount = 1;\n    createInfo\.pEnabledFeatures = &deviceFeatures;\n    \n    // Swapchain extension is required for rendering to screen\n    std::vector<const char\*> deviceExtensions = \{ VK_KHR_SWAPCHAIN_EXTENSION_NAME \};\n    createInfo\.enabledExtensionCount = static_cast<uint32_t>\(deviceExtensions\.size\(\)\);\n    createInfo\.ppEnabledExtensionNames = deviceExtensions\.data\(\);'
fix_replace = """    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.pQueueCreateInfos = &queueCreateInfo;
    createInfo.queueCreateInfoCount = 1;
    createInfo.pEnabledFeatures = &deviceFeatures;
    
    // Swapchain extension is required for rendering to screen
    std::vector<const char*> deviceExtensions = { VK_KHR_SWAPCHAIN_EXTENSION_NAME };
    createInfo.enabledExtensionCount = static_cast<uint32_t>(deviceExtensions.size());
    createInfo.ppEnabledExtensionNames = deviceExtensions.data();"""

code = re.sub(fix_pattern, fix_replace, code)

with open("app/src/main/cpp/vulkan_backend.cpp", "w") as f:
    f.write(code)
