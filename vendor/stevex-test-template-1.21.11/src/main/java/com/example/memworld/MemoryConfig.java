package com.example.memworld;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记忆世界配置。
 *
 * <p>配置文件：{@code <gameDir>/config/stevex-test/memory.json}
 * <pre>{@code
 * {
 *   "worldName": "MemoryWorld",        // 记忆世界存档名 / 文件夹名
 *   "sourceFile": "",                  // 方块实体源 NBT 文件；留空则自动探测
 *   "terrainFile": "",                 // 地形源 NBT 文件；留空则自动探测
 *   "entityFile": "",                  // 实体源 NBT 文件；留空则自动探测
 *   "pollIntervalTicks": 1,            // 每隔多少 tick 做一次源文件 mtime 检查（1 = 每 tick；
 *                                     //   mtime 未变不读文件，stat 成本≈零，见 §7.4）
 *   "autoOpenOnLaunch": true,          // 启动游戏后自动创建/进入记忆世界
 *   "resetOnLaunch": false,            // 每次启动删掉记忆世界重建（清空旧状态再恢复）
 *   "voidNetherEnd": true,             // v2.30：下界/末地同主世界也建成纯虚空（默认开；仅影响新建世界）
 *   "removalEnabled": true,            // v2.23：减量删除开关（§7.11）；false = 纯累积
 *   "removalPixelThreshold": 2,        // v2.23：采集侧删除判定阈值（像素越过计数≥此值判消失）
 *   "removalMaxRayDist": 96.0,         // v2.23：距离球过滤半径（格）；1/z 深度量化误差限制（§7.11）
 *   "removalTranslucentEnabled": true, // v2.36：Fabulous 下 translucent 场减量开关（水/满格透明，§7.12）；
 *                                      //   false = 该配置下暂留幽灵（岩浆走 main 场不受影响；Fancy/Fast 不生效）
 *   "memoryCellsWriteIntervalTicks": 10, // v2.23：cells 文件重算兜底间隔（tick）
 *   "memoryCellsFile": "",             // v2.23：memory_cells.bin 路径；留空自动探测
 *   "containerFile": "",               // v2.28（§5.2.2）：容器内容源 NBT 文件（containers.nbt）；留空自动探测
 *   "containerReconcileOnPoll": true,  // v2.28：每轮 reconcile 覆写容器内容（权威还原玩家改动）；
 *                                      //   false = 仅文件变化时覆写（允许手动摆放实验）
 *   "biomeFile": ""                    // v2.31（§5）：群系源 NBT 文件（biomes.nbt）；留空则自动探测
 * }
 * }</pre>
 */
