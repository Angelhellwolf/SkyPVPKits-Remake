package com.remiaft.skypvpkits.api;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.managers.KitManager;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class SkyPVPKitsAPI {

    private SkyPVPKitsAPI() {
    }

    public static CompletableFuture<KitManager.TemporaryGrantResult> grantTemporaryKitOrCompensateAsync(Player player, String kitType, int level, int durationDays, double compensationCoins) {
        SkyPVPKits plugin = SkyPVPKits.getInstance();
        if (plugin == null || plugin.getKitManager() == null) {
            return CompletableFuture.completedFuture(
                    new KitManager.TemporaryGrantResult(false, false, false, "SkyPVPKits 未启用")
            );
        }
        return plugin.getKitManager()
                .grantTemporaryKitOrCompensate(player, kitType, level, durationDays, compensationCoins)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("BattlePass API 异步发放临时礼包失败: " + throwable.getMessage());
                    return new KitManager.TemporaryGrantResult(false, false, false, "API 调用失败");
                });
    }

    public static KitManager.TemporaryGrantResult grantTemporaryKitOrCompensate(Player player, String kitType, int level, int durationDays, double compensationCoins) {
        try {
            return grantTemporaryKitOrCompensateAsync(player, kitType, level, durationDays, compensationCoins)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            SkyPVPKits plugin = SkyPVPKits.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("BattlePass API 发放临时礼包失败: " + exception.getMessage());
            }
            return new KitManager.TemporaryGrantResult(false, false, false, "API 调用失败");
        }
    }
}
