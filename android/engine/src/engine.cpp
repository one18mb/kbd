#include <jni.h>

#include <cstdint>
#include <fstream>
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

/** 上文最多保留 10 个字符（按字符，不按词）。 */
constexpr size_t kHistoryChars = 10;

size_t utf8CharLen(uint8_t c) {
    if (c < 0x80) {
        return 1;
    }
    if ((c >> 5) == 0x6) {
        return 2;
    }
    if ((c >> 4) == 0xE) {
        return 3;
    }
    if ((c >> 3) == 0x1E) {
        return 4;
    }
    return 1;
}

std::vector<std::string> splitUtf8(const std::string &text) {
    std::vector<std::string> out;
    for (size_t i = 0; i < text.size();) {
        size_t len = utf8CharLen(static_cast<uint8_t>(text[i]));
        if (i + len > text.size()) {
            len = text.size() - i;
        }
        out.emplace_back(text, i, len);
        i += len;
    }
    return out;
}

size_t utf8Count(const std::string &text) {
    size_t n = 0;
    for (size_t i = 0; i < text.size(); i += utf8CharLen(static_cast<uint8_t>(text[i]))) {
        ++n;
    }
    return n;
}

std::string versionString() {
    return "kbd-engine 1.1 / libime 1.1.17";
}

/** libime 1.1.17 内置 8 种双拼；KBD 只暴露 26 键的 5 种，quanpin 不使用 profile。 */
libime::ShuangpinBuiltinProfile builtinProfile(const std::string &scheme) {
    using P = libime::ShuangpinBuiltinProfile;
    if (scheme == "ziranma") {
        return P::Ziranma;
    }
    if (scheme == "abc") {
        return P::ABC;
    }
    if (scheme == "zhongwenzhixing") {
        return P::Zhongwenzhixing;
    }
    if (scheme == "gb") {
        return P::GB;
    }
    return P::Xiaohe;
}

std::string selfTest() {
    using namespace libime;
    struct Item {
        const char *code;
        ShuangpinBuiltinProfile profile;
    };
    const Item items[] = {
            {"xiaohe", ShuangpinBuiltinProfile::Xiaohe},
            {"ziranma", ShuangpinBuiltinProfile::Ziranma},
            {"abc", ShuangpinBuiltinProfile::ABC},
            {"zhongwenzhixing", ShuangpinBuiltinProfile::Zhongwenzhixing},
            {"gb", ShuangpinBuiltinProfile::GB},
    };
    std::ostringstream os;
    os << "libime 已链接\n";
    os << "26 键双拼 5 种 + 全拼 quanpin\n";
    for (const auto &item : items) {
        ShuangpinProfile profile(item.profile);
        os << item.code << "：合法键 " << profile.validInput().size()
           << "，映射 " << profile.table().size() << " 项\n";
    }
    return os.str();
}

/**
 * 引擎实例。所有方法由 JNI 侧在一个互斥锁内调用，暂不假设多线程。
 */
