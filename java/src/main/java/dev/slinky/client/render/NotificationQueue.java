package dev.slinky.client.render;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final strictfp class NotificationQueue {
   private final NotificationQueue.Message[] records = new NotificationQueue.Message[10];
   private long start;
   private int count;
   private String enabledName;
   private String disabledName;
   private int enabledCount;
   private int disabledCount;
   private boolean dirty;
   private boolean hidden;

   public synchronized strictfp void append(int kind, String text) {
      String limited = text == null ? "" : text;
      byte[] source = limited.getBytes(StandardCharsets.UTF_8);
      if (source.length > 100) {
         int length = 100;
         while (length > 0 && (source[length] & 192) == 128) {
            length--;
         }

         limited = new String(source, 0, length, StandardCharsets.UTF_8);
      }

      this.records[this.index(this.count)] = new NotificationQueue.Message(kind, limited);
      if (this.count == this.records.length) {
         this.start++;
      } else {
         this.count++;
      }

      this.dirty = true;
   }

   public synchronized strictfp void state(String name, boolean enabled) {
      if (enabled) {
         this.enabledName = name;
         this.enabledCount++;
      } else {
         this.disabledName = name;
         this.disabledCount++;
      }

      this.dirty = true;
   }

   public synchronized strictfp int advance(float elapsed, int oldFlags, boolean hidden) {
      boolean visibilityChanged = this.hidden != hidden;
      this.hidden = hidden;
      if (hidden) {
         // Hiding is not dismissal: retain the bounded queue until it can be seen.
         return oldFlags | (visibilityChanged ? 2 : 0);
      }

      boolean changed = this.dirty || visibilityChanged;
      this.dirty = false;
      float delta = !visibilityChanged && Float.isFinite(elapsed) && elapsed > 0.0F ? elapsed : 0.0F;
      for (int i = 0; i < this.count; i++) {
         this.records[this.index(i)].age += delta;
      }

      float expiry = RenderConstants.f(1384008);
      while (this.count != 0 && this.records[this.index(0)].age >= expiry) {
         this.records[this.index(0)] = null;
         this.start++;
         this.count--;
         changed = true;
      }

      // New state summaries start at age zero, not at the previous frame's age.
      if (this.enabledCount != 0) {
         this.append(
            1,
            this.enabledCount == 1
               ? RenderConstants.text(1384300) + " " + this.enabledName
               : RenderConstants.text(1384300) + " " + Integer.toUnsignedString(this.enabledCount) + " mods"
         );
         this.enabledCount = 0;
         this.enabledName = null;
      }

      if (this.disabledCount != 0) {
         this.append(
            1,
            this.disabledCount == 1
               ? RenderConstants.text(1384308) + " " + this.disabledName
               : RenderConstants.text(1384308) + " " + Integer.toUnsignedString(this.disabledCount) + " mods"
         );
         this.disabledCount = 0;
         this.disabledName = null;
      }

      changed |= this.dirty;
      this.dirty = false;
      if (this.count == 0) {
         return oldFlags | (changed ? 2 : 0);
      }

      boolean transitioning = false;
      double late = RenderConstants.d(1384016);
      double early = RenderConstants.d(1384024);
      for (int i = 0; i < this.count; i++) {
         float age = this.records[this.index(i)].age;
         if ((double)age > late || (double)age < early) {
            transitioning = true;
         }
      }

      return oldFlags | 1 | (changed || transitioning ? 2 : 0);
   }

   private strictfp int index(int offset) {
      return (int)Long.remainderUnsigned(this.start + (long)offset, (long)this.records.length);
   }

   public synchronized strictfp List<NotificationQueue.Message> messages() {
      List<NotificationQueue.Message> result = new ArrayList<>(this.count);
      for (int i = 0; i < this.count; i++) {
         result.add(this.records[this.index(i)]);
      }

      return Collections.unmodifiableList(result);
   }

   public synchronized strictfp void clearMessages() {
      Arrays.fill(this.records, null);
      this.start = 0L;
      this.count = 0;
      this.dirty = true;
   }

   public synchronized strictfp void clearAll() {
      this.clearMessages();
      this.enabledCount = this.disabledCount = 0;
      this.enabledName = this.disabledName = null;
   }

   public static strictfp float opacity(float value) {
      double age = (double)value;
      double end = RenderConstants.d(1384064);
      if (!Float.isFinite(value) || age <= 0.0 || age >= end) {
         return 0.0F;
      }

      double duration = RenderConstants.d(1384040);
      float progress = RenderConstants.f(1384048);
      if (age < duration) {
         progress = (float)(age / duration);
      } else if (age > RenderConstants.d(1384056)) {
         progress = (float)((end - age) / duration);
      }

      return RenderMath.smooth(Math.max(0.0F, Math.min(1.0F, progress)));
   }

   public static final strictfp class Message {
      public final int kind;
      public final String text;
      public float age;

      private Message(int kind, String text) {
         this.kind = kind;
         this.text = text;
      }
   }
}
