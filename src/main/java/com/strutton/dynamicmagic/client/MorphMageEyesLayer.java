package com.strutton.dynamicmagic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strutton.dynamicmagic.mage.MorphMageEvents;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.Optional;

/** Slightly glowing, model-aligned eyes that expose mages hiding in ordinary mob bodies. */
public final class MorphMageEyesLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final int[] COLORS = {
            0xff26ffff, 0xffff1ac7, 0xffa6ff0d,
            0xffff5910, 0xff8c33ff, 0xffffff1a
    };
    private static final ClassValue<Optional<Field>> HEAD_FIELDS = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> type) {
            for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
                try {
                    Field field = cursor.getDeclaredField("head");
                    if (ModelPart.class.isAssignableFrom(field.getType()) && field.trySetAccessible())
                        return Optional.of(field);
                } catch (NoSuchFieldException ignored) {
                    // Continue through the model hierarchy.
                } catch (RuntimeException inaccessible) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }
    };

    public MorphMageEyesLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!MorphMageEvents.isMorphMage(entity) || entity.isInvisible()) return;

        int color = COLORS[Math.floorMod(MorphMageEvents.eyeColor(entity), COLORS.length)];
        ModelPart head = getParentModel().young ? null : findHead(getParentModel());
        if (head != null && renderOnHead(head, poseStack, buffer, color, getParentModel() instanceof HumanoidModel<?>))
            return;
        renderFallback(entity, poseStack, buffer, color, netHeadYaw, headPitch);
    }

    private static boolean renderOnHead(ModelPart head, PoseStack poseStack, MultiBufferSource buffer,
                                        int color, boolean humanoid) {
        HeadCube selected = new HeadCube();
        head.visit(poseStack, (pose, path, index, cube) -> {
            float volume = (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ);
            if (volume > selected.volume) {
                selected.volume = volume;
                selected.pose = new Matrix4f(pose.pose());
                selected.cube = cube;
            }
        });
        if (selected.cube == null) return false;

        ModelPart.Cube cube = selected.cube;
        float width = cube.maxX - cube.minX;
        float height = cube.maxY - cube.minY;
        if (width <= 0 || height <= 0) return false;

        // Humanoid UVs place the eyes exactly halfway down the face. Animal faces tend slightly higher.
        float top = cube.minY + height * (humanoid ? .50f : .38f);
        float bottom = top + Math.max(.55f, height * .125f);
        float eyeWidth = Math.max(.55f, width * .25f);
        float center = (cube.minX + cube.maxX) * .5f;
        float separation = width * .25f;
        float front = cube.minZ - Math.max(.015f, (cube.maxZ - cube.minZ) * .002f);

        VertexConsumer vertices = buffer.getBuffer(RenderType.debugQuads());
        eye(vertices, selected.pose, center - separation - eyeWidth * .5f,
                center - separation + eyeWidth * .5f, top, bottom, front, color);
        eye(vertices, selected.pose, center + separation - eyeWidth * .5f,
                center + separation + eyeWidth * .5f, top, bottom, front, color);
        return true;
    }

    /** Covers unusual and modded models that do not expose a conventional head model part. */
    private static void renderFallback(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                       int color, float netHeadYaw, float headPitch) {
        poseStack.pushPose();
        float eyeHeight = entity.getEyeHeight(entity.getPose());
        poseStack.translate(0, 1.501f - eyeHeight, 0);
        poseStack.mulPose(new org.joml.Quaternionf(new AxisAngle4f((float) Math.toRadians(netHeadYaw), 0, 1, 0)));
        poseStack.mulPose(new org.joml.Quaternionf(new AxisAngle4f((float) Math.toRadians(headPitch), 1, 0, 0)));

        float width = Math.max(.16f, entity.getBbWidth() * .72f);
        float eyeWidth = Math.max(.035f, width * .22f);
        float eyeHeightPixels = Math.max(.035f, eyeWidth * .55f);
        float separation = width * .25f;
        float front = -Math.max(.08f, entity.getBbWidth() * .51f);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer vertices = buffer.getBuffer(RenderType.debugQuads());
        eye(vertices, pose, (-separation - eyeWidth * .5f) * 16f, (-separation + eyeWidth * .5f) * 16f,
                -eyeHeightPixels * 8f, eyeHeightPixels * 8f, front * 16f, color);
        eye(vertices, pose, (separation - eyeWidth * .5f) * 16f, (separation + eyeWidth * .5f) * 16f,
                -eyeHeightPixels * 8f, eyeHeightPixels * 8f, front * 16f, color);
        poseStack.popPose();
    }

    private static ModelPart findHead(EntityModel<?> model) {
        if (model instanceof HeadedModel headed) return headed.getHead();
        if (model instanceof HierarchicalModel<?> hierarchical && hierarchical.root().hasChild("head"))
            return hierarchical.root().getChild("head");
        try {
            Optional<Field> field = HEAD_FIELDS.get(model.getClass());
            return field.isPresent() ? (ModelPart) field.get().get(model) : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static void eye(VertexConsumer vertices, Matrix4f pose, float minX, float maxX,
                            float top, float bottom, float front, int color) {
        vertices.addVertex(pose, minX / 16f, top / 16f, front / 16f).setColor(color);
        vertices.addVertex(pose, minX / 16f, bottom / 16f, front / 16f).setColor(color);
        vertices.addVertex(pose, maxX / 16f, bottom / 16f, front / 16f).setColor(color);
        vertices.addVertex(pose, maxX / 16f, top / 16f, front / 16f).setColor(color);
    }

    private static final class HeadCube {
        private float volume = -1;
        private Matrix4f pose;
        private ModelPart.Cube cube;
    }
}
