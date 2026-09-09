package name.modid.vision;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md）：维度分桶文件契约小工具（采集端实现）。
 *
 * <p>统一文件契约：顶层 {@code { "currentDimension": "<dimId>", "worlds": { "<dimId>": <桶内容> } }}。
 * 桶内容保持各文件正文原有形态（agent 姿态也随桶、每维一份）。读取时顶层无 {@code worlds} 键 →
 * 视为旧版单维文件，整份正文归为 {@code worlds["minecraft:overworld"]} 桶（向后兼容，无需迁移）。
 * 记忆端 {@code com.example.memworld} 下另持一份同名小工具（readWorlds/writeWorlds 对称约定）。
 */
final class WorldsFile {

    static final String KEY_CURRENT_DIMENSION = "currentDimension";
    static final String KEY_WORLDS = "worlds";
    /** 旧文件 / 文件缺失时的回退维（单维时代即主世界）。 */
    static final String LEGACY_DIMENSION = "minecraft:overworld";

    private WorldsFile() {
    }

    /** 从根 tag 读出 currentDimension + 各维桶；{@code root == null} → 空 world（overworld 占位）。 */
    static Result read(final CompoundTag root) {
        if (root == null) {
            return new Result(LEGACY_DIMENSION, new LinkedHashMap<>());
        }
        if (root.contains(KEY_WORLDS)) {
            String current = root.getStringOr(KEY_CURRENT_DIMENSION, LEGACY_DIMENSION);
            Map<String, CompoundTag> worlds = new LinkedHashMap<>();
            CompoundTag worldsTag = root.getCompoundOrEmpty(KEY_WORLDS);
            for (String dim : worldsTag.keySet()) {
                worlds.put(dim, worldsTag.getCompoundOrEmpty(dim));
            }
            return new Result(current, worlds);
        }
        // 旧版单维文件：整份正文作为 overworld 桶。
        Map<String, CompoundTag> legacy = new LinkedHashMap<>();
        legacy.put(LEGACY_DIMENSION, root);
        return new Result(LEGACY_DIMENSION, legacy);
    }

    /** 组装分桶根 tag（currentDimension + worlds）。 */
    static CompoundTag wrap(final String currentDimension, final Map<String, CompoundTag> worlds) {
        CompoundTag root = new CompoundTag();
        root.putString(KEY_CURRENT_DIMENSION, currentDimension);
        CompoundTag worldsTag = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : worlds.entrySet()) {
            worldsTag.put(e.getKey(), e.getValue());
        }
        root.put(KEY_WORLDS, worldsTag);
        return root;
    }

    /** 读取结果：最近写入维 + 各维桶（键 = 出现的维 id，仅访问过的维有键）。 */
    record Result(String currentDimension, Map<String, CompoundTag> worlds) {
    }
}
