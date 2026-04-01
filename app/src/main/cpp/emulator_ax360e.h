// SPDX-License-Identifier: WTFPL

#ifndef AX360E_EMULATOR_AX360E_H
#define AX360E_EMULATOR_AX360E_H

#include <jni.h>
#include <string>
#include <vector>

extern jclass g_class_DocumentFile;
extern jclass g_class_Emulator;

extern jobject g_context;
extern jobject g_doocument_file_tree;

extern jmethodID mid_open_uri_fd;

extern std::vector<std::string> g_launch_args;
extern std::string g_uri_info_list_file_path;
extern std::string g_native_lib_dir;

#endif //AX360E_EMULATOR_AX360E_H
