cmake_minimum_required(VERSION 3.22.1)
project(kpswe C)
set(SWE ${CMAKE_CURRENT_SOURCE_DIR}/swisseph)
add_library(kpswe SHARED swe_jni.c
    ${SWE}/sweph.c ${SWE}/swephlib.c ${SWE}/swecl.c ${SWE}/swehouse.c ${SWE}/swedate.c
    ${SWE}/swejpl.c ${SWE}/swemmoon.c ${SWE}/swemplan.c ${SWE}/swehel.c)
target_compile_options(kpswe PRIVATE -O2 -w)
target_link_libraries(kpswe m)
