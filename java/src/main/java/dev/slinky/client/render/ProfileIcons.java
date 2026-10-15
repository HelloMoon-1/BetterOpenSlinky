package dev.slinky.client.render;

import dev.slinky.inject.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class ProfileIcons {
   private static final byte[] COVERAGE = coverage();
   private static final int TEXTURE = CanvasFonts.allocateTextureIdentity();

   private static byte[] coverage() {
      byte[] result = new byte[6400];

      try (InputStream input = Platform.resource("/assets/slinkysemantic/profile-icon-coverage.bin")) {
         if (input == null) {
            throw new IOException("Profile icon coverage is missing");
         } else {
            int position = 0;

            while (position < result.length) {
               int count = input.read(result, position, result.length - position);
               if (count < 0) {
                  throw new IOException("Incomplete profile icon atlas");
               }

               position += count;
            }

            return result;
         }
      } catch (IOException var15) {
         throw new IllegalStateException("Cannot load profile icons", var15);
      }
   }

   public static void draw(DrawPlan plan, RenderMath.Rect button, int action, boolean enabled, boolean hover) {
      float u0 = (float)action / 4.0F;
      float u1 = (float)(action + 1) / 4.0F;
      float x = button.x;
      float y = button.y;
      float right = x + button.width;
      float bottom = y + button.height;
      int color = action == 3 ? (!enabled ? -8901331 : (hover ? -1089697 : -2275513)) : (!enabled ? -11119018 : (hover ? -2368549 : -6710887));
      plan.alphaSprite(
         TEXTURE,
         160,
         40,
         COVERAGE,
         Arrays.asList(
            new float[]{x, y, u0, 0.0F},
            new float[]{right, bottom, u1, 1.0F},
            new float[]{right, y, u1, 0.0F},
            new float[]{x, y, u0, 0.0F},
            new float[]{x, bottom, u0, 1.0F},
            new float[]{right, bottom, u1, 1.0F}
         ),
         color
      );
   }

   private ProfileIcons() {
   }
}
