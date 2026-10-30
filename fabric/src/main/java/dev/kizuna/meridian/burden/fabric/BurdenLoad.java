package dev.kizuna.meridian.burden.fabric;

import dev.kizuna.bs3d.weights.server.Burden;
import dev.kizuna.meridian.human.fabric.HumanMovementLoad;
import dev.kizuna.meridian.human.fabric.HumanMovementLoads;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/** 把 Weights 的有效负重换算为 Human 行走负荷；库存暂不可算时按无负荷结算。 */
public final class BurdenLoad implements HumanMovementLoad {
    private static final String STATUS_FORMAT = "负重 %.1f kg，行走负荷 ×%.2f";
    private static final String UNAVAILABLE_STATUS = "负重暂不可用，行走负荷 ×1.00";
    private static volatile BurdenLoad installed;
    private final BurdenSettings settings;

    private BurdenLoad(BurdenSettings settings) {
        this.settings = settings;
    }

    static void install(BurdenSettings settings) {
        // 模组初始化时只安装一次；Human 拒绝重复注册，读取 API 与提供者共用同一份设置。
        var load = new BurdenLoad(settings);
        HumanMovementLoads.register(load);
        installed = load;
    }

    /** 当前读数；Weights 判定库存不可用时为空。只能在逻辑服务器主线程调用。 */
    public static Optional<BurdenReading> read(ServerPlayerEntity player) {
        BurdenLoad load = installed;
        if (load == null) {
            throw new IllegalStateException("负重附属尚未初始化");
        }
        return load.reading(player);
    }

    @Override
    public double multiplier(ServerPlayerEntity player) {
        // 损坏库存由 Weights 记录告警；这里按无负荷结算，不因读数缺失让 Human 暂停身体。
        return reading(player).map(BurdenReading::multiplier).orElse(BurdenReading.UNLOADED);
    }

    @Override
    public Optional<String> status(ServerPlayerEntity player) {
        // 库存暂不可算时说明按无负荷结算，不显示成 0 kg。
        String line = reading(player).map(BurdenLoad::describe).orElse(UNAVAILABLE_STATUS);
        return Optional.of(line);
    }

    private Optional<BurdenReading> reading(ServerPlayerEntity player) {
        // Weights 每 5 个服务端 tick 最多重算一次，同一 tick 内的实重与有效负重读自同一份结果。
        OptionalLong effective = Burden.effectiveGrams(player);
        OptionalLong total = Burden.totalGrams(player);
        if (effective.isEmpty() || total.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(BurdenReading.of(total.getAsLong(), effective.getAsLong(), settings));
    }

    private static String describe(BurdenReading reading) {
        double effectiveKilograms = reading.effectiveGrams() / BurdenReading.GRAMS_PER_KILOGRAM;
        return String.format(Locale.ROOT, STATUS_FORMAT, effectiveKilograms, reading.multiplier());
    }
}
