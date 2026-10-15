package dev.slinky.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.Map.Entry;

public final class ConfigurationJson {
   private ConfigurationJson() {
   }

   public static JsonObject parse(byte[] bytes) throws IOException {
      JsonElement value = parseValue(bytes);
      return value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
   }

   public static JsonElement parseValue(byte[] bytes) throws IOException {
      RecoveredJsonRuntime.Context context = new RecoveredJsonJvmServices().context();
      RecoveredJsonRuntime.Node root = RecoveredJsonParser.parse_4da70(bytes, context);
      if (root == null) {
         throw new IOException("Invalid configuration bytes at " + context.errorOffset);
      } else {
         JsonElement var3;
         try {
            var3 = value(root);
         } finally {
            RecoveredJsonRuntime.deleteTree_4d240(root, context);
         }

         return var3;
      }
   }

   private static JsonElement value(RecoveredJsonRuntime.Node node) {
      switch (node.type & 0xFF) {
         case 1:
            return new JsonPrimitive(false);
         case 2:
            return new JsonPrimitive(true);
         case 8:
            return new JsonPrimitive(node.valueNumber);
         case 16:
            return new JsonPrimitive(RecoveredJsonRuntime.utf8CString(node.valueString));
         case 32:
            JsonArray array = new JsonArray();

            for (RecoveredJsonRuntime.Node child = node.child; child != null; child = child.next) {
               array.add(value(child));
            }

            return array;
         case 64:
            JsonObject object = new JsonObject();

            for (RecoveredJsonRuntime.Node child = node.child; child != null && child.name != null; child = child.next) {
               String name = RecoveredJsonRuntime.utf8CString(child.name);
               if (!object.has(name)) {
                  object.add(name, value(child));
               }
            }

            return object;
         default:
            return JsonNull.INSTANCE;
      }
   }

   public static byte[] print(JsonObject value) throws IOException {
      RecoveredJsonRuntime.Context context = new RecoveredJsonJvmServices().context();
      RecoveredJsonRuntime.Node root = RecoveredJsonRuntime.createObject_4f590(context);
      if (root == null) {
         throw new ConfigurationFiles.Failure(5000);
      } else {
         byte[] var4;
         try {
            fill(root, value, context);
            byte[] text = RecoveredJsonRuntime.printCompact_4dbe0(root, context);
            if (text == null) {
               throw new ConfigurationFiles.Failure(5000);
            }

            var4 = text;
         } finally {
            RecoveredJsonRuntime.deleteTree_4d240(root, context);
         }

         return var4;
      }
   }

   private static void fill(RecoveredJsonRuntime.Node node, JsonElement value, RecoveredJsonRuntime.Context context) throws IOException {
      if (value.isJsonObject()) {
         for (Entry<String, JsonElement> member : value.getAsJsonObject().entrySet()) {
            add(node, RecoveredJsonRuntime.cString(member.getKey()), member.getValue(), context);
         }
      } else if (value.isJsonArray()) {
         for (JsonElement child : value.getAsJsonArray()) {
            add(node, null, child, context);
         }
      }
   }

   private static void add(RecoveredJsonRuntime.Node parent, byte[] name, JsonElement value, RecoveredJsonRuntime.Context context) throws IOException {
      RecoveredJsonRuntime.Node holder = name == null ? RecoveredJsonRuntime.createObject_4f590(context) : parent;
      byte[] key = name == null ? new byte[]{0} : name;
      if (holder == null) {
         throw new ConfigurationFiles.Failure(5000);
      } else {
         RecoveredJsonRuntime.Node child = null;

         try {
            if (value.isJsonObject()) {
               child = RecoveredJsonRuntime.writeObject_4f480(holder, key, context);
            } else if (value.isJsonArray()) {
               child = RecoveredJsonRuntime.writeArray_4f5d0(holder, key, context);
            } else if (value.isJsonPrimitive()) {
               JsonPrimitive primitive = value.getAsJsonPrimitive();
               if (primitive.isBoolean()) {
                  child = RecoveredJsonRuntime.writeBoolean_4ed40(holder, key, primitive.getAsBoolean() ? 1 : 0, context);
               } else if (primitive.isNumber()) {
                  child = RecoveredJsonRuntime.writeNumber_4eeb0(holder, key, primitive.getAsDouble(), context);
               } else {
                  child = RecoveredJsonRuntime.writeString_4f080(holder, key, RecoveredJsonRuntime.cString(primitive.getAsString()), context);
               }
            } else {
               child = RecoveredJsonRuntime.createObject_4f590(context);
               if (child != null) {
                  child.type = 4;
                  if (RecoveredJsonRuntime.attachName_4e5b0(holder, key, child, context) == 0) {
                     RecoveredJsonRuntime.deleteTree_4d240(child, context);
                     child = null;
                  }
               }
            }

            if (child == null) {
               throw new ConfigurationFiles.Failure(5000);
            }

            if (name == null) {
               holder.child = null;
               context.allocator.release(child.name);
               child.name = null;
               child.next = child.previous = null;
               if (RecoveredJsonRuntime.append_4e560(parent, child) == 0) {
                  RecoveredJsonRuntime.deleteTree_4d240(child, context);
                  throw new ConfigurationFiles.Failure(5000);
               }
            }

            fill(child, value, context);
         } finally {
            if (name == null) {
               RecoveredJsonRuntime.deleteTree_4d240(holder, context);
            }
         }
      }
   }
}
