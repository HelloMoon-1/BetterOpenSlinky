package dev.slinky.client.packet;

import dev.slinky.client.ClientContext;
import dev.slinky.client.rotation.RotationMath;
import dev.slinky.inject.ReflectionHelper;
import java.lang.reflect.Field;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

public final strictfp class TimingAccess {
   private static final Field X = ReflectionHelper.findField(EntityOtherPlayerMP.class, "otherPlayerMPX", "field_71185_c");
   private static final Field Y = ReflectionHelper.findField(EntityOtherPlayerMP.class, "otherPlayerMPY", "field_71182_d");
   private static final Field Z = ReflectionHelper.findField(EntityOtherPlayerMP.class, "otherPlayerMPZ", "field_71183_e");
   private static final Field TICKS = ReflectionHelper.findField(EntityOtherPlayerMP.class, "otherPlayerMPPosRotationIncrements", "field_71184_b");
   private static final Field YAW = ReflectionHelper.findField(EntityOtherPlayerMP.class, "otherPlayerMPYaw", "field_71180_f");

   private TimingAccess() {
   }

   public static strictfp Vec3 position(Entity p) {
      return new Vec3(p.posX, p.posY, p.posZ);
   }

   public static strictfp Vec3 server(Entity p) {
      if (!(p instanceof EntityOtherPlayerMP)) {
         return position(p);
      } else {
         try {
            return new Vec3(X.getDouble(p), Y.getDouble(p), Z.getDouble(p));
         } catch (IllegalAccessException var2) {
            throw new IllegalStateException(var2);
         }
      }
   }

   public static strictfp int interpolationTicks(Entity p) {
      if (!(p instanceof EntityOtherPlayerMP)) {
         return 0;
      } else {
         try {
            return TICKS.getInt(p);
         } catch (IllegalAccessException var2) {
            throw new IllegalStateException(var2);
         }
      }
   }

   public static strictfp double serverYaw(Entity p) {
      if (!(p instanceof EntityOtherPlayerMP)) {
         return (double)p.rotationYaw;
      } else {
         try {
            return YAW.getDouble(p);
         } catch (IllegalAccessException var2) {
            throw new IllegalStateException(var2);
         }
      }
   }

   public static strictfp boolean facingPlayer(ClientContext c) {
      Vec3 self = position(c.player());

      for (EntityPlayer p : c.world().playerEntities) {
         if (p != c.player() && !(squared(self, position(p)) > 36.0)) {
            float angle = RotationMath.wrap(RotationMath.angles(self, position(p))[0] - c.player().rotationYaw);
            if (!(RotationMath.abs(angle) > 90.0F)) {
               return true;
            }
         }
      }

      return false;
   }

   public static strictfp double squared(Vec3 a, Vec3 b) {
      double x = a.xCoord - b.xCoord;
      double y = a.yCoord - b.yCoord;
      double z = a.zCoord - b.zCoord;
      return z * z + (x * x + y * y);
   }
}
