package dev.slinky.client.render;

import dev.slinky.client.ClientContext;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleManager;
import dev.slinky.module.impl.FriendsModule;
import dev.slinky.module.impl.SprintModule;
import dev.slinky.setting.StringListSetting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

/** Donor console text buffer and strictly local command parser; never sends chat commands. */
public final class OpenB4Console {
   private static boolean bps;
   private final ModuleManager manager;
   private final List<String> lines = new ArrayList<>();
   private boolean firstOpen = true;

   public OpenB4Console(ModuleManager manager) {
      this.manager = manager;
   }

   public List<String> lines() {
      if (this.firstOpen) {
         this.firstOpen = false;
         this.print("Welcome,", 0);
         this.print("Use \"help\" for help.", 0);
      }
      return Collections.unmodifiableList(this.lines);
   }

   public void print(String text, int spacing) {
      if (spacing == 1 || spacing == 2) {
         this.lines.add("");
      }
      this.lines.add(text);
      if (spacing == 2 || spacing == 3) {
         this.lines.add("");
      }
      while (this.lines.size() > 20) {
         this.lines.remove(0);
      }
   }

   public void handle(String raw) {
      if (raw == null || raw.trim().isEmpty()) {
         return;
      }
      if (raw.length() > 256) {
         this.print("&cCommand is too long. [256 characters]", 1);
         return;
      }
      String[] args = raw.trim().split("\\s+");
      String command = args[0].toLowerCase(Locale.ROOT);
      try {
         switch (command) {
            case "help":
            case "?":
               this.help();
               return;
            case "clear":
               if (args.length != 1) {
                  this.syntax("clear");
               } else {
                  this.firstOpen = false;
                  this.lines.clear();
               }
               return;
            case "friend":
            case "f":
               this.friend(args);
               return;
            case "profiles":
            case "p":
               this.profiles(args);
               return;
            case "coords":
               if (args.length != 1) {
                  this.syntax("coords");
                  return;
               }
               if (!this.inWorld()) {
                  return;
               }
               Minecraft mc = this.manager.context().minecraft();
               this.chat("&7pX: &d" + mc.thePlayer.posX + " &7pY: &d" + mc.thePlayer.posY + " &7pZ: &d" + mc.thePlayer.posZ);
               BlockPos position = mc.thePlayer.getPosition();
               this.chat("&7bX: &d" + position.getX() + " &7bY: &d" + position.getY() + " &7bZ: &d" + position.getZ());
               this.print("Printed coordinates.", 1);
               return;
            case "entities":
               if (args.length != 1) {
                  this.syntax("entities");
                  return;
               }
               if (!this.inWorld()) {
                  return;
               }
               List<Entity> entities = this.manager.context().world().loadedEntityList;
               // Individual local chat lines avoid overflowing a single component.
               for (Entity entity : entities) {
                  this.chat("&7" + entity.getClass().getSimpleName() + ":" + entity.getEntityId() + ":&r" + entity.getName());
               }
               this.print("Printed " + entities.size() + " entities.", 1);
               return;
            case "ping":
               this.ping(args);
               return;
            case "shoutout":
               if (args.length != 1) {
                  this.syntax("shoutout");
                  return;
               }
               this.print("&eshout out", 1);
               for (String name : new String[]{"charlie", "pug", "jc", "alan", "mic", "syuto", "des"}) {
                  this.print("- " + name, 0);
               }
               return;
            case "enemy":
            case "e":
               this.unsupported(command, "there is no donor enemy-list backend");
               return;
            case "nick":
            case "cname":
            case "fakechat":
               this.unsupported(command, "the donor name/chat replacement hooks are not present");
               return;
            case "setspeed":
            case "setvelocity":
               this.unsupported(command, "donor movement/combat settings are not exposed by this local console");
               return;
            case "sprint":
               this.sprint(raw);
               return;
            case "bps":
               if (args.length != 1) {
                  this.syntax("bps");
               } else {
                  bps = !bps;
                  this.print("BPS " + (bps ? "enabled" : "disabled"), 1);
               }
               return;
            case "tcosm":
               this.unsupported(command, "remote donor cosmetics are not available");
               return;
            default:
               this.print("&cInvalid command. (" + (command.length() > 5 ? command.substring(0, 5) + "..." : command) + ")", 1);
         }
      } catch (IOException | IllegalArgumentException | IllegalStateException error) {
         this.print("&c" + (error.getMessage() == null ? "Command failed." : error.getMessage()), 1);
      }
   }

   private void help() {
      this.print("&eAvailable commands", 1);
      this.print("- friend/f [name/reset]", 0);
      this.print("- profiles/p", 0);
      this.print("- ping (cached, no probe)", 0);
      this.print("- coords / entities / bps", 0);
      this.print("- sprint [text]", 0);
      this.print("- clear / help / ? / shoutout", 0);
      this.print("&eUnavailable donor commands", 0);
      this.print("- nick / enemy / cname / fakechat", 0);
      this.print("- setspeed / setvelocity", 0);
      this.print("- tcosm / profiles rename", 0);
      this.print("&eUnavailable commands report unsupported.", 0);
   }

