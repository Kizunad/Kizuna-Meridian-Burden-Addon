package dev.kizuna.meridian.burden.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 发给玩家自己客户端的负重读数，负重附属唯一的 S2C 包。数值都是服务端算好的，客户端只显示。
 *
 * @param available Weights 这次算不算得出；算不出时两个重量为 0，倍率是设置里算不出时的结算倍率
 * @param totalGrams 随身实重，克
 * @param effectiveGrams 按来源系数折算后的负重，克
 * @param multiplier 走、跑时腿上肌肉做功的倍率，不小于 1
 * @param referenceKilograms 让倍率加 1 的负重，千克
 * @param overloadMultiplier 读数里算作超载的倍率，换算成千克就是负重上限
 */
public record BurdenView(boolean available, long totalGrams, long effectiveGrams, double multiplier,
                         double referenceKilograms, double overloadMultiplier) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BurdenView> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("meridian_burden", "reading"));
    static final StreamCodec<FriendlyByteBuf, BurdenView> CODEC = StreamCodec.of(BurdenView::write, BurdenView::read);

    static BurdenView of(BurdenReading reading, BurdenSettings settings) {
        // 算得出：两个重量和倍率来自同一次 Weights 计算。
        return new BurdenView(true, reading.totalGrams(), reading.effectiveGrams(), reading.multiplier(),
                settings.referenceKilograms(), settings.overloadMultiplier());
    }

    static BurdenView unavailable(BurdenSettings settings) {
        // 算不出：按设置里的倍率结算，重量不给数，客户端不显示成 0 千克。
        return new BurdenView(false, 0, 0, settings.unavailableMultiplier(),
                settings.referenceKilograms(), settings.overloadMultiplier());
    }

    private static void write(FriendlyByteBuf buffer, BurdenView view) {
        buffer.writeBoolean(view.available());
        buffer.writeVarLong(view.totalGrams());
        buffer.writeVarLong(view.effectiveGrams());
        buffer.writeDouble(view.multiplier());
        buffer.writeDouble(view.referenceKilograms());
        buffer.writeDouble(view.overloadMultiplier());
    }

    private static BurdenView read(FriendlyByteBuf buffer) {
        return new BurdenView(buffer.readBoolean(), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public Type<BurdenView> type() {
        // 收发两端都按这个类型查找通道。
        return TYPE;
    }
}
