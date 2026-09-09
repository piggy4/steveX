package name.modid.vision;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/**
 * 深度图采集 —— GPU PBO 非阻塞回读（v2 Phase 1）。
 *
 * <p>vanilla 的 {@code CommandEncoder.copyTextureToBuffer} 只支持把源纹理挂到
 * <b>color 附件</b>（GL_COLOR_ATTACHMENT0），深度纹理挂上去会导致 FBO 不完整、
 * glReadPixels 报 GL_INVALID_FRAMEBUFFER_OPERATION（1286）。因此这里<b>手写 GL 深度回读</b>，
 * 但沿用与 vanilla 相同的异步 PBO 机制：
 *
 * <ol>
 *   <li>创建 PBO（GL_PIXEL_PACK_BUFFER）+ 临时 read FBO，把主渲染目标深度纹理挂到
 *       <b>GL_DEPTH_ATTACHMENT</b>；</li>
 *   <li>{@code glReadPixels(GL_DEPTH_COMPONENT, GL_FLOAT)} 读进 PBO —— GPU 异步填充，CPU 不等待；</li>
 *   <li>{@link RenderSystem#queueFencedTask} 插入 fence，回调在
 *       {@code RenderSystem.executePendingTasks()} 轮询到 fence 完成后于<b>渲染线程</b>触发
 *       （Minecraft 帧循环 "gpuAsync" 阶段，两帧之间）。</li>
 * </ol>
 *
 * <p>因此本回读非阻塞、零卡顿，且按需触发（flag 门控），不做每帧回读。
 *
 * <p>调用时机：由 {@code LevelRendererMixin} 在 {@code LevelRenderer#renderLevel} 末尾注入
 * —— 世界主 pass 已渲染、主渲染目标深度尚未被 GameRenderer 清除（clearDepthTexture 1.0）。
 * 反投影所需上下文（相机位置 / 投影矩阵 / 模型视图矩阵）一并随快照捕获。
 *
 * <p>深度编码：DEPTH32，每像素 4 字节 float，范围 [0,1]（far=1.0，天空背景亦为 1.0）。
 * 行主序，行号 y 自底向上（GL 原点在左下角）。
 */
public class DepthCapture {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ==================== GL 常量（与 GlCommandEncoder 相同的硬编码风格） ====================

    private static final int GL_READ_FRAMEBUFFER = 36008; // GL_READ_FRAMEBUFFER
    private static final int GL_TEXTURE_2D = 3553; // GL_TEXTURE_2D
    private static final int GL_DEPTH_ATTACHMENT = 36096; // GL_DEPTH_ATTACHMENT
    private static final int GL_PIXEL_PACK_BUFFER = 35051; // GL_PIXEL_PACK_BUFFER
    private static final int GL_PACK_ROW_LENGTH = 3330; // GL_PACK_ROW_LENGTH
    private static final int GL_DEPTH_COMPONENT = 6402; // GL_DEPTH_COMPONENT
    private static final int GL_FLOAT = 5126; // GL_FLOAT
    private static final int GL_STREAM_READ = 35040; // GL_STREAM_READ
    private static final int GL_MAP_READ_BIT = 1; // GL_MAP_READ_BIT
    private static final int GL_NONE = 0; // GL_NONE
    private static final int GL_COLOR_ATTACHMENT0 = 36064; // GL_COLOR_ATTACHMENT0 (0x8CE0)

    private static final Object LOCK = new Object();
    private static volatile boolean captureRequested;
    private static CountDownLatch pendingLatch;
    private static DepthSnapshot readySnapshot;

    /**
     * 本帧 {@code extractVisibleEntities} 是否已消费请求并采集实体快照（v2.11 同帧握手）。
     *
     * <p>帧内两个注入点共用 {@code captureRequested}：实体快照在 {@code extractVisibleEntities}
     * @TAIL 采集（先执行）、深度在 {@code renderLevel} @TAIL 采集（后执行）。若请求在两者之间
     * 才置位，深度先捕获却无实体快照 → 整帧快照缺实体。规定：标志由帧内<b>第一个注入点</b>消费
     * （normal 帧即 extractVisibleEntities）；{@code tryCapture} 捕获前校验本标志，未采集则
     * 保留标志、推迟到下一帧——保证深度图与实体快照永远同帧。
     */
    private static volatile boolean entitiesCapturedThisFrame;

