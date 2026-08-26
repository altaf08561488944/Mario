import re

with open("app/src/main/cpp/gpu_engine.cpp", "r") as f:
    code = f.read()

sync_manager_code = """
class VulkanFrameSyncManager {
public:
    VkSemaphore imageAvailableSemaphore = VK_NULL_HANDLE;
    VkSemaphore renderFinishedSemaphore = VK_NULL_HANDLE;
    VkFence inFlightFence = VK_NULL_HANDLE;
    VkDevice logicalDevice = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;

    static VulkanFrameSyncManager& getInstance() {
        static VulkanFrameSyncManager instance;
        return instance;
    }

    void initialize(VkDevice device, VkQueue queue) {
        logicalDevice = device;
        graphicsQueue = queue;

        VkSemaphoreCreateInfo semaphoreInfo{};
        semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;

        VkFenceCreateInfo fenceInfo{};
        fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

        vkCreateSemaphore(logicalDevice, &semaphoreInfo, nullptr, &imageAvailableSemaphore);
        vkCreateSemaphore(logicalDevice, &semaphoreInfo, nullptr, &renderFinishedSemaphore);
        vkCreateFence(logicalDevice, &fenceInfo, nullptr, &inFlightFence);
        
        SwtcVulkan::VulkanValidationManager::getInstance().recordDiagnostic(
            SwtcVulkan::DiagnosticSeverity::Info, "FrameSync", "Vulkan Fence and Semaphore Synchronization Initialized.", 0);
    }

    void waitForPreviousFrame() {
        if (logicalDevice && inFlightFence) {
            vkWaitForFences(logicalDevice, 1, &inFlightFence, VK_TRUE, UINT64_MAX);
            vkResetFences(logicalDevice, 1, &inFlightFence);
            SwtcVulkan::VulkanValidationManager::getInstance().recordDiagnostic(
                SwtcVulkan::DiagnosticSeverity::Verbose, "FrameSync", "Fence synchronized: Race condition prevented.", 0);
        }
    }

    void submitFrame(VkCommandBuffer commandBuffer) {
        if (!graphicsQueue) return;

        VkSubmitInfo submitInfo{};
        submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;

        VkSemaphore waitSemaphores[] = {imageAvailableSemaphore};
        VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
        submitInfo.waitSemaphoreCount = 1;
        submitInfo.pWaitSemaphores = waitSemaphores;
        submitInfo.pWaitDstStageMask = waitStages;

        submitInfo.commandBufferCount = 1;
        submitInfo.pCommandBuffers = &commandBuffer;

        VkSemaphore signalSemaphores[] = {renderFinishedSemaphore};
        submitInfo.signalSemaphoreCount = 1;
        submitInfo.pSignalSemaphores = signalSemaphores;

        if (vkQueueSubmit(graphicsQueue, 1, &submitInfo, inFlightFence) != VK_SUCCESS) {
            LOGE("Failed to submit draw command buffer!");
            SwtcVulkan::VulkanValidationManager::getInstance().recordDiagnostic(
                SwtcVulkan::DiagnosticSeverity::Error, "FrameSync", "vkQueueSubmit failed during frame submission.", 0);
        } else {
            SwtcVulkan::VulkanValidationManager::getInstance().recordDiagnostic(
                SwtcVulkan::DiagnosticSeverity::Verbose, "FrameSync", "Frame command buffer submitted with Semaphore sync.", 0);
        }
    }
};

GpuEngine& GpuEngine::getInstance() {
"""

code = code.replace("GpuEngine& GpuEngine::getInstance() {", sync_manager_code)

# Let's add a sync demonstration in writeGpuRegister
write_find = r'if \(offset == 0x40\) \{ // Placeholder offset for Pushbuffer Trigger\n        // For example, if value is a pointer to a command list\n        // executeCommandList\(value, 256\);\n    \}'
write_replace = """if (offset == 0x40) { // Placeholder offset for Pushbuffer Trigger
        // For example, if value is a pointer to a command list
        // executeCommandList(value, 256);
    } else if (offset == 0x44) { // Simulated Display Surface Flip / End of Frame
        auto& syncMgr = VulkanFrameSyncManager::getInstance();
        
        // 1. Wait for previous frame execution to prevent CPU-GPU race conditions
        syncMgr.waitForPreviousFrame();
        
        // 2. Submit the recorded frame command buffer to the Vulkan queue safely
        VkCommandBuffer mockCmdBuffer = VK_NULL_HANDLE; 
        syncMgr.submitFrame(mockCmdBuffer);
    }"""
code = re.sub(write_find, write_replace, code)

with open("app/src/main/cpp/gpu_engine.cpp", "w") as f:
    f.write(code)

