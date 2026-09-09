package name.modid.vision;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * §5.2.1 方块实体可采集字段策略（v2.27）。
 *
 * <p>对 {@code ObjectResolver.recordBlock} 产出的方块实体 NBT 做 <b>typeId 白名单过滤</b>，把
 * "无交互可采集字段"严格限定为 L1 可观察层，杜绝在视觉快照里采下 L2 交互内部（容器物品/隐藏内容）。
 * 机制事实：采集在<b>客户端</b>执行，{@code saveWithFullMetadata} 只能序列化客户端 BE 副本
 * （= vanilla {@code getUpdateTag}/{@code getUpdatePacket} 同步面）；本策略在其上再叠一层显式白名单，
 * 使"未来某容器把 Items 暴露进同步面"也不会静默越权（fail-closed），并把"观察边界"固化为可审代码。
 *
 * <p>三种策略（键值基于 1.21.11 {@code decompiled_src_vf/client} 逐类核实，见设计 §5.2.1 决策表
 * A–D；升级版本须重跑 §5.2.1 审计后再扩展）：
 * <ul>
 *   <li><b>PASS</b>——整包保留。仅用于表 C「无交互内部型」与表 B 整类可见的 campfire/shelf：
 *       其客户端副本只含观察者所见（告示牌文字 / 旗帜纹样 / 头颅皮肤 / 信标效果 / 刷怪笼预览 /
 *       结构-拼图配置 / 可见陈列物品…），无隐藏内容；</li>
 *   <li><b>ALLOW</b>——只保留列出的数据键 + 元数据 {@code id/x/y/z}。用于"同步面携带内容、其中含
 *       隐藏字段须剥离"的类型：{@code decorated_pot}（只留 {@code sherds}，剥 {@code item}）、
 *       {@code brushable_block}（只留 {@code hit_direction}，剥 {@code item}）、{@code vault}
 *       （只留展示子集 {@code shared_data}，真实奖励表/配置仅服务端）、{@code trial_spawner}
 *       （只留 {@code spawn_data}/{@code next_mob_spawns_at}，注册玩家/配置仅服务端）；</li>
 *   <li><b>STRIP</b>——数据键全剥（保留元数据 {@code id/x/y/z}）。用于容器家族（表 A）、表 B
 *       NBT=∅ 行、表 C 无 NBT 机制型等<b>客户端副本恒空</b>类型：过滤只是保险——即使未来 vanilla
 *       给某类型新增同步内容，也不被采。</li>
 * </ul>
 *
 * <p><b>未登记 typeId</b>（新版本 / 模组方块）默认 {@code STRIP} + 每类型一次告警
 * （fail-closed，宁缺毋滥；登记需人工按 §5.2.1 审计后加入上表）。
 */
public final class BlockEntityFieldPolicy {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String NS = "minecraft:";
    /** 元数据键（位置 / 类型标识，由 {@code saveWithFullMetadata} 顶层写入）：任何策略都保留，
     *  记忆侧复原按此建 BE（{@code MemoryRestorer.place} 的 {@code loadStatic(pos, state, nbt, …)}）。 */
    private static final Set<String> META_KEYS = Set.of("id", "x", "y", "z");

    /** PASS：表 C 无交互内部型 + 表 B 整类可见（白名单 = 客户端可达全部键）。 */
    private static final Set<String> PASS = Set.of(
            NS + "sign",            // 告示牌（前后文字/颜色/点击命令，可见）
            NS + "hanging_sign",    // 悬挂告示牌（同上）
            NS + "banner",          // 旗帜（patterns 纹样 + CustomName）
            NS + "skull",           // 头颅（profile/note_block_sound/custom_name）
            NS + "conduit",         // 潮涌核心（Target 渲染用；isActive 等客户端自算）
            NS + "beacon",          // 信标（effect 选择 + Levels + CustomName + lock）
            NS + "mob_spawner",     // 刷怪笼（SpawnData 预览渲染；SpawnPotentials 仅服务端、本就不在副本）
            NS + "creaking_heart",  // 吱吱怪之心（creaking UUID）
            NS + "end_gateway",     // 末地折跃门（Age/exit_portal/ExactTeleport）
            NS + "structure_block", // 结构方块（配置全在同步面，无隐藏）
            NS + "jigsaw",          // 拼图方块（同上）
            NS + "test_block",      // 测试方块（mode/message/powered）
            NS + "test_instance_block", // 测试实例方块（data + errorMarkers）
            NS + "campfire",        // 营火（食物物品×4 渲染于顶，可见陈列）
            NS + "shelf"            // 陈列架（陈列物品×3 + align_items_to_bottom，可见陈列）
    );

    /** ALLOW：同步面携带内容、须剥离隐藏字段 → typeId → 允许的数据键。 */
    private static final Map<String, Set<String>> ALLOW = Map.ofEntries(
            Map.entry(NS + "decorated_pot", Set.of("sherds")),               // 饰纹陶罐：剥 item（罐内隐藏单格物）
            Map.entry(NS + "brushable_block", Set.of("hit_direction")),      // 可疑沙/砂砾：剥 item（揭示前隐藏）
            Map.entry(NS + "vault", Set.of("shared_data")),                  // 宝库：只留展示子集（真实奖励/配置仅服务端）
            Map.entry(NS + "trial_spawner", Set.of("spawn_data", "next_mob_spawns_at")) // 试炼刷怪笼：同上
    );

