package com.remiaft.skypvpkits.bridge;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;

public final class SkyPVPItemBridge {

    private static final String PROVIDER_CLASS_NAME = "com.remiaft.skypvpitem.api.SkyPVPItemProvider";

    private SkyPVPItemBridge() {
    }

    public static boolean isAvailable() {
        return resolveApi() != null;
    }

    public static boolean giveSet(Player player, String setId) {
        Object api = resolveApi();
        if (api == null) {
            return false;
        }

        try {
            Method giveSetMethod = api.getClass().getMethod("giveSet", Player.class, String.class);
            Object result = giveSetMethod.invoke(api, player, setId);
            if (result instanceof Collection) {
                return !((Collection<?>) result).isEmpty();
            }
            return result != null;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static Object resolveApi() {
        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS_NAME);
            Method getMethod = providerClass.getMethod("get");
            return getMethod.invoke(null);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
