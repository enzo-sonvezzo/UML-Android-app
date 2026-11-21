cmake_minimum_required(VERSION 3.22.1)

project("uml_jni")

add_library(
    uml_jni
    SHARED
    uml_jni.cpp
)

find_library(
    log-lib
    log
)

find_library(
    egl-lib
    EGL
)

find_library(
    gles-lib
    GLESv3
)

target_link_libraries(
    uml_jni
    ${log-lib}
    ${egl-lib}
    ${gles-lib}
)
