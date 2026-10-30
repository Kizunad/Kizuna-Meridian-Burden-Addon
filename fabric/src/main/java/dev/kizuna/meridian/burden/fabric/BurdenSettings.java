package dev.kizuna.meridian.burden.fabric;

import org.tomlj.Toml;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * meridian-burden.toml 的设置。
 *
 * @param referenceKilograms 使行走负荷倍率增加 1 的有效负重，千克
 */
record BurdenSettings(double referenceKilograms) {
    static final String FILE_NAME = "meridian-burden.toml";
    /** 随 JAR 提供的默认设置，首次启动时复制到配置目录。 */
    static final String DEFAULT_RESOURCE = "/meridian-burden.toml";
    private static final String REFERENCE_KEY = "reference_kg";

    BurdenSettings {
        // 参考负重是除数，零、负数和非有限数都会让倍率失真。
        if (!Double.isFinite(referenceKilograms) || referenceKilograms <= 0) {
            throw new IllegalArgumentException(REFERENCE_KEY + " 必须是正的有限数：" + referenceKilograms);
        }
    }

    static BurdenSettings load(Path path) {
        // 首次启动复制默认表；之后只读取，不覆盖服主调整过的文件。
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                try (InputStream source = BurdenSettings.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                    Files.copy(source, path);
                }
            }
            return parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException("无法读取负重设置 " + path, failure);
        }
    }

    static BurdenSettings parse(String text) {
        // 唯一的键必须显式配置；语法错误、缺键或多余键都拒绝整份设置，代码里没有默认参考值。
        var table = Toml.parse(text);
        if (table.hasErrors()) {
            throw new IllegalArgumentException("负重设置不是合法 TOML：" + table.errors().get(0).getMessage());
        }
        if (!table.keySet().equals(Set.of(REFERENCE_KEY))) {
            throw new IllegalArgumentException("负重设置只能且必须包含 " + REFERENCE_KEY + "：" + table.keySet());
        }
        Object value = table.get(REFERENCE_KEY);
        if (!(value instanceof Long) && !(value instanceof Double)) {
            throw new IllegalArgumentException(REFERENCE_KEY + " 必须是数字");
        }
        return new BurdenSettings(((Number) value).doubleValue());
    }
}
