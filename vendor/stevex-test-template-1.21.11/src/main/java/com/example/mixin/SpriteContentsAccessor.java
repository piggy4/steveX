package com.example.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v2.37（设计 §4.2）<b>本版唯一的 MixIn，且只读不改</b> —— 暴露 {@link SpriteContents#originalImage}
 * 以读取贴图的<b>连续 alpha</b>，供 {@code SpriteAlphaTable} 建立"sprite 逐 texel alpha 掩码表"。
 *
 * <p><b>为什么必须新开一个 accessor</b>：{@code SpriteContents} 的公开入口不够用——它只暴露
 * {@code isTransparent(frame, x, y)}（{@code SpriteContents:202-211}），而那只是 <b>alpha == 0 的布尔</b>；
 * 本设计需要的是与 {@code terrain.fsh:88-96} 的 {@code if (color.a < ALPHA_CUTOUT) discard;} 同口径的
 * <b>连续 alpha</b>（阈值逐渲染层不同：SOLID 无阈值 / CUTOUT 0.5 / TRANSLUCENT 0.01，见设计 §5.2）。
 * 用 0/1 的布尔建模会与真实 discard 判定不一致 → 破坏 {@code G ⊆ R}。
 *
 * <p>{@code originalImage} 字段为 {@code private final NativeImage}（{@code SpriteContents:46}），
 * 无 getter，故只能经 MixIn。@Accessor 的命名推导：剥掉 "get" → "originalImage"，与字段名一致。
 *
 * <p><b>失效时 fail-closed</b>（设计 §9）：若 MixIn 在某环境下未生效，调用点会抛
 * {@code ClassCastException}/返回 null → {@code SpriteAlphaTable} 捕获后返回"不可得"，该 quad
 * <b>整条不参与</b>（欠删，安全方向）。<b>明确禁止回退到"整面全通过"或 AABB</b>——那等于
 * {@code G ⊋ R}（几何盖住渲染不占的地方）→ 空余区射线变假证据 → 误删活体。
 *
 * <p>注意本 accessor 只应在<b>客户端线程</b>调用（{@code SpriteContents} 随资源重载整体重建），
 * 调用点见 {@code SpriteAlphaTable.intern} 的前置约束。
 */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {

    /** mip 0 原始贴图（动画贴图为竖直帧条：帧 i 位于 {@code y ∈ [i*height, (i+1)*height)}）。 */
    @Accessor("originalImage")
    NativeImage stevex$getOriginalImage();
}
