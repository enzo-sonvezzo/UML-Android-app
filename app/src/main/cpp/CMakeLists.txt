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

target_link_libraries(
    uml_jni
    ${log-lib}
    util
)