    /** STRIP：客户端副本恒空 / 仅 L0 结构类型（数据键全剥；白名单 = 空）。 */
    private static final Set<String> STRIP = Set.of(
            // —— 表 A 容器家族（Items 仅服务端持久化；无交互内部一律不采）——
            NS + "chest",            // 箱子（含铜质变体同 typeId）
            NS + "trapped_chest",    // 陷阱箱
            NS + "ender_chest",      // 末影箱（BE 无字段；内容=玩家末影箱）
            NS + "barrel",           // 木桶
            NS + "dispenser",        // 发射器
            NS + "dropper",          // 投掷器
            NS + "hopper",           // 漏斗
            NS + "shulker_box",      // 潜影盒
            NS + "crafter",          // 合成器
            NS + "brewing_stand",    // 酿造台
            NS + "furnace",          // 熔炉
            NS + "smoker",           // 烟熏炉
            NS + "blast_furnace",    // 高炉
            // —— 表 B NBT=∅ 行（内容 = 交互/独立通道才可见；客户端副本本为空）——
            NS + "jukebox",          // 唱片机（RecordItem 不进同步面）
            NS + "lectern",          // 讲台（Book/Page 仅开菜单同步）
            NS + "chiseled_bookshelf", // 雕纹书架（书不进同步面）
            NS + "beehive",          // 蜂巢/蜂箱（住户不进同步面）
            // —— 表 C：客户端恒空 / 内容仅 GUI 单独发包 或 仅组件不同步 ——
            NS + "command_block",    // 命令方块（命令/LastOutput 仅 openCommandBlock 单独发包）
            NS + "copper_golem_statue", // 铜傀儡雕像（自定义名仅组件、不同步）
            // —— 无 NBT 机制型（saveAdditional 仅运行时状态，无内容）——
            NS + "piston",           // 移动中的活塞（瞬态、渲染用字段；副本本为空）
            NS + "end_portal",       // 末地传送门
            NS + "bed",              // 床（颜色取 blockstate）
            NS + "daylight_detector",// 阳光传感器
            NS + "comparator",       // 红石比较器（OutputSignal 仅服务端）
            NS + "bell",             // 钟（动画 tick 不序列化）
            NS + "enchanting_table", // 附魔台（书旋转动画不序列化）
            NS + "sculk_sensor",     // 幽匿感测体（last_vibration_frequency/listener 仅服务端）
            NS + "calibrated_sculk_sensor", // 校定幽匿感测体
            NS + "sculk_catalyst",   // 幽匿催发体
            NS + "sculk_shrieker"    // 幽匿尖啸体
    );

    /** 已告警过的未登记 typeId（防刷屏；渲染线程单线程调用，无需并发容器）。 */
    private static final Set<String> WARNED = new HashSet<>();

    private BlockEntityFieldPolicy() {
    }

    /**
     * 对一次 {@code saveWithFullMetadata} 的原始 NBT 施加 typeId 白名单过滤。
     *
     * <p>PASS 直接返回原 tag（无复制，快路径）；ALLOW / STRIP 复制仅含允许键的 tag。
     *
     * @param typeId 方块实体注册 id（{@code BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(...)}，含命名空间）
     * @param raw    {@code saveWithFullMetadata} 序列化的完整客户端副本 NBT（不被修改）
     * @return 白名单过滤后的 NBT（数据键 ⊆ 允许集 ∪ ∅；元数据 {@code id/x/y/z} 恒保留）
     */
    public static CompoundTag filter(final String typeId, final CompoundTag raw) {
        if (typeId == null || raw == null || raw.isEmpty()) return raw;

        if (PASS.contains(typeId)) return raw;

        final Set<String> allowed = ALLOW.get(typeId);
        if (allowed != null) {
            return copyAllowed(raw, allowed);
        }

        if (!STRIP.contains(typeId)) {
            // 未登记 typeId → fail-closed：只留元数据 + 每类型一次告警（进"待归类"清单）
            if (WARNED.add(typeId)) {
                LOGGER.warn("[Vision] Unknown block entity type '{}': field whitelist not audited (design §5.2.1), "
                        + "storing metadata only (fail-closed). Add to BlockEntityFieldPolicy after re-running the "
                        + "§5.2.1 audit.", typeId);
            }
        }
        return copyAllowed(raw, Set.of());
    }

    /** 只复制允许的数据键 + 元数据键（保留 `raw` 不变）。 */
    private static CompoundTag copyAllowed(final CompoundTag raw, final Set<String> allowed) {
        final CompoundTag out = new CompoundTag();
        for (String key : raw.keySet()) {
            if (META_KEYS.contains(key) || allowed.contains(key)) {
                final Tag v = raw.get(key);
                if (v != null) out.put(key, v);
            }
        }
        return out;
    }
}