    /** 本帧 {@code extractVisibleEntities} 采集到的实际被渲染实体快照（供深度快照携带）。 */
    private static volatile List<EntitySnapshotData> pendingEntities;

    /**
     * 本帧 translucent 目标深度的 PBO 回读（v2.24 修复）。
     *
     * <p>translucent 目标是<b>帧内资源</b>：物理纹理只在 {@code frame.execute()} 的 main pass 执行
     * 期间被持有；此后（含 {@code LevelTargetBundle.clear()} 前）其 physicalResource 已被释放，
     * renderLevel TAIL 处 {@code getTranslucentTarget().get()} 抛 NPE、不能读取。故由
     * {@link #prepareTranslucentRead} 在 {@code renderGroup(TRANSLUCENT)} TAIL（main pass 内部、
     * 深度已定稿且资源仍存活）发起回读并暂存于此，{@link #tryCapture} 在帧末取走映射。
     * null = 未发起（非 Fabulous）或软失败。
     */
    private static volatile PendingRead frameTranslucentRead;

    /**
     * 本帧 late_debug pass 是否清空了主深度（v2.9 前哨）。
     *
     * <p>由 {@code LevelRendererMixin} 在 {@code LevelRenderer.finalizeGizmoCollection} 末尾写入：
     * F3 调试 gizmo（always-on-top 原语，如区块边界）非空时，late_debug pass 会在 renderLevel
     * TAIL 前 {@code clearDepthTexture(main, 1.0)}（LevelRenderer L799-801），导致捕获到全天空
     * 假深度——须检测并跳过该帧。正常游玩下该集合为空，不清深度。
     */
    private static volatile boolean lateDebugCleared;

    /** 最近一次捕获失败的原因（区分超时 / FBO 不完整 / GL error / late_debug 跳过等）。 */
    private static volatile String lastError;

    private DepthCapture() {
    }

    // ==================== API 线程接口 ====================

    /** 请求在下一帧采集深度图。 */
    public static void requestCapture() {
        synchronized (LOCK) {
            captureRequested = true;
            readySnapshot = null;
            pendingLatch = new CountDownLatch(1);
        }
    }

    /**
     * 等待采集完成。
     *
     * @param timeoutMs 超时毫秒
     * @return 深度快照；超时或采集失败时返回 null
     */
    public static DepthSnapshot awaitSnapshot(final long timeoutMs) throws InterruptedException {
        CountDownLatch latch;
        synchronized (LOCK) {
            if (readySnapshot != null) return readySnapshot;
            latch = pendingLatch;
        }
        if (latch == null || !latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null;
        synchronized (LOCK) {
            return readySnapshot;
        }
    }

    /** 最近一次采集失败的原因；成功或尚未采集时为 null。 */
    public static String lastError() {
        return lastError;
    }

    /** 是否有未消费的采集请求（渲染线程每帧做廉价门控，避免无请求时空转）。 */
    public static boolean isCaptureRequested() {
        return captureRequested;
    }

    // ==================== 渲染线程接口（Mixin 调用） ====================

    /**
     * 记录本帧 late_debug pass 是否会清空主深度（由 {@code LevelRendererMixin} 在
     * {@code LevelRenderer#finalizeGizmoCollection} 末尾调用，每帧刷新）。
     */
    public static void setLateDebugCleared(final boolean cleared) {
        lateDebugCleared = cleared;
    }

    /**
     * 采集本帧实际被渲染的实体快照（由 {@code LevelRendererMixin} 在
     * {@code LevelRenderer#extractVisibleEntities} @TAIL 调用）。
     *
     * <p>帧内第一个注入点消费 {@code captureRequested} 标志（v2.11 同帧握手）：无请求直接返回；
     * 有请求则消费并暂存实体列表，供本帧 {@link #tryCapture} 随深度快照携带。
     *
     * @param entities 复刻 {@code extractVisibleEntities} 裁剪谓词后的幸存实体快照
     */
    public static void storeEntitySnapshot(final List<EntitySnapshotData> entities) {
        synchronized (LOCK) {
            if (!captureRequested) return;
            captureRequested = false;
            entitiesCapturedThisFrame = true;
            pendingEntities = entities;
        }
    }

    /** 一次待回读的 PBO 请求：glReadPixels 已发出、GPU 异步填充中，fence 完成后由 {@link #mapDepth} 映射。 */
    private record PendingRead(int pbo, int width, int height, long bytes) {}

    /**
     * 发起一次深度纹理的异步 PBO 回读（GL_DEPTH_ATTACHMENT + glReadPixels 进 PBO，GPU 异步填充）。
     * 必须在渲染线程调用。失败抛 {@link IllegalStateException}——调用方据此决定硬失败（主深度）或
     * 降级（translucent 深度）。
     */
    private static PendingRead issueDepthRead(final GpuTexture depth) {
        final int width = depth.getWidth(0);
        final int height = depth.getHeight(0);
        final int pixelSize = depth.getFormat().pixelSize(); // DEPTH32 = 4
        final long bytes = (long) width * height * pixelSize;

        // ---- 读回用 PBO（GL_PIXEL_PACK_BUFFER）----
        final int pbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo);
        GlStateManager._glBufferData(GL_PIXEL_PACK_BUFFER, bytes, GL_STREAM_READ);
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);

