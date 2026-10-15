package dev.slinky.module;

import dev.slinky.module.impl.AimAssistModule;
import dev.slinky.module.impl.AntiBotModule;
import dev.slinky.module.impl.AntiDebuffModule;
import dev.slinky.module.impl.AutoBlockModule;
import dev.slinky.module.impl.AutoClickerModule;
import dev.slinky.module.impl.AutoPotModule;
import dev.slinky.module.impl.AutoRodModule;
import dev.slinky.module.impl.AutoSoupModule;
import dev.slinky.module.impl.AutoToolModule;
import dev.slinky.module.impl.AutoWeaponModule;
import dev.slinky.module.impl.BacktrackModule;
import dev.slinky.module.impl.BlinkModule;
import dev.slinky.module.impl.BlockEspModule;
import dev.slinky.module.impl.BedPlatesModule;
import dev.slinky.module.impl.BlockInModule;
import dev.slinky.module.impl.BridgeAssistModule;
import dev.slinky.module.impl.ChamsModule;
import dev.slinky.module.impl.ChestEspModule;
import dev.slinky.module.impl.ChestStealerModule;
import dev.slinky.module.impl.ClutchModule;
import dev.slinky.module.impl.CriticalsModule;
import dev.slinky.module.impl.DebugModule;
import dev.slinky.module.impl.FakeLagModule;
import dev.slinky.module.impl.FastAccelModule;
import dev.slinky.module.impl.FastMineModule;
import dev.slinky.module.impl.FastPlaceModule;
import dev.slinky.module.impl.FreeLookModule;
import dev.slinky.module.impl.FriendsModule;
import dev.slinky.module.impl.GhostHandModule;
import dev.slinky.module.impl.GuiModule;
import dev.slinky.module.impl.HitSelectModule;
import dev.slinky.module.impl.HitboxesModule;
import dev.slinky.module.impl.IndicatorsModule;
import dev.slinky.module.impl.InstantStopModule;
import dev.slinky.module.impl.InvManagerModule;
import dev.slinky.module.impl.InvWalkModule;
import dev.slinky.module.impl.ItemEspModule;
import dev.slinky.module.impl.ItemUseFixModule;
import dev.slinky.module.impl.KeepSprintModule;
import dev.slinky.module.impl.KnockbackDelayModule;
import dev.slinky.module.impl.KnockbackDisplaceModule;
import dev.slinky.module.impl.LagRangeModule;
import dev.slinky.module.impl.ModOverlayModule;
import dev.slinky.module.impl.RavenSettingsModule;
import dev.slinky.module.impl.TargetHudModule;
import dev.slinky.module.impl.RadarModule;
import dev.slinky.module.impl.InfoHudModule;
import dev.slinky.module.impl.KeystrokesModule;
import dev.slinky.module.impl.BridgeInfoModule;
import dev.slinky.module.impl.CommandLineModule;
import dev.slinky.module.impl.NameTagsModule;
import dev.slinky.module.impl.NoHitDelayModule;
import dev.slinky.module.impl.NoHurtCamModule;
import dev.slinky.module.impl.NoItemReleaseModule;
import dev.slinky.module.impl.NoJumpDelayModule;
import dev.slinky.module.impl.NoSlowModule;
import dev.slinky.module.impl.NoUseDelayModule;
import dev.slinky.module.impl.NotificationsModule;
import dev.slinky.module.impl.NullMoveModule;
import dev.slinky.module.impl.PiercingModule;
import dev.slinky.module.impl.PingFixModule;
import dev.slinky.module.impl.PlayerEspModule;
import dev.slinky.module.impl.PointersModule;
import dev.slinky.module.impl.ReachModule;
import dev.slinky.module.impl.RefillModule;
import dev.slinky.module.impl.SafeWalkModule;
import dev.slinky.module.impl.SpeedModule;
import dev.slinky.module.impl.SprintModule;
import dev.slinky.module.impl.SprintResetModule;
import dev.slinky.module.impl.StrafeModule;
import dev.slinky.module.impl.TeamsModule;
import dev.slinky.module.impl.TimerModule;
import dev.slinky.module.impl.TimerRangeModule;
import dev.slinky.module.impl.TrajectoriesModule;
import dev.slinky.module.impl.VelocityModule;
import dev.slinky.module.impl.WeaponsModule;
import dev.slinky.module.impl.WatermarkModule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreModules {
   private CoreModules() {
   }

   public static void register(ModuleManager manager) {
      WeaponsModule weapons = new WeaponsModule();
      List<Module> implemented = new ArrayList<>();
      implemented.add(weapons);
      implemented.add(new TimerModule());
      implemented.add(new AntiDebuffModule());
      implemented.add(new NoJumpDelayModule());
      implemented.add(new NullMoveModule());
      implemented.add(new NoHitDelayModule());
      implemented.add(new FastMineModule());
      implemented.add(new NoUseDelayModule());
      implemented.add(new AutoToolModule());
      implemented.add(new AutoWeaponModule(weapons));
      implemented.add(new NoHurtCamModule());
      implemented.add(new SprintModule(manager));
      implemented.add(new NoSlowModule());
      implemented.add(new ItemUseFixModule());
      implemented.add(new ReachModule());
      implemented.add(new HitboxesModule());
      implemented.add(new SafeWalkModule());
      implemented.add(new FastAccelModule(weapons));
      implemented.add(new AntiBotModule());
      implemented.add(new TeamsModule());
      implemented.add(new FriendsModule());
      implemented.add(new PiercingModule(weapons));
      implemented.add(new GhostHandModule());
      implemented.add(new NoItemReleaseModule());
      implemented.add(new PingFixModule());
      implemented.add(new AimAssistModule(weapons));
      implemented.add(new FreeLookModule());
      implemented.add(new StrafeModule(weapons));
      implemented.add(new InstantStopModule());
      implemented.add(new KeepSprintModule());
      implemented.add(new SprintResetModule(weapons));
      implemented.add(new VelocityModule(weapons));
      implemented.add(new FakeLagModule(weapons));
      implemented.add(new CriticalsModule(weapons));
      implemented.add(new BlinkModule(manager));
      implemented.add(new BacktrackModule(weapons));
      implemented.add(new LagRangeModule(weapons));
      implemented.add(new TimerRangeModule(weapons));
      implemented.add(new KnockbackDelayModule(weapons));
      implemented.add(new DebugModule());
      implemented.add(new KnockbackDisplaceModule(weapons, manager));
      implemented.add(new AutoSoupModule(manager));
      implemented.add(new AutoPotModule(manager));
      implemented.add(new AutoRodModule(manager, weapons));
      implemented.add(new RefillModule(manager));
      implemented.add(new InvWalkModule());
      implemented.add(new InvManagerModule());
      implemented.add(new AutoClickerModule(manager, weapons));
      implemented.add(new AutoBlockModule(manager));
      implemented.add(new BridgeAssistModule());
      implemented.add(new FastPlaceModule());
      implemented.add(new HitSelectModule(weapons));
      implemented.add(new ClutchModule(manager));
      implemented.add(new BlockInModule(manager));
      implemented.add(new PlayerEspModule());
      implemented.add(new ChestEspModule());
      implemented.add(new NameTagsModule());
      implemented.add(new PointersModule());
      implemented.add(new IndicatorsModule());
      implemented.add(new BlockEspModule());
      implemented.add(new ItemEspModule());
      implemented.add(new TrajectoriesModule());
      implemented.add(new ChamsModule());
      implemented.add(new ModOverlayModule(manager));
      implemented.add(new NotificationsModule(manager));
      implemented.add(new GuiModule(manager));
      Map<String, Module> byName = new LinkedHashMap<>();

      for (Module module : implemented) {
         if (byName.put(module.getName(), module) != null) {
            throw new IllegalStateException("Duplicate implementation " + module.getName());
         }
      }

      for (String name : EvidenceCatalog.moduleNames()) {
         if (EvidenceCatalog.descriptorCategory(name) >= 0) {
            Module modulex = byName.remove(name);
            if (modulex == null) {
               throw new IllegalStateException("Missing implementation " + name);
            }

            manager.register(modulex);
         }
      }

      if (!byName.isEmpty()) {
         throw new IllegalStateException("Unmatched implementations " + byName.keySet());
      }
      // Donor HUDs have no recovered Slinky evidence-catalog entries.
      manager.register(new RavenSettingsModule(manager));
      manager.register(new TargetHudModule(manager));
      manager.register(new RadarModule());
      manager.register(new InfoHudModule(manager));
      manager.register(new WatermarkModule(manager));
      manager.register(new BedPlatesModule());
      manager.register(new KeystrokesModule(manager));
      manager.register(new BridgeInfoModule(manager));
      manager.register(new CommandLineModule());
   }

   public static List<Module> sourceOnlyIntervals() {
      return Collections.unmodifiableList(Arrays.asList(new Module[]{new ChestStealerModule(), new SpeedModule()}));
   }
}
