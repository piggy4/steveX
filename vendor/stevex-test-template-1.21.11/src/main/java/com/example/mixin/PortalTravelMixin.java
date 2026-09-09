package com.example.mixin;

import com.example.memworld.MemoryWorldManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v2.33（记忆世界传送门冻结，见 docs/传送门冻结设计方案.md）：记忆世界里的传送门是<b>纯装饰</b>——
 * 禁止一切传送门触发的跨维旅行，杜绝 vanilla 在虚空目标维自动新建传送门 / 末地平台污染记忆世界。
 *
 * <p>为何打在 {@code Entity.canUsePortal}（设计 §2/§4）：1.21.11 传送门旅行完全由实体驱动——
 * 下界门 / 末地门 / 末地折跃门的 {@code entityInside} 先查 {@code canUsePortal(false)} 才
 * {@code setAsInsidePortal} 登记 {@code PortalProcessor} 累积 portalTime，累积满后
 * {@code handlePortal} 又以 {@code canUsePortal(false)} 放行旅行 → {@code getPortalDestination}
 *（下界 {@code PortalForcer.createPortal} 新建出口门、末地 {@code EndPlatformFeature} 铺平台）→
 * {@code teleport}。把该闸门强制置 {@code false} 让整条链在第一步短路：不登记进内、不累积、不旅行、
 * 不创建任何目的地方块。v2.21 全局冻结只停排程 tick / 冻结实体 tick，管不到这条「实体身处其内」的
 * 玩家路径，故需本版独立 Mixin 补洞。
 *
 * <p>为何打在基类而不逐个覆写：玩家是 {@code LivingEntity}，其 {@code canUsePortal} 为
 * {@code super.canUsePortal(...) && !isSleeping}（Creature 等同理 && super）——基类 HEAD 强制返回
 * {@code false} 经 super 链必然使最终结果为 false。该方法仅被传送门家族与跨维末影珍珠判定调用；
 * v2.32 镜像跟随跨维摆放走 {@code player.teleportTo} → {@code Entity.teleport(TeleportTransition)}，
 * <b>不经</b> {@code canUsePortal} → 完全不受影响。客户端渲染（粒子 / 声音）在 {@code animateTick}，
 * 本注入不碰 → 门照常「动」，只是不传送。
 */
@Mixin(Entity.class)
public abstract class PortalTravelMixin {

    /**
     * 实体在<b>记忆世界</b>（server 侧）时禁止使用任何传送门：下界门 / 末地门 / 末地折跃门不登记进内、
     * 不累积、不旅行；{@code getPortalDestination}（含目的地门创建 / 末地平台）永不触发。
     */
    @Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
    private void stevex$disablePortalTravel(final boolean ignorePassenger,
                                            final CallbackInfoReturnable<Boolean> cir) {
        if (((Entity) (Object) this).level() instanceof ServerLevel serverLevel
                && MemoryWorldManager.isMemoryWorld(serverLevel)) {
            cir.setReturnValue(false);
        }
    }
}
