import re

with open("app/src/main/cpp/vulkan_backend.cpp", "r") as f:
    code = f.read()

# Add JNI surface binding and swapchain creation
swapchain_code = """
#include <android/native_window_jni.h>
#include <vulkan/vulkan_android.h>

// Globals for Swapchain
std::vector<VkImage> swapChainImages;
std::vector<VkImageView> swapChainImageViews;
VkFormat swapChainImageFormat;
VkExtent2D swapChainExtent;
VkRenderPass renderPass;
std::vector<VkFramebuffer> swapChainFramebuffers;
std::vector<VkCommandBuffer> commandBuffers;
VkSemaphore imageAvailableSemaphore;
VkSemaphore renderFinishedSemaphore;
VkFence inFlightFence;

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeSetSurface(JNIEnv* env, jobject /* this */, jobject surfaceObj) {
    if (!vkCtx.isInitialized) return;
    
    ANativeWindow* window = ANativeWindow_fromSurface(env, surfaceObj);
    if (!window) {
        LOGE("Failed to get ANativeWindow from Surface.");
        return;
    }

    // 1. Create Surface
    VkAndroidSurfaceCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    createInfo.window = window;
    
    if (vkCreateAndroidSurfaceKHR(vkCtx.instance, &createInfo, nullptr, &vkCtx.surface) != VK_SUCCESS) {
        LOGE("Failed to create Vulkan Android Surface.");
        return;
    }
    
    LOGI("Vulkan Android Surface created successfully!");

    // 2. Query Capabilities & Create Swapchain
    VkSurfaceCapabilitiesKHR capabilities;
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(vkCtx.physicalDevice, vkCtx.surface, &capabilities);
    
    swapChainExtent = capabilities.currentExtent;
    swapChainImageFormat = VK_FORMAT_R8G8B8A8_UNORM;

    VkSwapchainCreateInfoKHR swapchainInfo{};
    swapchainInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    swapchainInfo.surface = vkCtx.surface;
    swapchainInfo.minImageCount = capabilities.minImageCount + 1;
    swapchainInfo.imageFormat = swapChainImageFormat;
    swapchainInfo.imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    swapchainInfo.imageExtent = swapChainExtent;
    swapchainInfo.imageArrayLayers = 1;
    swapchainInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    swapchainInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swapchainInfo.preTransform = capabilities.currentTransform;
    swapchainInfo.compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    swapchainInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR; // VSync
    swapchainInfo.clipped = VK_TRUE;
    
    if (vkCreateSwapchainKHR(vkCtx.logicalDevice, &swapchainInfo, nullptr, &vkCtx.swapchain) != VK_SUCCESS) {
        LOGE("Failed to create Swapchain.");
        return;
    }

    uint32_t imageCount;
    vkGetSwapchainImagesKHR(vkCtx.logicalDevice, vkCtx.swapchain, &imageCount, nullptr);
    swapChainImages.resize(imageCount);
    vkGetSwapchainImagesKHR(vkCtx.logicalDevice, vkCtx.swapchain, &imageCount, swapChainImages.data());
    
    LOGI("Vulkan Swapchain created with %d images. Extent: %dx%d", imageCount, swapChainExtent.width, swapChainExtent.height);

    // 3. Create Image Views
    swapChainImageViews.resize(imageCount);
    for (size_t i = 0; i < swapChainImages.size(); i++) {
        VkImageViewCreateInfo viewInfo{};
        viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        viewInfo.image = swapChainImages[i];
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = swapChainImageFormat;
        viewInfo.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
        viewInfo.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
        viewInfo.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
        viewInfo.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.baseMipLevel = 0;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.baseArrayLayer = 0;
        viewInfo.subresourceRange.layerCount = 1;

        if (vkCreateImageView(vkCtx.logicalDevice, &viewInfo, nullptr, &swapChainImageViews[i]) != VK_SUCCESS) {
            LOGE("Failed to create Image View.");
        }
    }

    // 4. Create Render Pass
    VkAttachmentDescription colorAttachment{};
    colorAttachment.format = swapChainImageFormat;
    colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
    colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    colorAttachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference colorAttachmentRef{};
    colorAttachmentRef.attachment = 0;
    colorAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorAttachmentRef;

    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.srcAccessMask = 0;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 1;
    renderPassInfo.pAttachments = &colorAttachment;
    renderPassInfo.subpassCount = 1;
    renderPassInfo.pSubpasses = &subpass;
    renderPassInfo.dependencyCount = 1;
    renderPassInfo.pDependencies = &dependency;

    if (vkCreateRenderPass(vkCtx.logicalDevice, &renderPassInfo, nullptr, &renderPass) != VK_SUCCESS) {
        LOGE("Failed to create Render Pass.");
        return;
    }

    // 5. Create Framebuffers
    swapChainFramebuffers.resize(swapChainImageViews.size());
    for (size_t i = 0; i < swapChainImageViews.size(); i++) {
        VkImageView attachments[] = { swapChainImageViews[i] };
        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = renderPass;
        framebufferInfo.attachmentCount = 1;
        framebufferInfo.pAttachments = attachments;
        framebufferInfo.width = swapChainExtent.width;
        framebufferInfo.height = swapChainExtent.height;
        framebufferInfo.layers = 1;

        if (vkCreateFramebuffer(vkCtx.logicalDevice, &framebufferInfo, nullptr, &swapChainFramebuffers[i]) != VK_SUCCESS) {
            LOGE("Failed to create Framebuffer.");
        }
    }

    // 6. Allocate Command Buffers
    commandBuffers.resize(swapChainFramebuffers.size());
    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = vkCtx.commandPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = (uint32_t) commandBuffers.size();

    if (vkAllocateCommandBuffers(vkCtx.logicalDevice, &allocInfo, commandBuffers.data()) != VK_SUCCESS) {
        LOGE("Failed to allocate Command Buffers.");
    }
    
    // 7. Create Sync Objects
    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    
    vkCreateSemaphore(vkCtx.logicalDevice, &semaphoreInfo, nullptr, &imageAvailableSemaphore);
    vkCreateSemaphore(vkCtx.logicalDevice, &semaphoreInfo, nullptr, &renderFinishedSemaphore);
    vkCreateFence(vkCtx.logicalDevice, &fenceInfo, nullptr, &inFlightFence);
    
    LOGI("Vulkan Framebuffers and Sync Objects Ready!");
}
"""

