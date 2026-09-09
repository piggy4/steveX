package com.example.memworld;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记忆世界的创建 / 打开 / 服务器集成。
 *
 * <p>客户端启动后自动创建或进入记忆世界（创造模式 + 作弊模式、超平坦空世界），
 * 进入后由 {@link MemoryRestorer} 等复原引擎持续把源 NBT 文件里的内容复原到世界中。
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md §5）：记忆世界包含主世界 /
 * 下界 / 末地三个全虚空维度（{@code voidNetherEnd}），记忆玩家<b>镜像跟随</b> agent 所在维——每 tick
 * 依 {@code terrain.nbt} 顶层 {@code currentDimension}（活动维权威源）路由到对应 ServerLevel 驱动
 * 复原并传送玩家跨维，各维内容只在它自己的维内还原。
 */
public final class MemoryWorldManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    private static final MemoryRestorer RESTORER = new MemoryRestorer();
    // v2.10：TerrainRestorer 需持有方块实体通道，用于在方块类型改变且新方块无 BE 时清除旧 BE 记录
    private static final TerrainRestorer TERRAIN = new TerrainRestorer(RESTORER);
    private static final EntityRestorer ENTITY = new EntityRestorer();
    // v2.23（§7.11）：减量执行器（消费采集侧 deletions）+ 反向通道上报器（写 memory_cells.bin）
    // 前者持有方块实体通道（删块清 BE）；后者持有地形 + 实体通道（上报实心不透明块 + 实体占用格）
    private static final DeletionApplier DELETION = new DeletionApplier(RESTORER, ENTITY);
    private static final MemoryCellReporter CELLS = new MemoryCellReporter(TERRAIN, ENTITY);
    // v2.28（§5.2.2）：容器内容记忆通道（读独立 containers.nbt；每轮 reconcile 覆写容器 + 末影箱玩家态）
    private static final ContainerMemoryApplier CONTAINER = new ContainerMemoryApplier();
    // v2.31（§5）：生物群系复原通道（读独立 biomes.nbt；/fillbiome 范式写入已加载区块）
    private static final BiomeRestorer BIOME = new BiomeRestorer();
    private static MinecraftServer lastServer;
    // ==================== 客户端自动开档状态机 ====================
    // 背景：旧实现烧在"标题刚出现那一帧"就 openWorld，客户端此时常仍在启动资源重载中；
    // 单进程内"开档预读 storage access"与"集成服务器挂载同一世界时的锁检查"并发，DirectoryLock
    // .isLocked 只兜跨进程（OverlappingFileLockException 属同 JVM 自撞）→ 自动进入可能被取消卡回
    // 标题。修复：要求主菜单连续稳定 N tick 后才发起开档（错开启动并发窗口），失败退回主菜单则
    // 自动重试（有上限）；成功进入一次后进程内不再自动开档。
    private static boolean enteredOnce;          // 已成功进入过世界（进程级，此后不再自动开档）
    private static boolean autoEngaged;          // 已判定配置 autoOpenOnLaunch
    private static int stableMenuTicks;          // 连续处于可交互主菜单（TitleScreen）的 tick 数
    private static int autoOpenAttempts;         // 本次启动已发起的开档尝试次数
    /** 主菜单需保持稳定多少 tick 才发起自动开档（60 tick = 3 秒，给启动收尾留出窗口）。 */
    private static final int STABLE_MENU_TICKS = 60;
    /** 自动开档失败后最多重试次数。 */
    private static final int MAX_AUTO_OPEN_ATTEMPTS = 3;

    /** 当前服务器是否已对玩家做过"创造 + 飞行 + 传送"的一次性初始化。 */
    private static boolean playerReady;

    /**
     * v2.32（§5.1 镜像跟随复原）：记忆镜像只路由 vanilla 三维。agent 处于其它（自定义 / 数据包）
     * 维度时采集端照样记录（文件 top {@code currentDimension} 记录任意维 id），但记忆端<b>不还原</b>
     * 非 vanilla 维（没有可用的记忆 ServerLevel）——记告警并暂停，等 agent 回到 vanilla 维再继续。
     */
    private static final Map<String, ResourceKey<Level>> DIM_BY_ID = Map.of(
            Level.OVERWORLD.identifier().toString(), Level.OVERWORLD,
            Level.NETHER.identifier().toString(), Level.NETHER,
            Level.END.identifier().toString(), Level.END
    );

    /** v2.32：最近一次已应用的 agent 姿态（底部传送门控）。跨维时无条件传送（见 {@link #onServerTick}）。 */
    private static MemoryRestorer.AgentPose lastAppliedPose;

    /** v2.32：agent 处于非 vanilla 维时降级告警的冷却计数器（每 300 tick 告警一次，防刷屏）。 */
    private static int unsupportedDimWarnCooldown;

    private MemoryWorldManager() {}

    // ==================== 客户端：启动进入记忆世界 ====================

    /**
     * 由 ClientTickEvents 每 tick 调用。启动后自动进入记忆世界，带"主菜单稳定门控 + 失败重试"
     * （见字段区注释）：只在可交互主菜单上连续稳定 {@value #STABLE_MENU_TICKS} tick 才发起开档，
     * 失败退回主菜单会再次累计稳定时长自动重试（上限 {@value #MAX_AUTO_OPEN_ATTEMPTS}），
     * 成功进入过世界一次后本进程不再自动开档（退出到标题也不强制拉回）。
     */
    public static void onClientTick(final Minecraft mc) {
        if (enteredOnce) return;                            // 已进入过：交给玩家 / 其它流程
        if (mc.level != null) {                             // 本次已成功进入（无论哪条路）
            enteredOnce = true;
            return;
        }
        if (mc.getSingleplayerServer() != null) return;     // 正在创建/进入服务器中

        if (!autoEngaged) {
            autoEngaged = true;
            if (!MemoryConfig.get().autoOpenOnLaunch) {
                enteredOnce = true;                         // 配置关闭：本进程不自动开档
                return;
            }
        }

        if (autoOpenAttempts >= MAX_AUTO_OPEN_ATTEMPTS) return;   // 尝试次数已耗尽
        if (!(mc.screen instanceof TitleScreen)) {          // 不在主菜单（其它界面 / 转场）：重置稳定计数
            stableMenuTicks = 0;
            return;
        }

        if (++stableMenuTicks < STABLE_MENU_TICKS) return;
        stableMenuTicks = 0;
        autoOpenAttempts++;
        LOGGER.info("[MemoryWorld] Auto-open attempt {}/{} (main menu stable for {} ticks)",
                autoOpenAttempts, MAX_AUTO_OPEN_ATTEMPTS, STABLE_MENU_TICKS);
        mc.execute(() -> bootstrap(mc));
    }

    private static void bootstrap(final Minecraft mc) {
        MemoryConfig config = MemoryConfig.get();
        if (!config.autoOpenOnLaunch) return;

        // 首次启动一次性初始化客户端设置（语言/音量/字幕），之后不再重复
        applyClientDefaults(mc);

        String worldId = config.worldName;
        LevelStorageSource storage = mc.getLevelSource();
        Screen parent = mc.screen;

        if (config.resetOnLaunch && storage.levelExists(worldId)) {
            deleteWorld(storage, worldId);
        }

        if (storage.levelExists(worldId)) {
            LOGGER.info("[MemoryWorld] Opening existing memory world '{}'", worldId);
            mc.createWorldOpenFlows().openWorld(worldId, () ->
                    LOGGER.warn("[MemoryWorld] Cancelled opening memory world"));
        } else {
            LOGGER.info("[MemoryWorld] Creating memory world '{}' (creative + cheats)", worldId);
            // v2.30（§7.9 ⑧）：初始化记忆世界即关闭生物自然生成——spawn_mobs=false。GameRules 随
            // LevelSettings 写入新世界 level.dat（PrimaryLevelData.getGameRules() = settings.gameRules()），
            // 从 tick 0 起自然刷怪与自定义刷怪全停（ServerChunkCache.tick 读 doMobSpawning=false →
            // spawningCategories 置空 + 跳过 tickCustomSpawners），避免非快照生物污染"冻结的瞬间"。
            GameRules gameRules = new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures());
            gameRules.set(GameRules.SPAWN_MOBS, false, null);
            LevelSettings settings = new LevelSettings(
                    worldId,
                    GameType.CREATIVE,   // 创造模式
                    false,               // 非硬核
                    // 非和平：和平模式会在实体 tick 之外强制移除 MONSTER 类生物（僵尸等），
                    // 使冻结复现失效（村民/展示实体属 MISC 不受影响）。刷怪已由上方
                    // SPAWN_MOBS=false 关闭，生物攻击/伤害因实体 NoAI+Invulnerable 冻结不存在。
                    Difficulty.NORMAL,
                    true,                // 允许作弊
                    gameRules,
                    WorldDataConfiguration.DEFAULT
            );
            mc.createWorldOpenFlows().createFreshLevel(
                    worldId,
                    settings,
                    WorldOptions.defaultWithRandomSeed(),
                    MemoryWorldManager::createVoidWorldDimensions, // 完全空世界（纯虚空）
                    parent
            );
        }
    }

    /**
     * 记忆端客户端设置一次性初始化（仅在第一次启动时执行）。
     *
     * <p>三项设置：语言 → 简体中文、主音量 → 0、隐藏式字幕 → 开。
     * 以标记文件 {@code config/stevex-test/client_defaults.applied} 持久化"已初始化"状态，
     * 后续启动直接跳过（语言经 options.txt 持久化，再次启动读到的就是已设值）。
     */
    private static void applyClientDefaults(final Minecraft mc) {
        Path marker = mc.gameDirectory.toPath().resolve("config").resolve("stevex-test")
                .resolve("client_defaults.applied");
        if (Files.exists(marker)) {
            LOGGER.info("[MemoryWorld] Client defaults already applied, skip");
            return;
        }

        // ① 语言：简体中文。setSelected 只记录 currentCode，须 reloadResourcePacks 才真正生效
        if (!"zh_cn".equals(mc.options.languageCode)) {
            mc.options.languageCode = "zh_cn";
            mc.getLanguageManager().setSelected("zh_cn");
        }
        // ② 主音量 0（1.21.11 无独立 soundMasterVolume，主音量 = SoundSource.MASTER 的 OptionInstance）
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        // ③ 隐藏式字幕打开
        mc.options.showSubtitles().set(true);
        // 统一持久化到 options.txt
        mc.options.save();
        // 语言重载资源后生效（异步，返回的 future 可忽略）
        mc.reloadResourcePacks();

        // 写入标记，确保整个生命周期只初始化一次
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, Long.toString(System.currentTimeMillis()));
            LOGGER.info("[MemoryWorld] Client defaults applied: language=zh_cn, masterVolume=0, subtitles=on");
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to write client defaults marker: {}", e.getMessage());
        }
    }

    private static void deleteWorld(final LevelStorageSource storage, final String worldId) {
        try {
            LevelStorageSource.LevelStorageAccess access = storage.validateAndCreateAccess(worldId);
            try {
                access.deleteLevel();
            } finally {
                access.safeClose();
            }
            LOGGER.info("[MemoryWorld] Deleted old memory world '{}'", worldId);
        } catch (Exception e) {
            LOGGER.warn("[MemoryWorld] Failed to delete world '{}': {}", worldId, e.getMessage());
        }
    }

    /**
     * 生成"完全空世界"的所有维度：没有图层（纯虚空）、没有结构（村庄/要塞）、没有湖泊。
     *
     * <p>主世界始终换成虚空生成器；{@code voidNetherEnd=true}（默认，见 {@link MemoryConfig}）时下界 /
     * 末地也一起换成各自的虚空生成器——每个维度保留自己的维度类型与原默认生物群系（下界暗、末地 0 天光），
     * 只是不再生成任何方块 / 结构，成为与主世界一致的空白画布。生成器只在世界创建时定型，改配置需重建
     * 记忆世界（删除存档或 {@code resetOnLaunch=true}）才生效。
     */
    private static WorldDimensions createVoidWorldDimensions(final HolderLookup.Provider registries) {
        WorldDimensions flatDimensions = WorldPresets.createFlatWorldDimensions(registries);
        WorldDimensions dims = flatDimensions.replaceOverworldGenerator(registries,
                voidGenerator(registries, Biomes.PLAINS));
        if (MemoryConfig.get().voidNetherEnd) {
            dims = replaceDimensionGenerator(dims, LevelStem.NETHER, voidGenerator(registries, Biomes.NETHER_WASTES));
            dims = replaceDimensionGenerator(dims, LevelStem.END, voidGenerator(registries, Biomes.THE_END));
        }
        return dims;
    }

    /** 用指定生物群系构造纯虚空生成器：0 图层、无结构、无湖泊。 */
    private static FlatLevelSource voidGenerator(final HolderLookup.Provider registries, final ResourceKey<Biome> biome) {
        FlatLevelGeneratorSettings voidSettings = new FlatLevelGeneratorSettings(
                // 空结构覆盖集：禁用所有结构（注意 Optional.empty() 反而会启用全部结构集）
                Optional.of(HolderSet.direct(List.of())),
                registries.lookupOrThrow(Registries.BIOME).getOrThrow(biome),
                List.of()          // 不生成湖泊
        );
        return new FlatLevelSource(voidSettings);
    }

    /**
     * 把指定维度（下界 / 末地）的生成器整体换成虚空生成器，保留其 {@link LevelStem} 的维度类型
     * （该维度的天光 / 氛围不变）。{@link WorldDimensions} 只公开 {@code replaceOverworldGenerator}，
     * 故此处仿其实现重建维度 map。
     */
    private static WorldDimensions replaceDimensionGenerator(final WorldDimensions dimensions,
                                                             final ResourceKey<LevelStem> key,
                                                             final ChunkGenerator generator) {
        LevelStem stem = dimensions.get(key).orElseThrow(
                () -> new IllegalStateException("Missing dimension stem " + key.identifier()));
        ImmutableMap.Builder<ResourceKey<LevelStem>, LevelStem> builder = ImmutableMap.builder();
        builder.putAll(dimensions.dimensions());
        builder.put(key, new LevelStem(stem.type(), generator));
        return new WorldDimensions(builder.buildKeepingLast());
    }

    // ==================== 服务器：每 tick 驱动复原 ====================

    /**
     * 由 ServerTickEvents 每 tick 调用；只在记忆世界里驱动复原引擎。
     *
     * <p>v2.32（§5.1 镜像跟随复原）：<b>镜像路由</b>——记忆玩家跟随 agent 所在维度。
     * <ul>
     *   <li>活动维权威源 = {@code terrain.nbt} 顶层 {@code currentDimension}（每快照必写；
     *       {@link TerrainRestorer#activeDimensionId()} 缓存最近一次成功读取的值）；</li>
     *   <li>每 tick 只驱动<b>活动维的 ServerLevel</b>（主世界 / 下界 / 末地）。agent 在非 vanilla
     *       自定义维 → 记告警并降级驱动玩家当前所在维（各复原器轮询不空转；agent 回 vanilla 时
     *       activeDimension 自然刷新、自动恢复）；</li>
     *   <li>各复原器对传入 level 只应用 <b>level 自己的维桶</b>（永不把别的维内容写进来）——镜像
     *       换维的那一瞬即使文件已指到新维、而本 manager 尚在驱动旧维，旧维也只拿到它自己的桶、新维
     *       内容等首次驱动新维时经"每读取代际 × 每维一次交付"补上，坐标永不跨维错写；</li>
     *   <li><b>跨维传送</b>：复原器把活动维内容铺到该维 level 后，底部用该维的姿态
     *       {@code player.teleportTo(level, ...)}——level 是另一维的 ServerLevel 时即完成换维
     *       （{@code TeleportTransition} 维度判等 → changeDimension，无新增 Mixin）。玩家当前所在维
     *       ≠ 活动维时无条件传送（同一姿态数值跨维也必须换），同维时与上次已应用姿态比较、相同则跳过。</li>
     * </ul>
     */
    public static void onServerTick(final MinecraftServer server) {
        if (server.isDedicatedServer()) return; // 只支持单人内置服务器
        if (server != lastServer) {
            lastServer = server;
            playerReady = false;
            lastAppliedPose = null;
            unsupportedDimWarnCooldown = 0;
            RESTORER.onServerStart();
            TERRAIN.onServerStart();
            ENTITY.onServerStart();
            DELETION.onServerStart();
            CELLS.onServerStart();
            CONTAINER.onServerStart();
            BIOME.onServerStart();
            LOGGER.info("[MemoryWorld] Active server level name: '{}' (expected '{}')",
                    server.getWorldData().getLevelName(), MemoryConfig.get().worldName);
        }

        if (!server.getWorldData().getLevelName().equals(MemoryConfig.get().worldName)) return;

        final List<ServerPlayer> players = server.getPlayerList().getPlayers();
        final ServerPlayer player = players.isEmpty() ? null : players.get(0);

        // v2.32（§5.1）：镜像路由——解析活动维 ServerLevel。
        final String activeDim = TERRAIN.activeDimensionId();
        final ResourceKey<Level> activeKey = DIM_BY_ID.get(activeDim);
        ServerLevel level = null;
        if (activeKey != null) {
            level = server.getLevel(activeKey);
        } else {
            // agent 处于非 vanilla 维 → 记忆端无可镜像的 ServerLevel：记告警、降级驱动玩家当前所在维
            //（其内容冻结 → 各复原器轮询几乎 no-op），保持 TERRAIN 持续读文件；agent 回 vanilla 维时
            // activeDimension 刷新、下一 tick 自动切回。玩家尚未进服时落到 overworld 兜底（不驱动复原）。
            if (unsupportedDimWarnCooldown-- <= 0) {
                LOGGER.warn("[MemoryWorld] Agent is in unsupported dimension '{}' (only overworld/the_nether/the_end "
                        + "are mirrored). Restoration paused until it returns to a vanilla dimension.", activeDim);
                unsupportedDimWarnCooldown = 300;
            }
            if (player != null && player.level() instanceof ServerLevel sl) {
                level = sl;
            }
        }
        if (level == null) level = server.getLevel(Level.OVERWORLD); // 兜底（玩家恒出生在 overworld）
        if (level == null) return;

        // 进入记忆世界后一次性：创造 + 飞行，防止掉入虚空（传送职责移至每次更新后的 teleportToPose）。
        // v2.21/v2.30：冻结 gamerule 是<b>世界级</b>状态、跨全部维共享（DerivedLevelData 委托同一
        // PrimaryLevelData），故在首个可用 level 上设置一次即全维生效。
        if (!playerReady) {
            if (player == null) return; // 玩家尚未进入服务器 → 等下轮
            setupPlayer(player);
            applyFreezeGameRules(level, server);
            playerReady = true;
        }

        // 先复原地形（地基），再叠加冻结实体，最后叠加方块实体（功能性方块）
        // v2.23（§7.11）：先增量后减量——DELETION.apply 在本帧增量把当前可见集构建进各 applied 表
        // 之后执行，减量据此拿「当前可见集」作假阳性防护；反序会把删掉的方块经指纹同步立即重建
        //（见 §7.11 执行顺序）。TERRAIN.tick 返回本次 TerrainData（blocks/deletions/cameraPos），
        // 供减量执行；CELLS.tick 在 deletions 应用之后上报下一轮 cells（滞后一快照，设计接受）。
        // v2.32：以上全部只作用于 level 的维（各复原器 / 减量 / cells 均限活动维）。
        final String dim = level.dimension().identifier().toString();
        TerrainRestorer.TerrainData terrain = TERRAIN.tick(level);
        ENTITY.tick(level);
        MemoryRestorer.AgentPose pose = RESTORER.tick(level);
        // v2.28（§5.2.2）：容器内容记忆紧跟在方块实体通道后（§5.2.2 tick 序 RESTORER→CONTAINER→DELETION→CELLS）。
        // 视觉先把容器方块/BE 放好，容器通道再覆写内容；在 DELETION 之前执行（容器非实心非减量候选）。
        CONTAINER.tick(level);
        // v2.31（§5.3）：生物群系在容器内容叠加后、减量删除前执行——群系属区块数据、非方块，减量候选
        // 按可见方块集判定，不会把已复现的群系当"消失"删掉；此处仅保证恢复序与渲染一致（先地形后群系）。
        BIOME.tick(level);
        DELETION.apply(level, terrain, ENTITY.currentUuids(dim));
        CELLS.tick(level);

        // v2.15/v2.32：每次更新后读位置 + 传送 + 调视角（跟随 agent 最新视角 / 所在维）。
        // pose 只在"新内容代际 × 首次驱动该维"时返回（同代际重复 tick 返回 null）。跨维（玩家当前所在
        // 维 ≠ 活动维，含换维过渡）时无条件传送——teleportToPose 的 level 即活动维 ServerLevel，
        // 不同维即完成换维，故同数值姿态也不会被误判为"无需传送"而困在旧维。
        if (pose != null && player != null) {
            final boolean crossDim = !player.level().dimension().equals(level.dimension());
            final boolean moved = !MemoryRestorer.samePose(pose, lastAppliedPose);
            if (crossDim || moved) {
                teleportToPose(player, level, pose);
                lastAppliedPose = pose;
            }
        }
    }

    /**
     * 判断给定维度是否属于记忆世界（v2.21：供全局 Mixin / {@code ServerLevelMixin}、
     * {@code LevelMixin} 在分发层取消 tick，见设计 §7.9）。
     *
     * <p>记忆世界与普通主世界同用 {@code minecraft:overworld} 维度，无法按维度区分，
     * 只能按世界名（存档名）判断——与 {@link #onServerTick} 的过滤条件完全一致。
     */
    public static boolean isMemoryWorld(final ServerLevel level) {
        if (level == null) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        return server.getWorldData().getLevelName().equals(MemoryConfig.get().worldName);
    }

    /**
     * 进入记忆世界时的一次性设置：切到创造模式 + 开启飞行（避免掉入虚空）。
     * 传送职责移至 {@link #teleportToPose}，在每次更新后执行（v2.15）。
     */
    private static void setupPlayer(final ServerPlayer player) {
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    /**
     * v2.21：进入记忆世界后一次性设置冻结 gamerule（§7.9）。
     *
     * <ul>
     *   <li>{@code random_tick_speed=0}：{@code ServerChunkCache.tick} 读它作 tickSpeed，为 0 时
     *       {@code ServerLevel.tickChunk} 整段跳过 → 作物生长 / 冰雪融化 / 藤蔓蔓延 / 岩浆 randomTick
     *       点火全部停止；</li>
     *   <li>{@code advance_time=false} + {@code advance_weather=false}：{@code tickTime} 里分别门控
     *       {@code setDayTime} 与天气推进 → 昼夜 / 天气不再自推进（时间对齐由 {@link MemoryRestorer}
     *       {@code setDayTime} 到采集值，§7.10，不冲突）。</li>
     *   <li>{@code spawn_mobs=false}（v2.30，§7.9 ⑧）：关闭生物<b>自然生成</b>（含自定义刷怪）。
     *       新建记忆世界已在建档时随 LevelSettings 写入该值；此处再设一次，覆盖"改版前创建的旧记忆世界"
     *       并随存档持久化重设，双保险。</li>
     * </ul>
     *
     * <p>用 {@link GameRules} 常量字段而非字符串（本版本 gamerule 已 snake_case 改名）。gamerule 是
     * 世界级状态、随记忆世界存档持久化，只影响记忆世界；仅在首次进入时设置。
     */
    private static void applyFreezeGameRules(final ServerLevel level, final MinecraftServer server) {
        GameRules rules = level.getGameRules();
        rules.set(GameRules.RANDOM_TICK_SPEED, 0, server);
        rules.set(GameRules.ADVANCE_TIME, false, server);
        rules.set(GameRules.ADVANCE_WEATHER, false, server);
        rules.set(GameRules.SPAWN_MOBS, false, server);
        LOGGER.info("[MemoryWorld] Freeze gamerules applied: random_tick_speed=0, advance_time=false, advance_weather=false, spawn_mobs=false");
    }

    /**
     * v2.15：把玩家传送到源文件记录的 agent 位置，并调整到记录的朝向。
     * 旧文件无 yaw/pitch（NaN）时沿用玩家当前朝向。
     *
     * <p>v2.18：agent 坐标为相机（眼睛）双精度坐标，传送时把脚部放到眼睛下方
     * {@code getEyeHeight()} 处，使记忆世界玩家的眼睛精确落在 agent 采集时的眼睛位置。
     *
     * <p>v2.19：同步 agent 基础视场角（{@link #applyFov}），使记忆世界与采集时看到同一
     * 视场角；旧文件无 FOV（{@code -1}）时沿用玩家当前 FOV。
     *
     * <p>v2.32（跨维传送）：{@code level} 恒为<b>活动维</b>的 ServerLevel；玩家当前所在维与它不同时，
     * {@code player.teleportTo(level, …)} 经 {@code TeleportTransition} 维度判等自动完成换维
     * （changeDimension），无需新增 Mixin / 专用 API。调用方（{@link #onServerTick}）在换维过渡时
     * 先铺内容（level 已可写）再在此传送玩家到已加载区块。
     */
    private static void teleportToPose(final ServerPlayer player, final ServerLevel level,
                                       final MemoryRestorer.AgentPose pose) {
        final float yaw = Float.isNaN(pose.yaw()) ? player.getYRot() : pose.yaw();
        final float pitch = Float.isNaN(pose.pitch()) ? player.getXRot() : pose.pitch();
        // 空 relative 集合 = 绝对坐标；眼睛 = agentPos，脚部 = 眼睛 − eyeHeight（还原同一视角）
        final double eyeHeight = player.getEyeHeight();
        player.teleportTo(level, pose.pos().x, pose.pos().y - eyeHeight, pose.pos().z,
                Set.of(), yaw, pitch, true);
        // v2.19：同步 agent 基础视场角（客户端选项，经 mc.execute 在渲染线程设置）
        applyFov(pose.fov());
        LOGGER.info("[MemoryWorld] Player teleported to agent eye pose {},{},{} yaw={} pitch={}",
                pose.pos().x, pose.pos().y, pose.pos().z, yaw, pitch);
    }

    /**
     * v2.19：把 agent 基础视场角应用到记忆世界客户端。
     *
     * <p>FOV 是客户端 {@code options.fov()}（与 yaw/pitch 不同，后者是服务端玩家的旋转），
     * 须经 {@code Minecraft.getInstance().execute} 在渲染线程设置；与当前值相等则跳过，
     * 避免每次传送都写 options.txt。旧文件 FOV 为 {@code -1} → 不改（沿用本地默认）。
     */
    private static void applyFov(final int fov) {
        if (fov < 0) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.options.fov().get() == fov) return;
            mc.options.fov().set(fov);
            mc.options.save();
            LOGGER.info("[MemoryWorld] Applied agent FOV {}", fov);
        });
    }

    /** 命令触发：强制重新读取源文件并同步世界。 */
    public static void forceRestore(final MinecraftServer server) {
        if (server == null) return;
        RESTORER.forceRefresh();
        TERRAIN.forceRefresh();
        ENTITY.forceRefresh();
        CONTAINER.forceRefresh();
        BIOME.forceRefresh();
    }
}
