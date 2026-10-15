package dev.slinky.config;

public final class RecoveredJsonParser {
   private static final byte[] NULL_BYTES = RecoveredJsonRuntime.cString("null");
   private static final byte[] FALSE_BYTES = RecoveredJsonRuntime.cString("false");
   private static final byte[] TRUE_BYTES = RecoveredJsonRuntime.cString("true");

   private RecoveredJsonParser() {
   }

   private static boolean accessible(RecoveredJsonParser.ParseBuffer buffer) {
      return Long.compareUnsigned(buffer.offset, buffer.length) < 0;
   }

   public static void skipWhitespace_4da00(RecoveredJsonParser.ParseBuffer buffer) {
      if (buffer != null && buffer.content != null && accessible(buffer)) {
         while (accessible(buffer) && (buffer.content[(int)buffer.offset] & 255) <= 32) {
            buffer.offset++;
         }

         if (buffer.offset == buffer.length) {
            buffer.offset--;
         }
      }
   }

   public static RecoveredJsonRuntime.Node parse_4da70(byte[] text, RecoveredJsonRuntime.Context context) {
      return text == null ? null : parseLength_4d410(text, (long)RecoveredJsonRuntime.stringLength(text, 0) + 1L, 0, context).node;
   }

   public static RecoveredJsonParser.ParseResult parseLength_4d410(byte[] text, long length, int requireTerminator, RecoveredJsonRuntime.Context context) {
      context.errorContent = null;
      context.errorOffset = 0L;
      RecoveredJsonParser.ParseBuffer buffer = new RecoveredJsonParser.ParseBuffer(null, 0L, context);
      RecoveredJsonRuntime.Node item = null;
      if (text != null && length != 0L) {
         buffer = new RecoveredJsonParser.ParseBuffer(text, length, context);
         item = RecoveredJsonRuntime.newItem(context.allocator);
         if (item != null) {
            if (length >= 5L && (text[0] & 255) == 239 && (text[1] & 255) == 187 && (text[2] & 255) == 191) {
               buffer.offset = 3L;
            }

            skipWhitespace_4da00(buffer);
            if (parseValue_4d600(item, buffer, context) != 0) {
               if (requireTerminator != 0) {
                  skipWhitespace_4da00(buffer);
               }

               if (requireTerminator == 0 || accessible(buffer) && buffer.content[(int)buffer.offset] == 0) {
                  return new RecoveredJsonParser.ParseResult(item, buffer.offset);
               }
            }

            RecoveredJsonRuntime.deleteTree_4d240(item, context);
         }
      }

      if (text != null) {
         long position = accessible(buffer) ? buffer.offset : (buffer.length != 0L ? buffer.length - 1L : 0L);
         context.errorContent = text;
         context.errorOffset = position;
         return new RecoveredJsonParser.ParseResult(null, position);
      } else {
         return new RecoveredJsonParser.ParseResult(null, 0L);
      }
   }

   public static RecoveredJsonRuntime.Node parseLength_4d410(
      byte[] text, long length, RecoveredJsonParser.ParseEnd end, int requireTerminator, RecoveredJsonRuntime.Context context
   ) {
      RecoveredJsonParser.ParseResult result = parseLength_4d410(text, length, requireTerminator, context);
      if (end != null && text != null) {
         end.content = text;
         end.offset = result.endOffset;
      }

      return result.node;
   }

   public static int parseHex_51310(byte[] content, int offset) {
      int value = 0;

      for (int i = 0; i < 4; i++) {
         int ch = content[offset + i] & 255;
         int digit;
         if ((ch - 48 & 0xFF) < 10) {
            digit = ch - 48;
         } else if ((ch - 65 & 0xFF) < 6) {
            digit = ch - 65 + 10;
         } else {
            if ((ch - 97 & 0xFF) >= 6) {
               return 0;
            }

            digit = ch - 97 + 10;
         }

         value = (value << 4) + digit;
      }

      return value;
   }

