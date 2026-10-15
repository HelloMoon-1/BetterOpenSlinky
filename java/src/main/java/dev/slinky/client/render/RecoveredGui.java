package dev.slinky.client.render;

import dev.slinky.client.RecoveredMath;
import dev.slinky.client.numeric.FloatNumeric;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final strictfp class RecoveredGui {
   private static strictfp float c(int at) {
      return RenderConstants.f(at);
   }

   private static strictfp double d(int at) {
      return RenderConstants.d(at);
   }

   public static strictfp RenderMath.Rect rounded(RenderMath.Rect r) {
      return new RenderMath.Rect(FloatNumeric.round(r.x), FloatNumeric.round(r.y), FloatNumeric.round(r.width), FloatNumeric.round(r.height));
   }

   public static strictfp boolean intersects(RenderMath.Rect a, RenderMath.Rect b) {
      return !(a.x > b.x + b.width) && !(b.x > a.x + a.width) && !(a.y > b.y + b.height) && !(b.y > a.y + a.height);
   }

   public static strictfp float[] range(
      RecoveredGui.Runtime runtime,
      Object lowId,
      Object highId,
      RecoveredGui.Slider low,
      RecoveredGui.Slider high,
      RenderMath.Rect r,
      float lower,
      float upper,
      float min,
      float max,
      float step,
      float displayScale,
      double now
   ) {
      float difference = max - min;
      float ratio = (float)((double)((float)(now - low.stamp)) / d(1314080));
      low.animation = limited(low.animation, RenderMath.clamp(0.0F, c(1314076), (lower - min) / difference), ratio);
      high.animation = limited(high.animation, RenderMath.clamp(0.0F, c(1314076), (upper - min) / difference), ratio);
      low.stamp = high.stamp = now;
      float lowX = RenderMath.lerp(r.x, r.x + r.width, low.animation);
      float highX = RenderMath.lerp(r.x, r.x + r.width, high.animation);
      RenderMath.Rect[] hits = GuiWidgets.rangeHits(r, r, lowX, highX);
      float a = low.action(runtime, lowId, r, hits[0], lower, min, max, step, displayScale);
      float b = upper;
      if (low.active) {
         if (a > upper) {
            b = a;
         }
      } else {
         b = high.action(runtime, highId, r, hits[1], upper, min, max, step, displayScale);
         if (a > b) {
            a = b;
         }
      }

      return new float[]{a, b};
   }

   public static strictfp float limited(float a, float b, float t) {
      return RenderMath.lerp(a, b, RenderMath.min(c(1304784), t));
   }

   public static strictfp float parseNumber(String text) {
      byte[] input = bytes(text);
      int i = 0;
      int sign = 1;
      int integer = 0;
      if (input.length > 0 && input[0] == 45) {
         i = 1;
         sign = -1;
      }

      while (i < input.length && (input[i] & 255) - 48 < 10 && (input[i] & 255) >= 48) {
         integer = integer * 10 + (input[i] & 255) - 48;
         i++;
      }

      if (i < input.length && (input[i] & 255 | 2) == 46) {
         float fraction = 0.0F;
         float multiplier = c(1314008);
         i++;

         while (i < input.length && (input[i] & 255) - 48 < 10 && (input[i] & 255) >= 48) {
            fraction += (float)((input[i] & 255) - 48) * multiplier;
            multiplier = (float)((double)multiplier * d(1314016));
            i++;
         }

         return ((float)integer + fraction) * (float)sign;
      } else {
         return (float)(integer * sign);
      }
   }

   public static strictfp void numericFilter(StringBuilder text, int character) {
      int b = character & 0xFF;
      if (text.length() != 100) {
         if (b >= 48 && b <= 57 || b == 45 || b == 46 || b == 44) {
            text.append((char)b);
         }
      }
   }

   public static strictfp void catalogFilter(StringBuilder text, int character) {
      if (text.length() <= 99 && RenderMath.catalogCharacter(character)) {
         text.append((char)(character & 0xFF));
      }
   }

   public static strictfp void profileNameFilter(StringBuilder text, int character) {
      int b = character & 0xFF;
      if (text.length() < 15 && (b - 32 & 0xFF) < 95 && b != 47 && b != 92) {
         text.append((char)b);
      }
   }

   public static strictfp void hexFilter(StringBuilder text, int character) {
      if (text.length() <= 5) {
         int b = character & 0xFF;
         if ((b - 65 & 0xFF) <= 5) {
            b |= 32;
         } else if ((b - 97 & 0xFF) >= 6 && (b - 48 & 0xFF) >= 10) {
            return;
         }

         text.append((char)b);
      }
   }

   private static strictfp byte[] bytes(String s) {
      return s.getBytes(StandardCharsets.UTF_8);
   }

   private static strictfp String clip(String s, int n) {
      byte[] a = bytes(s);
      return new String(a, 0, Math.min(n, a.length), StandardCharsets.UTF_8);
   }

   public static strictfp boolean substring(byte[] candidate, byte[] search) {
      if (search.length == 0) {
         return true;
      } else {
         for (int start = 0; start <= candidate.length - search.length; start++) {
            int matched = 0;

            while (lower(candidate[start + matched] & 255) == lower(search[matched] & 255)) {
               if (++matched == search.length) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static strictfp int lower(int b) {
      return (b - 65 & 0xFF) < 26 ? b | 32 : b;
   }

   public static strictfp float[] rgbToHsl(float[] rgb, boolean history, boolean modal) {
      float r = rgb[0];
      float g = rgb[1];
      float b = rgb[2];
      float max = b;
      float min = b;
      if (!Float.isNaN(g)) {
         max = RenderMath.max(b, g);
         min = RenderMath.min(b, g);
      }

      if (!Float.isNaN(r)) {
         max = RenderMath.max(max, r);
         min = RenderMath.min(min, r);
      }

      float half = c(modal ? 1314524 : 1314400);
      float one = c(modal ? 1314484 : 1314404);
      float six = c(modal ? 1314560 : 1314412);
      float sum = max + min;
      float light = half * sum;
      float hue = 0.0F;
      float saturation = 0.0F;
      if (max != min) {
         float diff = max - min;
         float den = half < light ? one - max - min : sum;
         if (max == r) {
            hue = (g - b) / diff + (g < b ? six : 0.0F);
         } else if (max == g) {
            hue = (b - r) / diff + c(modal ? 1314484 : 1314404);
         } else if (max == b) {
            hue = (r - g) / diff + c(modal ? 1314508 : 1314408);
         } else {
            hue = light;
         }

         saturation = diff / den;
         hue /= six;
      }

      return new float[]{hue, saturation, light};
   }

   private static strictfp float clamp(float v) {
      return v < 0.0F ? 0.0F : RenderMath.min(c(2838936), v);
   }

   private static strictfp float wrap(float v) {
      float one = c(2838936);
      if (v < 0.0F) {
         v += one;
      }

      if (one < v) {
         v += c(2838948);
      }

      return v;
   }

   private static strictfp float channel(float h, float high, float low) {
      if (c(2838952) > h) {
         return (high - low) * h * c(2838960) + low;
      } else if (c(2838940) > h) {
         return high;
      } else {
         return c(2838956) > h ? (c(2838956) - h) * (high - low) * c(2838960) + low : low;
      }
   }

   public static strictfp float[] hsl(float hue, float saturation, float light, int alpha) {
      hue = FloatNumeric.remainder(hue, c(2838936));
      if (hue < 0.0F) {
         hue += c(2838936);
      }

      saturation = clamp(saturation);
      light = clamp(light);
      float high = c(2838940) >= light ? (saturation + c(2838936)) * light : saturation + light - light * saturation;
      float low = light + light - high;
      return new float[]{
         clamp(channel(wrap(c(2838944) + hue), high, low)),
         clamp(channel(wrap(hue), high, low)),
         clamp(channel(wrap(hue + c(2838964)), high, low)),
         (float)(alpha & 0xFF) / c(2838968)
      };
   }

   public static strictfp float floor(float value) {
      int bits = Float.floatToRawIntBits(value);
      int exponent = bits >>> 23 & 0xFF;
      if (exponent >= 150) {
         return value;
      } else if (exponent >= 127) {
         int keep = -1 << (22 - exponent & 31);
         int discard = ~keep;
         return (bits & discard) == 0 ? value : Float.intBitsToFloat(bits + (-(bits >>> 31) & discard) & keep);
      } else if (bits >= 0) {
         return 0.0F;
      } else {
         return value == 0.0F ? value : c(2868916);
      }
   }

   public interface Filter {
      void append(StringBuilder var1, int var2);
   }

   public static final strictfp class Layout {
      public RenderMath.Rect rect;
      public RenderMath.Rect clip;
      public float spacing;
      public float inset;
      public float y;
      public float x;
      public int columns = 1;
      public int count;
      public boolean scissor;
      final RenderMath.Rect[] cache = new RenderMath.Rect[4];

      Layout(RenderMath.Rect r, float spacing, float inset) {
         this.rect = r;
         this.spacing = spacing;
         this.inset = inset;
         this.x = r.x;
         this.y = r.y;
      }

      private strictfp void flush(float initial) {
         if (this.count != 0) {
            int last = this.count - 1;
            RenderMath.Rect p = this.cache[last & 3];
            float bottom = p.y + p.height;
            float result = initial;

            for (int i = last - last % this.columns; i <= last; i++) {
               result = RenderMath.max(bottom, result);
            }

            if (result != initial) {
               this.y = result + this.spacing;
            }
         }
      }

      public strictfp void columns(int requested) {
         this.flush(RecoveredGui.c(1312804));
         this.columns = requested == 0 ? 1 : Math.min(4, requested);
         this.count = 0;
      }

      public strictfp void autoColumns(float width) {
         this.flush(RecoveredGui.c(1312808));
         this.columns = RecoveredMath.truncateInt((this.rect.width + this.spacing) / (width + this.spacing));
         this.columns = this.columns == 0 ? 1 : Math.min(4, this.columns);
         this.count = 0;
      }

      public strictfp RenderMath.Rect next(float height) {
         int current = this.count++;
         RenderMath.Rect previous = current >= this.columns ? this.cache[current - this.columns & 3] : null;
         float nx;
         float ny;
         float w;
         if (current % this.columns != 0) {
            RenderMath.Rect left = this.cache[current - 1 & 3];
            nx = left.x + left.width + this.spacing;
            w = left.width;
            ny = current >= this.columns ? this.spacing + (previous.y + previous.height) : this.y;
         } else {
            nx = this.x;
            if (current < this.columns) {
               ny = this.y;
               w = (this.rect.width - (float)(this.columns - 1) * this.spacing) / (float)this.columns;
            } else {
               ny = previous.y + previous.height + this.spacing;
               w = previous.width;
            }
         }

         RenderMath.Rect raw = new RenderMath.Rect(nx, ny, w, height);
         this.cache[current & 3] = raw;
         return RecoveredGui.rounded(raw);
      }

      public strictfp float end() {
         this.flush(RecoveredGui.c(1312800));
         return this.spacing + this.spacing + (this.y - this.rect.y);
      }
   }

   public static final strictfp class Runtime {
      public int mode;
      public int modal;
      public int selected;
      public boolean down;
      public boolean consumed;
      public boolean modifier;
      public boolean closeRequested;
      public boolean modalDrawing;
      public boolean submitted;
      public float mouseX;
      public float mouseY;
      public float rawX;
      public float rawY;
      public float scroll;
      public float scrollMaximum;
      public float fade;
      public float selectedAnimation;
      public float modalProgress;
      public float modalHeight;
      public float tooltipProgress;
      public double stamp;
      public double modalStamp;
      public double profileStamp;
      public Object identity;
      public RecoveredGui.Filter filter;
      public IntConsumer binding;
      public Consumer<String> textCompletion;
      public Runnable completion;
      public final StringBuilder text = new StringBuilder();
      public String title;
      public String prompt;
      public float[] rgb;
      public float[] hsl;
      public final float[][] history = new float[8][3];
      public int historyIndex;
      public List<String> options = Collections.emptyList();
      public final List<Integer> matches = new ArrayList<>();
      public int overflow;
      public double searchStamp;
      public final Deque<RecoveredGui.Layout> layouts = new ArrayDeque<>();
      public int contentBottom;
      public boolean[] sliderActive = new boolean[3];
      public boolean profileActionsConfirmed;
      private Consumer<Integer> selection;

      public strictfp void start(double now) {
         this.fade = 0.0F;
         this.stamp = now;
      }

      public strictfp RecoveredGui.Layout push(RenderMath.Rect input, float spacing, float inset, boolean scissor) {
         RenderMath.Rect r = input;
         if (!this.layouts.isEmpty()) {
            RenderMath.Rect parent = this.layouts.peek().rect;
            float x = parent.x > input.x ? parent.x : input.x;
            float y = parent.y > input.y ? parent.y : input.y;
            float w = input.width;
            float h = input.height;
            if (w + x > parent.x + parent.width) {
               w = parent.x + parent.width - x;
               if (0.0F > w) {
                  w = 0.0F;
               }
            }

            if (h + y > parent.y + parent.height) {
               h = parent.y + parent.height - y;
               if (0.0F > h) {
                  h = 0.0F;
               }
            }

            r = new RenderMath.Rect(x, y, w, h);
         }

         RecoveredGui.Layout l = new RecoveredGui.Layout(r.inset(inset), spacing, inset);
         l.scissor = scissor;
         if (scissor) {
            l.clip = RecoveredGui.rounded(input).inset(spacing);
         }

         this.layouts.push(l);
         return l;
      }

      public strictfp float pop() {
         RecoveredGui.Layout l = this.layouts.pop();
         float height = l.end();
         if (l.y > (float)this.contentBottom) {
            this.contentBottom = RecoveredMath.truncateInt(l.y);
         }

         return height;
      }

      public strictfp void resetLayout() {
         this.layouts.clear();
         this.contentBottom = 0;
      }

      public strictfp boolean hover(RenderMath.Rect r) {
         return (this.modal == 0 || this.modalDrawing) && r.contains(this.mouseX, this.mouseY) && (this.mode == 0 || this.mode == 3);
      }

      public strictfp boolean click(RenderMath.Rect r) {
         if (this.hover(r) && this.down && !this.consumed) {
            this.consumed = true;
            return true;
         } else {
            return false;
         }
      }

      public strictfp void finishFrame() {
         if (this.down && !this.consumed) {
            this.consumed = true;
         }

         this.consumed = this.down;
      }

      public strictfp void activateBinding(Object id, IntConsumer callback) {
         this.mode = 1;
         this.identity = id;
         this.binding = callback;
      }

      public strictfp void activateText(Object id, RecoveredGui.Filter callback) {
         if ((this.mode & -2) != 2 || this.identity != id) {
            this.mode = 2;
            this.text.setLength(0);
            this.submitted = false;
            this.identity = id;
            this.filter = callback;
         }
      }

      public strictfp boolean textActive(Object id) {
         return (this.mode & -2) == 2 && this.identity == id;
      }

      public strictfp void releaseText() {
         if (this.mode == 2) {
            this.mode = 3;
         }
      }

      public strictfp String takeText() {
         if ((this.mode & -2) == 2 && this.submitted) {
            this.mode = 0;
            this.submitted = false;
            return this.text.toString();
         } else {
            return null;
         }
      }

      public strictfp void clearInteraction() {
         this.mode = 0;
         this.down = false;
         this.consumed = false;
         this.modifier = false;
         this.submitted = false;
         this.identity = null;
         this.filter = null;
         this.binding = null;
      }

      public strictfp boolean input(int kind, int key, int x, int y, float wheel, boolean pressed, byte[] utf8) {
         if (kind == 1) {
            this.rawX = (float)x;
            this.rawY = (float)y;
            return false;
         } else if (kind == 3) {
            if (this.modal == 0) {
               // Forge forwards raw LWJGL deltas (usually 120 per notch),
               // while the recovered scroll speed is measured in wheel steps.
               this.scroll = Math.signum(wheel) * RecoveredGui.c(1312880) + this.scroll;
            }

            return false;
         } else if (kind != 2) {
            return false;
         } else {
            if (pressed) {
               if ((this.mode & -2) == 2) {
                  if (key == 1 && this.mode != 3) {
                     this.mode = 0;
                     return true;
                  }

                  if (key == 83) {
                     if (this.text.length() > 0) {
                        this.text.setLength(this.text.length() - 1);
                     }

                     return true;
                  }

                  if (key == 85) {
                     if (this.text.length() > 0) {
                        this.submitted = true;
                     }

                     return true;
                  }

                  if (key == 87) {
                     this.mode = 0;
                     return true;
                  }

                  if (key != 1) {
                     for (int i = 0; i < Math.min(4, utf8.length) && utf8[i] != 0; i++) {
                        this.filter.append(this.text, utf8[i] & 255);
                     }

                     return true;
                  }
               } else {
                  if (this.mode == 1) {
                     this.binding.accept(key != 1 && key != 87 ? key : 0);
                     this.mode = 0;
                     return true;
                  }

                  if (this.mode == 0 && this.modal != 0 && key == 87) {
                     this.finishModal(true);
                     return true;
                  }
               }
            }

            if (key == 100 || key == 101) {
               this.modifier = pressed;
            } else if (key == 1) {
               this.down = pressed;
            }

            return false;
         }
      }

      public strictfp long inputWord(long incomingSavedRsi, long callerStateWord, int kind, int key, int x, int y, float wheel, boolean pressed, byte[] utf8) {
         long saved = incomingSavedRsi;
         if (kind == 2 && pressed && Integer.compareUnsigned(this.mode, 1) <= 0) {
            saved = callerStateWord;
         }

         return this.input(kind, key, x, y, wheel, pressed, utf8) ? saved & 4294967040L | 1L : 0L;
      }

      public strictfp void beginText(String title, Object user, RecoveredGui.Filter filter, Consumer<String> callback, double now) {
         this.finishModal(true);
         this.modal = 1;
         this.title = title;
         this.modalStamp = now;
         this.modalProgress = this.modalHeight = 0.0F;
         this.textCompletion = callback;
         this.completion = () -> callback.accept(null);
         this.activateText(user, filter);
      }

      public strictfp void beginPrompt(String message, Runnable callback, double now) {
         this.finishModal(true);
         this.modal = 3;
         this.title = RenderConstants.text(1314628);
         this.prompt = RecoveredGui.clip(message, 100);
         this.completion = callback;
         this.modalStamp = now;
         this.modalProgress = this.modalHeight = 0.0F;
      }

      public strictfp void beginColor(float[] color, Consumer<float[]> callback, double now) {
         this.beginColor("Color", color, callback, now);
      }

      public strictfp void beginColor(String label, float[] color, Consumer<float[]> callback, double now) {
         this.finishModal(true);
         this.modal = 2;
         this.title = label;
         this.rgb = (float[])color.clone();
         this.hsl = RecoveredGui.rgbToHsl(this.rgb, false, false);
         this.completion = () -> callback.accept((float[])this.rgb.clone());
         this.modalStamp = now;
         this.modalProgress = this.modalHeight = 0.0F;
         Arrays.fill(this.sliderActive, false);
      }

      public strictfp void beginOptions(String title, List<String> candidates, Consumer<Integer> callback, double now) {
         this.finishModal(true);
         this.modal = 4;
         this.title = title;
         this.options = new ArrayList<>(candidates);
         this.completion = () -> callback.accept(null);
         this.selection = callback;
         this.modalStamp = now;
         this.modalProgress = this.modalHeight = 0.0F;
         this.matches.clear();
         this.overflow = 0;
         this.searchStamp = 0.0;
         this.activateText(this, RecoveredGui::catalogFilter);
         this.releaseText();
      }

      public strictfp void choose(int index) {
         Consumer<Integer> callback = this.selection;
         this.finishModal(false);
         callback.accept(index);
      }

      public strictfp void finishModal(boolean apply) {
         int old = this.modal;
         Runnable callback = this.completion;
         // Detach the old modal before invoking user code. A completion may
         // open another modal or replace the screen and finish this one again.
         this.modal = 0;
         this.mode = 0;
         this.completion = null;
         this.textCompletion = null;
         this.selection = null;
         this.submitted = false;

         if (old == 2 && this.rgb != null) {
            boolean found = false;

            for (int back = 8; back > 0; back--) {
               int index = this.historyIndex >= back ? this.historyIndex - back : this.historyIndex + 8 - back;
               float[] p = this.history[index];
               if (this.rgb[0] == p[0] && this.rgb[1] == p[1] && this.rgb[2] == p[2]) {
                  found = true;
                  break;
               }
            }

            if (!found) {
               this.historyIndex = this.historyIndex >= 1 ? this.historyIndex - 1 : 7;
               this.history[this.historyIndex] = (float[])this.rgb.clone();
            }
         }

         if (old == 4) {
            this.matches.clear();
            this.overflow = 0;
         }

         if ((old == 1 || old == 2 || old == 4) && apply && callback != null) {
            callback.run();
         }
      }

      public strictfp void confirmPrompt() {
         Runnable callback = this.completion;
         this.finishModal(false);
         if (callback != null) {
            callback.run();
         }
      }

      public strictfp void submitModalText() {
         String s = this.takeText();
         if (s != null) {
            Consumer<String> callback = this.textCompletion;
            this.finishModal(false);
            callback.accept(s);
         }
      }

      public strictfp void filterOptions(double now) {
         if (!(RecoveredGui.d(1314512) > now - this.searchStamp)) {
            this.matches.clear();
            this.overflow = 0;
            byte[] search = RecoveredGui.bytes(this.text.toString());

            for (int row = 0; row < this.options.size(); row++) {
               byte[] candidate = RecoveredGui.bytes(this.options.get(row));
               if (candidate.length >= search.length && RecoveredGui.substring(candidate, search)) {
                  if (this.matches.size() >= 30) {
                     this.overflow++;
                  } else {
                     this.matches.add(row);
                  }
               }
            }

            this.searchStamp = now;
         }
      }

      public strictfp boolean colorSlider(RenderMath.Rect r, int component, float inset) {
         if (this.click(r)) {
            this.sliderActive[component] = true;
         } else {
            if (!this.down) {
               this.sliderActive[component] = false;
               return false;
            }

            if (!this.sliderActive[component]) {
               return false;
            }
         }

         this.hsl[component] = RenderMath.clamp(0.0F, RecoveredGui.c(1314624), (this.mouseX - r.x - inset) / (r.width - 2.0F * inset));
         this.rgb = RecoveredGui.hsl(this.hsl[0], this.hsl[1], this.hsl[2], 255);
         return true;
      }

      public strictfp void hex(String hex) {
         if (hex.length() > 5) {
            for (int i = 0; i < 3; i++) {
               int a = hex.charAt(2 * i) & 255;
               int b = hex.charAt(2 * i + 1) & 255;
               int high = a + ((byte)a >= 97 ? 169 : 208) & 0xFF;
               int low = b + ((byte)b >= 97 ? 169 : 208) & 0xFF;
               this.rgb[i] = (float)((double)(high << 4 | low) / RecoveredGui.d(1314552));
            }
         }

         this.hsl = RecoveredGui.rgbToHsl(this.rgb, false, true);
      }

      public strictfp void selectHistory(int index) {
         this.rgb = (float[])this.history[index].clone();
         this.hsl = RecoveredGui.rgbToHsl(this.rgb, true, true);
      }

      public strictfp float elapsed(double now) {
         return (float)(now - this.stamp);
      }

      public strictfp RenderMath.Rect beginFrame(float width, float height, float scale, boolean wide, double now) {
         if (0.0F > this.scroll) {
            this.scroll = 0.0F;
         } else if (this.scroll > this.scrollMaximum) {
            this.scroll = this.scrollMaximum;
         }

         this.mouseX = this.rawX * (RecoveredGui.c(1312976) / scale);
         this.mouseY = this.rawY * (RecoveredGui.c(1312976) / scale) + this.scroll;
         float elapsed = this.elapsed(now);
         this.fade = RenderMath.min(RecoveredGui.c(1312976), (float)((double)this.fade + (double)elapsed / RecoveredGui.d(1313008)));
         float panelWidth = RenderMath.min(width, RecoveredGui.c(wide ? 1312964 : 1312968));
         float left = (width - panelWidth) * RecoveredGui.c(1312972);
         this.resetLayout();
         RenderMath.Rect panel = new RenderMath.Rect(left, 0.0F, panelWidth, height);
         this.push(panel, RecoveredGui.c(1313016), RecoveredGui.c(1313020), false).columns(1);
         return panel;
      }

      public strictfp void endFrame(float height, double now) {
         this.scrollMaximum = RenderMath.max(0.0F, (float)this.contentBottom - height + RecoveredGui.c(1313020));
         this.finishFrame();
         this.stamp = now;
      }
   }

   public static final strictfp class Slider {
      public boolean active;
      public float animation;
      public double stamp;

      public strictfp float update(
         RecoveredGui.Runtime runtime,
         Object id,
         RenderMath.Rect track,
         float value,
         float min,
         float max,
         float step,
         float displayScale,
         boolean decimal,
         double now
      ) {
         return this.update(runtime, id, track, track, value, min, max, step, displayScale, now);
      }

      public strictfp float update(
         RecoveredGui.Runtime runtime,
         Object id,
         RenderMath.Rect track,
         RenderMath.Rect hit,
         float value,
         float min,
         float max,
         float step,
         float displayScale,
         double now
      ) {
         float fraction = RenderMath.clamp(0.0F, RecoveredGui.c(1313988), (value - min) / (max - min));
         this.animation = RecoveredGui.limited(this.animation, fraction, (float)((double)((float)(now - this.stamp)) / RecoveredGui.d(1313992)));
         this.stamp = now;
         return this.action(runtime, id, track, hit, value, min, max, step, displayScale);
      }

      public strictfp float action(
         RecoveredGui.Runtime runtime, Object id, RenderMath.Rect track, RenderMath.Rect hit, float value, float min, float max, float step, float displayScale
      ) {
         if (runtime.click(hit)) {
            this.active = true;
            if (runtime.modifier) {
               runtime.activateText(id, RecoveredGui::numericFilter);
            }
         } else if (!runtime.down && !runtime.textActive(id)) {
            this.active = false;
         }

         if (!this.active) {
            return value;
         } else {
            if (runtime.textActive(id)) {
               String input = runtime.takeText();
               if (input == null) {
                  return value;
               }

               value = RecoveredGui.parseNumber(input);
               if (displayScale != 0.0F) {
                  value /= displayScale;
               }
            } else {
               value = (max - min) * ((runtime.mouseX - track.x) / track.width) + min;
            }

            if (step != 0.0F) {
               value = FloatNumeric.round(value / step) * step;
            }

            return min >= value ? min : (value >= max ? max : value);
         }
      }
   }

   public static final strictfp class Toggle {
      public float progress;
      public double stamp;

      public strictfp boolean animate(boolean value, double now) {
         double elapsed = (double)((float)(now - this.stamp));
         if (elapsed > RecoveredGui.d(1313856)) {
            this.progress = value ? 1.0F : 0.0F;
         } else if (value) {
            if (RecoveredGui.c(1313872) > this.progress) {
               this.progress = RenderMath.min((float)((double)this.progress + elapsed / RecoveredGui.d(1313856)), RecoveredGui.c(1313872));
            }
         } else if (this.progress > 0.0F) {
            this.progress = RenderMath.max((float)((double)this.progress + elapsed / RecoveredGui.d(1313864)), 0.0F);
         }

         this.stamp = now;
         return value;
      }

      public strictfp float smooth() {
         return RenderMath.smooth(this.progress);
      }
   }
}
