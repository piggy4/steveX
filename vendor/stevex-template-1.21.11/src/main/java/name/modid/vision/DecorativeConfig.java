package name.modid.vision;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * 展示实体采集白名单配置（v2.35，见 docs/展示实体内容记忆设计方案.md §6.1）。
 *
 * <p>采集侧没有通用设置持久化（SettingsApi = vanilla 游戏选项），故仿记忆侧 {@code memory.json}：
 * 从 {@code config/stevex/vision.json} 读取顶层键 {@code decorativeEntityTypes}（字符串数组，
 * 全限定注册名，如 {@code "minecraft:item_frame"}）。文件缺失 / 键缺失 / 解析失败 → 回退默认
 * 白名单 {@link #DEFAULT_TYPES}。
 *
 * <p>热重载：{@code mtime} 门控（与记忆侧同款）——每次查询时 stat 一次文件，仅当 mtime 变化才
 * 重新解析。允许游戏运行中直接改 {@code config/stevex/vision.json} 生效，无需重启 / 重进。
 * 白名单<b>只控制采集端</b>是否为某类型采集整份 NBT + 薄摘要（§3.3：记忆端无白名单，来者不拒）。
 */
public final class DecorativeConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 默认展示实体白名单（§4 范围 + 决策点 1「加」= 包含 painting）。 */
    private static final String[] DEFAULT_TYPES = {
            "minecraft:item_frame",
            "minecraft:glow_item_frame",
            "minecraft:armor_stand",
            "minecraft:mannequin",
            "minecraft:item_display",
            "minecraft:block_display",
            "minecraft:text_display",
            "minecraft:painting"
    };

    private static final String FILE_NAME = "config/stevex/vision.json";
    private static final String KEY_TYPES = "decorativeEntityTypes";

    /** 当前生效白名单（构造后即含默认值；首次解析失败也保持默认）。 */
    private static volatile Set<String> types = defaultTypes();
    private static volatile FileTime lastMtime;
    private static volatile boolean warnOnce = true;

    private DecorativeConfig() {
    }

    /** 类型是否在展示实体白名单内（采集端决定是否为其算 payload + content）。 */
    public static boolean isDecorative(final String typeId) {
        refreshIfChanged();
        return typeId != null && types.contains(typeId);
    }

    /** 当前白名单快照（供诊断日志）。 */
    public static Set<String> types() {
        refreshIfChanged();
        return types;
    }

    private static Set<String> defaultTypes() {
        Set<String> set = new LinkedHashSet<>();
        for (String t : DEFAULT_TYPES) {
            set.add(t);
        }
        return set;
    }

    /** mtime 门控热重载：仅当 {@code config/stevex/vision.json} 的 mtime 变化时重新读取。 */
    private static void refreshIfChanged() {
        final Path file = configFile();
        if (file == null) return;
        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return; // 文件不存在 → 保持默认 / 上次生效集合
        }
        if (mtime.equals(lastMtime)) return;
        lastMtime = mtime;

        final Set<String> parsed = parse(file);
        if (parsed == null) {
            // 文件存在但解析失败 → 回退默认白名单；只警告一次，避免每帧刷屏。
            types = defaultTypes();
            if (warnOnce) {
                warnOnce = false;
                LOGGER.warn("[Vision] Invalid decorativeEntityTypes in {}, falling back to default allowlist", file);
            }
        } else {
            types = parsed;
            LOGGER.info("[Vision] Decorative entity allowlist updated ({} types): {}", types.size(), types);
        }
    }

    /** 解析配置；文件缺失 / 键缺失 / 内容非法 → null（调用方回退默认）。 */
    private static Set<String> parse(final Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            if (root == null || !root.isJsonObject()) return null;
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(KEY_TYPES) || !obj.get(KEY_TYPES).isJsonArray()) return null;
            JsonArray arr = obj.getAsJsonArray(KEY_TYPES);
            Set<String> set = new LinkedHashSet<>();
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    String id = el.getAsString();
                    if (!id.isBlank()) set.add(id);
                }
            }
            if (set.isEmpty()) return null;
            return set;
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    private static Path configFile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameDirectory == null) return null;
        return mc.gameDirectory.toPath().resolve(FILE_NAME);
    }
}
