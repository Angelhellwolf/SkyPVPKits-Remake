package com.remiaft.skypvpkits.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Explicit shop provider. Missing definitions never fall back to legacy rewards. */
public final class AngelRewardsBridge {
    private final Plugin provider;
    private final Method prepare, preview, validate, deliver;

    public AngelRewardsBridge() throws ReflectiveOperationException {
        provider = Bukkit.getPluginManager().getPlugin("AngelKits");
        if (provider == null || !provider.isEnabled()) throw new IllegalStateException("AngelKits is required");
        Class<?> type = provider.getClass();
        prepare = type.getMethod("prepareShopRewards", String.class, int.class, long.class, String.class);
        preview = type.getMethod("previewShopRewards", String.class, int.class);
        validate = type.getMethod("validateShopSpace", Player.class, List.class);
        deliver = type.getMethod("deliverShopRewards", Player.class, List.class);
    }

    @SuppressWarnings("unchecked")
    public List<ItemStack> prepare(String family, int level, long deadline, String zone) {
        return (List<ItemStack>) invoke(prepare, family, level, deadline, zone);
    }
    @SuppressWarnings("unchecked")
    public List<ItemStack> preview(String family, int level) { return (List<ItemStack>) invoke(preview, family, level); }
    public void validate(Player player, List<ItemStack> items) {
        try {
            @SuppressWarnings("unchecked") List<ItemStack> filtered=(List<ItemStack>)invoke(provider.getClass().getMethod("filterShopRewards",Player.class,List.class),player,items);
            items.clear();items.addAll(filtered);
        } catch(NoSuchMethodException oldProvider) { /* Compatibility with the previous provider. */ }
        invoke(validate, player, items);
    }
    public void deliver(Player player, List<ItemStack> items) { invoke(deliver, player, items); }

    private Object invoke(Method method, Object... arguments) {
        if (!provider.isEnabled()) throw new IllegalStateException("Reward provider is unavailable");
        try { return method.invoke(provider, arguments); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Reward provider rejected request", cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Reward provider API unavailable", failure); }
    }
}
