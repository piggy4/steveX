package com.example.memworld;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * v2.32 统一文件契约小工具（记忆端副本，对应采集端 {@code name.modid.vision.WorldsFile}，
 * 见 docs/世界类型区分与镜像复原设计方案.md §3.2）。
 *
 * <p>所有"维度作用域"文件顶层形如
 * {@code { "currentDimension": "minecraft:overworld", "worlds": { <dim>: <桶正文> } }}；
 * 记忆端只读文件，故只实现 {@link #read} 读路径。旧版单维文件（顶层无 {@code worlds} 键）→
 * 整份正文视为 {@code worlds["minecraft:overworld"]} 桶、{@code currentDimension} 视为 overworld，
 * 与 v2.32 之前的行为逐字节等价（向后兼容，无迁移）。
 *
 * <p>活动维语义：各复原器每轮只对 <b>level.dimension() 对应的桶</b> 做 diff/apply（绝不把别的维
 * 桶内容写进当前 level），而 {@link #currentDimension()}（文件最近一次写入所属维 = agent 当前维）
 * 只用作 {@link MemoryWorldManager} 路由玩家到哪个 ServerLevel 的镜像权威。两者解耦可容忍镜像
 * 瞬时落后：落后时仍只 apply 自己维的桶 → 永不跨维错写。
 */
final class WorldsFile {

    static final String KEY_CURRENT_DIMENSION = "currentDimension";
    static final String KEY_WORLDS = "worlds";
    static final String LEGACY_DIMENSION = "minecraft:overworld";

    private WorldsFile() {}

    /**
     * 解析一个文件根 tag → (currentDimension, 各维桶 map)。
     * 无 {@code worlds} 键 → 旧版单维文件：整份根当 overworld 桶。
     * {@code root} 为 null（空文件）→ 空 worlds + overworld 占位。
     */
    static Result read(final CompoundTag root) {
        if (root == null) return new Result(LEGACY_DIMENSION, Map.of());
        if (root.contains(KEY_WORLDS)) {
            String current = root.getStringOr(KEY_CURRENT_DIMENSION, LEGACY_DIMENSION);
            Map<String, CompoundTag> worlds = new LinkedHashMap<>();
            CompoundTag worldsTag = root.getCompoundOrEmpty(KEY_WORLDS);
            for (String dim : worldsTag.keySet()) {
                worlds.put(dim, worldsTag.getCompoundOrEmpty(dim));
            }
            return new Result(current, worlds);
        }
        Map<String, CompoundTag> legacy = new LinkedHashMap<>();
        legacy.put(LEGACY_DIMENSION, root);
        return new Result(LEGACY_DIMENSION, legacy);
    }

    /** 一次文件解析结果：文件记录的当前维 + 维 id → 桶正文（含旧文件自动回退的 overworld 桶）。 */
    record Result(String currentDimension, Map<String, CompoundTag> worlds) {}
}
