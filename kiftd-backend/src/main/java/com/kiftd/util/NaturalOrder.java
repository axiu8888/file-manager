package com.kiftd.util;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * 文件名自然排序：1、2、10，而不是 1、10、2。
 * 与前端 localeCompare(..., { numeric: true }) 行为对齐。
 */
public final class NaturalOrder {
    private static final Collator COLLATOR = Collator.getInstance(Locale.CHINA);

    static {
        COLLATOR.setStrength(Collator.PRIMARY);
    }

    public static final Comparator<String> COMPARATOR = NaturalOrder::compare;

    private NaturalOrder() {}

    public static int compare(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        int ia = 0;
        int ib = 0;
        int na = a.length();
        int nb = b.length();
        while (ia < na && ib < nb) {
            char ca = a.charAt(ia);
            char cb = b.charAt(ib);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int za = ia;
                while (za < na && a.charAt(za) == '0') {
                    za++;
                }
                int zb = ib;
                while (zb < nb && b.charAt(zb) == '0') {
                    zb++;
                }
                int ea = za;
                while (ea < na && Character.isDigit(a.charAt(ea))) {
                    ea++;
                }
                int eb = zb;
                while (eb < nb && Character.isDigit(b.charAt(eb))) {
                    eb++;
                }
                int lenA = ea - za;
                int lenB = eb - zb;
                if (lenA != lenB) {
                    return Integer.compare(lenA, lenB);
                }
                for (int i = 0; i < lenA; i++) {
                    int d = a.charAt(za + i) - b.charAt(zb + i);
                    if (d != 0) {
                        return d;
                    }
                }
                // 数值相同：前导零更多的（更短有效位数前缀）排前面，与常见自然序一致
                int zerosA = za - ia;
                int zerosB = zb - ib;
                if (zerosA != zerosB) {
                    return Integer.compare(zerosA, zerosB);
                }
                ia = ea;
                ib = eb;
            } else {
                int cmp = COLLATOR.compare(String.valueOf(ca), String.valueOf(cb));
                if (cmp != 0) {
                    return cmp;
                }
                ia++;
                ib++;
            }
        }
        return Integer.compare(na - ia, nb - ib);
    }
}