class Engine {
public:
    bool load(const std::string &dir, const std::string &userDir) {
        try {
            auto dict = std::make_unique<libime::PinyinDictionary>();
            dict->load(0, (dir + "/sc.dict").c_str(),
                       libime::PinyinDictFormat::Binary);

            auto lmFile = std::make_shared<libime::StaticLanguageModelFile>(
                (dir + "/sc.lm").c_str());
            auto model = std::make_unique<libime::UserLanguageModel>(lmFile);

            // A：用户自学习（HistoryBigram）落盘文件
            userFile_ = userDir + "/history.bigram";
            {
                std::ifstream in(userFile_);
                if (in) {
                    model->load(in);
                }
            }

            ime_ = std::make_unique<libime::PinyinIME>(std::move(dict),
                                                       std::move(model));
            ime_->setShuangpinProfile(std::make_shared<libime::ShuangpinProfile>(
                builtinProfile(scheme_)));

            ctx_ = std::make_unique<libime::PinyinContext>(ime_.get());
            ctx_->setUseShuangpin(scheme_ != "quanpin");
            history_.clear();
            applyContext();   // B：恢复跨输入框的上文
            ready_ = true;
            dirty_ = false;
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

    void setScheme(const std::string &scheme) {
        scheme_ = scheme;
        if (ready_ && ime_) {
            applyScheme();
        }
    }

    /** 换方案时重建 context；保留上文。 */
    void applyScheme() {
        ime_->setShuangpinProfile(std::make_shared<libime::ShuangpinProfile>(
            builtinProfile(scheme_)));
        ctx_.reset();
        ctx_ = std::make_unique<libime::PinyinContext>(ime_.get());
        ctx_->setUseShuangpin(scheme_ != "quanpin");
        committed_.clear();
        applyContext();
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
            // A：把「上文 + 本次整段」记进 HistoryBigram
            ctx_->learn();
            // B：整段进入跨输入框上文，并刷新 context words
            for (const auto &word : ctx_->selectedWordsWithPinyin()) {
                history_.push_back(word);
            }
            trimHistory();
            ctx_->clear();
            committed_.clear();
            applyContext();
        }
        dirty_ = true;
        return delta;
    }

    void reset() {
        if (ctx_) {
            if (ctx_->selected()) {
                ctx_->learn();
            }
            for (const auto &word : ctx_->selectedWordsWithPinyin()) {
                history_.push_back(word);
            }
            trimHistory();
            ctx_->clear();
            applyContext();
        }
        committed_.clear();
    }

    /** 非候选上屏（英文/符号/转义文本）：按字符计入上文。 */
    void recordText(const std::string &text) {
        if (!ready_ || text.empty()) {
            return;
        }
        addHistory(text, std::string());
    }

    /** 已上屏文本被删除：按字符裁掉上文末尾（不关心编辑器实际删了哪个）。 */
    void recordBackspace() {
        if (!ready_ || history_.empty()) {
            return;
        }
        auto chars = splitUtf8(history_.back().first);
        if (chars.size() <= 1) {
            history_.pop_back();
        } else {
            std::string head;
            for (size_t i = 0; i + 1 < chars.size(); ++i) {
                head += chars[i];
            }
            history_.back() = {head, std::string()};
        }
        trimHistory();
        applyContext();
        dirty_ = true;
    }

    void saveUserDict() {
        if (!ready_ || userFile_.empty()) {
            return;
        }
        std::ofstream out(userFile_);
        if (!out) {
            return;
        }
        ime_->model()->save(out);
        dirty_ = false;
    }

private:
    void applyContext() {
        if (ctx_) {
            ctx_->setContextWordsWithPinyin(history_);
        }
    }

    void addHistory(const std::string &text, const std::string &code) {
        if (!code.empty()) {
            history_.emplace_back(text, code);
        } else {
            for (const auto &ch : splitUtf8(text)) {
                history_.emplace_back(ch, std::string());
            }
        }
        trimHistory();
        applyContext();
        dirty_ = true;
    }

    /** 上文按字符裁剪到 kHistoryChars。 */
    void trimHistory() {
        size_t total = 0;
        for (const auto &w : history_) {
            total += utf8Count(w.first);
        }
        while (total > kHistoryChars && !history_.empty()) {
            total -= utf8Count(history_.front().first);
            history_.erase(history_.begin());
        }
    }

    std::unique_ptr<libime::PinyinIME> ime_;
    std::unique_ptr<libime::PinyinContext> ctx_;
    /** 跨输入框的上文（词 + 拼音码），最多 kHistoryChars 个字符。 */
    std::vector<libime::HistoryBigram::WordWithCode> history_;
    std::string userFile_;
    bool ready_ = false;
    bool dirty_ = false;
    std::string scheme_ = "quanpin";
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
Java_com_kbd_ime_LibimeEngine_nativeLoad(JNIEnv *env, jclass, jstring dir,
                                         jstring userDir) {
    const char *chars = env->GetStringUTFChars(dir, nullptr);
    std::string path = chars ? chars : "";
    env->ReleaseStringUTFChars(dir, chars);
    const char *userChars = env->GetStringUTFChars(userDir, nullptr);
    std::string userPath = userChars ? userChars : "";
    env->ReleaseStringUTFChars(userDir, userChars);

    std::lock_guard<std::mutex> lock(engineMutex());
    if (!engine().load(path, userPath)) {
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
Java_com_kbd_ime_LibimeEngine_nativeSetScheme(JNIEnv *env, jclass,
                                              jstring scheme) {
    const char *chars = env->GetStringUTFChars(scheme, nullptr);
    std::string value = chars ? chars : "quanpin";
    env->ReleaseStringUTFChars(scheme, chars);
    std::lock_guard<std::mutex> lock(engineMutex());
    engine().setScheme(value);
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
Java_com_kbd_ime_LibimeEngine_nativeRecordText(JNIEnv *env, jclass,
                                               jstring text) {
    const char *chars = env->GetStringUTFChars(text, nullptr);
    std::string value = chars ? chars : "";
    env->ReleaseStringUTFChars(text, chars);
    std::lock_guard<std::mutex> lock(engineMutex());
    engine().recordText(value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_kbd_ime_LibimeEngine_nativeRecordBackspace(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(engineMutex());
    engine().recordBackspace();
}

extern "C" JNIEXPORT void JNICALL
Java_com_kbd_ime_LibimeEngine_nativeSaveUserDict(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(engineMutex());
    engine().saveUserDict();
}
