package com.kbd.ime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 从 Android KbdLayout 生成 webdemo 的对拍 fixture。
 * 运行：javac -d /tmp/kbd-fixture ... && java com.kbd.ime.GeometryFixture
 */
public final class GeometryFixture {

    private static final int[][] CASES = {
            {160, 256}, {160, 240}, {200, 300}, {256, 160}, {300, 260},
            {360, 360}, {360, 640}, {500, 500}, {500, 900}, {600, 900},
            {720, 1604}, {721, 1604}, {768, 1024}, {800, 800},
            {900, 600}, {1024, 768}, {1080, 2400}, {1280, 800},
            {1440, 3200}, {1604, 720}, {2340, 1080}
    };

    private static final String[] TEXTS = {
            "你", "你好", "中华人民共和国", "中华人民共和国成立",
            "中华人民共和国万岁中华人民共和国万岁中华人民共和国万岁"
    };

    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"cases\": [\n");
        for (int i = 0; i < CASES.length; i++) {
            int W = CASES[i][0];
            int H = CASES[i][1];
            KbdLayout L = new KbdLayout(W, H, 32);
            int rowKeys = L.landscape ? L.landRowKeys : 10;
            if (i > 0) {
                out.append(",\n");
            }
            out.append("    {\"W\":").append(W)
                    .append(",\"H\":").append(H)
                    .append(",\"b\":32")
                    .append(",\"mode\":\"").append(L.landscape ? "landscape" : "portrait").append('"')
                    .append(",\"n\":").append(L.n)
                    .append(",\"S\":").append(L.S)
                    .append(",\"K\":").append(L.k)
                    .append(",\"g\":").append(L.g)
                    .append(",\"c\":").append(L.c)
                    .append(",\"m\":").append(L.m)
                    .append(",\"keyH\":").append(L.keyH)
                    .append(",\"kbW\":").append(L.kbW)
                    .append(",\"kbH\":").append(L.kbH)
                    .append(",\"preeditX\":").append(L.preeditLeft)
                    .append(",\"preeditW\":").append(L.preeditWidth)
                    .append(",\"symbolCols\":").append(L.symbolCols)
                    .append(",\"rowKeys\":").append(rowKeys)
                    .append(",\"compact\":").append(L.compactLabels())
                    .append(",\"overflow\":").append(L.overflow)
                    .append(",\"tBeyond\":").append(L.tBeyond)
                    .append(",\"gCapped\":").append(L.gCappedByHeight)
                    .append(",\"kbRatio\":").append(String.format(java.util.Locale.ROOT, "%.6f", L.kbRatio))
                    .append(",\"candidates\":[");
            for (int j = 0; j < TEXTS.length; j++) {
                if (j > 0) {
                    out.append(',');
                }
                String text = TEXTS[j];
                out.append("{\"text\":\"").append(text)
                        .append("\",\"keys\":").append(L.candKeys(text, rowKeys))
                        .append(",\"lines\":").append(L.candLines(text, rowKeys).length)
                        .append('}');
            }
            out.append("],\"pack\":[");
            List<String> pack = new ArrayList<>(Arrays.asList(
                    "中华人民共和国", "你好", "拟好", "你"));
            List<KbdLayout.CandRow> rows = L.candidateRows(pack, rowKeys);
            for (int r = 0; r < rows.size(); r++) {
                if (r > 0) {
                    out.append(',');
                }
                KbdLayout.CandRow row = rows.get(r);
                out.append("{\"keys\":").append(row.keys)
                        .append(",\"slots\":").append(row.slots)
                        .append(",\"items\":[");
                for (int k = 0; k < row.items.size(); k++) {
                    if (k > 0) {
                        out.append(',');
                    }
                    out.append(row.items.get(k).keys);
                }
                out.append("]}");
            }
            out.append("]}");
        }
        out.append("\n  ]\n}\n");
        System.out.print(out);
    }
}
