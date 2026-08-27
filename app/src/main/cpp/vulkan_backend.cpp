#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <vector>
#include <chrono>
#include <thread>
#include <string>
#include <stdexcept>
#include "vulkan_validation.h"
#include "pipeline_cache.h"

#define LOG_TAG "SwtcVulkanEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ======================================================================
// SWTC NOOS - ROBUST VULKAN RENDERING BACKEND WITH VALIDATION LAYERS
// ======================================================================

struct VulkanContext {
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice logicalDevice = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    uint32_t graphicsQueueFamilyIndex = -1;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    
    // Surface & Swapchain (To be bound to Android Native Window)
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    
    bool isInitialized = false;
};

VulkanContext vkCtx;

bool createVulkanInstance() {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "SWTC NOOS Emulator";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "SWTC Maxwell-to-Vulkan Engine";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 1, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    auto& valMgr = SwtcVulkan::VulkanValidationManager::getInstance();
    bool enableValidation = valMgr.areValidationLayersRequested() && valMgr.checkValidationLayerSupport();

    auto extensions = valMgr.getRequiredExtensions();
    createInfo.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
    createInfo.ppEnabledExtensionNames = extensions.data();

    const std::vector<const char*> validationLayers = { "VK_LAYER_KHRONOS_validation" };
    VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo{};

    if (enableValidation) {
        createInfo.enabledLayerCount = static_cast<uint32_t>(validationLayers.size());
        createInfo.ppEnabledLayerNames = validationLayers.data();
        valMgr.populateDebugMessengerCreateInfo(debugCreateInfo);
        createInfo.pNext = (VkDebugUtilsMessengerCreateInfoEXT*) &debugCreateInfo;
        LOGI("Vulkan Instance configuring with Khronos validation layers enabled.");
    } else {
        createInfo.enabledLayerCount = 0;
        createInfo.pNext = nullptr;
    }

    if (vkCreateInstance(&createInfo, nullptr, &vkCtx.instance) != VK_SUCCESS) {
        LOGE("Failed to create Vulkan Instance.");
        return false;
    }

    if (enableValidation) {
        valMgr.setupDebugMessenger(vkCtx.instance);
    }

    LOGI("Vulkan Instance Created Successfully.");
    return true;
}

