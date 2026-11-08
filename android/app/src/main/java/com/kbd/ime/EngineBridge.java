package com.kbd.ime;

/**
 * 引擎接口：前端只依赖这个接口，libime 实现可替换。
 */
public interface EngineBridge {

    void load(String dictDir);

    boolean isReady();

    void setShuangpin(boolean flypy);

    void input(char key);

    void backspace();

    State state(int candidateLimit);

    String select(int index);

    void reset();

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