# Now replace nativeSubmitAndPresent and nativeVkCmdDraw
new_submit = """
extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeSubmitAndPresent(JNIEnv* env, jobject /* this */) {
    if (!vkCtx.isInitialized || !vkCtx.swapchain) return;
    
    vkWaitForFences(vkCtx.logicalDevice, 1, &inFlightFence, VK_TRUE, UINT64_MAX);
    vkResetFences(vkCtx.logicalDevice, 1, &inFlightFence);

    uint32_t imageIndex;
    vkAcquireNextImageKHR(vkCtx.logicalDevice, vkCtx.swapchain, UINT64_MAX, imageAvailableSemaphore, VK_NULL_HANDLE, &imageIndex);

    // Record Command Buffer for this frame
    vkResetCommandBuffer(commandBuffers[imageIndex], 0);
    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    vkBeginCommandBuffer(commandBuffers[imageIndex], &beginInfo);

    VkRenderPassBeginInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderPassInfo.renderPass = renderPass;
    renderPassInfo.framebuffer = swapChainFramebuffers[imageIndex];
    renderPassInfo.renderArea.offset = {0, 0};
    renderPassInfo.renderArea.extent = swapChainExtent;
    VkClearValue clearColor = {{{0.05f, 0.05f, 0.15f, 1.0f}}};
    renderPassInfo.clearValueCount = 1;
    renderPassInfo.pClearValues = &clearColor;

    vkCmdBeginRenderPass(commandBuffers[imageIndex], &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
    // Draw calls would normally go here based on Maxwell translations!
    vkCmdEndRenderPass(commandBuffers[imageIndex]);
    vkEndCommandBuffer(commandBuffers[imageIndex]);

    // Submit
    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    VkSemaphore waitSemaphores[] = {imageAvailableSemaphore};
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = waitSemaphores;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &commandBuffers[imageIndex];
    VkSemaphore signalSemaphores[] = {renderFinishedSemaphore};
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = signalSemaphores;

    if (vkQueueSubmit(vkCtx.graphicsQueue, 1, &submitInfo, inFlightFence) != VK_SUCCESS) {
        LOGE("Failed to submit draw command buffer!");
    }

    // Present
    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = signalSemaphores;
    VkSwapchainKHR swapChains[] = {vkCtx.swapchain};
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = swapChains;
    presentInfo.pImageIndices = &imageIndex;

    vkQueuePresentKHR(vkCtx.graphicsQueue, &presentInfo);

    nativePacer.pace();
}
"""

if "// JNI BINDINGS" in code:
    code = code.replace("// JNI BINDINGS", swapchain_code + "\n// JNI BINDINGS")

submit_pattern = r'extern "C" JNIEXPORT void JNICALL\nJava_com_example_emulator_gpu_VulkanTranslator_nativeSubmitAndPresent.*?\n}'
code = re.sub(submit_pattern, new_submit, code, flags=re.DOTALL)

# Enable device extensions in createLogicalDevice
logic_device_pattern = r'VkDeviceCreateInfo createInfo\{\};\n    createInfo\.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;'
logic_device_replacement = """
    std::vector<const char*> deviceExtensions = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(deviceExtensions.size());
    createInfo.ppEnabledExtensionNames = deviceExtensions.data();
"""
code = re.sub(logic_device_pattern, logic_device_replacement, code)

with open("app/src/main/cpp/vulkan_backend.cpp", "w") as f:
    f.write(code)