        // ---- 只挂深度附件的临时 read FBO ----
        final int readFbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
        final int depthGlId = ((GlTexture) depth).glId();
        GlStateManager._glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthGlId, 0);

        // 深度-only 读 FBO 无 color 附件：显式把读缓冲置为 GL_NONE（v2.9）。否则默认
        // READ_BUFFER=COLOR_ATTACHMENT0（无 color）在严格驱动上可能判 FBO 不完整；
        // glReadPixels 的深度格式走深度附件、不受读缓冲影响。
        GL30.glReadBuffer(GL_NONE);

        final int status = GL30.glCheckFramebufferStatus(GL_READ_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            GlStateManager._glDeleteFramebuffers(readFbo);
            GlStateManager._glDeleteBuffers(pbo);
            throw new IllegalStateException("depth read framebuffer incomplete (status 0x" + Integer.toHexString(status) + ")");
        }

        // ---- 异步回读：glReadPixels 进 PBO，GPU 异步填充，fence 完成后由调用方映射 ----
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo);
        GlStateManager._pixelStore(GL_PACK_ROW_LENGTH, width);
        GlStateManager._readPixels(0, 0, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, 0);
        final int error = GlStateManager._getError();
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        // 恢复 GL 状态（v2.9）：GlCommandEncoder 把 PACK_ROW_LENGTH 设成 width 后从不复位，
        // 读回须显式恢复默认值 0 + 读缓冲复位，否则污染 Mojang 后续截图回读。
        GlStateManager._pixelStore(GL_PACK_ROW_LENGTH, 0);
        GL30.glReadBuffer(GL_COLOR_ATTACHMENT0);
        GlStateManager._glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        GlStateManager._glDeleteFramebuffers(readFbo);

        if (error != 0) {
            GlStateManager._glDeleteBuffers(pbo);
            throw new IllegalStateException("depth glReadPixels GL error " + error);
        }
        return new PendingRead(pbo, width, height, bytes);
    }

    /** 丢弃一个尚未进入 fenced 任务的 PBO 回读（在跳过帧 / 主深度不可读 / 异常路径避免 PBO 泄漏）。 */
    private static void discardPendingRead(final PendingRead read) {
        if (read != null) {
            GlStateManager._glDeleteBuffers(read.pbo());
        }
    }

    /** 取出并清空本帧 translucent 回读（渲染线程调用；tryCapture 消费，杜绝跨帧残留）。 */
    private static PendingRead takeFrameTranslucentRead() {
        final PendingRead r = frameTranslucentRead;
        frameTranslucentRead = null;
        return r;
    }

    /**
     * v2.24 修复（第二轮）：发起本帧 translucent 目标深度的 PBO 回读。由
     * {@code ChunkSectionsToRenderMixin} 在 {@code ChunkSectionsToRender.renderGroup} 渲染完
     * {@code TRANSLUCENT} 组的瞬间（main pass 内部、{@code frame.execute()} 执行期间）调用。
     *
     * <p><b>为什么必须在这个时刻读取</b>：translucent 目标是帧内内部资源，物理纹理只在
     * {@code frame.execute()} 的 main pass 执行期间被持有（acquire）。main pass 内
     * {@code copyDepthFrom(main)} + TRANSLUCENT 组 LEQUAL 深度写绘制完毕后，translucent 深度即
     * 定稿（= 主深度拷贝 + "首个半透明面"，§3.3/§5.4 两深度锚点所需的语义），而此时
     * {@code getTranslucentTarget().get()} 返回的 physicalResource 仍非 null（vanilla 自身的
     * {@code group.outputTarget()} 也在同一时刻解引用该目标）。
     *
     * <p>任何更晚的注入点（含 {@code targets.clear()} 之前）都落在 {@code frame.execute()} 返回
     * 之后：最后一个使用 translucent 的 pass（透明度后处理链）已把它 release（
     * {@code InternalVirtualResource} 把 physicalResource 置 null），
     * {@code getTranslucentTarget().get()} 抛 NPE → 回读软失败 → 工序 B 恒空。故必须把读取插进
     * frame 执行流内部。PBO 读命令按顺序排在 TRANSLUCENT 组绘制命令之后，GPU 先绘制再读回，数据
     * 确定正确；主深度回读在 {@code renderLevel} TAIL 排在其后，两条 PBO 由同一 fence 保证填完。
     *
     * <p>门控：仅本帧确有采集（实体快照已消费请求 ⇒ {@code entitiesCapturedThisFrame}）且
     * {@code useShaderTransparency()}（Fabulous）时发起。软失败 → {@link #frameTranslucentRead} 置
     * null，tryCapture 降级为单深度快照（工序 B 空集、工序 C 行为等同 v2.23）。
     */
    public static void prepareTranslucentRead() {
        boolean pending;
        synchronized (LOCK) {
            pending = entitiesCapturedThisFrame;
        }
        if (!pending || !Minecraft.getInstance().useShaderTransparency()) return;
        try {
            // 防重入：本帧已有未消费的回读则先丢弃（正常帧 renderGroup(TRANSLUCENT) 每帧一次，
            // 不会触发；仅在重复注入/异常时序下避免覆盖好读或泄漏 PBO）。
            if (frameTranslucentRead != null) {
                discardPendingRead(frameTranslucentRead);
                frameTranslucentRead = null;
            }
            final RenderTarget translucentTarget = Minecraft.getInstance().levelRenderer.getTranslucentTarget();
            if (translucentTarget == null) {
                return; // 非 Fabulous：无 translucent 目标，工序 B 本就无意义
            }
            final GpuTexture tDepth = translucentTarget.getDepthTexture();
            if (tDepth == null) {
                LOGGER.warn("[DepthCapture] translucent target has no depth texture; translucentDepth unavailable");
                return;
            }
            final RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (main == null || tDepth.getWidth(0) != main.width || tDepth.getHeight(0) != main.height) {
                LOGGER.warn("[DepthCapture] translucent target size {}x{} != main {}x{}; translucentDepth unavailable",
                        tDepth.getWidth(0), tDepth.getHeight(0),
                        main == null ? -1 : main.width, main == null ? -1 : main.height);
                return;
            }
            frameTranslucentRead = issueDepthRead(tDepth);
        } catch (Exception e) {
            LOGGER.warn("[DepthCapture] translucent depth readback unavailable: {}", e.getMessage());
            frameTranslucentRead = null;
        }
    }

    /** 映射 PBO 回读数据到 float[]；map 失败返回 null（调用方决定降级）。始终复位 PBO 绑定。 */
    private static float[] mapDepth(final PendingRead read) {
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, read.pbo());
        try {
            final ByteBuffer mapped = GlStateManager._glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0L, read.bytes(), GL_MAP_READ_BIT);
            if (mapped == null) return null;
            final float[] data = new float[read.width() * read.height()];
            mapped.order(ByteOrder.nativeOrder()); // GL_FLOAT 为平台原生字节序
            mapped.asFloatBuffer().get(data);
            GlStateManager._glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
            return data;
        } finally {
            GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        }
    }

    /**
     * 若本帧有采集请求，发起一次 GPU 深度 PBO 回读。
     *
     * <p>必须在渲染线程调用（{@code LevelRenderer.renderLevel} 末尾）。
     *
     * <p>v2.24（两深度锚点）：同一帧同时发起<b>主深度</b>与<b>translucent 目标深度</b>两路 PBO 回读，
     * fence 在两条 {@code glReadPixels} 之后插入 → 回调触发时两套深度均已填充完成，逐像素 1:1 对齐。
     * translucent 深度由 {@link #prepareTranslucentRead} 在 {@code ChunkSectionsToRender.renderGroup}
     * 渲染完 TRANSLUCENT 组的瞬间（main pass 内、{@code frame.execute()} 执行期间）发起——帧内目标
     * 的物理纹理此后即被释放，TAIL 处 {@code getTranslucentTarget()} 已不可用，见该方法说明。本方法
     * 只消费其结果。读不到则降级为单深度快照（translucentDepth = null），下游工序 B 空集、工序 C
     * 行为等同 v2.23。
     *
     * @param camera 当前相机（渲染用位置/朝向已就绪）
     * @param projectionMatrix 世界 pass 实际使用的投影矩阵
     * @param modelViewMatrix 世界 pass 实际使用的模型视图矩阵
     */
    public static void tryCapture(final Camera camera, final Matrix4f projectionMatrix, final Matrix4f modelViewMatrix) {
        CountDownLatch latch;
        List<EntitySnapshotData> entities;
        synchronized (LOCK) {
            if (captureRequested) {
                // 请求在 extractVisibleEntities 之后才置位 → 本帧无法配齐实体快照 →
                // 不消费标志、不碰 latch，保留到下一帧（v2.11 同帧握手）。
                return;
            }
            if (!entitiesCapturedThisFrame) {
                // 无请求被消费过：正常帧里不应发生（只有 storeEntitySnapshot 会消费）。
                return;
            }
            entitiesCapturedThisFrame = false;
            readySnapshot = null;
            latch = pendingLatch;
            entities = pendingEntities;
        }
        if (latch == null) return;

        // 消费本帧 translucent 深度回读（v2.24 修复）：由 prepareTranslucentRead 在
        // renderGroup(TRANSLUCENT) TAIL 发起——本注入点（renderLevel TAIL）已越过 frame.execute()，
        // 帧内目标物理纹理已被释放，getTranslucentTarget() 不可用，必须用事先取到的读。
        // null = 未发起（非 Fabulous / 无采集帧）或软失败 → 单深度快照。
        final PendingRead translucentRead = takeFrameTranslucentRead();

        // late_debug 前哨（v2.9）：本帧 always-on-top 调试 gizmo 非空 → late_debug pass 已把
        // 主深度清成 1.0，捕获到的是全天空假深度 → 跳过本帧，不发起无意义的回读。
        if (lateDebugCleared) {
            discardPendingRead(translucentRead);
            complete(latch, null, "late_debug pass cleared main depth this frame; capture skipped");
            return;
        }

        try {
            final RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
            final GpuTexture depth = target != null ? target.getDepthTexture() : null;
            if (depth == null) {
                discardPendingRead(translucentRead);
                complete(latch, null, "main render target depth texture is null");
                return;
            }

            // 主深度回读：硬失败（读不到 → 整帧失败，原因经异常上报）
            final PendingRead mainRead = issueDepthRead(depth);
            final int width = mainRead.width();
            final int height = mainRead.height();

            // translucent 目标深度（v2.24 两深度锚点，§3.3/§5.4）：PBO 已由 prepareTranslucentRead
            // 在 renderGroup(TRANSLUCENT) TAIL 发起。null → 未发起 / 软失败 → 单深度快照
            // （工序 B 空集、工序 C 行为等同 v2.23）。
            final PendingRead mainReadFinal = mainRead;
            final PendingRead translucentReadFinal = translucentRead;

            Vec3 camPos = camera.position();
            // v2.15：随快照捕获相机朝向（yaw = camera.yRot，pitch = camera.xRot），
            // 供 agentPos 一并落盘，记忆世界据此还原观察者视角。
            float camYaw = camera.yRot();
            float camPitch = camera.xRot();
            // v2.19：随快照捕获相机基础 FOV（options.fov().get()，整数度），供记忆世界同步视场角。
            // 注意动态 FOV（疾跑/水下/望远镜）已烤进 projectionMatrix（§3.3），此处只记基础值。
            int camFov = Minecraft.getInstance().options.fov().get();
            // v2.21：随快照捕获世界时间（游戏时间 dayTime，如 6000=正午 18000=午夜），
            // 记忆世界据此把昼夜/天光对齐到采集那一刻（§7.10）。
            long worldTime = Minecraft.getInstance().level == null ? -1L : Minecraft.getInstance().level.getDayTime();
            Matrix4f proj = new Matrix4f(projectionMatrix);
            Matrix4f mv = new Matrix4f(modelViewMatrix);
            long timestamp = System.currentTimeMillis();

            // 回调在 GPU 完成两个 PBO 填充后于渲染线程触发（非阻塞）。fence 在两条 glReadPixels 之后
            // 插入 → 回调触发时主深度与 translucent 深度均已填充完成，同一帧两套深度天然对齐。
            RenderSystem.queueFencedTask(() -> {
                try {
                    final float[] depthData = mapDepth(mainReadFinal);
                    if (depthData == null) {
                        complete(latch, null, "depth PBO map returned null");
                        return;
                    }
                    float[] translucentData = null;
                    if (translucentReadFinal != null) {
                        translucentData = mapDepth(translucentReadFinal);
                        if (translucentData == null) {
                            LOGGER.warn("[DepthCapture] translucent depth PBO map returned null; translucentDepth unavailable");
                        } else {
                            // v2.24 语义校验：统计 t<m（半透明层在前）的像素数——若回读的其实是主深度拷贝
                            // （读错目标 / translucent 未写入深度），恒 t==m、此处为 0，即可从日志分辨。
                            int tLessThanM = 0;
                            float tMin = Float.MAX_VALUE;
                            for (int i = 0; i < translucentData.length; i++) {
                                if (translucentData[i] < depthData[i]) {
                                    tLessThanM++;
                                    if (translucentData[i] < tMin) tMin = translucentData[i];
                                }
                            }
                            LOGGER.info("[DepthCapture] translucent depth: t<m pixels={}/{} ({})",
                                    tLessThanM, translucentData.length,
                                    tLessThanM == 0 ? "all t==m" : String.format(java.util.Locale.ROOT, "tMin=%.4f", tMin));
                        }
                    }
                    complete(latch, new DepthSnapshot(width, height, depthData, translucentData, camPos, camYaw, camPitch, camFov, worldTime, proj, mv, entities, timestamp), null);
                } catch (Exception e) {
                    LOGGER.error("[DepthCapture] readback failed", e);
                    complete(latch, null, e.getMessage());
                } finally {
                    if (translucentReadFinal != null) {
                        GlStateManager._glDeleteBuffers(translucentReadFinal.pbo());
                    }
                    GlStateManager._glDeleteBuffers(mainReadFinal.pbo());
                }
            });
        } catch (Exception e) {
            discardPendingRead(translucentRead);
            LOGGER.error("[DepthCapture] capture failed", e);
            complete(latch, null, e.getMessage());
        }
    }

    private static void complete(final CountDownLatch latch, final DepthSnapshot snapshot, final String error) {
        synchronized (LOCK) {
            readySnapshot = snapshot;
            lastError = error;
        }
        if (error != null) {
            LOGGER.warn("[DepthCapture] {}", error);
        }
        latch.countDown();
    }

    /**
     * 返回天空判定阈值 {@code d_far}（v2.16 起固定为 1.0）。
     *
     * <p>远平面在深度缓冲中的值恒为 1.0（OpenGL NDC z 范围 [-1,+1]：远平面 → ndc_z=+1 →
     * depth=(+1+1)/2=1.0），天空 / 清空值同为 1.0，故判定天空用 {@code d >= d_far}（=1.0）。
     * <b>不要</b>用 0.999（深度 1/z 非线性下 d(47 格) ≈ 0.999，会把 ~47 格外对象当天空丢弃，
     * v2.3）也不要引入 {@code 1.0f-1e-6f} 容差（会吞 d∈[1-1e-6,1.0) 的真实远表面，v2.6）。
     *
     * <p>v2.6 曾尝试从 m22/m23/m32 反推远平面距离再代回，但假设了 gluPerspective 布局
     * （m23=2fn/(f-n)、m32=-1）；而 JOML {@code Matrix4f.perspective} 实为
     * {@code m22=(f+n)/(n-f)、m23=-1、m32=2fn/(n-f)}（m23/m32 语义互换）。用错误布局反推得
     * {@code far≈1.11 → dFar≈0.55}，把真实地形（depth≈0.98）整体误判成天空 →
     * {@code nonSkyPixels=0}、采集到 0 方块。故 v2.16 直接固定为 1.0。
     *
     * @param projectionMatrix 世界 pass 实际使用的投影矩阵（保留参数以兼容调用方；标准透视下不参与计算）
     * @return 远平面极限深度，恒为 1.0
     */
    public static float depthOfFarPlane(final Matrix4f projectionMatrix) {
        return 1.0f;
    }

    // ==================== 数据 ====================

    /**
     * 一帧的原始深度图 + 反投影所需上下文 + 同帧实体快照（v2.11）。
     *
     * @param depth 主目标原始深度 [0,1]，长度 width*height，行主序；行号自底向上
     *              （GL 原点在左下角，y=0 为最底行）
     * @param translucentDepth translucent 目标深度（v2.24，两深度锚点）：内容 = 主深度
     *                         （copyDepthFrom(main)）被 TRANSLUCENT 组（水/玻璃等）在前覆盖，
     *                         即"首个半透明面"，且恒 ≤ depth（同投影同尺寸、逐像素对齐）。
     *                         仅 Fabulous 且第二路 PBO 回读成功时非 null；否则为 null
     *                         （工序 B 不执行、工序 C 无残留瘦身，行为等同 v2.23）
     * @param entities 与深度图同帧（{@code extractVisibleEntities} @TAIL）采集的
     *                 实际被渲染实体快照；深度/相机/矩阵/实体四件套永远同帧
     * @param yaw      相机水平朝向（{@code camera.yRot()}，度）
     * @param pitch    相机俯仰朝向（{@code camera.xRot()}，度）
     * @param fov      相机基础视场角（{@code options.fov().get()}，整数度；动态 FOV 已烤进投影矩阵，见 §3.3）
     * @param dayTime  采集时刻的世界时间（{@code level.getDayTime()}，游戏时间单位；无世界时 -1）
     */
    public record DepthSnapshot(
            int width,
            int height,
            float[] depth,
            float[] translucentDepth,
            Vec3 cameraPos,
            float yaw,
            float pitch,
            int fov,
            long dayTime,
            Matrix4f projectionMatrix,
            Matrix4f modelViewMatrix,
            List<EntitySnapshotData> entities,
            long timestamp
    ) {
        /** 指定像素的主目标深度值；y 自底向上，越界返回 1.0（按天空处理）。 */
        public float depthAt(final int x, final int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) return 1.0f;
            return depth[y * width + x];
        }

        /** 是否有 translucent 目标深度（两深度锚点，v2.24）：仅 Fabulous 且第二路 PBO 回读成功时为非空。 */
        public boolean hasTranslucentDepth() {
            return translucentDepth != null;
        }

        /** 指定像素的 translucent 目标深度；越界返回 1.0（按天空处理）。无第二路深度时不可调用。 */
        public float translucentDepthAt(final int x, final int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) return 1.0f;
            return translucentDepth[y * width + x];
        }
    }

    /**
     * 一帧实际被渲染实体的快照（v2.9/v2.10/v2.11）。
     *
     * <p>由 {@code extractVisibleEntities} @TAIL 复刻裁剪谓词后捕获，供 API 线程建 SectionPos 桶、
     * {@code ObjectResolver} 做正向像素归属匹配。字段全部为渲染线程纯读取，捕获后与游戏解耦。
     *
     * @param box    按渲染帧 partialTick 插值对齐的 AABB（v2.10），供 contains / 射线-AABB 匹配
     * @param x,y,z  partialTick 插值位置（= 渲染位置，Tier-1 输出用）
     * @param health 仅 LivingEntity 有值；其余为 0
     * @param item   v2.34（掉落物记忆，见 docs/掉落物记忆设计方案.md）：当 typeId 为 {@code minecraft:item}
     *               时携带本帧被渲染的物品栈（{@code ItemStack.CODEC} + {@code NbtOps} 编码 tag）；其余类型为 null
     * @param payload v2.35（展示实体内容记忆，见 docs/展示实体内容记忆设计方案.md §6.3）：当 typeId ∈ 采集白名单
     *                时为本帧编码的整份 NBT payload（{@code VisionCollector#serializeEntityFull}）；其余类型 / 失败为 null
     * @param content v2.35（决策点 2 渠道 B）：与 payload 同帧构建的薄内容摘要（{@code DecorativeSummary}），
     *                仅采集端 snapshot JSON 消费；其余类型 / 失败为 null
     */
    public record EntitySnapshotData(
            int id,
            UUID uuid,
            String typeId,
            AABB box,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            double vx,
            double vy,
            double vz,
            boolean onGround,
            float health,
            CompoundTag item,
            CompoundTag payload,
            CompoundTag content
    ) {}
}
