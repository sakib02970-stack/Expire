#include <jni.h>
#include <string>
#include <sys/ptrace.h>
#include <android/asset_manager.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/sysinfo.h>
#include <android/asset_manager_jni.h>
#include <sys/time.h>
#include <dirent.h>
#include <sys/uio.h>
#include <sys/syscall.h>
#include <math.h>
#include <iostream>
#include <thread>
#include <android/log.h>
#include <curl/curl.h>
#include "eLogin/json.hpp"
#include "ImGuiInEGL.h"
#include "eLogin/Login.h"

// Simple XOR decryption
std::string XOR_decryption(const std::string& data, const std::string& key) {
    std::string result = data;
    for (size_t i = 0; i < data.size(); i++) {
        result[i] = data[i] ^ key[i % key.size()];
    }
    return result;
}

std::string get_Key_From_Public(const std::string& pub_key) {
    // Generate decryption key from public key
    std::string key = pub_key;
    for (size_t i = 0; i < key.size(); i++) {
        key[i] = key[i] ^ 0x5A;
    }
    return key;
}

std::string performOnlineLogin(const std::string& userKey, const std::string& hwid) {
    CURL *curl;
    CURLcode res;
    struct MemoryStruct chunk;
    chunk.memory = (char *)malloc(1);
    chunk.size = 0;

    std::string response;

    curl = curl_easy_init();
    if (curl) {
        // Create JSON request
        json requestData;
        requestData["game"] = "FreeFire";
        requestData["key"] = userKey;
        requestData["hwid"] = hwid;
        requestData["publicKey"] = "Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9EVm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E";

        std::string jsonStr = requestData.dump();

        curl_easy_setopt(curl, CURLOPT_URL, "https://lowhost.shop/connect");
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, jsonStr.c_str());
        
        struct curl_slist *headers = NULL;
        headers = curl_slist_append(headers, "Content-Type: application/json");
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteMemoryCallback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *)&chunk);
        
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
        
        res = curl_easy_perform(curl);
        
        if (res == CURLE_OK) {
            try {
                json responseJson = json::parse(chunk.memory);
                
                if (responseJson["status"] == true) {
                    std::string enc_msg = responseJson["auth"]["message"];
                    std::string pub_key = responseJson["auth"]["token_access"];
                    std::string dec_msg = XOR_decryption(enc_msg, get_Key_From_Public(pub_key));
                    
                    __android_log_print(ANDROID_LOG_ERROR, "LOGIN", "Decrypted: %s", dec_msg.c_str());
                    
                    if (dec_msg.find("Success") != std::string::npos || dec_msg.length() > 0) {
                        response = "OK";
                    } else {
                        response = "Security Validation Failed";
                    }
                } else {
                    response = responseJson["message"];
                    if (response.empty()) response = "Login Failed";
                }
            } catch (const std::exception& e) {
                __android_log_print(ANDROID_LOG_ERROR, "LOGIN", "JSON Parse Error: %s", e.what());
                response = "Parser Error";
            }
        } else {
            __android_log_print(ANDROID_LOG_ERROR, "LOGIN", "CURL Error: %d", res);
            response = "Connect Error";
        }
        
        curl_slist_free_all(headers);
        curl_easy_cleanup(curl);
    }
    
    free(chunk.memory);
    return response;
}

std::string SavePath, Kami, Imei;
ImGuiInPut *InPut = new ImGuiInPut;
ImGuiInEGL *InEGL = new ImGuiInEGL;

