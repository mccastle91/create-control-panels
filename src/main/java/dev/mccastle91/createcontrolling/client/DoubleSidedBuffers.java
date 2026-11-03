package dev.mccastle91.createcontrolling.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Makes font quads double-sided. Text render types cull back faces, so a
 * nixie digit standing in its tube would vanish from behind; this passes
 * every quad through and then emits it again with the winding reversed, so
 * from behind the same glyph shows mirrored, like a real tube's filament.
 *
 * Only for font output (position, colour, uv, light): a vertex is complete
 * once its light is set, which the font always writes last.
 */
final class DoubleSidedBuffers implements MultiBufferSource {

    private final MultiBufferSource delegate;

    DoubleSidedBuffers(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new Reversing(delegate.getBuffer(type));
    }

    private static final class Reversing implements VertexConsumer {

        private final VertexConsumer out;
        private final float[][] pos = new float[4][3];
        private final int[][] color = new int[4][4];
        private final float[][] uv = new float[4][2];
        private final int[][] light = new int[4][2];
        /** Index of the vertex being written within the current quad. */
        private int current = -1;

        Reversing(VertexConsumer out) {
            this.out = out;
        }

        private void start(double x, double y, double z) {
            current = (current + 1) % 4;
            pos[current][0] = (float) x;
            pos[current][1] = (float) y;
            pos[current][2] = (float) z;
        }

        private void storeColor(int r, int g, int b, int a) {
            int[] c = color[current];
            c[0] = r;
            c[1] = g;
            c[2] = b;
            c[3] = a;
        }

        private void storeUv(float u, float v) {
            uv[current][0] = u;
            uv[current][1] = v;
        }

        /** Light is the last element of a font vertex: after the quad's fourth, emit it backwards. */
        private void storeLight(int u, int v) {
            light[current][0] = u;
            light[current][1] = v;
            //? if >=1.21 {
            if (current == 3)
                emitReversed();
            //?}
        }

        private void emitReversed() {
            for (int i : new int[] { 0, 3, 2, 1 }) {
                int[] c = color[i];
                //? if >=1.21 {
                out.addVertex(pos[i][0], pos[i][1], pos[i][2]).setColor(c[0], c[1], c[2], c[3])
                        .setUv(uv[i][0], uv[i][1]).setUv2(light[i][0], light[i][1]);
                //?} else {
                /*out.vertex(pos[i][0], pos[i][1], pos[i][2]).color(c[0], c[1], c[2], c[3])
                        .uv(uv[i][0], uv[i][1]).uv2(light[i][0], light[i][1]).endVertex();
                *///?}
            }
        }

        //? if >=1.21 {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            start(x, y, z);
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            storeColor(r, g, b, a);
            out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            storeUv(u, v);
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            storeLight(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(x, y, z);
            return this;
        }
        //?} else {
        /*@Override
        public VertexConsumer vertex(double x, double y, double z) {
            start(x, y, z);
            out.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            storeColor(r, g, b, a);
            out.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            storeUv(u, v);
            out.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            out.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            storeLight(u, v);
            out.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            out.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            out.endVertex();
            if (current == 3) {
                emitReversed();
                current = -1;
            }
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            out.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
        *///?}
    }
}
