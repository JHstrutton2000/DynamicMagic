package com.strutton.dynamicmagic.compat;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
/** Optional, reflection-only access to Eternal Cultivation's qi attachment. */
public final class EternalCultivationBridge {
    private static final String MOD_ID = "eternal_cultivation";
    private static Object attachmentSupplier;
    private static Method getData;
    private static Method setData;
    private static Method getKi;
    private static Method getMaxKi;
    private static Method withKi;
    private static Method sync;
    private static boolean initialized;
    private static boolean usable;
    private static boolean warned;

    private EternalCultivationBridge() {}

    public static boolean installed() { return ModList.get().isLoaded(MOD_ID); }

    public static int current(ServerPlayer player) {
        Object data = data(player);
        if (data == null) return 0;
        try { return ((Number) getKi.invoke(data)).intValue(); }
        catch (ReflectiveOperationException | RuntimeException error) { disable(error); return 0; }
    }

    public static int maximum(ServerPlayer player) {
        Object data = data(player);
        if (data == null) return 0;
        try { return ((Number) getMaxKi.invoke(data)).intValue(); }
        catch (ReflectiveOperationException | RuntimeException error) { disable(error); return 0; }
    }

    public static boolean consume(ServerPlayer player, double amount) {
        if (amount <= 0) return true;
        Object data = data(player);
        if (data == null) return false;
        int required = Math.max(1, (int) Math.ceil(amount));
        try {
            int available = ((Number) getKi.invoke(data)).intValue();
            if (available < required) return false;
            Object changed = withKi.invoke(data, available - required);
            setData.invoke(player, attachmentSupplier, changed);
            sync.invoke(null, player, changed);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            disable(error);
            return false;
        }
    }

    private static Object data(ServerPlayer player) {
        initialize(player);
        if (!usable) return null;
        try { return getData.invoke(player, attachmentSupplier); }
        catch (ReflectiveOperationException | RuntimeException error) { disable(error); return null; }
    }

    private static synchronized void initialize(ServerPlayer player) {
        if (initialized) return;
        initialized = true;
        if (!installed()) return;
        try {
            Class<?> attachments = Class.forName("com.mrgoodwill.eternalcultivation.data.ModAttachments");
            attachmentSupplier = attachments.getField("CULTIVATION").get(null);
            for (Method method : player.getClass().getMethods()) {
                if (method.getName().equals("getData") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(attachmentSupplier)) getData = method;
                if (method.getName().equals("setData") && method.getParameterCount() == 2
                        && method.getParameterTypes()[0].isInstance(attachmentSupplier)) setData = method;
            }
            if (getData == null || setData == null) throw new NoSuchMethodException("NeoForge attachment access");
            Object cultivation = getData.invoke(player, attachmentSupplier);
            getKi = cultivation.getClass().getMethod("ki");
            getMaxKi = cultivation.getClass().getMethod("maxKi");
            withKi = cultivation.getClass().getMethod("withKi", int.class);
            Class<?> payload = Class.forName("com.mrgoodwill.eternalcultivation.network.CultivationActionPayload");
            sync = payload.getMethod("sync", ServerPlayer.class, cultivation.getClass());
            usable = true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            disable(error);
        }
    }

    private static void disable(Throwable error) {
        if (!warned) {
            DynamicMagic.LOGGER.warn("Eternal Cultivation qi bridge is unavailable", error);
            warned = true;
        }
        usable = false;
    }
}
