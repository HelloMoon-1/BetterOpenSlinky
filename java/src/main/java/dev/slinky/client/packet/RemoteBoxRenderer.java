package dev.slinky.client.packet;

import dev.slinky.client.SemanticEvent;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.setting.ColorSetting;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class RemoteBoxRenderer {
   private static final int[] FILLED = new int[]{3, 7, 1, 5, 4, 7, 6, 3, 2, 1, 0, 4, 2, 6};
   private static final int[] WIRE = new int[]{0, 1, 1, 5, 5, 4, 4, 0, 2, 3, 3, 7, 7, 6, 6, 2, 0, 2, 1, 3, 4, 6, 5, 7};

   private RemoteBoxRenderer() {
   }

   public static void draw(
      SemanticEvent e,
      Vec3 previous,
      Vec3 current,
      float previousYaw,
      float currentYaw,
      boolean rotate,
      boolean filled,
      float lineWidth,
      ColorSetting.Value color
   ) {
      Vec3 position = RotationMath.interpolate(previous, current, (double)e.timerRenderPartialTicks);
      GL11.glPushAttrib(1048575);
      GL11.glPushMatrix();

      try {
         GL11.glDisable(3553);
         GL11.glDisable(2896);
         GL11.glEnable(3042);
         GL11.glBlendFunc(770, 771);
         GL11.glLineWidth(lineWidth);
         GL11.glTranslatef((float)(position.xCoord - e.cameraX), (float)(position.yCoord - e.cameraY + 1.0), (float)(position.zCoord - e.cameraZ));
         GL11.glScalef(0.3F, 1.0F, 0.3F);
         if (rotate) {
            GL11.glRotatef(180.0F - ((currentYaw - previousYaw) * e.timerRenderPartialTicks + previousYaw), 0.0F, 1.0F, 0.0F);
         }

         if (filled) {
            GL11.glColor4f(color.red, color.green, color.blue, 0.4F);
            vertices(5, FILLED);
         }

         GL11.glColor3f(color.red, color.green, color.blue);
         vertices(1, WIRE);
      } finally {
         GL11.glPopMatrix();
         GL11.glPopAttrib();
      }
   }

   private static void vertices(int mode, int[] indices) {
      GL11.glBegin(mode);

      for (int i : indices) {
         GL11.glVertex3f((i & 4) == 0 ? -1.0F : 1.0F, (i & 2) == 0 ? -1.0F : 1.0F, (i & 1) == 0 ? -1.0F : 1.0F);
      }

      GL11.glEnd();
   }
}