   private static int stringFailure(RecoveredJsonParser.ParseBuffer buffer, long input, byte[] output) {
      if (output != null) {
         buffer.release.release(output);
      }

      buffer.offset = input;
      return 0;
   }

   public static int parseString_50b60(RecoveredJsonRuntime.Node item, RecoveredJsonParser.ParseBuffer buffer, RecoveredJsonRuntime.Context context) {
      long quote = buffer.offset;
      long input = quote + 1L;
      if (quote >= 0L && quote < (long)buffer.content.length && buffer.content[(int)quote] == 34 && input < buffer.length) {
         long end = input;

         long skipped;
         for (skipped = 0L; end < buffer.length && buffer.content[(int)end] != 34; end++) {
            if (buffer.content[(int)end] == 92) {
               if (end + 1L >= buffer.length) {
                  return stringFailure(buffer, input, null);
               }

               end++;
               skipped++;
            }
         }

         if (end >= buffer.length) {
            return stringFailure(buffer, input, null);
         } else {
            byte[] output = buffer.allocate.allocateBytes(end - quote - skipped + 1L);
            if (output == null) {
               return stringFailure(buffer, input, null);
            } else {
               int cursor = 0;

               while (input < end) {
                  if (buffer.content[(int)input] == 92) {
                     int consumed = 2;
                     switch (buffer.content[(int)input + 1] & 0xFF) {
                        case 34:
                        case 47:
                        case 92:
                           output[cursor++] = buffer.content[(int)input + 1];
                           break;
                        case 98:
                           output[cursor++] = 8;
                           break;
                        case 102:
                           output[cursor++] = 12;
                           break;
                        case 110:
                           output[cursor++] = 10;
                           break;
                        case 114:
                           output[cursor++] = 13;
                           break;
                        case 116:
                           output[cursor++] = 9;
                           break;
                        case 117:
                           if (end - input < 6L) {
                              return stringFailure(buffer, input, output);
                           }

                           int code = parseHex_51310(buffer.content, (int)input + 2);
                           consumed = 6;
                           if ((code & 64512) == 56320) {
                              return stringFailure(buffer, input, output);
                           }

                           if ((code & 64512) == 55296) {
                              if (end - input < 12L || buffer.content[(int)input + 6] != 92 || buffer.content[(int)input + 7] != 117) {
                                 return stringFailure(buffer, input, output);
                              }

                              int second = parseHex_51310(buffer.content, (int)input + 8);
                              if (Integer.compareUnsigned(second - 57344, -1024) < 0) {
                                 return stringFailure(buffer, input, output);
                              }

                              code = ((code & 1023) << 10) + (second & 1023) + 65536;
                              consumed = 12;
                           }

                           if (code < 128) {
                              output[cursor++] = (byte)code;
                           } else {
                              int width = code < 2048 ? 2 : (code < 65536 ? 3 : 4);
                              if (code > 1114111) {
                                 return stringFailure(buffer, input, output);
                              }

                              int rest = code;

                              for (int index = width - 1; index != 0; index--) {
                                 output[cursor + index] = (byte)(rest & 63 | 128);
                                 rest >>>= 6;
                              }

                              output[cursor] = (byte)(rest | (width == 2 ? 192 : (width == 3 ? 224 : 240)));
                              cursor += width;
                           }
                           break;
                        default:
                           return stringFailure(buffer, input, output);
                     }

                     input += (long)consumed;
                  } else {
                     output[cursor++] = buffer.content[(int)(input++)];
                  }
               }

               output[cursor] = 0;
               item.type = 16;
               item.valueString = output;
               buffer.offset = end + 1L;
               return 1;
            }
         }
      } else {
         return stringFailure(buffer, input, null);
      }
   }

