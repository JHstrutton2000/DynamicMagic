package com.strutton.dynamicmagic.morph;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.mana.Mana;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/** Optional MorphV2 bridge. Reflection keeps Dynamic Spellcraft runnable when MorphV2 is absent. */
public final class MorphV2Integration {
    private static final String MORPH_EVENT = "me.ichun.mods.morph.api.event.MorphEvent$Morph";
    private static final String ACTIVE = "DynamicMagicMorphActive";
    private static final String DEMORPHING = "DynamicMagicMorphDemorphing";
    private static final String COOLDOWN = "DynamicMagicMorphCooldown";
    private static final int EXHAUSTION_COOLDOWN_TICKS = 100;
    private static volatile Bridge bridge;

    private MorphV2Integration() {}

    public static boolean installed() {
        return ModList.get().isLoaded("morph");
    }

    public static void registerMorphEvent(IEventBus eventBus) {
        try {
            Class<? extends Event> eventClass = Class.forName(MORPH_EVENT).asSubclass(Event.class);
            registerExactEvent(eventBus, eventClass);
        } catch (ReflectiveOperationException | LinkageError exception) {
            DynamicMagic.LOGGER.error("MorphV2 is loaded but its morph event API could not be registered", exception);
        }
    }

    private static <T extends Event> void registerExactEvent(IEventBus eventBus, Class<T> eventClass) {
        eventBus.addListener(EventPriority.HIGHEST, false, eventClass, MorphV2Integration::onMorphEvent);
    }

    private static void onMorphEvent(Event event) {
        if (!event.getClass().getName().equals(MORPH_EVENT)
                || !(invokeNoArgs(event, "getEntity") instanceof ServerPlayer player)) return;
        Object variant = invokeNoArgs(event, "getVariant");
        if (isPlayerVariant(variant)) {
            player.getPersistentData().putBoolean(ACTIVE, false);
            player.getPersistentData().putBoolean(DEMORPHING, true);
            return;
        }
        if (!SkillKnowledge.knows(player, MagicSkill.MORPHING)) {
            cancel(event);
            player.displayClientMessage(Component.literal("You must learn the Morphing skill before assuming another form.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        long remaining = cooldownTicks(player);
        if (remaining > 0) {
            cancel(event);
            player.displayClientMessage(Component.literal("Your form is unstable for another "
                    + ((remaining + 19) / 20) + " seconds.").withStyle(ChatFormatting.RED), true);
            return;
        }
        player.getPersistentData().putBoolean(ACTIVE, true);
        player.getPersistentData().putBoolean(DEMORPHING, false);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        LivingEntity form = activeForm(player);
        boolean transformed = form != null && form.getType() != EntityType.PLAYER;
        var data = player.getPersistentData();
        if (!transformed) {
            data.putBoolean(ACTIVE, false);
            data.putBoolean(DEMORPHING, false);
            return;
        }
        if (data.getBoolean(DEMORPHING)) return;
        if (!SkillKnowledge.knows(player, MagicSkill.MORPHING)) {
            forceDemorph(player, "Your transformation ended because you no longer know the Morphing skill.", false);
            return;
        }
        data.putBoolean(ACTIVE, true);
        double upkeep = maintenanceCost(form);
        if (!Mana.consume(player, upkeep)) {
            forceDemorph(player, "Your transformation collapsed when its mana tether ran dry.", true);
        }
    }

    public static boolean isMorphed(ServerPlayer player) {
        LivingEntity form = activeForm(player);
        return form != null && form.getType() != EntityType.PLAYER;
    }

    public static double currentMaintenance(ServerPlayer player) {
        LivingEntity form = activeForm(player);
        return form == null || form.getType() == EntityType.PLAYER ? 0 : maintenanceCost(form);
    }

    public static long cooldownTicks(ServerPlayer player) {
        return Math.max(0, player.getPersistentData().getLong(COOLDOWN) - player.serverLevel().getGameTime());
    }

    public static double maintenanceCost(LivingEntity form) {
        boolean boss = form instanceof EnderDragon || form instanceof WitherBoss;
        boolean hostile = form instanceof Enemy || form.getType().getCategory() == MobCategory.MONSTER;
        boolean flying = form instanceof FlyingMob || switch (BuiltInRegistries.ENTITY_TYPE.getKey(form.getType()).getPath()) {
            case "allay", "bat", "bee", "parrot", "phantom", "vex", "ghast", "ender_dragon" -> true;
            default -> false;
        };
        return MorphManaCost.calculate(form.getMaxHealth(), form.getBbWidth(), form.getBbHeight(), hostile, flying, boss);
    }

    private static void forceDemorph(ServerPlayer player, String message, boolean exhausted) {
        var data = player.getPersistentData();
        data.putBoolean(ACTIVE, false);
        data.putBoolean(DEMORPHING, true);
        if (exhausted) data.putLong(COOLDOWN, player.serverLevel().getGameTime() + EXHAUSTION_COOLDOWN_TICKS);
        try {
            bridge().demorph.invoke(bridge().api, player);
            player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.DARK_PURPLE), true);
        } catch (ReflectiveOperationException | LinkageError exception) {
            data.putBoolean(DEMORPHING, false);
            DynamicMagic.LOGGER.error("Could not end MorphV2 transformation", exception);
        }
    }

    private static LivingEntity activeForm(Player player) {
        if (!installed()) return null;
        try {
            Object value = bridge().getActiveMorphEntity.invoke(bridge().api, player);
            return value instanceof LivingEntity living ? living : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            DynamicMagic.LOGGER.debug("MorphV2 state was temporarily unavailable", exception);
            return null;
        }
    }

    private static Bridge bridge() throws ReflectiveOperationException {
        Bridge result = bridge;
        if (result != null) return result;
        synchronized (MorphV2Integration.class) {
            if (bridge == null) {
                Class<?> apiClass = Class.forName("me.ichun.mods.morph.api.MorphApi");
                Object api = apiClass.getMethod("getApiImpl").invoke(null);
                if (api == null) throw new IllegalStateException("MorphV2 API is not initialized");
                bridge = new Bridge(api, api.getClass().getMethod("getActiveMorphEntity", Player.class),
                        api.getClass().getMethod("demorph", ServerPlayer.class));
            }
            return bridge;
        }
    }

    private static Object invokeNoArgs(Object target, String name) {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }

    private static boolean isPlayerVariant(Object variant) {
        if (variant == null) return false;
        try {
            Field idField = variant.getClass().getField("id");
            Object id = idField.get(variant);
            ResourceLocation playerId = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PLAYER);
            return playerId.equals(id);
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }

    private static void cancel(Event event) {
        if (event instanceof ICancellableEvent cancellable) cancellable.setCanceled(true);
    }

    private record Bridge(Object api, Method getActiveMorphEntity, Method demorph) {}
}
