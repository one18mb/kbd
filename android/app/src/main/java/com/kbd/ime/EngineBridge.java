package com.kbd.ime;

/**
 * 引擎接口：前端只依赖这个接口，libime 实现可替换。
 */
public interface EngineBridge {

    /** dataDir = 只读词典目录；userDir = 可写用户数据（自学习历史）。 */
    void load(String dataDir, String userDir);

    boolean isReady();

    /** quanpin / xiaohe / ziranma / abc / zhongwenzhixing / gb */
    void setScheme(String scheme);

    void input(char key);

    void backspace();

    State state(int candidateLimit);

    String select(int index);

    void reset();

    /** 非候选上屏（英文/符号/转义），按字符计入上文。 */
    void recordText(String text);

    /** 已上屏文本被删除，裁掉上文末尾一个字符。 */
    void recordBackspace();

    void saveUserDict();

    final class State {
        public final String preedit;
        public final String[] candidates;

        public State(String preedit, String[] candidates) {
            this.preedit = preedit;
            this.candidates = candidates;
        }
    }
}
