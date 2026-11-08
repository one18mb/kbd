#include <jni.h>

#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include <libime/core/userlanguagemodel.h>
#include <libime/pinyin/pinyincontext.h>
#include <libime/pinyin/pinyindictionary.h>
#include <libime/pinyin/pinyinime.h>
#include <libime/pinyin/shuangpinprofile.h>

namespace {

std::string versionString() {
    return "kbd-engine 0.1 / libime 1.1.17";
}

std::string selfTest() {
    using namespace libime;
    ShuangpinProfile profile(ShuangpinBuiltinProfile::Xiaohe);
    std::ostringstream os;
    os << "libime 已链接\n";
    os << "小鹤双拼：内置 profile\n";
    os << "合法键数 " << profile.validInput().size()
       << "，映射表 " << profile.table().size() << " 项";
    return os.str();
}

/**
 * 引擎实例。所有方法由 JNI 侧在一个互斥锁内调用，暂不假设多线程。
 */
class Engine {
public:
    bool load(const std::string &dir) {
        try {
            auto dict = std::make_unique<libime::PinyinDictionary>();
            dict->load(0, (dir + "/sc.dict").c_str(),
                       libime::PinyinDictFormat::Binary);

            auto lmFile = std::make_shared<libime::StaticLanguageModelFile>(
                (dir + "/sc.lm").c_str());
            auto model = std::make_unique<libime::UserLanguageModel>(lmFile);

            ime_ = std::make_unique<libime::PinyinIME>(std::move(dict),
                                                       std::move(model));
            ime_->setShuangpinProfile(std::make_shared<libime::ShuangpinProfile>(
                libime::ShuangpinBuiltinProfile::Xiaohe));

            ctx_ = std::make_unique<libime::PinyinContext>(ime_.get());
            ctx_->setUseShuangpin(flypy_);
            ready_ = true;
            error_.clear();
            committed_.clear();
            return true;
        } catch (const std::exception &e) {
            error_ = e.what();
            ready_ = false;
            ctx_.reset();
            ime_.reset();
            return false;
        }
    }

    bool ready() const { return ready_; }

    const std::string &error() const { return error_; }

    void setShuangpin(bool flypy) {
        flypy_ = flypy;
        if (ctx_) {
            ctx_->setUseShuangpin(flypy);
        }
    }

    bool input(char key) {
        if (!ready_) {
            return false;
        }
        return ctx_->type(std::string(1, key));
    }

    void backspace() {
        if (!ready_) {
            return;
        }
        if (!ctx_->backspace()) {
            return;
        }
        if (ctx_->size() == 0) {
            ctx_->clear();
            committed_.clear();
        }
    }

    std::string preedit() const {
        return ready_ ? ctx_->preedit() : std::string();
    }

    std::vector<std::string> candidates(int limit) const {
        std::vector<std::string> out;
        if (!ready_) {
            return out;
        }
        const auto &list = ctx_->candidates();
        const size_t n = limit > 0 ? static_cast<size_t>(limit) : list.size();
        out.reserve(n < list.size() ? n : list.size());
        for (size_t i = 0; i < list.size() && i < n; ++i) {
            out.push_back(list[i].toString());
        }
        return out;
    }

    /**
     * 选中候选，返回本次新增的上屏文本。
     * selectedSentence() 会累积整段组词，因此这里按已上屏前缀做差，避免重复。
     */
    std::string select(int index) {
        if (!ready_) {
            return {};
        }
        const auto &list = ctx_->candidates();
        if (index < 0 || static_cast<size_t>(index) >= list.size()) {
            return {};
        }
        ctx_->select(static_cast<size_t>(index));
        std::string sentence = ctx_->selectedSentence();
        std::string delta;
        if (sentence.size() > committed_.size()) {
            delta = sentence.substr(committed_.size());
        }
        committed_ = sentence;
        if (ctx_->selected()) {
            ctx_->clear();
            committed_.clear();
        }
        return delta;
    }

    void reset() {
        if (ctx_) {
            ctx_->clear();
        }
        committed_.clear();
    }

    void saveUserDict() {
        /* M2.1 接入用户词典持久化，当前无用户数据文件。 */
    }

private:
    std::unique_ptr<libime::PinyinIME> ime_;
    std::unique_ptr<libime::PinyinContext> ctx_;
    bool ready_ = false;
    bool flypy_ = true;
    std::string error_;
    /** 当前组词内已上屏的前缀，用于计算 select 的增量。 */
    std::string committed_;
};

Engine &engine() {
    static Engine instance;
    return instance;
}

std::mutex &engineMutex() {
    static std::mutex mutex;
    return mutex;
}

jstring newString(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_kbd_ime_LibimeEngine_nativeVersion(JNIEnv *env, jclass) {
    return newString(env, versionString());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kbd_ime_LibimeEngine_nativeSelfTest(JNIEnv *env, jclass) {
    return newString(env, selfTest());
}

extern "C" JNIEXPORT void JNICALL
Java_com_kbd_ime_LibimeEngine_nativeLoad(JNIEnv *env, jclass, jstring dir) {
    const char *chars = env->GetStringUTFChars(dir, nullptr);
    std::string path = chars ? chars : "";
    env->ReleaseStringUTFChars(dir, chars);

    std::lock_guard<std::mutex> lock(engineMutex());
    if (!engine().load(path)) {
        jclass ex = env->FindClass("java/lang/IllegalStateException");
        if (ex) {
            env->ThrowNew(ex, engine().error().c_str());
        }
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kbd_ime_LibimeEngine_nativeIsReady(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(engineMutex());
    return engine().ready() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kbd_ime_LibimeEngine_nativeSetShuangpin(JNIEnv *, jclass,
                                                 jboolean flypy) {
    std::lock_guard<std::mutex> lock(engineMutex());
    engine().setShuangpin(flypy == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kbd_ime_LibimeEngine_nativeInput(JNIEnv *, jclass, jchar key) {
    std::lock_guard<std::mutex> lock(engineMutex());
    return engine().input(static_cast<char>(key)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kbd_ime_LibimeEngine_nativeBackspace(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(engineMutex());
    engine().backspace();
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_kbd_ime_LibimeEngine_nativeState(JNIEnv *env, jclass, jint limit) {
    std::lock_guard<std::mutex> lock(engineMutex());
    std::string preedit = engine().preedit();
    std::vector<std::string> list = engine().candidates(limit);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(
        static_cast<jsize>(list.size()), stringClass, nullptr);
    for (size_t i = 0; i < list.size(); ++i) {
        jstring item = newString(env, list[i]);
        env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
        env->DeleteLocalRef(item);
    }

    jclass stateClass = env->FindClass("com/kbd/ime/EngineBridge$State");
    jmethodID ctor = env->GetMethodID(stateClass, "<init>",
                                      "(Ljava/lang/String;[Ljava/lang/String;)V");
    jstring pre = newString(env, preedit);
    jobject state = env->NewObject(stateClass, ctor, pre, array);
    env->DeleteLocalRef(pre);
    env->DeleteLocalRef(array);
    return state;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kbd_ime_LibimeEngine_nativeSelect(JNIEnv *env, jclass, jint index) {
    std::lock_guard<std::mutex> lock(engineMutex());
    return newString(env, engine().select(index));
}

extern "C" JNIEXPORT void JNICALL
Java_com_kbd_ime_LibimeEngine_nativeReset(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(engineMutex());
    engine().reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_kbd_ime_LibimeEngine_nativeSaveUserDict(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(engineMutex());
    engine().saveUserDict();
}
