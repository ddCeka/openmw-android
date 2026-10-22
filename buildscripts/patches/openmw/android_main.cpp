//int stderr = 0; // Hack: fix linker error

#include "SDL_main.h"
#include "engine.hpp"
#include "mwbase/windowmanager.hpp"
#include <SDL_gamecontroller.h>
#include <SDL_mouse.h>
#include <SDL_events.h>
#include <SDL_hints.h>

#include <osg/GraphicsContext>
#include <osg/OperationThread>

/*******************************************************************************
 Functions called by JNI
 *******************************************************************************/
#include <jni.h>

#include <adrenotools/driver.h>
#include <dlfcn.h>
#include <string>

/* Called before  to initialize JNI bindings  */

extern void SDL_Android_Init(JNIEnv* env, jclass cls);
extern int argcData;
extern const char **argvData;
void releaseArgv();


void* g_vulkanHandle = nullptr;

static std::string withTrailingSlash(std::string dir)
{
    if (!dir.empty() && dir.back() != '/')
        dir += '/';
    return dir;
}

bool initVulkanDriver()
{
    if (g_vulkanHandle)
        return true;

    const std::string libDir = withTrailingSlash(getenv("LIB_DIR"));
    const std::string tmpDir = withTrailingSlash(getenv("TMP_DIR"));
    const std::string driverDir = withTrailingSlash(getenv("FILES_DIR"));
    const std::string name = getenv("DRIVER_NAME");

    g_vulkanHandle = adrenotools_open_libvulkan(
        RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, tmpDir.c_str(), libDir.c_str(),
        driverDir.c_str(), name.c_str(), nullptr, nullptr);

    if (!g_vulkanHandle)
        g_vulkanHandle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);

    if (!g_vulkanHandle)
        return false;

    return true;
}

extern "C" int Java_org_libsdl_app_SDLActivity_getMouseX(JNIEnv *env, jclass cls, jobject obj) {
    int ret = 0;
    SDL_GetMouseState(&ret, nullptr);
    return ret;
}


extern "C" int Java_org_libsdl_app_SDLActivity_getMouseY(JNIEnv *env, jclass cls, jobject obj) {
    int ret = 0;
    SDL_GetMouseState(nullptr, &ret);
    return ret;
}

extern "C" int Java_org_libsdl_app_SDLActivity_isMouseShown(JNIEnv *env, jclass cls, jobject obj) {
    return SDL_ShowCursor(SDL_QUERY);
}

extern SDL_Window *Android_Window;
extern "C" int SDL_SendMouseMotion(SDL_Window * window, int mouseID, int relative, int x, int y);
extern "C" void Java_org_libsdl_app_SDLActivity_sendRelativeMouseMotion(JNIEnv *env, jclass cls, int x, int y) {
    SDL_SendMouseMotion(Android_Window, 0, 1, x, y);
}

extern "C" int SDL_SendMouseButton(SDL_Window * window, int mouseID, Uint8 state, Uint8 button);
extern "C" void Java_org_libsdl_app_SDLActivity_sendMouseButton(JNIEnv *env, jclass cls, int state, int button) {
    SDL_SendMouseButton(Android_Window, 0, state, button);
}

extern "C" int Java_org_libsdl_app_SDLActivity_nativeInit(JNIEnv* env, jclass cls, jobject obj) {
    setenv("OPENMW_DECOMPRESS_TEXTURES", "1", 1);

    initVulkanDriver();

    // On Android, we use a virtual controller with guid="Virtual"
     SDL_GameControllerAddMapping("5669727475616c000000000000000000,Virtual,a:b0,b:b1,back:b15,dpdown:h0.4,dpleft:h0.8,dpright:h0.2,dpup:h0.1,guide:b16,leftshoulder:b6,leftstick:b13,lefttrigger:a5,leftx:a0,lefty:a1,rightshoulder:b7,rightstick:b14,righttrigger:a4,rightx:a2,righty:a3,start:b11,x:b3,y:b4");

    SDL_SetHint(SDL_HINT_ANDROID_BLOCK_ON_PAUSE, "0");
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");

    return 0;
}

extern osg::ref_ptr<osgViewer::Viewer> g_viewer;
static osg::GraphicsContext *ctx;

class CtxReleaseOperation : public osg::Operation {
public:
    virtual void operator () (osg::Object* caller) {
        ctx->releaseContext();
    }
};

class CtxAcquireOperation : public osg::Operation {
public:
    virtual void operator () (osg::Object* caller) {
        ctx->makeCurrent();
    }
};

extern "C" void Java_org_libsdl_app_SDLActivity_omwSurfaceDestroyed(JNIEnv *env, jclass cls, jobject obj) {
  try {
    osg::GraphicsContext::GraphicsContexts contexts = osg::GraphicsContext::getRegisteredGraphicsContexts(0);
    if (!contexts.empty() && contexts[0])
    {
      osg::ref_ptr<CtxReleaseOperation> op = new CtxReleaseOperation();
      ctx = contexts[0];
      ctx->add(op);
    }
  } catch (...) {}
}

extern "C" void Java_org_libsdl_app_SDLActivity_omwSurfaceRecreated(JNIEnv *env, jclass cls, jobject obj) {
  try {
    osg::GraphicsContext::GraphicsContexts contexts = osg::GraphicsContext::getRegisteredGraphicsContexts(0);
    if (!contexts.empty() && contexts[0])
    {
      osg::ref_ptr<CtxAcquireOperation> op = new CtxAcquireOperation();
      ctx = contexts[0];
      ctx->add(op);
    }
  } catch (...) {}
}
