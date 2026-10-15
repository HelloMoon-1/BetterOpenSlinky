package dev.slinky.client;

import dev.slinky.SlinkyMod;
import dev.slinky.config.ConfigStore;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.FriendsModule;
import dev.slinky.setting.Setting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

public final class SlinkyCommand extends CommandBase {
   private final ModuleManager modules;

   public SlinkyCommand(ModuleManager modules) {
      this.modules = modules;
   }

   public String getCommandName() {
      return "slinky";
   }

   public String getCommandUsage(ICommandSender sender) {
      return "/slinky <list|toggle|settings|set|save|import>";
   }

   public int getRequiredPermissionLevel() {
      return 0;
   }

   public void processCommand(ICommandSender sender, String[] args) {
      try {
         if (args.length != 0 && !"list".equalsIgnoreCase(args[0])) {
            if ("import".equalsIgnoreCase(args[0])) {
               this.importConfiguration(sender, args);
            } else if ("save".equalsIgnoreCase(args[0])) {
               this.modules.setEnabled(this.requireModule(args, 1), this.requireModule(args, 1).isEnabled(), true);
               reply(sender, "configuration saved");
            } else {
               Module module = this.requireModule(args, 1);
               if ("friend".equalsIgnoreCase(args[0]) && module instanceof FriendsModule) {
                  if (!this.modules.context().ready()) {
                     throw new IllegalArgumentException("Enter a world first");
                  } else if (args.length < 3) {
                     throw new IllegalArgumentException("usage: /slinky friend friends <add|remove|near|clear> [name|radius]");
                  } else {
                     reply(sender, ((FriendsModule)module).command(this.modules.context(), args[2], args.length > 3 ? args[3] : ""));
                     this.modules.setEnabled(module, module.isEnabled(), true);
                  }
               } else if ("toggle".equalsIgnoreCase(args[0])) {
                  this.modules.toggle(module);
                  reply(sender, module.getName() + " is " + (module.isEnabled() ? "enabled" : "disabled"));
               } else if ("settings".equalsIgnoreCase(args[0])) {
                  for (Setting<?> setting : module.getSettings()) {
                     reply(sender, setting.getKey() + " = " + setting.format());
                  }
               } else if ("set".equalsIgnoreCase(args[0])) {
                  if (args.length < 4) {
                     throw new IllegalArgumentException("usage: /slinky set <module> <key> <value>");
                  } else {
                     Setting<?> setting = module.findSetting(args[2]);
                     if (setting == null) {
                        throw new IllegalArgumentException("unknown setting " + args[2]);
                     } else {
                        setting.parseAndSet(join(args, 3));
                        this.modules.setEnabled(module, module.isEnabled(), true);
                        reply(sender, module.getName() + "." + setting.getKey() + " = " + setting.format());
                     }
                  }
               } else {
                  throw new IllegalArgumentException(this.getCommandUsage(sender));
               }
            }
         } else {
            for (Module module : this.modules.all()) {
               reply(sender, (module.isEnabled() ? "[on] " : "[off] ") + module.getId() + " - " + module.getDescription());
            }
         }
      } catch (IllegalArgumentException var6) {
         reply(sender, var6.getMessage());
      }
   }

   public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
      if (args.length == 1) {
         return getListOfStringsMatchingLastWord(args, new String[]{"list", "toggle", "settings", "set", "save", "import"});
      } else if (args.length == 2) {
         List<String> ids = new ArrayList<>();

         for (Module module : this.modules.all()) {
            ids.add(module.getId());
         }

         return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
      } else {
         if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            Module module = this.modules.find(args[1]);
            if (module != null) {
               List<String> keys = new ArrayList<>();

               for (Setting<?> setting : module.getSettings()) {
                  keys.add(setting.getKey());
               }

               return getListOfStringsMatchingLastWord(args, keys.toArray(new String[keys.size()]));
            }
         }

         return Collections.emptyList();
      }
   }

   private void importConfiguration(ICommandSender sender, String[] args) {
      try {
         byte[] text;
         String source;
         if (args.length < 2) {
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard == null || clipboard.trim().isEmpty()) {
               throw new IllegalArgumentException("the clipboard is empty");
            }

            text = clipboard.trim().getBytes(StandardCharsets.UTF_8);
            source = "the clipboard";
         } else {
            String name = join(args, 1);
            Path file = SlinkyMod.slinkyDirectory().resolve("profiles").resolve(name);
            if (!Files.isRegularFile(file)) {
               file = Paths.get(name);
            }

            if (!Files.isRegularFile(file)) {
               throw new IllegalArgumentException("no file " + name);
            }

            text = Files.readAllBytes(file);
            source = file.getFileName().toString();
         }

         int records = ConfigStore.importText(this.modules, text);
         this.modules.persist();
         reply(sender, "imported " + records + " modules from " + source);
      } catch (IOException var7) {
         throw new IllegalArgumentException("import failed: " + var7.getMessage());
      }
   }

   private Module requireModule(String[] args, int index) {
      if (args.length <= index) {
         throw new IllegalArgumentException("module id is required");
      } else {
         Module module = this.modules.find(args[index]);
         if (module == null) {
            throw new IllegalArgumentException("unknown module " + args[index]);
         } else {
            return module;
         }
      }
   }

   private static String join(String[] values, int start) {
      StringBuilder joined = new StringBuilder();

      for (int i = start; i < values.length; i++) {
         if (joined.length() > 0) {
            joined.append(' ');
         }

         joined.append(values[i]);
      }

      return joined.toString();
   }

   private static void reply(ICommandSender sender, String text) {
      sender.addChatMessage(new ChatComponentText("[Slinky] " + text));
   }
}
