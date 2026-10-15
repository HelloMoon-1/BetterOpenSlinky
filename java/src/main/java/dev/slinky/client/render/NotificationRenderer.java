package dev.slinky.client.render;

import java.util.List;

/** Screenshot-style toasts. All coordinates, including font sizes, are framebuffer pixels. */
public final class NotificationRenderer {
   private static final int PURPLE = 0xE332F5;
   private static final int GREEN = 0x19E62B;
   private static final int RED = 0xF16B73;
   private static final int WHITE = 0xEEEEEE;
   private static final float FONT = 13.0F;

   private NotificationRenderer() {
   }

   public static DrawPlan plan(List<NotificationQueue.Message> messages, float width, float height, float requestedScale) {
      DrawPlan plan = new DrawPlan().canvas();
      if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F || messages.isEmpty()) {
         return plan;
      }
      float scale = Float.isFinite(requestedScale) && requestedScale > 0.0F ? requestedScale : 1.0F;
      // Fit even the minimum card on small windows without mixing GUI-scaled and raw coordinates.
      scale = Math.min(scale, Math.min(width / 200.0F, height / 86.0F));
      float margin = 10.0F * scale;
      float bottom = height - margin;
      float maximumWidth = Math.min(320.0F * scale, width - 2.0F * margin);
      try (CanvasFonts.GuiFaceScope fonts = CanvasFonts.guiFace(1.0F)) {
         // Newest at the bottom; older messages that do not fit still expire in the original queue.
         for (int i = messages.size() - 1; i >= 0; i--) {
            NotificationQueue.Message message = messages.get(i);
            float alpha = NotificationQueue.opacity(message.age);
            if (alpha <= 0.0F) {
               continue;
            }
            String title = stateTitle(message, true);
            boolean enabled = title != null;
            if (title == null) {
               title = stateTitle(message, false);
            }
            boolean module = title != null;
            float cardHeight = (module ? 66.0F : 44.0F) * scale;
            if (bottom - cardHeight < margin) {
               break;
            }
            String body = module ? "Module has been " + (enabled ? "enabled" : "disabled") : singleLine(message.text);
            float inset = (module ? 18.0F : 30.0F) * scale;
            float contentWidth = CanvasFonts.width(body, 0, FONT * scale);
            if (module) {
               contentWidth = Math.max(contentWidth, CanvasFonts.width(title, 0, FONT * scale));
            }
            float cardWidth = Math.min(maximumWidth, Math.max((module ? 180.0F : 170.0F) * scale,
               contentWidth + inset + 10.0F * scale));
            // Entrance/exit shifts stay inside the framebuffer, including at very small resolutions.
            float x = width - margin - cardWidth + (1.0F - alpha) * margin * 0.5F;
            float y = bottom - cardHeight;
            background(plan, x, y, cardWidth, cardHeight, scale, alpha);
            int accent;
            if (module) {
               accent = PURPLE;
               text(plan, fit(title, cardWidth - 20.0F * scale, scale), x + 10.0F * scale, y + 18.0F * scale, PURPLE, scale, alpha);
               text(plan, fit(body, cardWidth - 20.0F * scale, scale), x + 10.0F * scale, y + 38.0F * scale, WHITE, scale, alpha);
            } else {
               // Only genuine completed configuration/friend events receive a success check.
               boolean success = message.kind == 0 || message.kind == 1
                  && ("Friend added".equals(message.text) || "Friend removed".equals(message.text));
               accent = message.kind == 2 ? RED : GREEN;
               icon(plan, x + 16.0F * scale, y + 18.0F * scale, scale, alpha, accent, success, message.kind == 2);
               text(plan, fit(body, cardWidth - 40.0F * scale, scale), x + 30.0F * scale, y + 18.0F * scale, WHITE, scale, alpha);
            }
            float lifetime = RenderConstants.f(1384008);
            float remaining = Float.isFinite(lifetime) && lifetime > 0.0F
               ? Math.max(0.0F, Math.min(1.0F, 1.0F - message.age / lifetime)) : 0.0F;
            float barWidth = Math.min(80.0F * scale, cardWidth - 20.0F * scale);
            float right = x + cardWidth - 10.0F * scale;
            color(plan, accent, alpha * 0.12F).rect(right - barWidth, y + cardHeight - 9.0F * scale, barWidth, 4.0F * scale, 2.0F * scale);
            if (remaining > 0.0F) {
               float filled = barWidth * remaining;
               color(plan, accent, alpha).rect(right - filled, y + cardHeight - 9.0F * scale, filled, 4.0F * scale,
                  Math.min(2.0F * scale, filled * 0.5F));
            }
            bottom -= (cardHeight + 8.0F * scale) * alpha;
         }
      }
      return plan;
   }

   private static String stateTitle(NotificationQueue.Message message, boolean enabled) {
      if (message.kind != 1) {
         return null;
      }
      // Decode the exact prefixes used by NotificationQueue.state summaries; do not guess from names.
      String prefix = RenderConstants.text(enabled ? 1384300 : 1384308) + " ";
      return message.text.startsWith(prefix) && message.text.length() > prefix.length()
         ? singleLine(message.text.substring(prefix.length())) : null;
   }

   private static String singleLine(String text) {
      return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
   }

   private static String fit(String text, float width, float scale) {
      if (CanvasFonts.width(text, 0, FONT * scale) <= width) {
         return text;
      }
      String suffix = "...";
      if (CanvasFonts.width(suffix, 0, FONT * scale) > width) {
         return "";
      }
      int end = text.length();
      while (end > 0) {
         end = text.offsetByCodePoints(end, -1);
         String candidate = text.substring(0, end) + suffix;
         if (CanvasFonts.width(candidate, 0, FONT * scale) <= width) {
            return candidate;
         }
      }
      return suffix;
   }

   private static void background(DrawPlan plan, float x, float y, float width, float height, float scale, float alpha) {
      // Soft layered shadow, not a fabricated blur/backdrop operation.
      plan.color(0.0F, 0.0F, 0.0F, 0.07F * alpha).rect(x - 3.0F * scale, y - scale, width + 6.0F * scale, height + 5.0F * scale, 11.0F * scale);
      plan.color(0.0F, 0.0F, 0.0F, 0.12F * alpha).rect(x - scale, y, width + 2.0F * scale, height + 2.0F * scale, 9.0F * scale);
      plan.color(0.07F, 0.08F, 0.08F, 0.88F * alpha).rect(x, y, width, height, 8.0F * scale);
   }

   private static void icon(DrawPlan plan, float x, float y, float scale, float alpha, int accent, boolean success, boolean error) {
      color(plan, accent, alpha).op("circle", x, y, 6.0F * scale);
      plan.color(0.035F, 0.10F, 0.04F, alpha).lineWidth(1.4F * scale);
      if (success) {
         plan.line(x - 2.8F * scale, y, x - 0.7F * scale, y + 2.0F * scale);
         plan.line(x - 0.7F * scale, y + 2.0F * scale, x + 3.0F * scale, y - 2.3F * scale);
      } else {
         plan.rect(x - 0.65F * scale, y + (error ? -3.0F : -1.0F) * scale, 1.3F * scale, 4.0F * scale, 0.4F * scale);
         plan.op("circle", x, y + (error ? 3.0F : -3.0F) * scale, 0.8F * scale);
      }
   }

   private static void text(DrawPlan plan, String text, float x, float y, int rgb, float scale, float alpha) {
      plan.canvasText(text, x, y, ((int)(alpha * 255.0F) << 24) | rgb, FONT * scale, 17);
   }

   private static DrawPlan color(DrawPlan plan, int rgb, float alpha) {
      return plan.color((rgb >>> 16 & 255) / 255.0F, (rgb >>> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, alpha);
   }
}
