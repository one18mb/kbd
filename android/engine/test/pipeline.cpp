// 宿主机联调程序：验证 libime 的 input → candidates → select 全链路（小鹤双拼）。
// 用法：sh scripts/host-selftest.sh
#include <iostream>
#include <memory>
#include <string>

#include <libime/core/languagemodel.h>
#include <libime/core/userlanguagemodel.h>
#include <libime/pinyin/pinyincontext.h>
#include <libime/pinyin/pinyindictionary.h>
#include <libime/pinyin/pinyinime.h>
#include <libime/pinyin/shuangpinprofile.h>

namespace {

void dump(const char *tag, libime::PinyinContext &ctx) {
    std::cout << tag << " preedit=[" << ctx.preedit() << "] 候选:";
    size_t n = 0;
    for (const auto &c : ctx.candidates()) {
        if (n++ >= 8) {
            break;
        }
        std::cout << " " << c.toString();
    }
    std::cout << " (共 " << ctx.candidates().size() << ")\n";
}

} // namespace

int main(int argc, char **argv) {
    if (argc < 3) {
        std::cout << "usage: pipeline <sc.dict> <sc.lm>\n";
        return 1;
    }

    auto dict = std::make_unique<libime::PinyinDictionary>();
    dict->load(0, argv[1], libime::PinyinDictFormat::Binary);

    auto lmFile = std::make_shared<libime::StaticLanguageModelFile>(argv[2]);
    auto model = std::make_unique<libime::UserLanguageModel>(lmFile);

    libime::PinyinIME ime(std::move(dict), std::move(model));
    ime.setShuangpinProfile(std::make_shared<libime::ShuangpinProfile>(
        libime::ShuangpinBuiltinProfile::Xiaohe));

    libime::PinyinContext ctx(&ime);
    ctx.setUseShuangpin(true);

    // 小鹤输入 "nihc" -> ni'hao -> 你好
    for (char ch : std::string("nihc")) {
        ctx.type(std::string(1, ch));
    }
    dump("输入 nihc:", ctx);

    if (!ctx.candidates().empty()) {
        std::cout << "首选: " << ctx.candidates()[0].toString() << "\n";
        ctx.select(0);
        dump("选0后:", ctx);
        std::cout << "已上屏: [" << ctx.selectedSentence() << "]\n";
        ctx.learn();
    }

    // 分段选词：验证 selectedSentence() 是累积的（上层据此做增量上屏）
    ctx.clear();
    for (char ch : std::string("nihc")) {
        ctx.type(std::string(1, ch));
    }
    std::cout << "\n分段选词：\n";
    int single = -1;
    for (size_t i = 0; i < ctx.candidates().size(); ++i) {
        if (ctx.candidates()[i].toString().size() <= 3) { // 单个汉字（UTF-8 3 字节）
            single = static_cast<int>(i);
            break;
        }
        if (i > 10) {
            break;
        }
    }
    if (single >= 0) {
        std::cout << "  先选单字 index=" << single << " ("
                  << ctx.candidates()[single].toString() << ")\n";
        ctx.select(static_cast<size_t>(single));
        std::cout << "  累积句=" << ctx.selectedSentence()
                  << "   preedit=" << ctx.preedit() << "\n";
        if (!ctx.candidates().empty()) {
            ctx.select(0);
            std::cout << "  再选首选，累积句=" << ctx.selectedSentence()
                      << "   preedit=" << ctx.preedit()
                      << "   selected=" << (ctx.selected() ? "true" : "false") << "\n";
        }
    } else {
        std::cout << "  未找到单字候选\n";
    }

    return 0;
}
