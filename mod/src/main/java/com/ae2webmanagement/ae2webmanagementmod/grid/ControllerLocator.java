package com.ae2webmanagement.ae2webmanagementmod.grid;

import java.util.Optional;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.blockentity.networking.ControllerBlockEntity;

/**
 * 从 AE2 网络中定位 ME 控制器，返回其纬度+坐标锚点。
 *
 * <p>判定依据与 AE2 内部 {@code PathingService} 完全一致：{@code node.getOwner() instanceof ControllerBlockEntity}。
 *
 * <p><b>版本耦合说明</b>：这里引用了 AE2 内部类 {@link ControllerBlockEntity}（位于 {@code appeng.blockentity}，
 * 非 {@code appeng.api} 包）。这是 AE2 官方自己识别控制器的唯一信号，但属于版本耦合点，升级 AE2 时需复查。
 * 刻意把这段逻辑隔离在本类中，不扩散到业务代码，方便日后替换。
 */
public final class ControllerLocator {

    private ControllerLocator() {
    }

    /**
     * 在网络中定位 ME 控制器。
     *
     * <p>一个合法的控制器是由若干 {@code ControllerBlockEntity} 组成的多方块结构，其每个方块都会作为一个节点
     * 出现在 {@link IGrid#getNodes()} 里。虽然它们同属一个网络、任取其一即可，但为了让结果<b>稳定可复现</b>
     * （例如锚点写入 SavedData 后每次重启都指向同一处），这里取坐标字典序最小者。
     *
     * @param grid 目标网络
     * @return 控制器锚点；网络中没有 ME 控制器时返回 {@link Optional#empty()}
     */
    public static Optional<ControllerAnchor> locate(IGrid grid) {
        ControllerAnchor best = null;

        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof ControllerBlockEntity controller) {
                ControllerAnchor candidate = ControllerAnchor.of(node.getLevel(), controller.getBlockPos());
                if (best == null || candidate.pos().compareTo(best.pos()) < 0) {
                    best = candidate;
                }
            }
        }

        return Optional.ofNullable(best);
    }
}