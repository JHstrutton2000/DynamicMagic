package com.strutton.dynamicmagic.dragon;

import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

/** Optional bridges for vanilla, DMR, Age of Dragons, and Dragon Survival. */
public final class DragonIntegration {
    private static final String DMR_DRAGON = "dmr.DragonMounts.server.entity.dragon.AbstractDragonEntity";
    private static final String DS_PROVIDER = "by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider";
    private DragonIntegration() {}

    public static DragonProfile profile(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String className = entity.getClass().getName();
        String descriptor = (namespace + ':' + path + ' ' + className).toLowerCase(Locale.ROOT);
        boolean dragon = entity instanceof EnderDragon;
        if (!dragon && namespace.equals("dmr")) dragon = isInstance(DMR_DRAGON, entity);
        if (!dragon && namespace.equals("age_of_dragons")) {
            dragon = (className.contains("Dragon") || path.contains("singularity"))
                    && !path.contains("dead") && !path.contains("hit_box") && !path.contains("player");
        }
        if (!dragon && entity instanceof Player) dragon = isTransformedPlayer(entity);
        if (!dragon && !namespace.equals("age_of_dragons") && path.contains("dragon")
                && !path.contains("dragonfly") && !path.contains("dead") && !path.contains("hit_box")) dragon = true;
        if (!dragon) return DragonProfile.notDragon();

        String breed = invokeString(entity, "getBreedId");
        if (!breed.isBlank()) descriptor += ' ' + breed.toLowerCase(Locale.ROOT);
        if (entity instanceof Player) descriptor += ' ' + dragonSurvivalSpecies(entity);
        EnumSet<Element> elements = elementsFor(descriptor);
        EnumSet<Element> weaknesses = EnumSet.noneOf(Element.class);
        for (Element element : elements) weaknesses.addAll(opposing(element));
        Age age = age(entity);
        return new DragonProfile(true, elements, weaknesses, age.scale, age.stage,
                namespace + ':' + path + '/' + breed);
    }

    public static boolean isDragon(LivingEntity entity) { return profile(entity).dragon(); }

