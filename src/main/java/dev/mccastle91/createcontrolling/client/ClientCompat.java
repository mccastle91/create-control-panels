package dev.mccastle91.createcontrolling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Client rendering APIs that differ between Minecraft 1.20.1 and 1.21.1.
 * Kept apart from {@code McCompat} because these types don't exist on a
 * dedicated server.
 */
public final class ClientCompat {

    private ClientCompat() {
    }

    /** One position+colour vertex (e.g. for {@code RenderType.debugQuads()}). */
    public static void colorVertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int argb) {
        //? if >=1.21 {
        consumer.addVertex(pose, x, y, z).setColor(argb);
        //?} else {
        /*consumer.vertex(pose, x, y, z)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
        *///?}
    }

    /** One BLOCK-format vertex (e.g. for {@code RenderType.solid()}). */
    public static void blockVertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int argb,
                                   float u, float v, int light, float nx, float ny, float nz) {
        //? if >=1.21 {
        consumer.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setLight(light).setNormal(nx, ny, nz);
        //?} else {
        /*consumer.vertex(pose, x, y, z)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .uv(u, v).uv2(light).normal(nx, ny, nz)
                .endVertex();
        *///?}
    }

    /** One LINES-format vertex (e.g. for {@code RenderType.lines()}); the normal is the line's direction. */
    public static void lineVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int argb,
                                  float nx, float ny, float nz) {
        //? if >=1.21 {
        consumer.addVertex(pose, x, y, z).setColor(argb).setNormal(pose, nx, ny, nz);
        //?} else {
        /*consumer.vertex(pose.pose(), x, y, z)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        *///?}
    }

    /** Atlas u for a position {@code px} (0-16) across the sprite. */
    public static float spriteU(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, float px) {
        //? if >=1.21 {
        return sprite.getU(px / 16f);
        //?} else {
        /*return sprite.getU(px);
        *///?}
    }

    /** Atlas v for a position {@code px} (0-16) down the sprite. */
    public static float spriteV(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, float px) {
        //? if >=1.21 {
        return sprite.getV(px / 16f);
        //?} else {
        /*return sprite.getV(px);
        *///?}
    }

    public static void mulPose(PoseStack poseStack, Matrix4f matrix) {
        //? if >=1.21 {
        poseStack.mulPose(matrix);
        //?} else {
        /*poseStack.mulPoseMatrix(matrix);
        *///?}
    }
}
