package dev.slinky.client.render;

import dev.slinky.client.SemanticEvent;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.World;

public final class RenderEventPayload {
   private static final Map<SemanticEvent, RenderEventPayload> ATTACHED = Collections.synchronizedMap(new WeakHashMap<>());
   public int redrawFlags;
   public int action;
   public int errorCode;
   public int regionKind;
   public int x;
   public int y;
   public int z;
   public int blockId;
   public String profileName;
   public String message;
   public boolean friendAdded;
   public Runnable guiDraw;
   public World world;

   public static RenderEventPayload attach(SemanticEvent event) {
      RenderEventPayload payload = new RenderEventPayload();
      ATTACHED.put(event, payload);
      return payload;
   }

   public static RenderEventPayload get(SemanticEvent event) {
      return ATTACHED.get(event);
   }

   public static void detach(SemanticEvent event) {
      ATTACHED.remove(event);
   }
}