   public static int parseObject_51000(RecoveredJsonRuntime.Node item, RecoveredJsonParser.ParseBuffer buffer, RecoveredJsonRuntime.Context context) {
      if (Long.compareUnsigned(buffer.depth, 999L) > 0) {
         return 0;
      } else {
         buffer.depth++;
         if (accessible(buffer) && buffer.content[(int)buffer.offset] == 123) {
            buffer.offset++;
            skipWhitespace_4da00(buffer);
            if (!accessible(buffer)) {
               buffer.offset--;
               return 0;
            } else if (buffer.content[(int)buffer.offset] == 125) {
               buffer.depth--;
               item.type = 64;
               item.child = null;
               buffer.offset++;
               return 1;
            } else {
               buffer.offset--;
               RecoveredJsonRuntime.Node head = null;
               RecoveredJsonRuntime.Node tail = null;

               while (true) {
                  RecoveredJsonRuntime.Node next = RecoveredJsonRuntime.newItem(buffer.allocate);
                  if (next == null) {
                     if (head != null) {
                        RecoveredJsonRuntime.deleteTree_4d240(head, context);
                     }

                     return 0;
                  }

                  if (tail != null) {
                     tail.next = next;
                     next.previous = tail;
                  } else {
                     head = next;
                  }

                  tail = next;
                  buffer.offset++;
                  skipWhitespace_4da00(buffer);
                  if (parseString_50b60(next, buffer, context) != 0) {
                     skipWhitespace_4da00(buffer);
                     next.name = next.valueString;
                     next.valueString = null;
                     if (accessible(buffer) && buffer.content[(int)buffer.offset] == 58) {
                        buffer.offset++;
                        skipWhitespace_4da00(buffer);
                        if (parseValue_4d600(next, buffer, context) != 0) {
                           skipWhitespace_4da00(buffer);
                           if (accessible(buffer)) {
                              int current = buffer.content[(int)buffer.offset] & 255;
                              if (current == 44) {
                                 continue;
                              }

                              if (current == 125) {
                                 buffer.depth--;
                                 head.previous = next;
                                 item.type = 64;
                                 item.child = head;
                                 buffer.offset++;
                                 return 1;
                              }
                           }
                        }
                     }
                  }

                  RecoveredJsonRuntime.deleteTree_4d240(head, context);
                  return 0;
               }
            }
         } else {
            return 0;
         }
      }
   }

   private static int parseArray(RecoveredJsonRuntime.Node item, RecoveredJsonParser.ParseBuffer buffer, RecoveredJsonRuntime.Context context) {
      if (Long.compareUnsigned(buffer.depth, 999L) > 0) {
         return 0;
      } else {
         buffer.depth++;
         if (buffer.content[(int)buffer.offset] != 91) {
            return 0;
         } else {
            buffer.offset++;
            skipWhitespace_4da00(buffer);
            if (!accessible(buffer)) {
               buffer.offset--;
               return 0;
            } else if (buffer.content[(int)buffer.offset] == 93) {
               buffer.depth--;
               item.type = 32;
               item.child = null;
               buffer.offset++;
               return 1;
            } else {
               buffer.offset--;
               RecoveredJsonRuntime.Node head = null;
               RecoveredJsonRuntime.Node tail = null;

               while (true) {
                  RecoveredJsonRuntime.Node next = RecoveredJsonRuntime.newItem(buffer.allocate);
                  if (next == null) {
                     if (head != null) {
                        RecoveredJsonRuntime.deleteTree_4d240(head, context);
                     }

                     return 0;
                  }

                  if (tail != null) {
                     tail.next = next;
                     next.previous = tail;
                  } else {
                     head = next;
                  }

                  tail = next;
                  buffer.offset++;
                  skipWhitespace_4da00(buffer);
                  if (parseValue_4d600(next, buffer, context) != 0) {
                     skipWhitespace_4da00(buffer);
                     if (accessible(buffer)) {
                        int current = buffer.content[(int)buffer.offset] & 255;
                        if (current == 44) {
                           continue;
                        }

                        if (current == 93) {
                           buffer.depth--;
                           head.previous = next;
                           item.type = 32;
                           item.child = head;
                           buffer.offset++;
                           return 1;
                        }
                     }
                  }

                  RecoveredJsonRuntime.deleteTree_4d240(head, context);
                  return 0;
               }
            }
         }
      }
   }

