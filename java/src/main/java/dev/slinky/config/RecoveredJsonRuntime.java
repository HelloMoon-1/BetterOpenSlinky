package dev.slinky.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final strictfp class RecoveredJsonRuntime {
   public static final int FALSE = 1;
   public static final int TRUE = 2;
   public static final int NULL = 4;
   public static final int NUMBER = 8;
   public static final int STRING = 16;
   public static final int ARRAY = 32;
   public static final int OBJECT = 64;
   public static final int RAW = 128;
   public static final int REFERENCE = 256;
   public static final int CONSTANT_NAME = 512;

   private RecoveredJsonRuntime() {
   }

   public static strictfp byte[] cString(String text) {
      if (text == null) {
         return null;
      } else {
         byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
         return Arrays.copyOf(bytes, bytes.length + 1);
      }
   }

   public static strictfp int stringLength(byte[] text, int offset) {
      int end = offset;

      while (end < text.length && text[end] != 0) {
         end++;
      }

      if (end == text.length) {
         throw new IllegalArgumentException("C string lacks terminator");
      } else {
         return end - offset;
      }
   }

   public static strictfp String utf8CString(byte[] text) {
      return text == null ? null : new String(text, 0, stringLength(text, 0), StandardCharsets.UTF_8);
   }

   static strictfp RecoveredJsonRuntime.Node newItem(RecoveredJsonRuntime.Allocator allocator) {
      RecoveredJsonRuntime.Node item = allocator.allocateNode();
      if (item != null) {
         item.next = item.previous = item.child = null;
         item.type = item.typePadding = item.valueInteger = item.integerPadding = 0;
         item.valueString = item.name = null;
         item.valueNumber = 0.0;
      }

      return item;
   }

   private static strictfp RecoveredJsonRuntime.Node create(int type, RecoveredJsonRuntime.Context context) {
      RecoveredJsonRuntime.Node item = newItem(context.allocator);
      if (item != null) {
         item.type = type;
      }

      return item;
   }

   private static strictfp byte[] duplicate(byte[] text, RecoveredJsonRuntime.Context context) {
      int size = stringLength(text, 0) + 1;
      byte[] copy = context.allocator.allocateBytes((long)size);
      if (copy != null) {
         System.arraycopy(text, 0, copy, 0, size);
      }

      return copy;
   }

   private static strictfp RecoveredJsonRuntime.Node finishNamed(
      RecoveredJsonRuntime.Node parent, byte[] name, RecoveredJsonRuntime.Node item, RecoveredJsonRuntime.Context context
   ) {
      if (attachName_4e5b0(parent, name, item, context) != 0) {
         return item;
      } else {
         deleteTree_4d240(item, context);
         return null;
      }
   }

   public static strictfp void deleteTree_4d240(RecoveredJsonRuntime.Node item, RecoveredJsonRuntime.Context context) {
      while (item != null) {
         RecoveredJsonRuntime.Node current = item;
         item = item.next;
         int flags = current.type;
         if ((flags & 256) == 0 && current.child != null) {
            deleteTree_4d240(current.child, context);
            flags = current.type;
         }

         if ((flags & 256) == 0 && current.valueString != null) {
            context.allocator.release(current.valueString);
            flags = current.type;
         }

         if ((flags & 512) == 0 && current.name != null) {
            context.allocator.release(current.name);
         }

         context.allocator.release(current);
      }
   }

   public static strictfp void releaseText_4d3b0(Object text, RecoveredJsonRuntime.Context context) {
      context.allocator.release(text);
   }

   public static strictfp int append_4e560(RecoveredJsonRuntime.Node parent, RecoveredJsonRuntime.Node item) {
      if (parent != item && parent != null && item != null) {
         RecoveredJsonRuntime.Node head = parent.child;
         if (head == null) {
            parent.child = item;
            item.previous = item;
            item.next = null;
         } else if (head.previous != null) {
            RecoveredJsonRuntime.Node tail = head.previous;
            tail.next = item;
            item.previous = tail;
            head.previous = item;
         }

         return 1;
      } else {
         return 0;
      }
   }

   public static strictfp int attachName_4e5b0(
      RecoveredJsonRuntime.Node parent, byte[] name, RecoveredJsonRuntime.Node item, RecoveredJsonRuntime.Context context
   ) {
      if (parent != item && parent != null && name != null && item != null) {
         byte[] copy = duplicate(name, context);
         if (copy == null) {
            return 0;
         } else {
            int flags = item.type & -513;
            if ((item.type & 512) == 0 && item.name != null) {
               context.allocator.release(item.name);
            }

            item.name = copy;
            item.type = flags;
            return append_4e560(parent, item);
         }
      } else {
         return 0;
      }
   }

   public static strictfp RecoveredJsonRuntime.Node createObject_4f590(RecoveredJsonRuntime.Context context) {
      return create(64, context);
   }

   public static strictfp RecoveredJsonRuntime.Node writeObject_4f480(RecoveredJsonRuntime.Node parent, byte[] name, RecoveredJsonRuntime.Context context) {
      return finishNamed(parent, name, create(64, context), context);
   }

   public static strictfp RecoveredJsonRuntime.Node writeArray_4f5d0(RecoveredJsonRuntime.Node parent, byte[] name, RecoveredJsonRuntime.Context context) {
      return finishNamed(parent, name, create(32, context), context);
   }

   public static strictfp RecoveredJsonRuntime.Node writeBoolean_4ed40(
      RecoveredJsonRuntime.Node parent, byte[] name, int value, RecoveredJsonRuntime.Context context
   ) {
      return finishNamed(parent, name, create(value == 0 ? 1 : 2, context), context);
   }

   public static strictfp RecoveredJsonRuntime.Node writeNumber_4eeb0(
      RecoveredJsonRuntime.Node parent, byte[] name, double value, RecoveredJsonRuntime.Context context
   ) {
      RecoveredJsonRuntime.Node item = create(8, context);
      if (item != null) {
         item.valueNumber = value;
         item.valueInteger = saturatedInteger(value);
      }

      return finishNamed(parent, name, item, context);
   }

   public static strictfp RecoveredJsonRuntime.Node writeString_4f080(
      RecoveredJsonRuntime.Node parent, byte[] name, byte[] value, RecoveredJsonRuntime.Context context
   ) {
      RecoveredJsonRuntime.Node item = create(16, context);
      if (item != null) {
         item.valueString = value == null ? null : duplicate(value, context);
         if (item.valueString == null) {
            deleteTree_4d240(item, context);
            item = null;
         }
      }

      return finishNamed(parent, name, item, context);
   }

   static strictfp int saturatedInteger(double value) {
      if (value >= 2.147483647E9) {
         return Integer.MAX_VALUE;
      } else if (-2.1474836E9F >= value) {
         return Integer.MIN_VALUE;
      } else {
         return Double.isNaN(value) ? Integer.MIN_VALUE : (int)value;
      }
   }

   public static strictfp RecoveredJsonRuntime.Node lookup_4e4d0(RecoveredJsonRuntime.Node parent, byte[] name, RecoveredJsonRuntime.Context context) {
      if (parent != null && name != null) {
         for (RecoveredJsonRuntime.Node child = parent.child; child != null; child = child.next) {
            if (child.name == null) {
               return null;
            }

            if (context.comparisons.compareCString(name, child.name) == 0) {
               return child;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static strictfp int isNumber_4d150(RecoveredJsonRuntime.Node item) {
      return item != null && (item.type & 0xFF) == 8 ? 1 : 0;
   }

   public static strictfp int isTrue_508c0(RecoveredJsonRuntime.Node item) {
      return item != null && (item.type & 0xFF) == 2 ? 1 : 0;
   }

   public static strictfp byte[] printCompact_4dbe0(RecoveredJsonRuntime.Node root, RecoveredJsonRuntime.Context context) {
      return printAllocated_4dad0(root, 0, context);
   }

   public static strictfp byte[] printAllocated_4dad0(RecoveredJsonRuntime.Node root, int formatted, RecoveredJsonRuntime.Context context) {
      byte[] initial = context.allocator.allocateBytes(256L);
      RecoveredJsonRuntime.PrintBuffer buffer = new RecoveredJsonRuntime.PrintBuffer(initial, 256L, context);
      buffer.formatted = formatted;
      if (initial == null) {
         return null;
      } else {
         if (printValue_4dc80(root, buffer, context) != 0) {
            updateOffset(buffer);
            byte[] old = buffer.memory;
            RecoveredJsonRuntime.Resizer finalResizer = context.resizer;
            if (finalResizer != null) {
               byte[] result = finalResizer.resize(old, buffer.offset + 1L);
               if (result != null) {
                  return result;
               }
            } else {
               byte[] result = context.allocator.allocateBytes(buffer.offset + 1L);
               if (result != null) {
                  System.arraycopy(old, 0, result, 0, (int)Math.min(buffer.capacity, buffer.offset + 1L));
                  result[(int)buffer.offset] = 0;
                  context.allocator.release(old);
                  return result;
               }
            }
         }

         if (buffer.memory != null) {
            context.allocator.release(buffer.memory);
         }

         return null;
      }
   }

   public static strictfp RecoveredJsonRuntime.PrintSlice reserve_51400(
      RecoveredJsonRuntime.PrintBuffer buffer, long required, RecoveredJsonRuntime.Context context
   ) {
      byte[] old = buffer.memory;
      if (old == null) {
         return null;
      } else {
         long capacity = buffer.capacity;
         if ((capacity == 0L || Long.compareUnsigned(buffer.offset, capacity) < 0) && Long.compareUnsigned(required, 2147483647L) <= 0) {
            long size = required + buffer.offset + 1L;
            if (Long.compareUnsigned(size, capacity) <= 0) {
               return new RecoveredJsonRuntime.PrintSlice(old, buffer.offset);
            } else if (buffer.noAllocation != 0) {
               return null;
            } else {
               if (Long.compareUnsigned(size, 1073741824L) >= 0) {
                  if (size >>> 31 != 0L) {
                     return null;
                  }

                  size = 2147483647L;
               } else {
                  size *= 2L;
               }

               byte[] next;
               if (buffer.resize != null) {
                  next = buffer.resize.resize(old, size);
               } else {
                  next = buffer.allocate.allocateBytes(size);
                  if (next != null) {
                     System.arraycopy(buffer.memory, 0, next, 0, (int)(buffer.offset + 1L));
                     buffer.release.release(buffer.memory);
                  }
               }

               if (next == null) {
                  buffer.release.release(buffer.memory);
                  buffer.memory = null;
                  buffer.capacity = 0L;
                  return null;
               } else {
                  buffer.capacity = size;
                  buffer.memory = next;
                  return new RecoveredJsonRuntime.PrintSlice(next, buffer.offset);
               }
            }
         } else {
            return null;
         }
      }
   }

   private static strictfp long stringPrintRequiredSize(long length, long extra) {
      return length + extra + 3L;
   }

   public static strictfp int printString_514f0(byte[] string, RecoveredJsonRuntime.PrintBuffer buffer, RecoveredJsonRuntime.Context context) {
      if (string == null) {
         RecoveredJsonRuntime.PrintSlice out = reserve_51400(buffer, 3L, context);
         if (out == null) {
            return 0;
         } else {
            out.memory[out.offset] = 34;
            out.memory[out.offset + 1] = 34;
            out.memory[out.offset + 2] = 0;
            return 1;
         }
      } else {
         long length = 0L;
         long extra = 0L;

         while (true) {
            int value = string[Math.toIntExact(length)] & 255;
            if (value <= 34 && (17179883264L >>> value & 1L) != 0L) {
               extra++;
            } else {
               if (value == 0) {
                  RecoveredJsonRuntime.PrintSlice out = reserve_51400(buffer, stringPrintRequiredSize(length, extra), context);
                  if (out == null) {
                     return 0;
                  }

                  byte[] memory = out.memory;
                  int start = out.offset;
                  int inputLength = Math.toIntExact(length);
                  int closingQuote = Math.toIntExact((long)start + length + extra + 1L);
                  int terminator = Math.toIntExact((long)start + length + extra + 2L);
                  memory[start] = 34;
                  if (extra == 0L) {
                     System.arraycopy(string, 0, memory, start + 1, inputLength);
                  } else {
                     int cursor = start;

                     for (int i = 0; string[i] != 0; i++) {
                        int valuex = string[i] & 255;
                        if (valuex >= 32 && valuex != 34 && valuex != 92) {
                           cursor++;
                           memory[cursor] = (byte)valuex;
                        } else {
                           memory[cursor + 1] = 92;
                           int escaped;
                           switch (valuex) {
                              case 8:
                                 escaped = 98;
                                 break;
                              case 9:
                                 escaped = 116;
                                 break;
                              case 10:
                                 escaped = 110;
                                 break;
                              case 12:
                                 escaped = 102;
                                 break;
                              case 13:
                                 escaped = 114;
                                 break;
                              case 34:
                                 escaped = 34;
                                 break;
                              case 92:
                                 escaped = 92;
                                 break;
                              default:
                                 RecoveredJsonRuntime.FormattedNumber result = context.numbers.format("u%04x", (long)valuex);
                                 int width = stringLength(result.text, 0) + 1;
                                 System.arraycopy(result.text, 0, memory, cursor + 2, width);
                                 cursor += 6;
                                 continue;
                           }

                           memory[cursor + 2] = (byte)escaped;
                           cursor += 2;
                        }
                     }
                  }

                  memory[closingQuote] = 34;
                  memory[terminator] = 0;
                  return 1;
               }

               if (value == 92) {
                  extra++;
               } else if (value < 32) {
                  extra += 5L;
               }
            }

            length++;
         }
      }
   }

   private static strictfp void updateOffset(RecoveredJsonRuntime.PrintBuffer buffer) {
      if (buffer.memory != null) {
         buffer.offset = buffer.offset + (long)stringLength(buffer.memory, (int)buffer.offset);
      }
   }

   private static strictfp int printNumber(RecoveredJsonRuntime.Node item, RecoveredJsonRuntime.PrintBuffer buffer, RecoveredJsonRuntime.Context context) {
      double value = item.valueNumber;
      int decimal = context.numbers.decimalPoint() & 0xFF;
      RecoveredJsonRuntime.FormattedNumber text;
      if ((Double.doubleToRawLongBits(value) & Long.MAX_VALUE) >= 9218868437227405312L) {
         text = new RecoveredJsonRuntime.FormattedNumber(cString("null"), 4);
      } else if (value == (double)item.valueInteger) {
         text = context.numbers.format("%d", Integer.toUnsignedLong(item.valueInteger));
      } else {
         text = context.numbers.format("%1.15g", Double.doubleToRawLongBits(value));
         RecoveredJsonRuntime.ScannedNumber scanned = context.numbers.scan(text.text, "%lg");
         double maximum = sseMaximum(Math.abs(scanned.value), Math.abs(value));
         double error = Math.abs(scanned.value - value);
         double tolerance = maximum * Double.longBitsToDouble(4372995238176751616L);
         if (scanned.count != 1 || !(tolerance >= error)) {
            text = context.numbers.format("%1.17g", Double.doubleToRawLongBits(value));
         }
      }

      if (Integer.compareUnsigned(text.length, 25) > 0) {
         return 0;
      } else {
         RecoveredJsonRuntime.PrintSlice out = reserve_51400(buffer, (long)text.length + 1L, context);
         if (out == null) {
            return 0;
         } else {
            for (int i = 0; i < text.length; i++) {
               out.memory[out.offset + i] = (text.text[i] & 255) == decimal ? 46 : text.text[i];
            }

            out.memory[out.offset + text.length] = 0;
            buffer.offset = buffer.offset + (long)text.length;
            return 1;
         }
      }
   }

   private static strictfp double sseMaximum(double left, double right) {
      return !Double.isNaN(left) && !Double.isNaN(right) && !(left <= right) ? left : right;
   }

   public static strictfp int printValue_4dc80(RecoveredJsonRuntime.Node item, RecoveredJsonRuntime.PrintBuffer buffer, RecoveredJsonRuntime.Context context) {
      if (item == null) {
         return 0;
      } else {
         int type = item.type & 0xFF;
         if (type != 1 && type != 2 && type != 4) {
            if (type == 8) {
               return printNumber(item, buffer, context);
            } else if (type == 16) {
               return printString_514f0(item.valueString, buffer, context);
            } else if (type == 128) {
               if (item.valueString == null) {
                  return 0;
               } else {
                  int length = stringLength(item.valueString, 0) + 1;
                  RecoveredJsonRuntime.PrintSlice out = reserve_51400(buffer, (long)length, context);
                  if (out == null) {
                     return 0;
                  } else {
                     System.arraycopy(item.valueString, 0, out.memory, out.offset, length);
                     return 1;
                  }
               }
            } else if (type == 32) {
               return printArray(item, buffer, context);
            } else {
               return type == 64 ? printObject(item, buffer, context) : 0;
            }
         } else {
            byte[] literal = cString(type == 1 ? "false" : (type == 2 ? "true" : "null"));
            RecoveredJsonRuntime.PrintSlice out = reserve_51400(buffer, (long)literal.length, context);
            if (out == null) {
               return 0;
            } else {
               System.arraycopy(literal, 0, out.memory, out.offset, literal.length);
               return 1;
            }
         }
      }
   }

   private static strictfp int printArray(RecoveredJsonRuntime.Node item, RecoveredJsonRuntime.PrintBuffer buffer, RecoveredJsonRuntime.Context context) {
      RecoveredJsonRuntime.Node child = item.child;
      RecoveredJsonRuntime.PrintSlice out = reserve_51400(buffer, 1L, context);
      if (out == null) {
         return 0;
      } else {
         out.memory[out.offset] = 91;
         buffer.offset++;
         buffer.depth++;

         while (child != null) {
            if (printValue_4dc80(child, buffer, context) == 0) {
               return 0;
            }

            updateOffset(buffer);
            if (child.next == null) {
               break;
            }

            int compact = buffer.formatted == 0 ? 1 : 0;
            out = reserve_51400(buffer, (long)(compact ^ 3), context);
            if (out == null) {
               return 0;
            }

            out.memory[out.offset] = 44;
            out.memory[out.offset + 1] = 0;
            if (buffer.formatted != 0) {
               out.memory[out.offset + 1] = 32;
               out.memory[out.offset + 2] = 0;
            }

            buffer.offset += (long)(2 - compact);
            child = child.next;
         }

         out = reserve_51400(buffer, 2L, context);
         if (out == null) {
            return 0;
         } else {
            out.memory[out.offset] = 93;
            out.memory[out.offset + 1] = 0;
            buffer.depth--;
            return 1;
         }
      }
   }

   private static strictfp int printObject(RecoveredJsonRuntime.Node item, RecoveredJsonRuntime.PrintBuffer buffer, RecoveredJsonRuntime.Context context) {
      RecoveredJsonRuntime.Node child = item.child;
      int compact = buffer.formatted == 0 ? 1 : 0;
      RecoveredJsonRuntime.PrintSlice out = reserve_51400(buffer, (long)(compact ^ 3), context);
      if (out == null) {
         return 0;
      } else {
         out.memory[out.offset] = 123;
         buffer.depth++;
         if (buffer.formatted != 0) {
            out.memory[out.offset + 1] = 10;
         }

         for (buffer.offset += (long)(2 - compact); child != null; child = child.next) {
            if (buffer.formatted != 0) {
               out = reserve_51400(buffer, buffer.depth, context);
               if (out == null) {
                  return 0;
               }

               Arrays.fill(out.memory, out.offset, out.offset + (int)buffer.depth, (byte)9);
               buffer.offset = buffer.offset + buffer.depth;
            }

            if (printString_514f0(child.name, buffer, context) == 0) {
               return 0;
            }

            updateOffset(buffer);
            long colonWidth = Integer.compareUnsigned(buffer.formatted, 1) >= 0 ? 2L : 1L;
            out = reserve_51400(buffer, colonWidth, context);
            if (out == null) {
               return 0;
            }

            out.memory[out.offset] = 58;
            if (buffer.formatted != 0) {
               out.memory[out.offset + 1] = 9;
            }

            buffer.offset += colonWidth;
            if (printValue_4dc80(child, buffer, context) == 0) {
               return 0;
            }

            updateOffset(buffer);
            long suffixWidth = (long)((child.next != null ? 1 : 0) + (Integer.compareUnsigned(buffer.formatted, 1) >= 0 ? 1 : 0));
            out = reserve_51400(buffer, suffixWidth + 1L, context);
            if (out == null) {
               return 0;
            }

            int cursor = out.offset;
            if (child.next != null) {
               out.memory[cursor++] = 44;
            }

            if (buffer.formatted != 0) {
               out.memory[cursor++] = 10;
            }

            out.memory[cursor] = 0;
            buffer.offset += suffixWidth;
         }

         out = reserve_51400(buffer, buffer.formatted != 0 ? buffer.depth + 1L : 2L, context);
         if (out == null) {
            return 0;
         } else {
            int cursorx = out.offset;
            if (buffer.formatted != 0 && buffer.depth != 1L) {
               long count = 0L;

               do {
                  out.memory[cursorx++] = 9;
               } while (++count < buffer.depth - 1L);
            }

            out.memory[cursorx] = 125;
            out.memory[cursorx + 1] = 0;
            buffer.depth--;
            return 1;
         }
      }
   }

   public interface Allocator {
      RecoveredJsonRuntime.Node allocateNode();

      byte[] allocateBytes(long var1);

      void release(Object var1);
   }

   public interface ByteComparisons {
      int compareCString(byte[] var1, byte[] var2);

      int compareBytes(byte[] var1, int var2, byte[] var3, int var4);
   }

   public static final strictfp class Context {
      public RecoveredJsonRuntime.Allocator allocator;
      public RecoveredJsonRuntime.Resizer resizer;
      public RecoveredJsonRuntime.NumberServices numbers;
      public RecoveredJsonRuntime.ByteComparisons comparisons;
      public RecoveredJsonRuntime.ExternalInvoker external;
      public byte[] errorContent;
      public long errorOffset;

      public Context(
         RecoveredJsonRuntime.Allocator allocator,
         RecoveredJsonRuntime.Resizer resizer,
         RecoveredJsonRuntime.NumberServices numbers,
         RecoveredJsonRuntime.ByteComparisons comparisons
      ) {
         if (allocator != null && numbers != null && comparisons != null) {
            this.allocator = allocator;
            this.resizer = resizer;
            this.numbers = numbers;
            this.comparisons = comparisons;
         } else {
            throw new NullPointerException("JSON caller services");
         }
      }

      public strictfp Object invoke(int target, Object... arguments) {
         Object[] a = new Object[5];
         if (arguments != null) {
            System.arraycopy(arguments, 0, a, 0, Math.min(arguments.length, a.length));
         }

         switch (target) {
            case 315728:
               return RecoveredJsonRuntime.isNumber_4d150((RecoveredJsonRuntime.Node)a[0]);
            case 315968:
               RecoveredJsonRuntime.deleteTree_4d240((RecoveredJsonRuntime.Node)a[0], this);
               return null;
            case 316336:
               RecoveredJsonRuntime.releaseText_4d3b0(a[0], this);
               return null;
            case 316432:
               return RecoveredJsonParser.parseLength_4d410((byte[])a[0], word(a[1]), (RecoveredJsonParser.ParseEnd)a[2], (int)word(a[3]), this);
            case 316928:
               return RecoveredJsonParser.parseValue_4d600((RecoveredJsonRuntime.Node)a[0], (RecoveredJsonParser.ParseBuffer)a[1], this);
            case 317952:
               RecoveredJsonParser.skipWhitespace_4da00((RecoveredJsonParser.ParseBuffer)a[0]);
               return null;
            case 318064:
               return RecoveredJsonParser.parse_4da70((byte[])a[0], this);
            case 318160:
               return RecoveredJsonRuntime.printAllocated_4dad0((RecoveredJsonRuntime.Node)a[0], (int)word(a[1]), this);
            case 318432:
               return RecoveredJsonRuntime.printCompact_4dbe0((RecoveredJsonRuntime.Node)a[0], this);
            case 318592:
               return RecoveredJsonRuntime.printValue_4dc80((RecoveredJsonRuntime.Node)a[0], (RecoveredJsonRuntime.PrintBuffer)a[1], this);
            case 320720:
               return RecoveredJsonRuntime.lookup_4e4d0((RecoveredJsonRuntime.Node)a[0], (byte[])a[1], this);
            case 320864:
               return RecoveredJsonRuntime.append_4e560((RecoveredJsonRuntime.Node)a[0], (RecoveredJsonRuntime.Node)a[1]);
            case 320944:
               return RecoveredJsonRuntime.attachName_4e5b0((RecoveredJsonRuntime.Node)a[0], (byte[])a[1], (RecoveredJsonRuntime.Node)a[2], this);
            case 322880:
               return RecoveredJsonRuntime.writeBoolean_4ed40((RecoveredJsonRuntime.Node)a[0], (byte[])a[1], (int)word(a[2]), this);
            case 323248:
               return RecoveredJsonRuntime.writeNumber_4eeb0((RecoveredJsonRuntime.Node)a[0], (byte[])a[1], Double.longBitsToDouble(word(a[2])), this);
            case 323712:
               return RecoveredJsonRuntime.writeString_4f080((RecoveredJsonRuntime.Node)a[0], (byte[])a[1], (byte[])a[2], this);
            case 324736:
               return RecoveredJsonRuntime.writeObject_4f480((RecoveredJsonRuntime.Node)a[0], (byte[])a[1], this);
            case 325008:
               return RecoveredJsonRuntime.createObject_4f590(this);
            case 325072:
               return RecoveredJsonRuntime.writeArray_4f5d0((RecoveredJsonRuntime.Node)a[0], (byte[])a[1], this);
            case 329920:
               return RecoveredJsonRuntime.isTrue_508c0((RecoveredJsonRuntime.Node)a[0]);
            case 330592:
               return RecoveredJsonParser.parseString_50b60((RecoveredJsonRuntime.Node)a[0], (RecoveredJsonParser.ParseBuffer)a[1], this);
            case 331776:
               return RecoveredJsonParser.parseObject_51000((RecoveredJsonRuntime.Node)a[0], (RecoveredJsonParser.ParseBuffer)a[1], this);
            case 332560:
               return RecoveredJsonParser.parseHex_51310((byte[])a[0], 0);
            case 332800:
               return RecoveredJsonRuntime.reserve_51400((RecoveredJsonRuntime.PrintBuffer)a[0], word(a[1]), this);
            case 333040:
               return RecoveredJsonRuntime.printString_514f0((byte[])a[0], (RecoveredJsonRuntime.PrintBuffer)a[1], this);
            default:
               if (this.external == null) {
                  throw new UnsupportedOperationException("Unbound JSON target " + target);
               } else {
                  return this.external.invoke(target, arguments);
               }
         }
      }

      private static strictfp long word(Object argument) {
         return argument == null ? 0L : ((Number)argument).longValue();
      }
   }

   public interface ExternalInvoker {
      Object invoke(int var1, Object[] var2);
   }

   public static final strictfp class FormattedNumber {
      public final byte[] text;
      public final int length;

      public FormattedNumber(byte[] text, int length) {
         this.text = text;
         this.length = length;
      }
   }

   public static final class Node {
      public RecoveredJsonRuntime.Node next;
      public RecoveredJsonRuntime.Node previous;
      public RecoveredJsonRuntime.Node child;
      public int type;
      public int typePadding;
      public int valueInteger;
      public int integerPadding;
      public byte[] valueString;
      public byte[] name;
      public double valueNumber;
   }

   public interface NumberServices {
      int decimalPoint();

      RecoveredJsonRuntime.FormattedNumber format(String var1, long var2);

      RecoveredJsonRuntime.ScannedNumber scan(byte[] var1, String var2);

      RecoveredJsonRuntime.ParsedNumber parse(byte[] var1);
   }

   public static final strictfp class ParsedNumber {
      public final double value;
      public final int consumed;

      public ParsedNumber(double value, int consumed) {
         this.value = value;
         this.consumed = consumed;
      }
   }

   public static final strictfp class PrintBuffer {
      public byte[] memory;
      public long capacity;
      public long offset;
      public long depth;
      public int noAllocation;
      public int formatted;
      public RecoveredJsonRuntime.Allocator allocate;
      public RecoveredJsonRuntime.Allocator release;
      public RecoveredJsonRuntime.Resizer resize;

      public PrintBuffer(byte[] memory, long capacity, RecoveredJsonRuntime.Context context) {
         this.memory = memory;
         this.capacity = capacity;
         this.allocate = context.allocator;
         this.release = context.allocator;
         this.resize = context.resizer;
      }
   }

   public static final strictfp class PrintSlice {
      public final byte[] memory;
      public final int offset;

      private PrintSlice(byte[] memory, long offset) {
         this.memory = memory;
         this.offset = Math.toIntExact(offset);
      }
   }

   public interface Resizer {
      byte[] resize(byte[] var1, long var2);
   }

   public static final strictfp class ScannedNumber {
      public final int count;
      public final double value;

      public ScannedNumber(int count, double value) {
         this.count = count;
         this.value = value;
      }
   }
}
