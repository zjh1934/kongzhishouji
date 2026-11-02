cmake_minimum_required(VERSION 3.22.1)
project(mikalocal CXX)

# P3a 只放一个空壳 .so，验证「给 Mika 首次引入 native 构建」这条路通不通。
# 真编 llama.cpp / mtmd 是 P3b 的事，先把地基踩稳。
add_library(mikalocal SHARED native-bridge.cpp)

target_link_libraries(mikalocal android log)