extern "C"
{
    // ONLINE LOGIN - Server verification
    jstring JNICALL login(JNIEnv *env, jclass clazz, jstring mUserKey) {
        const char* userKey = env->GetStringUTFChars(mUserKey, 0);
        
        // Get device HWID
        std::string hwid = Imei.empty() ? "UnknownDevice" : Imei;
        
        __android_log_print(ANDROID_LOG_ERROR, "LOGIN", "Attempting online login for key: %s, hwid: %s", userKey, hwid.c_str());
        
        // Perform online verification
        std::string result = performOnlineLogin(std::string(userKey), hwid);
        
        __android_log_print(ANDROID_LOG_ERROR, "LOGIN", "Login result: %s", result.c_str());
        
        env->ReleaseStringUTFChars(mUserKey, userKey);
        
        if (result == "OK") {
            return env->NewStringUTF("OK");
        } else {
            std::string errorMsg = "Login Failed: " + result;
            return env->NewStringUTF(errorMsg.c_str());
        }
    }

    void SurfaceCreate(JNIEnv * env, jobject thiz, jobject surface, jint width, jint high) {
        InEGL->onSurfaceCreate(env, surface, width, high);
        InEGL->SetSaveMessage(SavePath, Kami, Imei);
        InEGL->SetInPut(InPut);
    }
    
    void SurfaceChange(JNIEnv * env, jobject thiz, jint widt, jint heig) {
        InEGL->onSurfaceChange(widt, heig);
    }
    
    void MotionEventClick(JNIEnv * env, jobject thiz, jint action, jfloat pos_x, jfloat pos_y) {
        InPut->InputTouchEvent(action, pos_x, pos_y);
    }
    
    float winData[4];
    jfloatArray GetImGuiwinsize(JNIEnv * env, jobject thiz) {
        jfloatArray newFloatArray = env->NewFloatArray(4);
        if (InEGL->Window) {
            winData[0] = InEGL->Window->Pos.x;
            winData[1] = InEGL->Window->Pos.y;
            winData[2] = InEGL->Window->Size.x;
            winData[3] = InEGL->Window->Size.y;
            env->SetFloatArrayRegion(newFloatArray, 0, 4, winData);
            return newFloatArray;
        }
        return newFloatArray;
    }
    
    void SetKamiImei(JNIEnv *env, jclass clazz, jstring km, jstring jqm) {
        Imei = env->GetStringUTFChars(jqm, JNI_FALSE);
        Kami = env->GetStringUTFChars(km, JNI_FALSE);
        __android_log_print(ANDROID_LOG_ERROR, "LOGIN", "HWID Set: %s", Imei.c_str());
    }
    
    void SetSavePath(JNIEnv *env, jclass clazz, jstring PH) {
        SavePath = env->GetStringUTFChars(PH, JNI_FALSE);
    }
    
    #ifndef NELEM
    # define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
    #endif

    int jniRegisterNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *gMethods, int numMethods) {
        jclass clazz = env->FindClass(className);
        if (clazz == NULL) {
            return JNI_FALSE;
        }
        if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
    
    static JNINativeMethod native_method[] = {    
        {"SetKamiImei", "(Ljava/lang/String;Ljava/lang/String;)V", (void*) SetKamiImei},
        {"SurfaceCreate", "(Landroid/view/Surface;II)V", (void*) SurfaceCreate},
        {"SetSavePath", "(Ljava/lang/String;)V", (void*) SetSavePath},
        {"MotionEventClick", "(IFF)V", (void*) MotionEventClick},
        {"GetImGuiwinsize", "()[F", (void*) GetImGuiwinsize},
        {"Check", "(Ljava/lang/String;)Ljava/lang/String;", (void*) login},
        {"SurfaceChange", "(II)V", (void*) SurfaceChange}
    };
    
    int native_api(JNIEnv *env) {
        return jniRegisterNativeMethods(env,
            "ropl/momo/item/JNIInterface",
            native_method,
            NELEM(native_method));
    }
    
    jint JNI_OnLoad(JavaVM *vm, void *reserved) {
        JNIEnv *env;
        if (vm->GetEnv((void**) (&env), JNI_VERSION_1_6) != JNI_OK) {
            return -1;
        }
        assert(env != NULL);
        if (!native_api(env)) {
            return -1;
        }
        return JNI_VERSION_1_6;
    }
}
