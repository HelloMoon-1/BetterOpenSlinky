package dev.slinky.module.impl;

import dev.slinky.client.ClientContext;
import dev.slinky.client.SemanticEvent;
import dev.slinky.client.hud.BridgeInfoEditor;
import dev.slinky.client.hud.InfoHud;
import dev.slinky.client.render.DrawPlan;
import dev.slinky.client.render.RavenRender;
import dev.slinky.client.render.RenderAccess;
import dev.slinky.module.Module;
import dev.slinky.module.ModuleCategory;
import dev.slinky.module.ModuleManager;
import dev.slinky.setting.BooleanSetting;
import dev.slinky.setting.NumberSetting;
import dev.slinky.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.BlockPos;

/** Solos only. Opponent and goal positions come exclusively from actual server observations. */
public final class BridgeInfoModule extends Module {
   public static final int COLOR = -16725816;
   private final ModuleManager manager;
   private final BooleanSetting editPosition;
   private final NumberSetting hudX, hudY;
   private final ConcurrentLinkedQueue<String> chat = new ConcurrentLinkedQueue<>();
   private String enemy = "";
   private boolean awaitingOpponent;
   private BlockPos defendPos, scorePos;
   private Double distance, enemyDistance;
   private int blocks;
   private Object world, player;

   public BridgeInfoModule(ModuleManager manager) {
      super("bridge_info", "Bridge Info", "Only for solos.", ModuleCategory.UTILITY);
      this.manager = manager;
      this.editPosition = this.option(new BooleanSetting("edit_position", "Edit position", false));
      this.hudX = this.option(new NumberSetting("hud_x", "Position X", 5, -100000, 100000, 1));
      this.hudY = this.option(new NumberSetting("hud_y", "Position Y", 70, -100000, 100000, 1));
   }

   private <T, S extends Setting<T>> S option(S setting) {
      setting.set(setting.get()); setting.saveCurrent(); return this.add(setting);
   }
   public int x() { return this.hudX.intValue(); }
   public int y() { return this.hudY.intValue(); }
   public void position(int x, int y) { this.hudX.set((double)x); this.hudY.set((double)y); }
   public void openEditor() {
      BridgeInfoEditor screen = new BridgeInfoEditor(this.manager, this);
      Module gui = this.manager.find("gui");
      if (gui instanceof GuiModule) ((GuiModule)gui).openEditor(screen);
      else this.manager.context().minecraft().displayGuiScreen(screen);
   }

   @Override
   public boolean afterSettingControl(String key, ClientContext c) {
      if (key.equals("edit_position") && this.editPosition.isEnabled()) {
         this.editPosition.set(false); this.openEditor(); return true;
      }
      return false;
   }
   @Override
   public boolean settingVisible(String key) { return !key.equals("hud_x") && !key.equals("hud_y"); }
   @Override
   public boolean listens(int type) { return type == 2; }
   @Override
   public void onEvent(ClientContext c, SemanticEvent e) {
      if (!e.cancelled && e.packet instanceof S02PacketChat) {
         S02PacketChat packet = (S02PacketChat)e.packet;
         if (packet.getType() != 2) this.chat.add(packet.getChatComponent().getUnformattedText());
      }
   }

