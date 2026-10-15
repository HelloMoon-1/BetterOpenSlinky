package dev.slinky.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** Donor ClickGui title and Watermark algorithms. No account UID is invented. */
final class OpenB4GuiBranding {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final FontRenderer fontRendererObj = mc.fontRendererObj;
    static long openedAt;

    void opened() {
        openedAt = System.currentTimeMillis();
    }

    public void drawTitle(float cx, float cy) {
        long now = System.currentTimeMillis();
        float since = now - openedAt;
        String title = "Slinky";
        String tag = "b4";
        float intro = this.clamp(since / 950.0f);
        float base = 2.5f;
        float pulse = 1.0f + (float) Math.sin((double) now * 0.0017) * 0.012f;
        float scale = base * pulse;
        float spacing = 0.8f;
        float width = 0.0f;
        for (int i = 0; i < title.length(); ++i) {
            width += ((float) this.fontRendererObj.getCharWidth(title.charAt(i)) + spacing) * scale;
        }
        float startX = cx - (width -= spacing * scale) / 2.0f;
        float bob = (float) Math.sin((double) now * 0.0021) * 1.2f;
        GL11.glEnable(3042);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        this.drawSparkles(cx, cy + 3.0f, width * 0.58f, 20.0f, intro, now);
        float px = startX;
        for (int i = 0; i < title.length(); ++i) {
            char c = title.charAt(i);
            float cw = this.fontRendererObj.getCharWidth(c);
            float t = this.clamp((since - (float) i * 70.0f) / 760.0f);
            if (t <= 0.0f) {
                px += (cw + spacing) * scale;
                continue;
            }
            float e = this.easeOutBack(t);
            float ang = -1.3f + (float) i * 0.48f;
            float dist = (1.0f - e) * (36.0f + (float) i * 3.5f);
            float bx = px + cw * scale / 2.0f;
            float by = cy + bob;
            float lx = bx + (float) Math.cos(ang) * dist;
            float ly = by + (float) Math.sin(ang) * dist * 0.55f + (1.0f - e) * 14.0f;
            float rot = (1.0f - e) * ((i & 1) == 0 ? -86.0f : 86.0f);
            float s = scale * (0.72f + 0.28f * e);
            int color = OpenB4Theme.rainbow(2L, (long) (-i) * 320L);
            this.drawGlowText(String.valueOf(c), lx, ly, s, rot, color, (int) (255.0f * t));
            px += (cw + spacing) * scale;
        }
        float tagT = this.clamp((since - 420.0f) / 850.0f);
        if (tagT > 0.0f) {
            float e = this.easeOutCubic(tagT);
            float s = 1.05f * (0.82f + 0.18f * e);
            float tw = (float) this.fontRendererObj.getStringWidth(tag) * s;
            float sway = (float) Math.sin((double) now * 0.00115) * 2.2f;
            float tbob = (float) Math.sin((double) now * 0.0021) * 0.9f;
            float phase = (float) (now % 4200L) / 4200.0f;
            float rock = (float) Math.sin((double) phase * Math.PI * 2.0) * 7.0f;
            float tx = cx + width * 0.28f + sway + 6.0f;
            float ty = cy + 17.0f + (1.0f - e) * 10.0f + tbob;
            float rot = (1.0f - e) * 8.0f + rock;
            this.drawGlowText(tag, tx + tw / 2.0f, ty, s, rot, OpenB4Theme.rainbow(2L, -1700L), (int) (235.0f * tagT));
        }
        OpenB4GuiDrawing.resetColor();
        GL11.glDisable(3042);
    }

