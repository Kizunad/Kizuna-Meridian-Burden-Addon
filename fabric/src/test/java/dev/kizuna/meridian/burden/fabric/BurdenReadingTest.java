package dev.kizuna.meridian.burden.fabric;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/** 倍率 = 1 + 有效负重 kg / 参考负重 kg，实重只随读数原样带出。 */
public final class BurdenReadingTest {
    private static final BurdenSettings SEVENTY_KG = new BurdenSettings(70);
    private static final double PRECISION = 1e-12;

    @Test
    public void referenceLoadDoublesWalkingWork() {
        var reading = BurdenReading.of(35_000, 70_000, SEVENTY_KG);
        assertEquals(35_000, reading.totalGrams());
        assertEquals(70_000, reading.effectiveGrams());
        assertEquals(2, reading.multiplier(), PRECISION);
    }

    @Test
    public void emptyEffectiveLoadKeepsUnloadedMultiplier() {
        // 快捷栏物品计入实重但有效负重为 0，倍率保持 1。
        var reading = BurdenReading.of(64_000, 0, SEVENTY_KG);
        assertEquals(BurdenReading.UNLOADED, reading.multiplier(), 0);
    }

    @Test
    public void multiplierGrowsLinearlyWithEffectiveKilograms() {
        // 64 块石头放主库存按 2 倍计为 128 kg 有效负重。
        var reading = BurdenReading.of(64_000, 128_000, SEVENTY_KG);
        assertEquals(1 + 128.0 / 70, reading.multiplier(), PRECISION);
    }
}
