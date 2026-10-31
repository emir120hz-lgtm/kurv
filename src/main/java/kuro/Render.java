package kuro;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;

public class Render {
   static final MinecraftClient mc = MinecraftClient.getInstance();
   static final double MAX_DIST = 256.0;

   public static void render(WorldRenderContext ctx) {
      if (!(Kuro.on[Kuro.STORAGE] || Kuro.on[Kuro.BLOCK] || Kuro.on[Kuro.PLAYER])) {
         return;
      }

      if (mc.world == null || mc.player == null) {
         return;
      }

      MatrixStack ms = ctx.matrixStack();
      if (ms == null) {
         return;
      }

      Vec3d cam = ctx.camera().getPos();
      float td = ctx.tickCounter().getTickDelta(false);
      ms.push();
      ms.translate(-cam.x, -cam.y, -cam.z);
      Matrix4f m = ms.peek().getPositionMatrix();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      boxCount = 0;
      Vec3d camLook = Vec3d.fromPolar(ctx.camera().getPitch(), ctx.camera().getYaw());

      if (Kuro.on[Kuro.STORAGE]) {
         RenderSystem.lineWidth(2.0F);
         BufferBuilder b = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
         drawHits(b, m, Mods.storageHits.values(), cam, camLook, 65536.0, 1.0F);
         flush(b);
      }

      if (Kuro.on[Kuro.BLOCK]) {
         RenderSystem.lineWidth(Math.max(1.0F, Config.beThick));
         BufferBuilder b = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
         double r = Config.beRange;
         drawHits(b, m, Mods.blockHits.values(), cam, camLook, r * r, Config.beAlpha / 100.0F);
         flush(b);
      }

      if (Kuro.on[Kuro.PLAYER]) {
         RenderSystem.lineWidth(Math.max(1.0F, Config.peThick));
         BufferBuilder b = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
         playerEsp(b, m, cam, camLook, td);
         flush(b);
      }

      RenderSystem.lineWidth(1.0F);
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
      ms.pop();
   }

   private static void flush(BufferBuilder b) {
      BuiltBuffer built = b.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }
   }

   private static void playerEsp(BufferBuilder b, Matrix4f m, Vec3d cam, Vec3d camLook, float td) {
      boolean box = Config.peBox >= 0.5F;
      boolean tracer = Config.peTracer >= 0.5F;
      if (!box && !tracer) {
         return;
      }

      int base = Config.hsb(Config.peHue, Config.peSat / 100.0F, 1.0F);
      float alpha = Config.peAlpha / 100.0F;
      float tAlpha = Config.peTracerAlpha / 100.0F;
      boolean health = Config.peHealth >= 0.5F;
      double range = Config.peRange;
      double range2 = range * range;
      Vec3d start = cam.add(camLook);

      for (AbstractClientPlayerEntity e : mc.world.getPlayers()) {
         if (e == mc.player) {
            continue;
         }

         Vec3d off = e.getLerpedPos(td).subtract(e.getPos());
         Box bb = e.getBoundingBox().offset(off);
         Vec3d c = bb.getCenter();
         if (c.squaredDistanceTo(cam) > range2) {
            continue;
         }

         int col = base;
         if (health) {
            float f = Math.max(0.0F, Math.min(1.0F, (e.getHealth() + e.getAbsorptionAmount()) / Math.max(1.0F, e.getMaxHealth())));
            col = Config.hsb(120.0F * f, 1.0F, 1.0F);
         }

         float r = (float)(col >> 16 & 0xFF) / 255.0F;
         float g = (float)(col >> 8 & 0xFF) / 255.0F;
         float bl = (float)(col & 0xFF) / 255.0F;
         if (box) {
            box(b, m, bb, r, g, bl, alpha);
         }

         if (tracer) {
            line(b, m, start.x, start.y, start.z, c.x, c.y, c.z, r, g, bl, tAlpha);
         }
      }
   }

   // Kare basina cizilecek en fazla kutu sayisi (aksi halde binlerce kutu FPS'i dusurur)
   static final int MAX_BOXES = 2500;
   static int boxCount;

   static void drawHits(BufferBuilder b, Matrix4f m, Iterable<List<Mods.Hit>> all, Vec3d cam, Vec3d look, double maxDistSq, float alpha) {
      for (List<Mods.Hit> l : all) {
         for (int i = 0, n = l.size(); i < n; i++) {
            if (boxCount >= MAX_BOXES) {
               return;
            }

            Mods.Hit h = l.get(i);
            BlockPos p = h.pos();
            double dx = (double)p.getX() + 0.5 - cam.x;
            double dy = (double)p.getY() + 0.5 - cam.y;
            double dz = (double)p.getZ() + 0.5 - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > maxDistSq) {
               continue;
            }

            // kameranin arkasinda kalanlari cizme (yaklasik gorus konisi testi)
            if (d2 > 9.0 && dx * look.x + dy * look.y + dz * look.z < 0.0) {
               continue;
            }

            int c = h.color();
            box(
               b, m,
               (double)p.getX(), (double)p.getY(), (double)p.getZ(),
               (double)(p.getX() + 1), (double)(p.getY() + 1), (double)(p.getZ() + 1),
               (float)(c >> 16 & 0xFF) / 255.0F, (float)(c >> 8 & 0xFF) / 255.0F, (float)(c & 0xFF) / 255.0F, alpha
            );
            boxCount++;
         }
      }
   }

   static void box(BufferBuilder b, Matrix4f m, Box bx, float r, float g, float bl, float a) {
      box(b, m, bx.minX, bx.minY, bx.minZ, bx.maxX, bx.maxY, bx.maxZ, r, g, bl, a);
   }

   static void box(BufferBuilder b, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float bl, float a) {
      line(b, m, x1, y1, z1, x2, y1, z1, r, g, bl, a);
      line(b, m, x2, y1, z1, x2, y1, z2, r, g, bl, a);
      line(b, m, x2, y1, z2, x1, y1, z2, r, g, bl, a);
      line(b, m, x1, y1, z2, x1, y1, z1, r, g, bl, a);
      line(b, m, x1, y2, z1, x2, y2, z1, r, g, bl, a);
      line(b, m, x2, y2, z1, x2, y2, z2, r, g, bl, a);
      line(b, m, x2, y2, z2, x1, y2, z2, r, g, bl, a);
      line(b, m, x1, y2, z2, x1, y2, z1, r, g, bl, a);
      line(b, m, x1, y1, z1, x1, y2, z1, r, g, bl, a);
      line(b, m, x2, y1, z1, x2, y2, z1, r, g, bl, a);
      line(b, m, x2, y1, z2, x2, y2, z2, r, g, bl, a);
      line(b, m, x1, y1, z2, x1, y2, z2, r, g, bl, a);
   }

   static void line(BufferBuilder b, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float bl, float a) {
      b.vertex(m, (float)x1, (float)y1, (float)z1).color(r, g, bl, a);
      b.vertex(m, (float)x2, (float)y2, (float)z2).color(r, g, bl, a);
   }
}
