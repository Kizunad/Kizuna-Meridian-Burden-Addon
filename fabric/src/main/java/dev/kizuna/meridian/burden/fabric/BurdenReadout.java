package dev.kizuna.meridian.burden.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 负重读数的文字：摘要里一行，悬浮里几行。只把服务端发来的读数写成字，不重新算负重。 */
public final class BurdenReadout {
    private static final double PERCENT = 100;
    private static final List<BurdenTier> TIERS_BELOW_OVERLOAD =
            List.of(BurdenTier.LIGHT, BurdenTier.MEDIUM, BurdenTier.HEAVY);

    private BurdenReadout() {
        // 只有静态入口。
    }

    /** 摘要那一行，例如「负重：18.0 / 224 kg · 轻（实重 9.0 kg）」。 */
    public static String line(BurdenView view) {
        // 算不出时不给千克数，只说按哪一档结算。
        String tier = BurdenTier.of(view.multiplier(), view.overloadMultiplier()).label();
        if (!view.available()) {
            return "负重：算不出 · 按" + tier + "计";
        }
        return String.format(Locale.ROOT, "负重：%.1f / %.0f kg · %s（实重 %.1f kg）",
                kilograms(view.effectiveGrams()), limitKilograms(view), tier, kilograms(view.totalGrams()));
    }

    /** 悬浮说明：实重和负重各是什么、腿多累几成、四档从多少千克算起。 */
    public static List<String> hover(BurdenView view) {
        var lines = new ArrayList<String>();
        double extra = (view.multiplier() - BurdenReading.UNLOADED) * PERCENT;
        if (view.available()) {
            lines.add(String.format(Locale.ROOT, "随身东西实重 %.1f kg，按放在哪里折算成负重 %.1f kg，上限 %.0f kg",
                    kilograms(view.totalGrams()), kilograms(view.effectiveGrams()), limitKilograms(view)));
            lines.add(String.format(Locale.ROOT, "现在走、跑时腿多累 %.0f%%", extra));
        } else {
            lines.add("随身容器或物品太多、套得太深，或者物品数据异常，重量算不出");
            lines.add(String.format(Locale.ROOT, "算不出时走、跑腿多累 %.0f%%，整理随身物品后恢复", extra));
        }
        lines.add(tiers(view));
        lines.add("四档只是提醒：负重每重一分，腿就多累一分；负重本身不减移动速度");
        return List.copyOf(lines);
    }

    private static String tiers(BurdenView view) {
        // 超载线设得低时，够不着的档不列。
        double overload = view.overloadMultiplier();
        var parts = new ArrayList<String>();
        double from = BurdenReading.UNLOADED;
        for (BurdenTier tier : TIERS_BELOW_OVERLOAD) {
            if (from >= overload) {
                break;
            }
            double below = tier.below(overload);
            parts.add(String.format(Locale.ROOT, "不到 %.0f kg %s",
                    BurdenTier.kilogramsAt(below, view.referenceKilograms()), tier.label()));
            from = below;
        }
        parts.add("再多" + BurdenTier.OVERLOADED.label());
        return String.join("，", parts);
    }

    /** 负重上限的千克数，也就是超载线；别的界面要显示上限时取这里，不另算。 */
    public static double limitKilograms(BurdenView view) {
        return BurdenTier.kilogramsAt(view.overloadMultiplier(), view.referenceKilograms());
    }

    private static double kilograms(long grams) {
        return grams / BurdenReading.GRAMS_PER_KILOGRAM;
    }
}