    public void drawSparkles(float cx, float cy, float rx, float ry, float intro, long now) {
        char[] chars = new char[]{'.', '+', 'x', '*', '.', '+'};
        for (int i = 0; i < chars.length; ++i) {
            float scale;
            boolean even = (i & 1) == 0;
            double ang = (double) i * (Math.PI * 2 / (double) chars.length) + (even ? 0.0 : Math.PI / (double) chars.length);
            double speed = even ? 4.2E-4 : -3.1E-4;
            double a = ang + (double) now * speed;
            float ex = rx * (even ? 1.02f : 0.78f);
            float ey = ry * (even ? 1.0f : 0.72f);
            float sx = cx + (float) Math.cos(a) * ex;
            float sy = cy + (float) Math.sin(a) * ey;
            float twinkle = 0.65f + 0.35f * (float) Math.sin((double) now * 0.0042 + (double) i * 1.37);
            int alpha = (int) (120.0f * intro * twinkle);
            if (alpha <= 3) continue;
            alpha = OpenB4GuiDrawing.clampAlpha(alpha);
            int color = OpenB4GuiDrawing.withAlpha(OpenB4Theme.rainbow(2L, (long) i * 430L), alpha);
            char c = chars[i];
            float rot = 0.0f;
            switch (c) {
                case '.':
                    scale = 0.85f;
                    break;
                case '+':
                    scale = 0.72f;
                    rot = (float) (((double) now * 0.045 + (double) (i * 37)) % 360.0);
                    break;
                case 'x':
                    scale = 0.72f;
                    rot = (float) (((double) now * 0.04 + (double) (i * 53)) % 360.0);
                    break;
                default:
                    scale = 0.78f;
                    rot = (float) (((double) now * 0.03 + (double) (i * 29)) % 360.0);
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(sx, sy, 0.0f);
            if (rot != 0.0f) {
                GlStateManager.rotate(rot, 0.0f, 0.0f, 1.0f);
            }
            GlStateManager.scale(scale, scale, 1.0f);
            String s = String.valueOf(c);
            this.fontRendererObj.drawString(s, (float) (-this.fontRendererObj.getStringWidth(s)) / 2.0f, (float) (-this.fontRendererObj.FONT_HEIGHT) / 2.0f, color, true);
            GlStateManager.popMatrix();
        }
    }

    public void drawGlowText(String s, float x, float y, float scale, float rot, int color, int alpha) {
        if (alpha <= 3) {
            return;
        }
        alpha = OpenB4GuiDrawing.clampAlpha(alpha);
        int w = this.fontRendererObj.getStringWidth(s);
        int h = this.fontRendererObj.FONT_HEIGHT;
        float ox = (float) (-w) / 2.0f;
        float oy = (float) (-h) / 2.0f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0f);
        GlStateManager.rotate(rot, 0.0f, 0.0f, 1.0f);
        GlStateManager.scale(scale, scale, 1.0f);
        for (int i = 2; i >= 1; --i) {
            float gs = 1.0f + (float) i * 0.055f;
            int ga = (int) ((float) alpha * (0.1f + (float) i * 0.035f));
            if (ga < 4) continue;
            ga = OpenB4GuiDrawing.clampAlpha(ga);
            GlStateManager.pushMatrix();
            GlStateManager.scale(gs, gs, 1.0f);
            this.fontRendererObj.drawString(s, ox, oy, OpenB4GuiDrawing.withAlpha(color, ga), false);
            GlStateManager.popMatrix();
        }
        this.fontRendererObj.drawString(s, ox, oy, OpenB4GuiDrawing.withAlpha(color, alpha), true);
        GlStateManager.popMatrix();
    }

    public float easeOutCubic(float f) {
        float t = 1.0f - f;
        return 1.0f - t * t * t;
    }

    public float clamp(float f) {
        return f < 0.0f ? 0.0f : (f > 1.0f ? 1.0f : f);
    }

    public float easeOutBack(float f) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float t = f - 1.0f;
        return 1.0f + c3 * t * t * t + c1 * t * t;
    }

    static void watermark(int x, int y, boolean animate) {
        FontRenderer fr = mc.fontRendererObj;
        int cx = x;
        int color = 0xFF3992E5;
        String user = mc.getSession().getUsername();
        fr.drawStringWithShadow("welcome, ", (float) cx, (float) (y - 20), color);
        int end = rainbowText(user, cx += 45, y - 20, fr);
        fr.drawStringWithShadow("Slinky b4", (float) x, (float) (y - 10), color);
        scramble("dev. ", "Liuyuzhe", x, y, animate, fr);
    }

    private static int rainbowText(String s, int x, int y, FontRenderer fr) {
        if (s.isEmpty()) {
            return x;
        }
        long offset = 0L;
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            fr.drawString(String.valueOf(c), (float) x, (float) y, OpenB4Theme.rainbow(3L, offset), true);
            x += fr.getCharWidth(c);
            if (c == ' ') continue;
            offset -= 90L;
        }
        OpenB4GuiDrawing.resetColor();
        return x;
    }

    private static void scramble(String prefix, String name, float x, float y, boolean animate, FontRenderer fr) {
        long now = System.currentTimeMillis();
        int base = 0xFF3992E5;
        fr.drawStringWithShadow(prefix, x, y, base);
        float cx = x + (float) fr.getStringWidth(prefix);
        long since = now - openedAt;
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            float reveal = animate ? MathHelper.clamp_float(((float) since - (float) i * 110.0f) / 400.0f, 0.0f, 1.0f) : 1.0f;
            if (reveal <= 0.0f) continue;
            char shown = c;
            if (reveal < 0.75f && (now / 45L + (long) i) % 3L != 0L) {
                shown = (char) (97 + Math.abs((int) (now / 35L + (long) (i * 13))) % 26);
            }
            float bob = (float) Math.sin((double) now / 220.0 + (double) i * 0.7) * 0.6f * reveal;
            double wave = (double) (now % 1800L) / 1800.0 * (double) (name.length() + 4) - 2.0;
            double dist = Math.abs((double) i - wave);
            float glow = (float) Math.max(0.0, 1.0 - dist / 1.5);
            int r = base >> 16 & 0xFF, g = base >> 8 & 0xFF, b = base & 0xFF;
            r = Math.min(255, (int) ((float) r + (float) (255 - r) * glow * 0.8f));
            g = Math.min(255, (int) ((float) g + (float) (255 - g) * glow * 0.8f));
            b = Math.min(255, (int) ((float) b + (float) (255 - b) * glow * 0.8f));
            int a = (int) (255.0f * reveal);
            fr.drawStringWithShadow(String.valueOf(shown), cx, y + bob, a << 24 | r << 16 | g << 8 | b);
            cx += (float) fr.getCharWidth(c);
        }
        OpenB4GuiDrawing.resetColor();
    }
}