   private static boolean literal(byte[] content, long length, long offset, byte[] expected, int width, RecoveredJsonRuntime.Context context) {
      return Long.compareUnsigned(offset + (long)width, length) <= 0 && context.comparisons.compareBytes(content, (int)offset, expected, width) == 0;
   }

   public static int parseValue_4d600(RecoveredJsonRuntime.Node item, RecoveredJsonParser.ParseBuffer buffer, RecoveredJsonRuntime.Context context) {
      if (buffer != null && buffer.content != null) {
         byte[] content = buffer.content;
         long length = buffer.length;
         long offset = buffer.offset;
         if (literal(content, length, offset, NULL_BYTES, 4, context)) {
            item.type = 4;
            buffer.offset = offset + 4L;
            return 1;
         } else if (literal(content, length, offset, FALSE_BYTES, 5, context)) {
            item.type = 1;
            buffer.offset = offset + 5L;
            return 1;
         } else if (literal(content, length, offset, TRUE_BYTES, 4, context)) {
            item.type = 2;
            item.valueInteger = 1;
            buffer.offset = offset + 4L;
            return 1;
         } else if (Long.compareUnsigned(offset, length) >= 0) {
            return 0;
         } else {
            int ch = content[(int)offset] & 255;
            if (ch == 34) {
               return parseString_50b60(item, buffer, context);
            } else if (ch == 123) {
               return parseObject_51000(item, buffer, context);
            } else if (ch == 91) {
               return parseArray(item, buffer, context);
            } else if (ch != 45 && (ch - 48 & 0xFF) >= 10) {
               return 0;
            } else {
               int decimal = context.numbers.decimalPoint();
               if (buffer.content == null) {
                  return 0;
               } else {
                  byte[] number = new byte[64];

                  for (int count = 0; count < 63 && buffer.offset + (long)count < buffer.length; count++) {
                     int current = buffer.content[(int)buffer.offset + count] & 255;
                     if (current >= 48 && current <= 57) {
                        number[count] = (byte)current;
                     } else if (current != 43 && current != 45 && current != 101 && current != 69) {
                        if (current != 46) {
                           break;
                        }

                        number[count] = (byte)decimal;
                     } else {
                        number[count] = (byte)current;
                     }
                  }

                  RecoveredJsonRuntime.ParsedNumber parsed = context.numbers.parse(number);
                  if (parsed.consumed == 0) {
                     return 0;
                  } else {
                     item.valueNumber = parsed.value;
                     item.valueInteger = RecoveredJsonRuntime.saturatedInteger(parsed.value);
                     item.type = 8;
                     buffer.offset = buffer.offset + (long)parsed.consumed;
                     return 1;
                  }
               }
            }
         }
      } else {
         return 0;
      }
   }

   public static final class ParseBuffer {
      public byte[] content;
      public long length;
      public long offset;
      public long depth;
      public RecoveredJsonRuntime.Allocator allocate;
      public RecoveredJsonRuntime.Allocator release;
      public RecoveredJsonRuntime.Resizer resize;

      public ParseBuffer(byte[] content, long length, RecoveredJsonRuntime.Context context) {
         if (length >= 0L && (content == null || length <= (long)content.length)) {
            this.content = content;
            this.length = length;
            this.allocate = this.release = context.allocator;
            this.resize = context.resizer;
         } else {
            throw new IllegalArgumentException("JSON byte length exceeds supplied storage");
         }
      }
   }

   public static final class ParseEnd {
      public byte[] content;
      public long offset;
   }

   public static final class ParseResult {
      public final RecoveredJsonRuntime.Node node;
      public final long endOffset;

      private ParseResult(RecoveredJsonRuntime.Node node, long endOffset) {
         this.node = node;
         this.endOffset = endOffset;
      }

      public boolean succeeded() {
         return this.node != null;
      }
   }
}
