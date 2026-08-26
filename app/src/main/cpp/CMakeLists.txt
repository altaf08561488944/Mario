cmake_minimum_required(VERSION 3.22.1)
project("vulkan_backend")

add_library(
        vulkan_backend
        SHARED
        vulkan_backend.cpp
        vulkan_validation.cpp
        pipeline_cache.cpp
        cpu_engine.cpp
        gpu_engine.cpp
        gpu_maxwell.cpp
        shader_recompiler.cpp
        horizon_hle.cpp
        horizon_ipc.cpp
        memory_mmu.cpp
        fs_parser.cpp)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
        vulkan_backend
        ${log-lib}
        ${android-lib}
        vulkan)
