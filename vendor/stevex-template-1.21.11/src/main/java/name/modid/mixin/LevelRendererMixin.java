package name.modid.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import java.util.ArrayList;
import java.util.List;
import name.modid.vision.DecorativeConfig;
import name.modid.vision.DecorativeSummary;
import name.modid.vision.DepthCapture;
import name.modid.vision.VisionCollector;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * 注入 {@link LevelRenderer#renderLevel} 末尾 —— 世界主 pass 渲染完成后、主渲染目标深度
 * 被 GameRenderer 清除（clearDepthTexture）之前，按需触发深度图 PBO 回读。
 *
 * <p>直接取目标方法参数中的相机 / 投影矩阵 / 模型视图矩阵，无需重建或读 RenderSystem 状态。
 * 注意：TAIL 注入必须声明目标方法的全部参数（Mixin 严格匹配完整签名）。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    private Minecraft minecraft;

    @Shadow
    private EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    private ClientLevel level;

    /** LevelRenderer.isSectionCompiledAndVisible(BlockPos) —— 复刻谓词链需调用。 */
    @Shadow
    public abstract boolean isSectionCompiledAndVisible(BlockPos blockPos);

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onLevelRenderDone(
            final GraphicsResourceAllocator resourceAllocator,
            final DeltaTracker deltaTracker,
            final boolean renderOutline,
            final Camera camera,
            final Matrix4f modelViewMatrix,
            final Matrix4f projectionMatrix,
            final Matrix4f projectionMatrixForCulling,
            final GpuBufferSlice terrainFog,
            final Vector4f fogColor,
            final boolean shouldRenderSky,
            final CallbackInfo ci
    ) {
        DepthCapture.tryCapture(camera, projectionMatrix, modelViewMatrix);
    }

    /**
     * 实体快照（v2.9）：在 {@code LevelRenderer#extractVisibleEntities} @TAIL 复刻其 L821-826 的
     * 裁剪谓词链，对幸存实体快照完整数据——这是"实际被渲染实体"的权威来源。
     *
     * <p>为何不直接用 {@code levelRenderState.entityRenderStates}（v2.9 核查）：它是
     * {@code List<EntityRenderState>}（DTO），缺 AABB/id/uuid/rot/motion/health、无回指 Entity 的
     * 引用，且 {@code levelRenderState.reset()} 在 renderLevel 紧挨 TAIL 前已清空。
     * 为何不用 {@code entitiesForRendering()} 原样全收：返回全部已加载实体、无视锥裁剪
     * （= {@code byId.values()}），对"列表有实体、深度是其后方块"会假阳性（纯累积语义最忌）。
     * 复刻谓词即与 vanilla 逐字节一致（对 EnderDragonPart 等调用同一 {@code shouldRender}）。
     *
     * <p>帧内第一个注入点消费 {@code captureRequested} 并采实体快照（v2.11 同帧握手），
     * {@code renderLevel} TAIL 据此把深度与实体配成同帧。
     */
    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void onExtractVisibleEntitiesDone(
            final Camera camera,
            final Frustum frustum,
            final DeltaTracker deltaTracker,
            final LevelRenderState output,
            final CallbackInfo ci
    ) {
        // 无采集请求时零成本门控（避免每帧空转迭代全部实体）
        if (!DepthCapture.isCaptureRequested()) return;
        final ClientLevel lvl = this.level;
        if (lvl == null) return;
        final Vec3 cameraPos = camera.position();
        final double camX = cameraPos.x();
        final double camY = cameraPos.y();
        final double camZ = cameraPos.z();
        final TickRateManager tickRateManager = this.minecraft.level.tickRateManager();

        final List<DepthCapture.EntitySnapshotData> out = new ArrayList<>();
        for (Entity entity : lvl.entitiesForRendering()) {
            if (entity.isRemoved()) continue;
            // 复刻 extractVisibleEntities L821-826 谓词链
            if (this.entityRenderDispatcher.shouldRender(entity, frustum, camX, camY, camZ)
                    || entity.hasIndirectPassenger(this.minecraft.player)) {
                final BlockPos blockPos = entity.blockPosition();
                if ((lvl.isOutsideBuildHeight(blockPos.getY()) || this.isSectionCompiledAndVisible(blockPos))
                        && (entity != camera.entity() || camera.isDetached()
                            || (camera.entity() instanceof LivingEntity living && living.isSleeping()))
                        && (!(entity instanceof LocalPlayer) || camera.entity() == entity)) {
                    // AABB 按渲染帧 partialTick 插值对齐（v2.10）；prev 取 xo/yo/zo（v2.11，
                    // 勿用 deltaMovement 代理——碰撞/传送后运动矢量与帧间位移不一致）
                    final float partial = deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));
                    final double lerpX = (entity.getX() - entity.xo) * partial;
                    final double lerpY = (entity.getY() - entity.yo) * partial;
                    final double lerpZ = (entity.getZ() - entity.zo) * partial;
                    final AABB box = entity.getBoundingBox().move(lerpX, lerpY, lerpZ);
                    final double x = entity.getX() + lerpX;
                    final double y = entity.getY() + lerpY;
                    final double z = entity.getZ() + lerpZ;
                    final Vec3 motion = entity.getDeltaMovement();
                    final float health = entity instanceof LivingEntity living ? living.getHealth() : 0.0f;
                    // 实体注册名：v2.34 掉落物 / v2.35 展示实体分支的判定依据（先算一次共用）。
                    final String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                    // v2.34（掉落物记忆）：本帧物品栈在快照瞬间编码（读到的正是"本帧被渲染的栈"，
                    // 杜绝 ObjectResolver 延后解析时栈被合并/拾取换掉的竞态）。掉落实体大类判定经
                    // VisionCollector#isItemEntity（注册表派生的单一事实来源），与 ObjectResolver
                    // 的半透明掉落物候选共用同一判定（防子类异 id 等边角与解析侧不一致）。
                    // instanceof 仍需保留以取栈实例。空栈/失败 → null。
                    final CompoundTag itemTag = entity instanceof ItemEntity itemEntity
                            && VisionCollector.isItemEntity(typeId)
                            ? encodeItemStack(itemEntity.getItem(), lvl.registryAccess())
                            : null;
                    // v2.35（展示实体内容记忆，设计 §6.2）：typeId ∈ 采集白名单（DecorativeConfig，
                    // config/stevex/vision.json 热重载）→ 本帧编码整份可装载 NBT payload + 同帧
                    // 薄内容摘要。白名单只控采集端；内存侧无白名单（§3.3）。非白名单 → 两者 null。
                    final CompoundTag payload;
                    final CompoundTag content;
                    if (DecorativeConfig.isDecorative(typeId)) {
                        payload = VisionCollector.serializeEntityFull(entity);
                        content = DecorativeSummary.build(entity, typeId, payload, lvl.registryAccess());
                    } else {
                        payload = null;
                        content = null;
                    }
                    out.add(new DepthCapture.EntitySnapshotData(
                            entity.getId(),
                            entity.getUUID(),
                            typeId,
                            box, x, y, z,
                            entity.getYRot(), entity.getXRot(),
                            motion.x, motion.y, motion.z,
                            entity.onGround(), health, itemTag, payload, content));
                }
            }
        }
        DepthCapture.storeEntitySnapshot(out);
    }

    /**
     * late_debug 前哨（v2.9）：在 {@code LevelRenderer#finalizeGizmoCollection} 把收集的 gizmo
     * 落盘到 {@code finalizedGizmos} 时，记录本帧是否有 always-on-top 调试原语。
     *
     * <p>若非空，稍后 {@code addLateDebugPass} 会在 renderLevel TAIL 前把主深度
     * {@code clearDepthTexture(main, 1.0)}（LevelRenderer L799-801）——捕获到的是全天空假深度，
     * {@link DepthCapture#tryCapture} 据此跳过该帧。正常游玩下该集合为空，不清深度。
     *
     * <p>注入点取 {@code finalizedGizmos} 字段写入之前（两个局部量此时仍存活）；
     * 用 {@link LocalCapture#CAPTURE_FAILSOFT} 按声明顺序捕获两个 {@code DrawableGizmoPrimitives}
     * 局部量（{@code standardPrimitives}、{@code alwaysOnTopPrimitives}），取第二个判空。
     * FAILSOFT：局部量捕获失败时静默跳过（late_debug 检测退化为不生效），不导致启动崩溃。
     */
    @Inject(
            method = "finalizeGizmoCollection",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizedGizmos:Lnet/minecraft/client/renderer/LevelRenderer$FinalizedGizmos;"
            ),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void onFinalizedGizmos(
            final CallbackInfo ci,
            final DrawableGizmoPrimitives standardPrimitives,
            final DrawableGizmoPrimitives alwaysOnTopPrimitives
    ) {
        DepthCapture.setLateDebugCleared(!alwaysOnTopPrimitives.isEmpty());
    }

    /**
     * v2.34（掉落物记忆，见 docs/掉落物记忆设计方案.md）：把物品栈编码成 {@code ItemStack.CODEC} tag
     * （{@code {id, count?, components?}}，与容器/末影箱条目同一路径，记忆侧同法解码）。空栈 / 失败 → null。
     */
    private static CompoundTag encodeItemStack(final ItemStack stack, final HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            Tag tag = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                    .resultOrPartial(err -> { }).orElse(null);
            return tag instanceof CompoundTag c ? c : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