public class MemoryConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    public String worldName = "MemoryWorld";
    public String sourceFile = "";
    public String terrainFile = "";
    public String entityFile = "";
    // v2.13：mtime 门控（§7.4）。每 tick 仅 stat 源文件 mtime，未变不读 → 默认 1（每 tick）几乎零成本；
    // 仍可在 memory.json 调大以降低 stat 频率（代价是更新延迟最多加长到该间隔）。
    public int pollIntervalTicks = 1;
    public boolean autoOpenOnLaunch = true;
    public boolean resetOnLaunch = false;
    /** v2.30：下界 / 末地同主世界一样建成纯虚空（世界生成器只在创建时定型，改此值须重建记忆世界才生效）。 */
    public boolean voidNetherEnd = true;
    // v2.23（§7.11）：减量删除配置（反向通道）。removalEnabled=false → 纯累积语义（v2 原状）。
    public boolean removalEnabled = true;
    /** 采集侧删除判定阈值：像素越过计数 ≥ 此值才判消失（随 cells 文件头下发给采集侧，单一来源）。 */
    public int removalPixelThreshold = 2;
    /** 距离球过滤半径（格）：只上报 |cell − agentPos| ≤ 此值的格（§7.11：深度 1/z 非线性下 float32
     *  量化误差随距离放大，δ=5cm 只在 ≤~100 格内可靠）。 */
    public double removalMaxRayDist = 96.0;
    /** v2.36（§7.12）：Fabulous 下 translucent 场减量开关（随 cells 文件头下发给采集侧；默认 true）。
     *  false = Fabulous 下不透写 translucent 目标的格（水/满格透明）不判删、暂留幽灵（岩浆写 main、走 main
     *  场不受影响）；Fancy/Fast 下不生效（水/满格透明写 main、本就由 main 场判据覆盖）。 */
    public boolean removalTranslucentEnabled = true;
    /** cells 文件重算兜底间隔（tick）：世界/姿态未变时也每 N tick 重算一次（§7.11 触发）。 */
    public int memoryCellsWriteIntervalTicks = 10;
    /** memory_cells.bin 自定义路径；留空则自动探测（源 terrain.nbt 同级，采集侧读同一路径）。 */
    public String memoryCellsFile = "";
    // v2.28（§5.2.2）：容器内容记忆（独立交互通道，见 ContainerMemoryApplier）。
    /** containers.nbt 自定义路径；留空则自动探测（源 block_entities.nbt 同级，采集侧写同一路径）。 */
    public String containerFile = "";
    /** 每轮 reconcile 覆写容器内容（权威还原玩家改动，定案 C）；false = 仅文件变化时覆写。 */
    public boolean containerReconcileOnPoll = true;
    // v2.31（§5）：生物群系独立通道（采集侧写 biomes.nbt，见 BiomeRestorer）。
    /** biomes.nbt 自定义路径；留空则自动探测（与 terrain.nbt 同级，采集侧写同一路径）。 */
    public String biomeFile = "";

    private static MemoryConfig INSTANCE;

    public static MemoryConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new MemoryConfig();
        }
        return INSTANCE;
    }

    private MemoryConfig() {
        load();
    }

    private void load() {
        if (Minecraft.getInstance() == null) {
            return; // 非客户端环境（如专用服务器），不加载
        }

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path configFile = gameDir.resolve("config").resolve("stevex-test").resolve("memory.json");
        if (Files.exists(configFile)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
                if (json.has("worldName") && json.get("worldName").isJsonPrimitive()) worldName = json.get("worldName").getAsString();
                if (json.has("sourceFile") && json.get("sourceFile").isJsonPrimitive()) sourceFile = json.get("sourceFile").getAsString();
                if (json.has("terrainFile") && json.get("terrainFile").isJsonPrimitive()) terrainFile = json.get("terrainFile").getAsString();
                if (json.has("entityFile") && json.get("entityFile").isJsonPrimitive()) entityFile = json.get("entityFile").getAsString();
                if (json.has("pollIntervalTicks") && json.get("pollIntervalTicks").isJsonPrimitive()) pollIntervalTicks = json.get("pollIntervalTicks").getAsInt();
                if (json.has("autoOpenOnLaunch") && json.get("autoOpenOnLaunch").isJsonPrimitive()) autoOpenOnLaunch = json.get("autoOpenOnLaunch").getAsBoolean();
                if (json.has("resetOnLaunch") && json.get("resetOnLaunch").isJsonPrimitive()) resetOnLaunch = json.get("resetOnLaunch").getAsBoolean();
                if (json.has("voidNetherEnd") && json.get("voidNetherEnd").isJsonPrimitive()) voidNetherEnd = json.get("voidNetherEnd").getAsBoolean();
                if (json.has("removalEnabled") && json.get("removalEnabled").isJsonPrimitive()) removalEnabled = json.get("removalEnabled").getAsBoolean();
                if (json.has("removalPixelThreshold") && json.get("removalPixelThreshold").isJsonPrimitive()) removalPixelThreshold = json.get("removalPixelThreshold").getAsInt();
                if (json.has("removalMaxRayDist") && json.get("removalMaxRayDist").isJsonPrimitive()) removalMaxRayDist = json.get("removalMaxRayDist").getAsDouble();
                if (json.has("removalTranslucentEnabled") && json.get("removalTranslucentEnabled").isJsonPrimitive()) removalTranslucentEnabled = json.get("removalTranslucentEnabled").getAsBoolean();
                if (json.has("memoryCellsWriteIntervalTicks") && json.get("memoryCellsWriteIntervalTicks").isJsonPrimitive()) memoryCellsWriteIntervalTicks = json.get("memoryCellsWriteIntervalTicks").getAsInt();
                if (json.has("memoryCellsFile") && json.get("memoryCellsFile").isJsonPrimitive()) memoryCellsFile = json.get("memoryCellsFile").getAsString();
                if (json.has("containerFile") && json.get("containerFile").isJsonPrimitive()) containerFile = json.get("containerFile").getAsString();
                if (json.has("containerReconcileOnPoll") && json.get("containerReconcileOnPoll").isJsonPrimitive()) containerReconcileOnPoll = json.get("containerReconcileOnPoll").getAsBoolean();
                if (json.has("biomeFile") && json.get("biomeFile").isJsonPrimitive()) biomeFile = json.get("biomeFile").getAsString();
                LOGGER.info("[MemoryWorld] Loaded config from {}", configFile);
            } catch (Exception e) {
                LOGGER.warn("[MemoryWorld] Failed to parse config {}: {}", configFile, e.getMessage());
            }
        } else {
            writeDefaultConfig(configFile);
        }

        Path source = resolveSourceFile();
        if (source != null) {
            LOGGER.info("[MemoryWorld] Source NBT file: {}", source);
        } else {
            LOGGER.warn("[MemoryWorld] Source NBT file not found (gameDir={}). Run the stevex mod first to collect "
                    + "vision data, or set 'sourceFile' in {}.", gameDir, configFile);
        }
    }

    /** 首次运行生成一份默认配置，方便查看 / 修改 sourceFile。 */
    private void writeDefaultConfig(final Path configFile) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("worldName", worldName);
            json.addProperty("sourceFile", sourceFile);
            json.addProperty("terrainFile", terrainFile);
            json.addProperty("entityFile", entityFile);
            json.addProperty("pollIntervalTicks", pollIntervalTicks);
            json.addProperty("autoOpenOnLaunch", autoOpenOnLaunch);
            json.addProperty("resetOnLaunch", resetOnLaunch);
            json.addProperty("voidNetherEnd", voidNetherEnd);
            json.addProperty("removalEnabled", removalEnabled);
            json.addProperty("removalPixelThreshold", removalPixelThreshold);
            json.addProperty("removalMaxRayDist", removalMaxRayDist);
            json.addProperty("removalTranslucentEnabled", removalTranslucentEnabled);
            json.addProperty("memoryCellsWriteIntervalTicks", memoryCellsWriteIntervalTicks);
            json.addProperty("memoryCellsFile", memoryCellsFile);
            json.addProperty("containerFile", containerFile);
            json.addProperty("containerReconcileOnPoll", containerReconcileOnPoll);
            json.addProperty("biomeFile", biomeFile);
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, json.toString());
            LOGGER.info("[MemoryWorld] Wrote default config to {}", configFile);
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to write default config: {}", e.getMessage());
        }
    }

    /** 客户端游戏目录；非客户端环境返回 null。 */
    public Path gameDirectory() {
        return Minecraft.getInstance() == null ? null : Minecraft.getInstance().gameDirectory.toPath();
    }

    /**
     * 解析源 NBT 文件路径；找不到时返回 null（运行后会定期重试）。
     *
     * <p>探测顺序：配置的 sourceFile → 本客户端 stevex 的存储 → 兄弟项目（stevex-template）的运行目录。
     */
    public Path resolveSourceFile() {
        if (Minecraft.getInstance() == null) return null;

        if (sourceFile != null && !sourceFile.isBlank()) {
            Path p = Path.of(sourceFile);
            return Files.exists(p) ? p : null;
        }

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        // 候选路径：本客户端 stevex 存储 → 兄弟项目 run 目录（同级需 ../..）→ 上级嵌套的兄弟（..）→ 配置目录。
        List<Path> candidates = List.of(
                gameDir.resolve("stevex/vision/block_entities.nbt"),
                gameDir.resolve("..").resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/block_entities.nbt"),
                gameDir.resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/block_entities.nbt"),
                gameDir.resolve("config").resolve("stevex-test").resolve("block_entities.nbt")
        );
        for (Path c : candidates) {
            if (Files.exists(c)) return c.normalize();
        }
        return null;
    }

    /**
     * 解析地形源 NBT 文件（terrain.nbt）；找不到时返回 null（运行后会定期重试）。
     *
     * <p>探测顺序与 {@link #resolveSourceFile()} 相同，只是把文件名换成 terrain.nbt。
     */
    public Path resolveTerrainFile() {
        if (Minecraft.getInstance() == null) return null;

        if (terrainFile != null && !terrainFile.isBlank()) {
            Path p = Path.of(terrainFile);
            return Files.exists(p) ? p : null;
        }

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        List<Path> candidates = List.of(
                gameDir.resolve("stevex/vision/terrain.nbt"),
                gameDir.resolve("..").resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/terrain.nbt"),
                gameDir.resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/terrain.nbt"),
                gameDir.resolve("config").resolve("stevex-test").resolve("terrain.nbt")
        );
        for (Path c : candidates) {
            if (Files.exists(c)) return c.normalize();
        }
        return null;
    }

    /**
     * 解析实体源 NBT 文件（entities.nbt）；找不到时返回 null（运行后会定期重试）。
     *
     * <p>探测顺序与 {@link #resolveSourceFile()} 相同，只是把文件名换成 entities.nbt。
     */
    public Path resolveEntityFile() {
        if (Minecraft.getInstance() == null) return null;

        if (entityFile != null && !entityFile.isBlank()) {
            Path p = Path.of(entityFile);
            return Files.exists(p) ? p : null;
        }

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        List<Path> candidates = List.of(
                gameDir.resolve("stevex/vision/entities.nbt"),
                gameDir.resolve("..").resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/entities.nbt"),
                gameDir.resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/entities.nbt"),
                gameDir.resolve("config").resolve("stevex-test").resolve("entities.nbt")
        );
        for (Path c : candidates) {
            if (Files.exists(c)) return c.normalize();
        }
        return null;
    }

    /**
     * 解析容器内容源 NBT 文件（containers.nbt）；找不到时返回 null（运行后会定期重试）。
     *
     * <p>v2.28（§5.2.2）：交互内容通道的独立文件。探测顺序与 {@link #resolveSourceFile()} 相同，
     * 只是把文件名换成 containers.nbt（与 block_entities.nbt 同目录 —— 采集侧写进同一 stevex/vision/）。
     */
    public Path resolveContainerFile() {
        if (Minecraft.getInstance() == null) return null;

        if (containerFile != null && !containerFile.isBlank()) {
            Path p = Path.of(containerFile);
            return Files.exists(p) ? p : null;
        }

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        List<Path> candidates = List.of(
                gameDir.resolve("stevex/vision/containers.nbt"),
                gameDir.resolve("..").resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/containers.nbt"),
                gameDir.resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/containers.nbt"),
                gameDir.resolve("config").resolve("stevex-test").resolve("containers.nbt")
        );
        for (Path c : candidates) {
            if (Files.exists(c)) return c.normalize();
        }
        return null;
    }

    /**
     * 解析群系源 NBT 文件（biomes.nbt）；找不到时返回 null（运行后会定期重试）。
     *
     * <p>v2.31（§5）：生物群系独立通道。探测顺序与 {@link #resolveSourceFile()} 相同，
     * 只是把文件名换成 biomes.nbt（与 terrain.nbt 同目录 —— 采集侧写进同一 stevex/vision/）。
     */
    public Path resolveBiomeFile() {
        if (Minecraft.getInstance() == null) return null;

        if (biomeFile != null && !biomeFile.isBlank()) {
            Path p = Path.of(biomeFile);
            return Files.exists(p) ? p : null;
        }

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        List<Path> candidates = List.of(
                gameDir.resolve("stevex/vision/biomes.nbt"),
                gameDir.resolve("..").resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/biomes.nbt"),
                gameDir.resolve("..").resolve("stevex-template-1.21.11").resolve("run/stevex/vision/biomes.nbt"),
                gameDir.resolve("config").resolve("stevex-test").resolve("biomes.nbt")
        );
        for (Path c : candidates) {
            if (Files.exists(c)) return c.normalize();
        }
        return null;
    }

    /**
     * 解析 memory_cells.bin 目标路径（{@link MemoryCellReporter} 写入，采集侧读同一路径）。
     *
     * <p>探测顺序：配置的 {@code memoryCellsFile} → 源 terrain.nbt 所在目录的 {@code memory_cells.bin}
     * （terrain 与 cells 同级——采集侧把二者都写进同一个 {@code stevex/vision/} 目录）
     * → {@code gameDir/stevex/vision/memory_cells.bin} 兜底。文件可能尚不存在（首次写入由
     * MemoryCellReporter 创建目录）。
     */
    public Path resolveMemoryCellsFile() {
        if (Minecraft.getInstance() == null) return null;

        if (memoryCellsFile != null && !memoryCellsFile.isBlank()) {
            return Path.of(memoryCellsFile);
        }

        Path terrain = resolveTerrainFile();
        if (terrain != null) {
            return terrain.getParent().resolve("memory_cells.bin");
        }
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("stevex/vision/memory_cells.bin");
    }
}
