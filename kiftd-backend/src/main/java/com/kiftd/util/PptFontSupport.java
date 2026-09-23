package com.kiftd.util;

import org.apache.poi.common.usermodel.fonts.FontInfo;
import org.apache.poi.sl.draw.DrawFontManagerDefault;
import org.apache.poi.sl.draw.Drawable;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * PPT/PPTX 渲染中文字体支持。
 * <p>
 * POI 用 {@code Font#getFontName()} 写入 {@code TextAttribute.FAMILY}，思源/Noto 等
 * face 名与 family 不一致时中文会变方块。策略：
 * <ul>
 *   <li>正文映射到本机已验证的 CJK 字体（微软雅黑等）</li>
 *   <li>缺字回退到同时覆盖 © 等符号的字体（Segoe UI / Arial）</li>
 * </ul>
 */
public final class PptFontSupport {

    public static final String CACHE_TAG = "cjk-font-v9";

    private static final String CJK_PROBE = "汉字测试年终总结Aa";
    private static final String SYMBOL_PROBE = "©•—…★◆【】";

    private static final String[] PREFERRED_CJK = {
            "微软雅黑",
            "Microsoft YaHei",
            "Microsoft YaHei UI",
            "Noto Sans SC",
            "SimHei",
            "黑体",
            "Microsoft JhengHei UI",
            "Noto Serif SC",
            "SimSun",
            "宋体",
            "PingFang SC"
    };

    private static final String[] PREFERRED_SYMBOL = {
            "Segoe UI",
            "Arial",
            "Microsoft Sans Serif",
            "Microsoft YaHei",
            "Tahoma",
            "Calibri"
    };

    private static final Set<String> SYMBOL_FONTS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        SYMBOL_FONTS.add("Wingdings");
        SYMBOL_FONTS.add("Wingdings 2");
        SYMBOL_FONTS.add("Wingdings 3");
        SYMBOL_FONTS.add("Symbol");
        SYMBOL_FONTS.add("Webdings");
        SYMBOL_FONTS.add("Marlett");
    }

    private static volatile String cjkFamily;
    private static volatile String symbolFamily;

    private PptFontSupport() {
    }

    public static void prepareGraphics(Graphics2D g) {
        ensureInit();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("*", cjkFamily);
        map.put("+mn-ea", cjkFamily);
        map.put("+mj-ea", cjkFamily);
        map.put("+mn-lt", cjkFamily);
        map.put("+mj-lt", cjkFamily);
        map.put("+mn-cs", cjkFamily);
        map.put("+mj-cs", cjkFamily);

        Map<String, String> fallbackMap = new LinkedHashMap<>();
        fallbackMap.put("*", symbolFamily);

        g.setRenderingHint(Drawable.FONT_MAP, map);
        g.setRenderingHint(Drawable.FONT_FALLBACK, fallbackMap);
        g.setRenderingHint(Drawable.FONT_HANDLER, new CjkDrawFontManager(cjkFamily, symbolFamily));
    }

    public static String cjkFallbackFamily() {
        ensureInit();
        return cjkFamily;
    }

    private static void ensureInit() {
        if (cjkFamily != null) {
            return;
        }
        synchronized (PptFontSupport.class) {
            if (cjkFamily != null) {
                return;
            }
            Map<String, String> lower = loadFamilies();
            cjkFamily = toPoiFamilyName(pick(lower, PREFERRED_CJK, true));
            symbolFamily = toPoiFamilyName(pick(lower, PREFERRED_SYMBOL, false));
            if (!canDisplay(symbolFamily, SYMBOL_PROBE)) {
                for (String family : lower.values()) {
                    String poiName = toPoiFamilyName(family);
                    if (canDisplay(poiName, SYMBOL_PROBE)) {
                        symbolFamily = poiName;
                        break;
                    }
                }
            }
            if (!canDisplay(cjkFamily, CJK_PROBE)) {
                cjkFamily = toPoiFamilyName(Font.SANS_SERIF);
            }
        }
    }

    /** POI 用 getFontName 当 FAMILY，这里统一转成 face 名 */
    private static String toPoiFamilyName(String logicalOrFace) {
        if (logicalOrFace == null || logicalOrFace.isBlank()) {
            return Font.SANS_SERIF;
        }
        Font font = new Font(logicalOrFace, Font.PLAIN, 12);
        String face = font.getFontName(Locale.ROOT);
        if (face == null || face.isBlank() || Font.DIALOG.equalsIgnoreCase(font.getFamily())) {
            return logicalOrFace;
        }
        // 用 face 重建，确保后续 getFontName == 可用家族解析名
        Font physical = new Font(face, Font.PLAIN, 12);
        if (Font.DIALOG.equalsIgnoreCase(physical.getFamily())) {
            return logicalOrFace;
        }
        return face;
    }

    private static Map<String, String> loadFamilies() {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames(Locale.ROOT);
        Map<String, String> lower = new LinkedHashMap<>();
        for (String f : families) {
            if (f != null && !f.isBlank()) {
                lower.putIfAbsent(f.toLowerCase(Locale.ROOT), f);
            }
        }
        return lower;
    }

    private static String pick(Map<String, String> lower, String[] preferred, boolean requireCjk) {
        for (String want : preferred) {
            String hit = lower.get(want.toLowerCase(Locale.ROOT));
            if (hit == null) {
                continue;
            }
            if (requireCjk && !canDisplay(hit, CJK_PROBE)) {
                continue;
            }
            if (!requireCjk && !canDisplay(hit, SYMBOL_PROBE) && !canDisplay(hit, CJK_PROBE)) {
                continue;
            }
            return hit;
        }
        if (requireCjk) {
            for (String family : lower.values()) {
                if (canDisplay(family, CJK_PROBE)) {
                    return family;
                }
            }
            return Font.SANS_SERIF;
        }
        return cjkFamily != null ? cjkFamily : Font.SANS_SERIF;
    }

    private static boolean canDisplay(String family, String probe) {
        try {
            Font font = new Font(family, Font.PLAIN, 12);
            if (Font.DIALOG.equalsIgnoreCase(font.getFamily()) && !Font.DIALOG.equalsIgnoreCase(family)) {
                return false;
            }
            return font.canDisplayUpTo(probe) == -1;
        } catch (Exception e) {
            return false;
        }
    }

    static final class CjkDrawFontManager extends DrawFontManagerDefault {
        private final String cjk;
        private final String symbol;

        CjkDrawFontManager(String cjk, String symbol) {
            this.cjk = cjk;
            this.symbol = symbol;
        }

        private boolean isSymbolFont(FontInfo fontInfo) {
            if (fontInfo == null) {
                return false;
            }
            String tf = fontInfo.getTypeface();
            return tf != null && SYMBOL_FONTS.contains(tf);
        }

        @Override
        public FontInfo getMappedFont(Graphics2D graphics, FontInfo fontInfo) {
            if (isSymbolFont(fontInfo)) {
                return super.getMappedFont(graphics, fontInfo);
            }
            return () -> cjk;
        }

        @Override
        public FontInfo getFallbackFont(Graphics2D graphics, FontInfo fontInfo) {
            if (isSymbolFont(fontInfo)) {
                return super.getFallbackFont(graphics, fontInfo);
            }
            // 主字体缺的字形（如 ©）走符号能力更强的字体
            return () -> symbol;
        }

        @Override
        public Font createAWTFont(Graphics2D graphics, FontInfo fontInfo, double fontSize, boolean bold, boolean italic) {
            if (isSymbolFont(fontInfo)) {
                return super.createAWTFont(graphics, fontInfo, fontSize, bold, italic);
            }
            int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
            String tf = fontInfo != null ? fontInfo.getTypeface() : null;
            String logical = symbol.equals(tf) ? symbol : cjk;
            Font base = new Font(logical, style, 12);
            // POI 把 Font#getFontName() 填进 TextAttribute.FAMILY。
            // 微软雅黑的 face 名是「微软雅黑」而 family 是 Microsoft YaHei，必须按 face 名重建，
            // 否则 AttributedString 解析失败，© 等符号会画成方块。
            String face = base.getFontName(Locale.ROOT);
            Font physical = (face != null && !face.isBlank()) ? new Font(face, style, 12) : base;
            if (Font.DIALOG.equalsIgnoreCase(physical.getFamily()) && !Font.DIALOG.equalsIgnoreCase(face)) {
                physical = base;
            }
            return physical.deriveFont((float) fontSize);
        }
    }
}
