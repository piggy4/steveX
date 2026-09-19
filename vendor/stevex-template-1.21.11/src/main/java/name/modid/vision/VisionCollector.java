package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 视觉系统 —— 序列化中枢 + 数据层访问。
 *
 * <p>v2（GPU 深度缓冲驱动）重构后，本类收窄为"非采集"部分：
 * <ul>
 *   <li><b>实体整份 payload 序列化</b>：{@link #serializeEntityFull(Entity)}（v2.35 展示实体白名单
 *       采集复用）。v2.40 起<b>不再提供按需直读端点</b>：{@code vision/entity} 改读落盘 store
 *       （{@link VisionEntityStore#findEntity}），"直读任意活体实体"的通路已关闭</li>
 *   <li><b>BlockState 序列化</b>：blockId / stateProps 助手</li>
 *   <li><b>快照记录类型</b>：BlockEntitySnapshot / TerrainBlockSnapshot / EntityLightSnapshot，
 *       供深度驱动采集管线与各 store 复用</li>
 *   <li><b>数据层</b>：持有一个 {@link VisionBlockEntityStore} 实例（增量持久化）</li>
 * </ul>
 *
 * <p>原 v1 采集逻辑（Path A/B/C/D 遍历、visibleSections 帧缓存、collectAndSave）已删除，
 * 由 v2 的 DepthCapture → Unprojector → ObjectResolver 三阶段管线取代。
 */
public class VisionCollector {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** NBT 持久化存储 —— 增量保存，自动去重。 */
    private static final VisionBlockEntityStore store = new VisionBlockEntityStore();

    /** 地形方块快照存储 —— 快照覆盖写（v2 起内容 = 本次可见方块，无 scannedSections）。 */
    private static final VisionTerrainStore terrainStore = new VisionTerrainStore();

    /** 实体轻量快照存储 —— 快照覆盖写（v2 起内容 = 本次可见实体，无 scannedSections）。 */
    private static final VisionEntityStore entityStore = new VisionEntityStore();

    /** v2.31：群系 cell 存储 —— 单调 union 覆盖写（独立 biomes.nbt，见 VisionBiomeStore）。 */
    private static final VisionBiomeStore biomeStore = new VisionBiomeStore();

    // ==================== 实体整份 payload 序列化（v2.35） ====================

    /**
     * 把实体序列化成<b>标准整份 NBT payload</b>（{@code {id, ...saveWithoutId}}，设计 §5）：可被
     * vanilla {@code EntityType.create(ValueInput…)} 完整装载的标准实体存档。返回 null = 不可装载
     * （实体不存在 / 已移除 / 类型不可序列化如玩家 / 保存失败）。
     *
     * <p>v2.35（展示实体内容记忆）抽出复用：采集管线（§6.2，{@code LevelRendererMixin} 对白名单
     * 展示实体本帧编码）与本方法共用同一保存路径，两侧格式保证一致。必须在渲染线程调用
     * （读实体状态 / registryAccess 均有竞态）。
     */
    public static CompoundTag serializeEntityFull(final Entity entity) {
        if (entity == null || entity.isRemoved()) return null;
        // getEncodeId() 是 protected；用注册表 key 等价取 id，且类型需可序列化（如玩家返回 null）
        if (!entity.getType().canSerialize()) return null;
        final String encodeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            output.putString("id", encodeId);
            entity.saveWithoutId(output);
            return output.buildResult();
        } catch (Exception e) {
            LOGGER.warn("[Vision] Failed to serialize entity {}: {}", entity.getUUID(), e.getMessage());
            return null;
        }
    }

    /**
     * 把物品栈编码成 {@code ItemStack.CODEC} tag（{@code {id, count?, components?}}，与容器/末影箱条目
     * 同一路径，记忆侧同法解码）。空栈 / 失败 → null。
     *
     * <p>v2.34 原在 {@code LevelRendererMixin} 内（掉落物专用）；v2.41 提到此处，供三处复用同一路径：
     * 掉落物 {@code item}、活体 {@code living.equipment}（{@link LivingSummary}）、展示实体 payload
     * 内的装备（后者由 {@code saveWithoutId} 内部自编码，不走此方法）。必须在渲染线程调用。
     */
    public static CompoundTag encodeItemStack(final ItemStack stack, final HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            Tag tag = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                    .resultOrPartial(err -> { }).orElse(null);
            return tag instanceof CompoundTag c ? c : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==================== 掉落物实体大类（v2.34，单一事实来源） ====================

    /**
     * 掉落物实体注册名（{@code minecraft:item}），懒加载缓存。
     *
     * <p>drop item 载荷（v2.34）的实体大类判定曾分散在多处（mixin 用 {@code instanceof ItemEntity}、
     * ObjectResolver 用 {@code EntityType.ITEM} 注册表键），此处收敛为<b>唯一来源</b>：
     * 一律经 {@code EntityType.ITEM} 从注册表派生，避免硬编码字符串在 namespace/版本演化下漂移。
     * 懒加载（首次调用才触注册表），避开类初始化时机问题。
     */
    private static String itemEntityTypeId;

    /** 掉落物实体 typeId；注册表就绪前的首次调用在运行时发生，安全。 */
    public static String itemTypeId() {
        String t = itemEntityTypeId;
        if (t == null) {
            t = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ITEM).toString();
            itemEntityTypeId = t;
        }
        return t;
    }

    /** typeId 是否掉落物实体 —— drop item 载荷的产端（采集）/消费端（存储、半透明候选）共用此判定。 */
    public static boolean isItemEntity(final String typeId) {
        return typeId != null && itemTypeId().equals(typeId);
    }

    // ==================== BlockState 序列化（供 ObjectResolver 复用，v2.2 改 package-private） ====================

    /** 方块注册名，如 "minecraft:chest"。 */
    static String blockId(final BlockState state) {
        if (state == null) return "";
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** 方块状态属性，如 {"facing":"east","waterlogged":"false"}。 */
    static Map<String, String> stateProps(final BlockState state) {
        Map<String, String> props = new LinkedHashMap<>();
        if (state == null) return props;
        for (Property<?> prop : state.getProperties()) {
            props.put(prop.getName(), String.valueOf(state.getValue(prop)));
        }
        return props;
    }

    // ==================== 数据结构 ====================

    public record BlockEntitySnapshot(
            BlockPos pos,
            String typeId,
            String blockId,
            Map<String, String> stateProps,
            CompoundTag nbt,
            long timestamp
    ) {}

    /**
     * 地形方块快照：只有 block + state，没有 NBT（块实体由方块实体通道单独采集）。
     *
     * <p>v2.23（§7.11）：{@code surface} 字段已移除——减量改由采集侧 {@link DeletionJudge}
     * 用记忆侧反向通道 cells 文件逐块深度判定（v2.22 的「表面点 → 记忆侧 DDA 投票」因稀疏
     * 采样盲区被完全取代）；{@code Unprojector.visibleBlockHits} 内部的 nudged 点仍保留，
     * 供 §5.1 方块直查 / air 近侧回退使用（不落盘）。
     */
    public record TerrainBlockSnapshot(
            BlockPos pos,
            String blockId,
            Map<String, String> stateProps,
            long timestamp
    ) {}

    /**
     * 实体轻量物理状态（Tier 1）：纯字段读取即可获得，不含 NBT。
     * 更深的内容只有白名单展示实体（{@code payload}）、掉落物（{@code item}）与活体属性
     * （{@code living}，v2.41）随帧编码。
     *
     * @param item    v2.34（掉落物记忆）：当 typeId 为 {@code minecraft:item} 时携带物品栈
     *                （{@code ItemStack.CODEC} + {@code NbtOps} 编码 tag）；其余类型 / 空栈为 null。
     *                采集端 snapshot 帧已编码，读侧（file/JSON）只消费 tag、无需再触游戏。
     * @param payload v2.35（展示实体内容记忆）：当 typeId ∈ 采集白名单（{@link DecorativeConfig}）
     *               时为整份可装载 NBT payload（{@code {id, ...saveWithoutId}}，见
     *               设计 §3/§5，由 {@link #serializeEntityFull} 本帧编码）；其余类型 / 失败为 null。
     *               记忆侧用它做<b>内容复原</b>（整份装载）。
     * @param content v2.35（决策点 2 渠道 B）：与 payload 同帧构建的<b>薄内容摘要</b>
     *                （{@link DecorativeSummary}），仅用于采集端 snapshot JSON（{@code entities[].content}），
     *                记忆侧不使用；无摘要类型 / 解码失败 → null。
     * @param living  v2.41（实体属性观测面，见 docs/实体属性观测面设计方案.md）：活体实体的<b>复原口径</b>
     *                全量属性 tag（{@link LivingSummary#buildSave}，装备整份物品栈 / 名字 Component /
     *                完整药水效果…），落盘到 {@code entities.nbt} 的 {@code living} 键；
     *                非 {@code LivingEntity} / 编码失败 → null。<b>活体恒非 null</b>（可为空 compound）——
     *                键的存在性编码"是不是活体"，见 {@link LivingSummary#buildSave}。
     * @param livingView v2.41：与 living 同帧构建的<b>可见口径</b>薄摘要（{@link LivingSummary#buildView}），
     *                仅用于采集端 snapshot JSON（实体级键）；记忆侧不使用 → 非活体 / 失败为 null。
     */
    public record EntityLightSnapshot(
            int id,
            UUID uuid,
            String typeId,
            double x, double y, double z,
            float yaw, float pitch,
            double vx, double vy, double vz,
            boolean onGround,
            float health,
            CompoundTag item,
            CompoundTag payload,
            CompoundTag content,
            CompoundTag living,
            CompoundTag livingView
    ) {}

    // ==================== 查询接口 ====================

    public static VisionBlockEntityStore getStore() {
        return store;
    }

    public static VisionTerrainStore getTerrainStore() {
        return terrainStore;
    }

    public static VisionEntityStore getEntityStore() {
        return entityStore;
    }

    /** v2.31：群系 cell 存储访问。 */
    public static VisionBiomeStore getBiomeStore() {
        return biomeStore;
    }
}