   /** Same source as donor SumoFences.scoreLines: the visible sidebar, never inferred teams. */
   public static List<String> scoreLines(ClientContext c) {
      List<String> result = new ArrayList<>();
      if (c.world() == null) return result;
      Scoreboard board = c.world().getScoreboard();
      if (board == null) return result;
      ScoreObjective objective = board.getObjectiveInDisplaySlot(1);
      if (objective == null) return result;
      List<Score> scores = new ArrayList<>();
      for (Score score : board.getSortedScores(objective)) {
         if (score != null && score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) scores.add(score);
      }
      for (int i = Math.max(0, scores.size() - 15); i < scores.size(); ++i) {
         Score score = scores.get(i);
         result.add(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()), score.getPlayerName()));
      }
      return result;
   }

   public boolean inBridge(ClientContext c) {
      if (!c.ready() || c.minecraft().isSingleplayer() || c.minecraft().getCurrentServerData() == null) return false;
      String host = c.minecraft().getCurrentServerData().serverIP.toLowerCase(Locale.ROOT);
      int port = host.lastIndexOf(':');
      if (port >= 0) host = host.substring(0, port);
      if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return false;
      for (String line : scoreLines(c)) {
         String lower = InfoHud.strip(line).toLowerCase(Locale.ROOT);
         if (lower.contains("mode") && lower.contains("the brid")) return true;
      }
      return false;
   }

   public void onChat(String message) {
      String text = InfoHud.strip(message);
      if (text == null || text.isEmpty() || text.charAt(0) != ' ') return;
      if (text.contains("First player to score 5 goals wins")) {
         this.enemy = "";
         this.defendPos = this.scorePos = null;
         this.distance = this.enemyDistance = null;
         this.awaitingOpponent = true;
      } else if (this.awaitingOpponent && text.contains("Opponent:")) {
         String name = text.substring(text.indexOf("Opponent:") + "Opponent:".length()).trim();
         int rank = name.lastIndexOf("] ");
         if (rank >= 0) name = name.substring(rank + 2);
         this.enemy = name;
         this.awaitingOpponent = false;
      }
   }

   @Override
   protected void onEnable(ClientContext c) {
      this.reset();
      this.world = c.world(); this.player = c.player();
   }

   @Override
   public void onClientTick(ClientContext c) {
      if (this.world != c.world() || this.player != c.player()) {
         this.reset();
         this.world = c.world(); this.player = c.player();
      }
      String message;
      while ((message = this.chat.poll()) != null) if (c.ready()) this.onChat(message);
      if (!this.inBridge(c)) {
         this.distance = this.enemyDistance = null;
         this.defendPos = this.scorePos = null;
         return;
      }
      EntityPlayer opponent = null;
      for (Entity entity : c.world().loadedEntityList) {
         if (entity instanceof EntityPlayer) {
            if (!this.enemy.isEmpty() && entity.getName().equals(this.enemy)) opponent = (EntityPlayer)entity;
         } else if (entity instanceof EntityArmorStand) {
            if (entity.getName().contains("Defend!")) this.defendPos = entity.getPosition();
            else if (entity.getName().contains("Jump in to score!")) this.scorePos = entity.getPosition();
         }
      }
      this.distance = this.scorePos == null ? null : roundedDistance(c.player(), this.scorePos);
      this.enemyDistance = opponent == null || this.defendPos == null ? null : roundedDistance(opponent, this.defendPos);
      int total = 0;
      for (int slot = 0; slot < 9; ++slot) {
         ItemStack stack = c.player().inventory.getStackInSlot(slot);
         if (stack != null && stack.getItem() instanceof ItemBlock && ((ItemBlock)stack.getItem()).getBlock().equals(Blocks.stained_hardened_clay)) total += stack.stackSize;
      }
      this.blocks = total;
   }

   private static double roundedDistance(EntityPlayer player, BlockPos goal) {
      return Math.max(0, (double)Math.round((player.getDistance(goal.getX(), goal.getY(), goal.getZ()) - 1.4) * 10) / 10);
   }

   public DrawPlan plan(ClientContext c) {
      DrawPlan p = new DrawPlan();
      int h = c.minecraft().fontRendererObj.FONT_HEIGHT;
      p.text("Enemy: " + this.enemy, this.x(), this.y(), COLOR, true, h);
      p.text("Distance to goal: " + (this.distance == null ? "?" : this.distance), this.x(), this.y() + 11, COLOR, true, h);
      p.text("Enemy distance to goal: " + (this.enemyDistance == null ? "?" : this.enemyDistance), this.x(), this.y() + 22, COLOR, true, h);
      p.text("Blocks: " + this.blocks, this.x(), this.y() + 33, COLOR, true, h);
      return p;
   }
   @Override
   public void onRenderTick(ClientContext c) {
      if (!RavenRender.visible(c.minecraft()) || !this.inBridge(c)) return;
      try (RavenRender.Scope scope = RavenRender.begin()) { RenderAccess.submit(new SemanticEvent(10), this.plan(c)); }
   }
   private void reset() {
      this.enemy = ""; this.awaitingOpponent = false; this.defendPos = this.scorePos = null;
      this.distance = this.enemyDistance = null; this.blocks = 0; this.chat.clear();
   }
   @Override
   protected void onDisable(ClientContext c) { this.reset(); this.world = this.player = null; }
   @Override
   public void onWorldUnload(ClientContext c) { this.reset(); this.world = this.player = null; }
}
