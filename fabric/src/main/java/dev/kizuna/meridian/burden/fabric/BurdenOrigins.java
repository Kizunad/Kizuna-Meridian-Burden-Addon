package dev.kizuna.meridian.burden.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kizuna.meridian.origin.fabric.OriginEntrypoint;
import dev.kizuna.meridian.origin.fabric.OriginRegistrar;
import dev.kizuna.meridian.origin.fabric.OriginTalentType;
import dev.kizuna.meridian.origin.fabric.Origins;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * 出身附属的登记入口：常驻天赋 {@code meridian_burden:carry_capacity}，参数 {@code reference_scale} 是参考负重的倍数。
 * 背同样的有效负重，参考负重大的人行走负荷倍率低，读数里的负重上限也跟着高；一个出身写了几条就连乘。
 */
public final class BurdenOrigins implements OriginEntrypoint {
    private static final Codec<Double> SCALE = Codec.DOUBLE.validate(BurdenOrigins::positive);
    static final Codec<CarryCapacity> PARAMS = RecordCodecBuilder.create(instance -> instance.group(
            SCALE.fieldOf("reference_scale").forGetter(CarryCapacity::referenceScale))
            .apply(instance, CarryCapacity::new));
    /** 负重天赋这一类；别的附属要按出身读这条参数时，拿它问 {@link Origins#talents}。 */
    public static final OriginTalentType<CarryCapacity> CARRY_CAPACITY = OriginTalentType.standing(
            Identifier.parse("meridian_burden:carry_capacity"), PARAMS);

    @Override
    public void register(OriginRegistrar registrar) {
        // 出身附属读数据包前收齐各附属的类型。
        registrar.talent(CARRY_CAPACITY);
    }

    static BurdenSettings settingsFor(ServerPlayer player, BurdenSettings settings) {
        // 出身没选、或没带这条天赋，就是设置里的参考负重；读的是出身附属的内存缓存，每 tick 调也不读身体。
        double scale = 1;
        for (CarryCapacity capacity : Origins.talents(player, CARRY_CAPACITY)) {
            scale *= capacity.referenceScale();
        }
        return scale == 1 ? settings : settings.withReferenceScale(scale);
    }

    private static DataResult<Double> positive(Double scale) {
        // 参考负重是除数，倍数得是正的有限数；写错就让这个出身读取失败。
        if (!Double.isFinite(scale) || scale <= 0) {
            return DataResult.error(() -> "reference_scale 必须是正的有限数：" + scale);
        }
        return DataResult.success(scale);
    }

    /** 出身 JSON 里这一条天赋的参数。 */
    public record CarryCapacity(double referenceScale) {
        // 只有一个倍数。
    }
}
