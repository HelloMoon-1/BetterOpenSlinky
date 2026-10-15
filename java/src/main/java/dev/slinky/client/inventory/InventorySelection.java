package dev.slinky.client.inventory;

import dev.slinky.client.RecoveredMath;
import java.util.Arrays;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

public final strictfp class InventorySelection {
   private InventorySelection() {
   }

   public static strictfp long material(int type) {
      int category = 0;
      int tier = 0;
      switch (type) {
         case 256:
            category = 16;
            tier = 3;
            break;
         case 257:
            category = 14;
            tier = 3;
            break;
         case 258:
            category = 1;
            tier = 3;
            break;
         case 267:
            category = 22;
            tier = 3;
            break;
         case 268:
            category = 22;
            tier = 1;
            break;
         case 269:
            category = 16;
            tier = 1;
            break;
         case 270:
            category = 14;
            tier = 1;
            break;
         case 271:
            category = 1;
            tier = 1;
            break;
         case 272:
            category = 22;
            tier = 2;
            break;
         case 273:
            category = 16;
            tier = 2;
            break;
         case 274:
            category = 14;
            tier = 2;
            break;
         case 275:
            category = 1;
            tier = 2;
            break;
         case 276:
            category = 22;
            tier = 5;
            break;
         case 277:
            category = 16;
            tier = 5;
            break;
         case 278:
            category = 14;
            tier = 5;
            break;
         case 279:
            category = 1;
            tier = 5;
            break;
         case 283:
            category = 22;
            tier = 4;
            break;
         case 284:
            category = 16;
            tier = 4;
            break;
         case 285:
            category = 14;
            tier = 4;
            break;
         case 286:
            category = 1;
            tier = 4;
            break;
         case 359:
            category = 15;
            tier = 1;
      }

      return (long)tier << 32 | (long)category;
   }

   public static strictfp int weighted(int[][] tags, int table) {
      int total = 0;

      for (int[] tag : tags) {
         int id = tag[0] & 65535;
         int level = tag[1];
         int coefficient = 0;
         if (table == 0) {
            coefficient = id == 16 ? 50 : (id != 19 && id != 20 ? 0 : 1);
         } else if (table == 1) {
            coefficient = id == 48 ? 100 : (id == 49 ? 10 : (id != 50 && id != 51 ? 0 : 1));
         } else {
            coefficient = id == 16 ? 1 : (id == 32 ? 10 : (id == 62 ? 83 : 0));
         }

         if ((short)level >= 0) {
            total += coefficient * level;
         }
      }

      return total;
   }

   public static strictfp int category(int category, InventorySelection.Stack s) {
      if (s != null && category >= 1 && category <= 26) {
         int item = s.type;
         int damage = s.damage;
         switch (category) {
            case 2:
               switch (item) {
                  case 1:
                  case 2:
                  case 3:
                  case 4:
                  case 5:
                  case 17:
                  case 18:
                  case 20:
                  case 24:
                  case 35:
                  case 45:
                  case 49:
                  case 78:
                  case 87:
                  case 95:
                  case 98:
                  case 121:
                  case 125:
                  case 133:
                  case 155:
                  case 159:
                  case 162:
                     return 1;
                  default:
                     return 0;
               }
            case 3:
               return item == 261 ? weighted(s.tags, 1) + 1 & 65535 : 0;
            case 4:
               return item == 54 ? 1 : 0;
            case 5:
               return item == 368 ? (s.flag ? 1 : 2) : 0;
            case 6:
               return item == 385 ? 1 : 0;
            case 7:
               return item == 346 ? 1 : 0;
            case 8:
               return item == 259 ? 1 : 0;
            case 9:
               return item == 322 ? (damage == 1 ? 2 : 1) : 0;
            case 10:
            case 11:
            case 19:
            case 21:
               if (item != 373) {
                  return 0;
               } else {
                  int effect = damage & 15;
                  int score;
                  if (effect == 1 && category == 10) {
                     score = 1;
                  } else if (effect == 5 && category == 10) {
                     score = 2;
                  } else {
                     if ((effect != 2 || category != 19) && (effect != 9 || category != 21) && (effect != 11 || category != 11)) {
                        return 0;
                     }

                     score = 1;
                  }

                  return score + (damage >>> 6 & 1) + (damage >>> 5 & 1);
               }
            case 12:
               return item == 65 ? 1 : 0;
            case 13:
               return item == 327 ? 1 : 0;
            case 14:
            case 15:
            case 16:
            case 22:
            case 25:
            default:
               long classified = material(item);
               int kind = (int)classified;
               int tier = (int)(classified >>> 32);
               if (category != 22 && category != 25) {
                  return category == kind ? weighted(s.tags, 2) + tier * 100 & 65535 : 0;
               } else {
                  if (category == 22 && kind != 22) {
                     return 0;
                  }

                  int preference = category == 22 ? 4 : (kind == 1 ? 3 : (kind == 14 ? 2 : (kind == 16 ? 1 : (kind == 22 ? 4 : 0))));
                  int material = new int[]{0, 0, 1, 2, 0, 3}[tier];
                  return weighted(s.tags, 0) + (preference + material) * 40 & 65535;
               }
            case 17:
               return item != 332 && item != 344 ? 0 : 1;
            case 18:
               return item != 413 && item != 282 ? 0 : 1;
            case 20:
               return item == 280 ? 1 : 0;
            case 23:
               return item == 46 ? 1 : 0;
            case 24:
               return item == 326 ? 1 : 0;
            case 26:
               return item == 35 ? 1 : 0;
         }
      } else {
         return 0;
      }
   }

   public static strictfp int policy(int category, InventorySelection.Stack s, boolean fallback) {
      int score = category(category, s);
      if (!fallback) {
         return score;
      } else if (score != 0) {
         return score << 16;
      } else {
         return category == 22 ? category(25, s) : (category == 17 ? category(7, s) : (category == 7 ? category(17, s) : 0));
      }
   }

   public static strictfp int armor(InventorySelection.Stack s, int slot) {
      if (s == null) {
         return 0;
      } else {
         int index = s.type - 298;
         if (index >= 0 && index < 20 && index % 4 == slot) {
            int material = new int[]{0, 2, 3, 4, 1}[index / 4];
            int base = new int[]{1, 3, 2, 1, 2, 5, 3, 1, 2, 5, 4, 1, 2, 6, 5, 2, 3, 8, 6, 3}[material * 4 + slot];
            float other = 0.0F;
            float protection = 0.0F;

            for (int[] tag : s.tags) {
               int id = tag[0] & 65535;
               int level = (short)tag[1];
               if (level >= 0) {
                  if (id >= 1 && id <= 4) {
                     other = (float)((double)level * 0.1 + (double)other);
                  } else if (id == 0) {
                     int quantized = (int)InventoryActions.truncateDoubleLong((double)(level * level + 6) * 0.75 / 3.0);
                     if (Integer.compareUnsigned(quantized, 25) >= 0) {
                        quantized = 25;
                     }

                     protection = (float)((quantized >>> 1) + (quantized + 1 & 62)) * 0.5F;
                  }
               }
            }

            double enchantments = (double)(other + protection);
            float combined = (float)(1.0 - (1.0 - enchantments * 0.04) * (1.0 - (double)base * 0.04));
            return (int)RecoveredMath.truncateLong(combined * 1000.0F);
         } else {
            return 0;
         }
      }
   }

   public static strictfp InventorySelection.Action planArmor(InventorySelection.Stack[] main, InventorySelection.Stack equipped, int armor) {
      int bestScore = armor(equipped, armor);
      int best = -1;

      for (int slot = 0; slot < 36; slot++) {
         int score = armor(main[slot], armor);
         if (Integer.compareUnsigned(bestScore, score) < 0) {
            bestScore = score;
            best = slot;
         }
      }

      if (best == -1) {
         return null;
      } else if (equipped == null) {
         return new InventorySelection.Action(0, best < 9 ? best + 36 : best, 0);
      } else {
         return best < 9 ? new InventorySelection.Action(1, armor + 5, best) : new InventorySelection.Action(2, best, armor + 5);
      }
   }

   public static strictfp InventorySelection.Action planHotbar(InventorySelection.Stack[] main, int destination, int category, boolean fallback, long processed) {
      if (category == 0) {
         return null;
      } else {
         InventorySelection.Stack original = main[destination];
         int originalCount = original == null ? 0 : original.count;
         int max = original == null ? 0 : original.maximum;
         int originalScore = policy(category, original, fallback);
         long identity = original == null ? 0L : original.identity();
         boolean empty = false;

         for (int i = 9; i < 36; i++) {
            if (main[i] == null) {
               empty = true;
               break;
            }
         }

         if (originalCount <= 0) {
            originalCount = 0;
         }

         int bestScore = originalCount * originalScore;
         int best = destination;
         int mergeFlag = empty ? 0 : 36;

         for (int slot = 0; slot < 36; slot++) {
            InventorySelection.Stack candidate = main[slot];
            if (slot != destination && (processed >>> slot & 1L) == 0L && candidate != null) {
               int count = candidate.count;
               int merged = 0;
               if (originalCount < max && count < max && candidate.identity() == identity && original != null && original.sameTags(candidate)) {
                  int combined = count + originalCount;
                  if (combined >= max) {
                     combined = max;
                  }

                  merged = combined * originalScore;
               }

               int score = policy(category, candidate, fallback) * count;
               boolean merge = Integer.compareUnsigned(score - 1, merged) < 0;
               if (merge) {
                  score = merged;
               }

               if (Integer.compareUnsigned(bestScore, score) < 0) {
                  if (slot < 9 && !empty && merge) {
                     mergeFlag &= 255;
                  } else {
                     best = slot;
                     bestScore = score;
                     mergeFlag = merge ? 1 : 0;
                  }
               }
            }
         }

         return best == destination ? null : new InventorySelection.Action(~mergeFlag & 1, best < 9 ? best + 36 : best, (mergeFlag & 1) != 0 ? 0 : destination);
      }
   }

   public static strictfp long processedBit(int scan) {
      return (long)(1 << (scan & 31));
   }

   public static final strictfp class Action {
      public int kind;
      public int source;
      public int destination;

      public Action(int kind, int source, int destination) {
         this.kind = kind;
         this.source = source;
         this.destination = destination;
      }
   }

   public static final strictfp class Stack {
      public final int type;
      public final int damage;
      public final int count;
      public final int maximum;
      public final boolean flag;
      public final int[][] tags;
      public final ItemStack source;

      public Stack(int type, int damage, int count, int maximum, boolean flag, int[][] tags) {
         this(type, damage, count, maximum, flag, tags, null);
      }

      private Stack(int type, int damage, int count, int maximum, boolean flag, int[][] tags, ItemStack source) {
         this.type = type;
         this.damage = damage;
         this.count = count;
         this.maximum = maximum;
         this.flag = flag;
         this.tags = tags;
         this.source = source;
      }

      public static strictfp InventorySelection.Stack of(ItemStack stack) {
         if (stack == null) {
            return null;
         } else {
            NBTTagList nbt = stack.getEnchantmentTagList();
            int[][] tags = new int[nbt == null ? 0 : nbt.tagCount()][2];

            for (int i = 0; i < tags.length; i++) {
               tags[i][0] = nbt.getCompoundTagAt(i).getShort("id") & '\uffff';
               tags[i][1] = nbt.getCompoundTagAt(i).getShort("lvl");
            }

            return new InventorySelection.Stack(
               InventoryActions.type(stack), stack.getItemDamage(), stack.stackSize, stack.getMaxStackSize(), stack.hasDisplayName(), tags, stack
            );
         }
      }

      public strictfp long identity() {
         return (long)this.type << 32 | (long)this.damage;
      }

      public strictfp boolean sameTags(InventorySelection.Stack other) {
         return this.source == null && other.source == null
            ? Arrays.deepEquals(this.tags, other.tags)
            : ItemStack.areItemStackTagsEqual(this.source, other.source);
      }
   }
}
