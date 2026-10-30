package dev.kizuna.meridian.burden.fabric;

/**
 * 一名玩家当前的负重读数，来自同一次 Weights 计算。
 *
 * @param totalGrams 随身实重，克
 * @param effectiveGrams 按 Weights 来源系数加权的有效负重，克
 * @param multiplier 行走与疾跑目标功率的负荷倍率，不小于 1
 */
public record BurdenReading(long totalGrams, long effectiveGrams, double multiplier) {
    static final double GRAMS_PER_KILOGRAM = 1000;
    /** 无负荷倍率：有效负重为 0 时 Human 按原目标功率做功。 */
    static final double UNLOADED = 1;

    static BurdenReading of(long totalGrams, long effectiveGrams, BurdenSettings settings) {
        // 倍率 = 1 + 有效负重 kg / 参考负重 kg；有效负重来自 Weights，恒为非负。
        double effectiveKilograms = effectiveGrams / GRAMS_PER_KILOGRAM;
        double multiplier = UNLOADED + effectiveKilograms / settings.referenceKilograms();
        return new BurdenReading(totalGrams, effectiveGrams, multiplier);
    }
}
