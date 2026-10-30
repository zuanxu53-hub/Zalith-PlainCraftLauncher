//
// Created by maks on 21.09.2022.
//
#include <stddef.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <string.h>
#include "br_loader.h"
#include "egl_loader.h"

EGLBoolean (*eglMakeCurrent_p) (EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
EGLBoolean (*eglDestroyContext_p) (EGLDisplay dpy, EGLContext ctx);
EGLBoolean (*eglDestroySurface_p) (EGLDisplay dpy, EGLSurface surface);
EGLBoolean (*eglTerminate_p) (EGLDisplay dpy);
EGLBoolean (*eglReleaseThread_p) (void);
EGLContext (*eglGetCurrentContext_p) (void);
EGLDisplay (*eglGetDisplay_p) (NativeDisplayType display);
EGLBoolean (*eglInitialize_p) (EGLDisplay dpy, EGLint *major, EGLint *minor);
EGLBoolean (*eglChooseConfig_p) (EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config);
EGLBoolean (*eglGetConfigAttrib_p) (EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint *value);
EGLBoolean (*eglBindAPI_p) (EGLenum api);
EGLSurface (*eglCreatePbufferSurface_p) (EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list);
EGLSurface (*eglCreateWindowSurface_p) (EGLDisplay dpy, EGLConfig config, NativeWindowType window, const EGLint *attrib_list);
EGLBoolean (*eglSwapBuffers_p) (EGLDisplay dpy, EGLSurface draw);
EGLint (*eglGetError_p) (void);
EGLContext (*eglCreateContext_p) (EGLDisplay dpy, EGLConfig config, EGLContext share_list, const EGLint *attrib_list);
EGLBoolean (*eglSwapInterval_p) (EGLDisplay dpy, EGLint interval);
EGLSurface (*eglGetCurrentSurface_p) (EGLint readdraw);
EGLBoolean (*eglQuerySurface_p)(EGLDisplay display, EGLSurface surface, EGLint attribute, EGLint * value);

/**
 * 这个名字是不是 ANGLE 的 libGLESv2。
 *
 * 只比文件名本体（basename），不比整串：渲染器插件（MobileGlues 的「借用启动器 ANGLE」）
 * 交付的往往是**绝对路径** `…/lib/arm64/libGLESv2_angle.so`，拿整串去 `strncmp`
 * 前缀永远不相等 —— 于是走 else 分支，把 libGLESv2_angle.so 当成 EGL 库去 dlopen，
 * 失败后回落到系统 libEGL.so，表现就是「选了 ANGLE，进游戏却没在用 ANGLE」。
 */
static int isAngleGLES(const char* name) {
    if (name == NULL) return 0;
    const char* base = strrchr(name, '/');
    base = (base == NULL) ? name : base + 1;
    return strncmp(base, "libGLESv2_angle", 16) == 0;
}

/**
 * 由 GLES 库名推出同目录下的 ANGLE EGL 库名。
 *
 * 带目录前缀时把目录原样保留（插件的 so 可能在自己 APK 的 lib 目录里，
 * 不带上目录是找不到的）；不带目录就直接给文件名。
 */
static void angleEGLName(const char* gles, char* out, size_t outSize) {
    const char* slash = strrchr(gles, '/');
    if (slash != NULL) {
        size_t dirLen = (size_t) (slash - gles) + 1;
        if (dirLen + strlen("libEGL_angle.so") + 1 <= outSize) {
            memcpy(out, gles, dirLen);
            strcpy(out + dirLen, "libEGL_angle.so");
            return;
        }
    }
    strncpy(out, "libEGL_angle.so", outSize - 1);
    out[outSize - 1] = '\0';
}

void dlsym_EGL() {
    void* dl_handle = NULL;
    char* eglName = NULL;
    char angleEglName[512];
    char* gles = getenv("LIBGL_GLES");

    if (isAngleGLES(gles))
    {
        angleEGLName(gles, angleEglName, sizeof(angleEglName));
        eglName = angleEglName;
    } else {
        // LIBGL_EGL 是插件可以显式指定的 EGL 库（ANGLE 之外的自定义实现也走这里）
        char* directEgl = getenv("LIBGL_EGL");
        char* execEgl = getenv("POJAVEXEC_EGL");
        eglName = directEgl ? directEgl : (gles ? gles : (execEgl ? execEgl : "libEGL.so"));
    }

    if (eglName)
        dl_handle = dlopen(eglName, RTLD_LOCAL | RTLD_LAZY);

    if (dl_handle == NULL)
        dl_handle = dlopen("libEGL.so", RTLD_LOCAL | RTLD_LAZY);

    if (dl_handle == NULL) abort();

    eglBindAPI_p = GLGetProcAddress(dl_handle, "eglBindAPI");
    eglChooseConfig_p = GLGetProcAddress(dl_handle, "eglChooseConfig");
    eglCreateContext_p = GLGetProcAddress(dl_handle, "eglCreateContext");
    eglCreatePbufferSurface_p = GLGetProcAddress(dl_handle, "eglCreatePbufferSurface");
    eglCreateWindowSurface_p = GLGetProcAddress(dl_handle, "eglCreateWindowSurface");
    eglDestroyContext_p = GLGetProcAddress(dl_handle, "eglDestroyContext");
    eglDestroySurface_p = GLGetProcAddress(dl_handle, "eglDestroySurface");
    eglGetConfigAttrib_p = GLGetProcAddress(dl_handle, "eglGetConfigAttrib");
    eglGetCurrentContext_p = GLGetProcAddress(dl_handle, "eglGetCurrentContext");
    eglGetDisplay_p = GLGetProcAddress(dl_handle, "eglGetDisplay");
    eglGetError_p = GLGetProcAddress(dl_handle, "eglGetError");
    eglInitialize_p = GLGetProcAddress(dl_handle, "eglInitialize");
    eglMakeCurrent_p = GLGetProcAddress(dl_handle, "eglMakeCurrent");
    eglSwapBuffers_p = GLGetProcAddress(dl_handle, "eglSwapBuffers");
    eglReleaseThread_p = GLGetProcAddress(dl_handle, "eglReleaseThread");
    eglSwapInterval_p = GLGetProcAddress(dl_handle, "eglSwapInterval");
    eglTerminate_p = GLGetProcAddress(dl_handle, "eglTerminate");
    eglGetCurrentSurface_p = GLGetProcAddress(dl_handle,"eglGetCurrentSurface");
    eglQuerySurface_p = GLGetProcAddress(dl_handle, "eglQuerySurface");
}