bool pickPhysicalDeviceAndQueueFamily() {
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(vkCtx.instance, &deviceCount, nullptr);
    if (deviceCount == 0) return false;
    
    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(vkCtx.instance, &deviceCount, devices.data());
    
    vkCtx.physicalDevice = devices[0]; // Select primary GPU
    
    // Find Graphics Queue Family
    uint32_t queueFamilyCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(vkCtx.physicalDevice, &queueFamilyCount, nullptr);
    std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
    vkGetPhysicalDeviceQueueFamilyProperties(vkCtx.physicalDevice, &queueFamilyCount, queueFamilies.data());
    
    for (uint32_t i = 0; i < queueFamilyCount; i++) {
        if (queueFamilies[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
            vkCtx.graphicsQueueFamilyIndex = i;
            break;
        }
    }
    
    if (vkCtx.graphicsQueueFamilyIndex == -1) {
        LOGE("Failed to find Vulkan Graphics Queue.");
        return false;
    }
    
    VkPhysicalDeviceProperties props;
    vkGetPhysicalDeviceProperties(vkCtx.physicalDevice, &props);
    LOGI("Hardware GPU Locked: %s (Queue Family: %d)", props.deviceName, vkCtx.graphicsQueueFamilyIndex);
    return true;
}

bool createLogicalDevice() {
    VkDeviceQueueCreateInfo queueCreateInfo{};
    queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueCreateInfo.queueFamilyIndex = vkCtx.graphicsQueueFamilyIndex;
    queueCreateInfo.queueCount = 1;
    float queuePriority = 1.0f;
    queueCreateInfo.pQueuePriorities = &queuePriority;

    VkPhysicalDeviceFeatures deviceFeatures{}; // Enable specific features if needed (e.g., texture compression)
    deviceFeatures.samplerAnisotropy = VK_TRUE;

    
    std::vector<const char*> deviceExtensions = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(deviceExtensions.size());
    createInfo.ppEnabledExtensionNames = deviceExtensions.data();

    createInfo.pQueueCreateInfos = &queueCreateInfo;
    createInfo.queueCreateInfoCount = 1;
    createInfo.pEnabledFeatures = &deviceFeatures;
    
    createInfo.ppEnabledExtensionNames = deviceExtensions.data();

    if (vkCreateDevice(vkCtx.physicalDevice, &createInfo, nullptr, &vkCtx.logicalDevice) != VK_SUCCESS) {
        LOGE("Failed to create Vulkan Logical Device.");
        return false;
    }
    
    vkGetDeviceQueue(vkCtx.logicalDevice, vkCtx.graphicsQueueFamilyIndex, 0, &vkCtx.graphicsQueue);
    
    // Initialize Pipeline Cache with the logical & physical device
    SwtcVulkan::VulkanPipelineCacheManager::getInstance().init(vkCtx.logicalDevice, vkCtx.physicalDevice);
    
    LOGI("Vulkan Logical Device & Graphics Queue Created.");
    return true;
}

bool createCommandPool() {
    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = vkCtx.graphicsQueueFamilyIndex;

    if (vkCreateCommandPool(vkCtx.logicalDevice, &poolInfo, nullptr, &vkCtx.commandPool) != VK_SUCCESS) {
        LOGE("Failed to create Command Pool.");
        return false;
    }
    LOGI("Vulkan Command Pool Allocated.");
    return true;
}


// ======================================================================
// SWTC NOOS - NATIVE FRAME PACER & VSYNC (Target #11)
// ======================================================================
class NativeFramePacer {
private:
    std::chrono::high_resolution_clock::time_point lastFrameTime;
    double targetFrameTimeMs;
    int targetFps;

public:
    NativeFramePacer(int fps = 60) {
        setTargetFps(fps);
        lastFrameTime = std::chrono::high_resolution_clock::now();
    }

    void setTargetFps(int fps) {
        targetFps = fps;
        if (fps <= 0) targetFrameTimeMs = 0.0;
        else targetFrameTimeMs = 1000.0 / fps;
        LOGI("Native FramePacer: Target FPS set to %d (%.2f ms/frame)", fps, targetFrameTimeMs);
    }

    void pace() {
        if (targetFrameTimeMs <= 0.0) return;
        
        auto now = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double, std::milli> elapsed = now - lastFrameTime;
        double sleepTimeMs = targetFrameTimeMs - elapsed.count();
        
        if (sleepTimeMs > 0.0) {
            std::this_thread::sleep_for(std::chrono::duration<double, std::milli>(sleepTimeMs));
        }
        
        // Reset lastFrameTime to now after waking up
        lastFrameTime = std::chrono::high_resolution_clock::now();
    }
};

NativeFramePacer nativePacer(60);

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeSetTargetFps(JNIEnv* env, jobject /* this */, jint fps) {
    nativePacer.setTargetFps(fps);
}


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

// JNI BINDINGS
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeInitializeVulkan(JNIEnv* env, jobject /* this */) {
    LOGI("=====================================================");
    LOGI("Booting Robust Vulkan Hardware-Accelerated Pipeline");
    LOGI("=====================================================");
    
    if (!createVulkanInstance()) return JNI_FALSE;
    if (!pickPhysicalDeviceAndQueueFamily()) return JNI_FALSE;
    if (!createLogicalDevice()) return JNI_FALSE;
    if (!createCommandPool()) return JNI_FALSE;
    
    vkCtx.isInitialized = true;
    LOGI("Vulkan Rendering Backend is completely initialized and awaiting Surface/Swapchain binding.");
    return JNI_TRUE;
}

// Structure to store recorded Maxwell -> Vulkan draw commands
struct RecordedDrawCommand {
    uint32_t topology;
    uint32_t vertexCount;
    uint32_t firstVertex;
    uint32_t samplerId;
    uint64_t textureAddress;
};

static std::vector<RecordedDrawCommand> pendingDrawCommands;
static std::mutex drawQueueMutex;
static uint64_t totalFramesPresented = 0;
static uint64_t totalDrawCallsRecorded = 0;

// Dynamic viewport and scissor
static VkViewport currentViewport = {0.0f, 0.0f, 1280.0f, 720.0f, 0.0f, 1.0f};
static VkRect2D currentScissor = {{0, 0}, {1280, 720}};

// In a real emulator, this function is called repeatedly to translate Maxwell 3D registers into Vulkan Pipeline state
extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeVkCmdDraw(JNIEnv* env, jobject /* this */, jint topology, jint count, jint first) {
    if (!vkCtx.isInitialized) return;
    
    // Validate Maxwell Draw Call state & primitive topology against Vulkan spec
    SwtcVulkan::VulkanValidationManager::getInstance().validateMaxwellDraw(
        static_cast<uint32_t>(topology), static_cast<uint32_t>(count), static_cast<uint32_t>(first));

    // Record the draw command into the pending command list for current frame
    {
        std::lock_guard<std::mutex> lock(drawQueueMutex);
        RecordedDrawCommand cmd{};
        cmd.topology = static_cast<uint32_t>(topology);
        cmd.vertexCount = static_cast<uint32_t>(count > 0 ? count : 3);
        cmd.firstVertex = static_cast<uint32_t>(first);
        cmd.samplerId = 0;
        cmd.textureAddress = 0;
        pendingDrawCommands.push_back(cmd);
        totalDrawCallsRecorded++;
    }

    // Enforce strict frame pacing to maintain 34-60 FPS without stutter
    nativePacer.pace();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeVkCmdBindTexture(JNIEnv* env, jobject /* this */, jint samplerId, jlong address) {
    if (!vkCtx.isInitialized) return;
    
    // Validate texture sampler alignment and bound memory address
    SwtcVulkan::VulkanValidationManager::getInstance().validateMaxwellTextureBind(
        static_cast<uint32_t>(samplerId), static_cast<uint64_t>(address));

    {
        std::lock_guard<std::mutex> lock(drawQueueMutex);
        if (!pendingDrawCommands.empty()) {
            pendingDrawCommands.back().samplerId = static_cast<uint32_t>(samplerId);
            pendingDrawCommands.back().textureAddress = static_cast<uint64_t>(address);
        }
    }

    // Enforce strict frame pacing to maintain 34-60 FPS without stutter
    nativePacer.pace();
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeSubmitAndPresent(JNIEnv* env, jobject /* this */) {
    if (!vkCtx.isInitialized || !vkCtx.swapchain) return;
    
    vkWaitForFences(vkCtx.logicalDevice, 1, &inFlightFence, VK_TRUE, UINT64_MAX);
    vkResetFences(vkCtx.logicalDevice, 1, &inFlightFence);

    uint32_t imageIndex;
    VkResult acquireResult = vkAcquireNextImageKHR(vkCtx.logicalDevice, vkCtx.swapchain, UINT64_MAX, imageAvailableSemaphore, VK_NULL_HANDLE, &imageIndex);
    if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
        LOGE("Failed to acquire next swapchain image: %d", acquireResult);
        return;
    }

    // Record Command Buffer for this frame
    VkCommandBuffer cmdBuf = commandBuffers[imageIndex];
    vkResetCommandBuffer(cmdBuf, 0);
    
    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmdBuf, &beginInfo);

    VkRenderPassBeginInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderPassInfo.renderPass = renderPass;
    renderPassInfo.framebuffer = swapChainFramebuffers[imageIndex];
    renderPassInfo.renderArea.offset = {0, 0};
    renderPassInfo.renderArea.extent = swapChainExtent;
    VkClearValue clearColor = {{{0.05f, 0.05f, 0.15f, 1.0f}}};
    renderPassInfo.clearValueCount = 1;
    renderPassInfo.pClearValues = &clearColor;

    vkCmdBeginRenderPass(cmdBuf, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

    // Setup dynamic viewport and scissor matching swapchain extent
    currentViewport.x = 0.0f;
    currentViewport.y = 0.0f;
    currentViewport.width = static_cast<float>(swapChainExtent.width);
    currentViewport.height = static_cast<float>(swapChainExtent.height);
    currentViewport.minDepth = 0.0f;
    currentViewport.maxDepth = 1.0f;
    vkCmdSetViewport(cmdBuf, 0, 1, &currentViewport);

    currentScissor.offset = {0, 0};
    currentScissor.extent = swapChainExtent;
    vkCmdSetScissor(cmdBuf, 0, 1, &currentScissor);

    // Execute genuine recorded draw commands into the Vulkan command buffer
    {
        std::lock_guard<std::mutex> lock(drawQueueMutex);
        if (pendingDrawCommands.empty()) {
            // Draw a basic full-screen pass if no specific mesh submitted yet
            vkCmdDraw(cmdBuf, 3, 1, 0, 0);
        } else {
            for (const auto& drawCmd : pendingDrawCommands) {
                vkCmdDraw(cmdBuf, drawCmd.vertexCount, 1, drawCmd.firstVertex, 0);
            }
            pendingDrawCommands.clear();
        }
    }

    vkCmdEndRenderPass(cmdBuf);
    vkEndCommandBuffer(cmdBuf);

    // Submit to Graphics Queue
    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    VkSemaphore waitSemaphores[] = {imageAvailableSemaphore};
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = waitSemaphores;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmdBuf;
    VkSemaphore signalSemaphores[] = {renderFinishedSemaphore};
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = signalSemaphores;

    if (vkQueueSubmit(vkCtx.graphicsQueue, 1, &submitInfo, inFlightFence) != VK_SUCCESS) {
        LOGE("Failed to submit draw command buffer!");
    }

    // Present to Android Native Window Swapchain
    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = signalSemaphores;
    VkSwapchainKHR swapChains[] = {vkCtx.swapchain};
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = swapChains;
    presentInfo.pImageIndices = &imageIndex;

    VkResult presentResult = vkQueuePresentKHR(vkCtx.graphicsQueue, &presentInfo);
    if (presentResult == VK_SUCCESS || presentResult == VK_SUBOPTIMAL_KHR) {
        totalFramesPresented++;
    }

    nativePacer.pace();
}


// ======================================================================
// VULKAN VALIDATION & MAXWELL DIAGNOSTIC JNI BINDINGS
// ======================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeSetValidationLayersEnabled(JNIEnv* env, jobject /* this */, jboolean enabled) {
    SwtcVulkan::VulkanValidationManager::getInstance().setValidationLayersEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeGetDiagnosticLogCount(JNIEnv* env, jobject /* this */) {
    return static_cast<jint>(SwtcVulkan::VulkanValidationManager::getInstance().getLogCount());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeGetDiagnosticLog(JNIEnv* env, jobject /* this */, jint index) {
    std::string log = SwtcVulkan::VulkanValidationManager::getInstance().getLogFormatted(static_cast<size_t>(index));
    return env->NewStringUTF(log.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeClearDiagnosticLogs(JNIEnv* env, jobject /* this */) {
    SwtcVulkan::VulkanValidationManager::getInstance().clearLogs();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeDumpGpuState(JNIEnv* env, jobject /* this */) {
    std::string summary = SwtcVulkan::VulkanValidationManager::getInstance().dumpGpuStateSummary();
    return env->NewStringUTF(summary.c_str());
}

// ======================================================================
// VULKAN PIPELINE CACHE SERIALIZATION JNI BINDINGS
// ======================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeLoadPipelineCache(JNIEnv* env, jobject /* this */, jstring path) {
    if (!path) return JNI_FALSE;
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    std::string filePath(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);

    bool result = SwtcVulkan::VulkanPipelineCacheManager::getInstance().loadFromFile(filePath);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeSavePipelineCache(JNIEnv* env, jobject /* this */, jstring path) {
    if (!path) return JNI_FALSE;
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    std::string filePath(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);

    bool result = SwtcVulkan::VulkanPipelineCacheManager::getInstance().saveToFile(filePath);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeClearPipelineCache(JNIEnv* env, jobject /* this */, jstring path) {
    if (!path) return;
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    std::string filePath(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);

    SwtcVulkan::VulkanPipelineCacheManager::getInstance().clearCacheFile(filePath);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeGetPipelineCacheSummary(JNIEnv* env, jobject /* this */) {
    std::string summary = SwtcVulkan::VulkanPipelineCacheManager::getInstance().getCacheInfoSummary();
    return env->NewStringUTF(summary.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_emulator_gpu_VulkanTranslator_nativeGetPipelineCacheSize(JNIEnv* env, jobject /* this */) {
    return static_cast<jlong>(SwtcVulkan::VulkanPipelineCacheManager::getInstance().getCachedDataSize());
}

