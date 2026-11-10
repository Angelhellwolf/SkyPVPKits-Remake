package com.remiaft.skypvpkits.bridge;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

public final class SkyPVPItemBridge {

    private static final String PROVIDER_CLASS_NAME = "com.remiaft.skypvpitem.api.SkyPVPItemProvider";
    private static volatile Access cachedAccess;

    private SkyPVPItemBridge() {
    }

    public static boolean isAvailable() {
        return resolveAccess() != null;
    }

    public static List<ItemStack> previewSet(String setId) {
        Access access = resolveAccess();
        if (access == null || access.previewSetMethod == null) throw new IllegalStateException("Versioned set preview unavailable");
        try {
            Object value = access.previewSetMethod.invoke(access.api, setId);
            if (!(value instanceof Collection) || ((Collection<?>) value).isEmpty()) throw new IllegalStateException("Empty set preview");
            List<ItemStack> result = new ArrayList<>();
            for (Object item : (Collection<?>) value) result.add(((ItemStack) item).clone());
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Versioned set preview failed", failure);
        }
    }

    public static boolean supportsActivityExpiry() {
        Access access = resolveAccess();
        return access != null && access.giveSetUntilMethod != null && access.giveActivityItemsMethod != null;
    }

    public static boolean giveSet(Player player, String setId) {
        Access access = resolveAccess();
        if (access == null) {
            return false;
        }

        try {
            Object result = access.giveSetMethod.invoke(access.api, player, setId);
            if (result instanceof Collection) {
                return !((Collection<?>) result).isEmpty();
            }
            return result != null;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static boolean giveSetUntil(
            Player player,
            String setId,
            long expiresAtEpochMillis,
            String displayZoneId) {
        Access access = resolveAccess();
        if (access == null || access.giveSetUntilMethod == null) {
            return false;
        }

        try {
            Object result = access.giveSetUntilMethod.invoke(
                    access.api,
                    player,
                    setId,
                    expiresAtEpochMillis,
                    displayZoneId);
            return result instanceof Collection && !((Collection<?>) result).isEmpty();
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static int giveActivityItems(
            Player player,
            Collection<ItemStack> items,
            String activityId,
            long expiresAtEpochMillis,
            String displayZoneId) {
        Access access = resolveAccess();
        if (access == null || access.giveActivityItemsMethod == null) {
            return 0;
        }

        try {
            Object result = access.giveActivityItemsMethod.invoke(
                    access.api,
                    player,
                    items,
                    activityId,
                    expiresAtEpochMillis,
                    displayZoneId);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    private static Access resolveAccess() {
        Access current = cachedAccess;
        if (current != null) {
            return current;
        }

        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS_NAME);
            Method getMethod = providerClass.getMethod("get");
            Object api = getMethod.invoke(null);
            if (api == null) {
                return null;
            }
            Access created = new Access(api);
            cachedAccess = created;
            return created;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static final class Access {
        private final Object api;
        private final Method giveSetMethod;
        private final Method giveSetUntilMethod;
        private final Method giveActivityItemsMethod;
        private final Method previewSetMethod;

        private Access(Object api) throws NoSuchMethodException {
            this.api = api;
            this.giveSetMethod = api.getClass().getMethod("giveSet", Player.class, String.class);
            this.previewSetMethod = findMethod(api.getClass(), "previewSet", String.class);
            this.giveSetUntilMethod = findMethod(
                    api.getClass(),
                    "giveSetUntil",
                    Player.class,
                    String.class,
                    long.class,
                    String.class);
            this.giveActivityItemsMethod = findMethod(
                    api.getClass(),
                    "giveActivityItems",
                    Player.class,
                    Collection.class,
                    String.class,
                    long.class,
                    String.class);
        }

        private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            try {
                return type.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }
}
