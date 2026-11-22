package dev.kizuna.meridian.burden.fabric;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** 负重读数的四档分界、摘要和悬浮的文字，以及发给客户端的包来回一遍不走样。 */
public final class BurdenReadoutTest {
    private static final double LIMIT_DELTA = 1e-6;
    private static final double UNAVAILABLE = 4.2;
    private static final double OVERLOAD = 4.2;
    private static final BurdenSettings SEVENTY_KG = new BurdenSettings(70, UNAVAILABLE, OVERLOAD);
    private static final String NOTE = "四档只是提醒：负重每重一分，腿就多累一分；负重本身不减移动速度";

    @Test
    public void tiersSwitchExactlyAtTheirBoundaries() {
        // 分界值归到上面那档：×1.5 起是中，×2 起是重，到超载线起是超载。
        assertEquals(BurdenTier.LIGHT, BurdenTier.of(1, OVERLOAD));
        assertEquals(BurdenTier.LIGHT, BurdenTier.of(Math.nextDown(1.5), OVERLOAD));
        assertEquals(BurdenTier.MEDIUM, BurdenTier.of(1.5, OVERLOAD));
        assertEquals(BurdenTier.MEDIUM, BurdenTier.of(Math.nextDown(2.0), OVERLOAD));
        assertEquals(BurdenTier.HEAVY, BurdenTier.of(2.0, OVERLOAD));
        assertEquals(BurdenTier.HEAVY, BurdenTier.of(Math.nextDown(OVERLOAD), OVERLOAD));
        assertEquals(BurdenTier.OVERLOADED, BurdenTier.of(OVERLOAD, OVERLOAD));
        // 服主把超载线设得很低时，到线就是超载，线以下照旧分档。
        assertEquals(BurdenTier.OVERLOADED, BurdenTier.of(1.3, 1.2));
        assertEquals(BurdenTier.LIGHT, BurdenTier.of(1.1, 1.2));
        assertEquals(BurdenTier.MEDIUM, BurdenTier.of(1.6, 1.8));
    }

    @Test
    public void tiersFollowTheKilogramsOfTheReading() {
        // 默认设置下 35 kg 起中、70 kg 起重、224 kg 起超载；差一克还在下面那档。
        assertEquals("轻", tier(34_999));
        assertEquals("中", tier(35_000));
        assertEquals("中", tier(69_999));
        assertEquals("重", tier(70_000));
        assertEquals("重", tier(223_999));
        assertEquals("超载", tier(224_000));
    }

    @Test
    public void lineAndHoverGiveWeightTierLimitAndExtraLegWork() {
        // 主库存 9 块石头：实重 9 kg，折算负重 18 kg，倍率 1 + 18/70。
        BurdenView light = view(9_000, 18_000, SEVENTY_KG);
        assertEquals("负重：18.0 / 224 kg · 轻（实重 9.0 kg）", BurdenReadout.line(light));
        assertEquals(List.of("随身东西实重 9.0 kg，按放在哪里折算成负重 18.0 kg，上限 224 kg",
                "现在走、跑时腿多累 26%",
                "不到 35 kg 轻，不到 70 kg 中，不到 224 kg 重，再多超载", NOTE), BurdenReadout.hover(light));

        // 别的界面单独取上限时和这一行里写的是同一个数。
        assertEquals(224, BurdenReadout.limitKilograms(light), LIMIT_DELTA);
        assertEquals("负重：0.0 / 224 kg · 轻（实重 0.0 kg）", BurdenReadout.line(view(0, 0, SEVENTY_KG)));
        assertEquals("现在走、跑时腿多累 0%", BurdenReadout.hover(view(0, 0, SEVENTY_KG)).get(1));
        BurdenView heavy = view(64_000, 128_000, SEVENTY_KG);
        assertEquals("负重：128.0 / 224 kg · 重（实重 64.0 kg）", BurdenReadout.line(heavy));
        assertEquals("现在走、跑时腿多累 183%", BurdenReadout.hover(heavy).get(1));
        BurdenView overloaded = view(112_000, 224_000, SEVENTY_KG);
        assertEquals("负重：224.0 / 224 kg · 超载（实重 112.0 kg）", BurdenReadout.line(overloaded));
    }

    @Test
    public void unavailableReadingNamesTheTierItIsChargedAt() {
        // 算不出时不显示成 0 kg，照设置里的倍率说按哪一档、腿多累几成。
        BurdenView unavailable = BurdenView.unavailable(SEVENTY_KG);
        assertEquals("负重：算不出 · 按超载计", BurdenReadout.line(unavailable));
        assertEquals(List.of("随身容器或物品太多、套得太深，或者物品数据异常，重量算不出",
                "算不出时走、跑腿多累 320%，整理随身物品后恢复",
                "不到 35 kg 轻，不到 70 kg 中，不到 224 kg 重，再多超载", NOTE), BurdenReadout.hover(unavailable));
        // 服主写 1 表示算不出时不计负重：那就是按轻计，不是超载。
        BurdenView uncounted = BurdenView.unavailable(new BurdenSettings(70, 1, OVERLOAD));
        assertEquals("负重：算不出 · 按轻计", BurdenReadout.line(uncounted));
        assertEquals("算不出时走、跑腿多累 0%，整理随身物品后恢复", BurdenReadout.hover(uncounted).get(1));
    }

    @Test
    public void lowOverloadLineDropsTheTiersItCutsOff() {
        // 参考负重 50 kg、超载线 ×1.8：上限 40 kg，轻到 25 kg，中到 40 kg，没有重这一档。
        var settings = new BurdenSettings(50, UNAVAILABLE, 1.8);
        BurdenView medium = view(30_000, 30_000, settings);
        assertEquals("负重：30.0 / 40 kg · 中（实重 30.0 kg）", BurdenReadout.line(medium));
        assertEquals("不到 25 kg 轻，不到 40 kg 中，再多超载", BurdenReadout.hover(medium).get(2));
        // 超载线 ×1.2：上限 10 kg，只剩轻和超载。
        BurdenView low = view(4_000, 4_000, new BurdenSettings(50, UNAVAILABLE, 1.2));
        assertEquals("不到 10 kg 轻，再多超载", BurdenReadout.hover(low).get(2));
    }

    @Test
    public void viewSurvivesTheWire() {
        // 六个字段写出再读回，一个不差；算不出的那一种也一样。
        for (BurdenView view : List.of(view(64_000, 128_000, SEVENTY_KG), BurdenView.unavailable(SEVENTY_KG))) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            BurdenView.CODEC.encode(buffer, view);
            assertEquals(view, BurdenView.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        }
    }

    private static String tier(long effectiveGrams) {
        // 摘要行里「·」后面到括号前的就是档位。
        String line = BurdenReadout.line(view(effectiveGrams, effectiveGrams, SEVENTY_KG));
        return line.substring(line.indexOf("· ") + 2, line.indexOf('（'));
    }

    private static BurdenView view(long totalGrams, long effectiveGrams, BurdenSettings settings) {
        return BurdenView.of(BurdenReading.of(totalGrams, effectiveGrams, settings), settings);
    }
}