   private void friend(String[] args) {
      if (args.length != 2) {
         this.syntax("friend [name/reset]");
         return;
      }
      Module module = this.manager.find("friends");
      if (!(module instanceof FriendsModule)) {
         this.unsupported("friend", "the local Friends module is unavailable");
         return;
      }
      FriendsModule friends = (FriendsModule)module;
      if (args[1].equalsIgnoreCase("reset")) {
         String result = friends.command(this.manager.context(), "clear", "");
         this.changed();
         this.print(result.replace('§', '&'), 1);
         return;
      }
      if (!this.inWorld()) {
         return;
      }
      EntityPlayer found = null;
      for (EntityPlayer player : this.manager.context().world().playerEntities) {
         if (player != this.manager.context().player() && player.getName().equalsIgnoreCase(args[1])) {
            found = player;
            break;
         }
      }
      if (found == null) {
         this.print("&cPlayer not loaded. Offline donor friend names are unsupported.", 1);
         return;
      }
      StringListSetting setting = (StringListSetting)friends.findSetting("friends");
      List<String> before = new ArrayList<>(setting.get());
      friends.toggle(found);
      if (before.equals(setting.get())) {
         this.print("&cFriend was not changed (excluded by Friends checks).", 1);
         return;
      }
      this.changed();
      this.print(setting.get().size() > before.size() ? "&aAdded friend:" : "&aRemoved friend:", 1);
      this.print("\"" + found.getName() + "\"", 0);
   }

   private void sprint(String raw) {
      int start = raw.toLowerCase(Locale.ROOT).indexOf("sprint") + "sprint".length();
      String remaining = raw.substring(start);
      if (!remaining.startsWith(" ")) {
         this.print("&cInvalid syntax.", 1);
         return;
      }
      String text = remaining.substring(1).trim();
      if (text.isEmpty()) {
         this.print("&cInvalid message.", 1);
         return;
      }
      Module module = this.manager.find("sprint");
      if (!(module instanceof SprintModule)) {
         this.unsupported("sprint", "the local Sprint text display is unavailable");
         return;
      }
      this.manager.configurationSession().controlChanged();
      // The real setter applies donor color codes and persists through its HUD manager.
      ((SprintModule)module).setText(text);
      this.print("&aSprint text updated!", 1);
   }

   private void profiles(String[] args) throws IOException {
      if (args.length != 1) {
         if (args.length >= 2 && args[1].equalsIgnoreCase("rename")) {
            this.unsupported("profiles rename", "the local profile service has no rename operation");
         } else {
            this.syntax("profiles");
         }
         return;
      }
      List<ProfileServices.Record> profiles = this.manager.input().profiles().records();
      this.print("&aAvailable profiles:", 1);
      if (profiles.isEmpty()) {
         this.print("None", 0);
      } else {
         int n = 0;
         for (ProfileServices.Record profile : profiles) {
            this.print(++n + ". " + profile.name, 0);
         }
      }
   }

   private void ping(String[] args) {
      if (args.length != 1) {
         this.syntax("ping");
         return;
      }
      if (!this.inWorld()) {
         return;
      }
      Minecraft mc = this.manager.context().minecraft();
      NetworkPlayerInfo info = mc.getNetHandler() == null ? null : mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
      if (info == null || info.getResponseTime() < 0) {
         this.unsupported("ping", "no cached server latency; active probes are disabled");
      } else {
         this.print("Your ping: " + info.getResponseTime() + "ms (cached)", 1);
      }
   }

   /** Called once by the shared HUD hook, independent of the console toggle. */
   public static void renderHud(Minecraft mc) {
      if (!bps || !RavenRender.visible(mc)) {
         return;
      }
      double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX;
      double dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
      double speed = Math.round(Math.sqrt(dx * dx + dz * dz) * 20.0 * 100.0) / 100.0;
      try (RavenRender.Scope scope = RavenRender.begin()) {
         mc.fontRendererObj.drawString(speed + "bps", 20.0F, 20.0F, -1, true);
      }
   }

   private boolean inWorld() {
      if (!this.manager.context().ready()) {
         this.print("&cEnter a world first.", 1);
         return false;
      }
      return true;
   }

   private void chat(String text) {
      ClientContext context = this.manager.context();
      if (context.player() != null) {
         context.player().addChatMessage(new ChatComponentText(text.replace('&', '§')));
      }
   }

   private void changed() {
      this.manager.configurationSession().controlChanged();
      this.manager.persist();
   }

   private void syntax(String usage) {
      this.print("&cInvalid syntax. " + usage, 1);
   }

   private void unsupported(String command, String reason) {
      this.print("&cUnsupported: " + command + " (" + reason + ").", 1);
   }
}