    public static boolean isTransformedPlayer(Entity entity) {
        if (!(entity instanceof Player)) return false;
        try {
            Class<?> provider = Class.forName(DS_PROVIDER, false, entity.getClass().getClassLoader());
            return Boolean.TRUE.equals(provider.getMethod("isDragon", Entity.class).invoke(null, entity));
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }

    /** Applies dragon resistance/weakness and inherited Dragonborn affinity to an elemental spell. */
    public static float adjustSpellDamage(ServerPlayer caster, LivingEntity target, Element element, float amount) {
        DragonProfile targetProfile = profile(target);
        if (targetProfile.dragon()) {
            if (targetProfile.weaknesses().contains(element)) amount *= 1.35f;
            else if (targetProfile.elements().contains(element)) amount *= .18f;
            else amount *= .35f;
        }
        int attackAffinity = DragonProgression.affinity(caster, element);
        if (attackAffinity > 0) amount *= 1f + Math.min(.4f, attackAffinity * .08f);
        if (target instanceof ServerPlayer player) {
            int defenseAffinity = DragonProgression.affinity(player, element);
            if (defenseAffinity > 0) amount *= 1f - Math.min(.5f, defenseAffinity * .08f);
        }
        return amount;
    }

    public static float dragonHunterMultiplier(ServerPlayer player) {
        float multiplier = 1;
        if (SkillKnowledge.knows(player, MagicSkill.DRAGON_KILLER)) multiplier *= 1.15f;
        if (SkillKnowledge.knows(player, MagicSkill.DRAGON_SLAYER)) multiplier *= 1.25f;
        return multiplier;
    }

    private static EnumSet<Element> elementsFor(String value) {
        EnumSet<Element> result = EnumSet.noneOf(Element.class);
        addIf(result, value, Element.FIRE, "fire", "nether", "infernal", "blaze", "karatos", "romora", "ignis");
        addIf(result, value, Element.ICE, "ice", "frost", "glacier");
        addIf(result, value, Element.WATER, "water", "sea", "ocean", "aqua", "kaneri");
        addIf(result, value, Element.AIR, "air", "wind", "aether", "cloud", "rejin", "valarian");
        addIf(result, value, Element.EARTH, "earth", "forest", "lush", "rock", "ahorn", "fenrock", "grecko");
        addIf(result, value, Element.LIGHTNING, "lightning", "storm", "electric", "charge");
        addIf(result, value, Element.SPIRIT, "ghost", "spirit", "faerie");
        addIf(result, value, Element.SHADOW, "end", "void", "sculk", "shadow");
        addIf(result, value, Element.ARCANE, "amethyst", "arcane");
        if (result.isEmpty()) result.add(Element.ARCANE);
        return result;
    }

    private static void addIf(EnumSet<Element> result, String value, Element element, String... tokens) {
        for (String token : tokens) if (value.contains(token)) { result.add(element); return; }
    }

    private static EnumSet<Element> opposing(Element element) {
        return switch (element) {
            case FIRE, LAVA, SCORCH -> EnumSet.of(Element.WATER, Element.ICE);
            case WATER -> EnumSet.of(Element.LIGHTNING, Element.ICE);
            case ICE -> EnumSet.of(Element.FIRE, Element.LAVA, Element.SCORCH);
            case LIGHTNING, STORM -> EnumSet.of(Element.EARTH);
            case AIR -> EnumSet.of(Element.EARTH, Element.LIGHTNING);
            case EARTH, SAND, METAL -> EnumSet.of(Element.AIR, Element.WATER);
            case SHADOW, UNDEAD, BLOOD -> EnumSet.of(Element.LIGHT, Element.DIVINE);
            case SPIRIT -> EnumSet.of(Element.DIVINE);
            case ARCANE, SPACE -> EnumSet.of(Element.LIGHT);
            case KI -> EnumSet.noneOf(Element.class);
            default -> EnumSet.noneOf(Element.class);
        };
    }

    private static Age age(LivingEntity entity) {
        if (invokeBoolean(entity, "isHatchling")) return new Age(.2, "hatchling");
        if (invokeBoolean(entity, "isJuvenile") || entity.isBaby()) return new Age(.5, "juvenile");
        if (invokeBoolean(entity, "isAdult")) return new Age(1, "adult");
        if (entity instanceof Player && isTransformedPlayer(entity)) {
            String stage = dragonSurvivalStage(entity);
            if (stage.contains("newborn")) return new Age(.2, stage);
            if (stage.contains("young")) return new Age(.5, stage);
            if (stage.contains("adult") || stage.contains("ancient")) return new Age(1, stage);
            return new Age(.7, stage);
        }
        return new Age(1, "adult");
    }

    private static String dragonSurvivalSpecies(Entity entity) { return dragonStateString(entity, "speciesId"); }
    private static String dragonSurvivalStage(Entity entity) { return dragonStateString(entity, "stageId"); }
    private static String dragonStateString(Entity entity, String methodName) {
        try {
            Class<?> provider = Class.forName(DS_PROVIDER, false, entity.getClass().getClassLoader());
            Object optional = provider.getMethod("getOptional", Entity.class).invoke(null, entity);
            Object state = optional instanceof Optional<?> value ? value.orElse(null) : null;
            return state == null ? "" : String.valueOf(state.getClass().getMethod(methodName).invoke(state)).toLowerCase(Locale.ROOT);
        } catch (ReflectiveOperationException | LinkageError ignored) { return ""; }
    }

    private static boolean isInstance(String className, Object value) {
        try { return Class.forName(className, false, value.getClass().getClassLoader()).isInstance(value); }
        catch (ClassNotFoundException | LinkageError ignored) { return false; }
    }
    private static boolean invokeBoolean(Object value, String name) {
        try { return Boolean.TRUE.equals(value.getClass().getMethod(name).invoke(value)); }
        catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }
    private static String invokeString(Object value, String name) {
        try { return String.valueOf(value.getClass().getMethod(name).invoke(value)); }
        catch (ReflectiveOperationException | LinkageError ignored) { return ""; }
    }
    private record Age(double scale, String stage) {}
}
