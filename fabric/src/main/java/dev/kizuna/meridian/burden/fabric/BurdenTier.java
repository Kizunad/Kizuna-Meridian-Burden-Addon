package dev.kizuna.meridian.burden.fabric;

/**
 * 读数里的轻重四档，按走跑的做功倍率分：
 * 不到一倍半是轻，不到两倍是中（两倍就是背着一份参考负重），不到超载线是重，到了超载线就是超载。
 * 超载线来自设置；档位只用来显示，不参与结算。
 */
public enum BurdenTier {
    LIGHT("轻", 1.5), MEDIUM("中", 2.0), HEAVY("重", Double.POSITIVE_INFINITY),
    OVERLOADED("超载", Double.POSITIVE_INFINITY);

    private final String label;
    /** 倍率不到这个数才算这一档；超载线比它低时以超载线为准。 */
    private final double below;

    BurdenTier(String label, double below) {
        this.label = label;
        this.below = below;
    }

    public String label() {
        return label;
    }

    static BurdenTier of(double multiplier, double overloadMultiplier) {
        // 超载先判：服主把超载线设得比两倍还低时，到线就是超载，中或重这两档就没有了。
        if (multiplier >= overloadMultiplier) {
            return OVERLOADED;
        }
        if (multiplier < LIGHT.below) {
            return LIGHT;
        }
        if (multiplier < MEDIUM.below) {
            return MEDIUM;
        }
        return HEAVY;
    }

    /** 这一档到多少倍率为止，不超过超载线。 */
    double below(double overloadMultiplier) {
        return Math.min(below, overloadMultiplier);
    }

    /** 某个倍率对应多少千克负重；超载线对应的就是负重上限。 */
    static double kilogramsAt(double multiplier, double referenceKilograms) {
        return (multiplier - BurdenReading.UNLOADED) * referenceKilograms;
    }
}
