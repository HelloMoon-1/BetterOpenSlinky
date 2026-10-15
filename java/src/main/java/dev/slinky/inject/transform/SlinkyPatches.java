package dev.slinky.inject.transform;

import dev.slinky.inject.Mappings;
import dev.slinky.shaded.asm.Opcodes;
import dev.slinky.shaded.asm.tree.AbstractInsnNode;
import dev.slinky.shaded.asm.tree.ClassNode;
import dev.slinky.shaded.asm.tree.FieldInsnNode;
import dev.slinky.shaded.asm.tree.InsnList;
import dev.slinky.shaded.asm.tree.InsnNode;
import dev.slinky.shaded.asm.tree.JumpInsnNode;
import dev.slinky.shaded.asm.tree.LabelNode;
import dev.slinky.shaded.asm.tree.LdcInsnNode;
import dev.slinky.shaded.asm.tree.MethodInsnNode;
import dev.slinky.shaded.asm.tree.MethodNode;
import dev.slinky.shaded.asm.tree.TryCatchBlockNode;
import dev.slinky.shaded.asm.tree.VarInsnNode;
import java.util.ArrayList;
import java.util.List;

public final class SlinkyPatches implements Opcodes {
   static final String GAME_HOOKS = "dev/slinky/client/hook/GameHooks";
   static final String RENDER_HOOKS = "dev/slinky/client/hook/RenderHooks";
   static final String EVENTS = "dev/slinky/client/ClientEventBridge";
   static final String EVENT = "dev/slinky/client/SemanticEvent";
   static final String MINECRAFT = "net/minecraft/client/Minecraft";
   static final String ENTITY = "net/minecraft/entity/Entity";
   static final String LIVING = "net/minecraft/entity/EntityLivingBase";
   static final String PLAYER = "net/minecraft/client/entity/EntityPlayerSP";
   static final String CONTROLLER = "net/minecraft/client/multiplayer/PlayerControllerMP";
   static final String NETWORK = "net/minecraft/network/NetworkManager";
   static final String WORLD = "net/minecraft/world/World";
   static final String RENDER_INFO = "net/minecraft/client/renderer/ActiveRenderInfo";
   static final String RENDER_MANAGER = "net/minecraft/client/renderer/entity/RenderManager";
   static final String LIVING_RENDERER = "net/minecraft/client/renderer/entity/RendererLivingEntity";
   static final String RENDER = "net/minecraft/client/renderer/entity/Render";
   static final String ENTITY_RENDERER = "net/minecraft/client/renderer/EntityRenderer";
   static final String CHUNK_PROVIDER = "net/minecraft/client/multiplayer/ChunkProviderClient";
   static final String GL_STATE = "net/minecraft/client/renderer/GlStateManager";
   static final String GUI_SCREEN = "net/minecraft/client/gui/GuiScreen";

   private SlinkyPatches() {
   }

   static String cls(String named) {
      return Mappings.mapClass(named);
   }

   static String desc(String named) {
      return Mappings.mapDescriptor(named);
   }

   static boolean isCall(AbstractInsnNode insn, String owner, String name, String desc) {
      if (!(insn instanceof MethodInsnNode)) {
         return false;
      } else {
         MethodInsnNode call = (MethodInsnNode)insn;
         return call.owner.equals(cls(owner)) && call.desc.equals(desc(desc)) && call.name.equals(Mappings.mapMethod(owner, name, desc));
      }
   }

   static boolean isExternalCall(AbstractInsnNode insn, String owner, String name, String desc) {
      if (!(insn instanceof MethodInsnNode)) {
         return false;
      } else {
         MethodInsnNode call = (MethodInsnNode)insn;
         return call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc);
      }
   }

   static MethodInsnNode hook(String owner, String name, String namedDesc) {
      return new MethodInsnNode(184, owner, name, desc(namedDesc), false);
   }

   static AbstractInsnNode nextReal(AbstractInsnNode insn) {
      for (AbstractInsnNode node = insn.getNext(); node != null; node = node.getNext()) {
         if (node.getOpcode() >= 0) {
            return node;
         }
      }

      return null;
   }

   static int redirectInputLoop(MethodNode method, String extOwner, String extName, String extDesc, String hookOwner, String hookName) {
      int count = 0;

      for (AbstractInsnNode insn : method.instructions.toArray()) {
         if (isExternalCall(insn, extOwner, extName, extDesc)) {
            AbstractInsnNode cond = nextReal(insn);
            if (cond != null && cond.getOpcode() == 153) {
               int pollAt = method.instructions.indexOf(insn);
               if (method.instructions.indexOf(((JumpInsnNode)cond).label) > pollAt) {
                  AbstractInsnNode body = nextReal(cond);
                  if (body == null || body.getOpcode() != 167 || method.instructions.indexOf(((JumpInsnNode)body).label) > pollAt) {
                     method.instructions.set(insn, hook(hookOwner, hookName, extDesc));
                     count++;
                  }
               }
            }
         }
      }

      return count;
   }

   static boolean isReturn(AbstractInsnNode insn) {
      return insn.getOpcode() >= 172 && insn.getOpcode() <= 177;
   }

   static InsnList list(AbstractInsnNode... nodes) {
      InsnList list = new InsnList();

      for (AbstractInsnNode node : nodes) {
         list.add(node);
      }

      return list;
   }

   static InsnList returnIfTrue() {
      LabelNode proceed = new LabelNode();
      return list(new JumpInsnNode(153, proceed), new InsnNode(177), proceed);
   }

   static int beforeReturns(MethodNode method, SlinkyPatches.InsnFactory factory) {
      int count = 0;

      for (AbstractInsnNode insn : method.instructions.toArray()) {
         if (isReturn(insn)) {
            method.instructions.insertBefore(insn, factory.create());
            count++;
         }
      }

      return count;
   }

   static int scope(MethodNode method, InsnList entry, SlinkyPatches.InsnFactory exit) {
      LabelNode start = new LabelNode();
      LabelNode end = new LabelNode();
      LabelNode handler = new LabelNode();
      int returns = beforeReturns(method, exit);
      InsnList head = new InsnList();
      if (entry != null) {
         head.add(entry);
      }

      head.add(start);
      method.instructions.insert(head);
      method.instructions.add(end);
      method.instructions.add(handler);
      method.instructions.add(exit.create());
      method.instructions.add(new InsnNode(191));
      method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
      return returns;
   }

   public static List<SlinkyPatches.ClassEdits> create(boolean forge) {
      List<SlinkyPatches.ClassEdits> patches = new ArrayList<>();
      String doRender = "(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V";
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/renderer/entity/RendererLivingEntity")
            .edit(
               "doRender",
               doRender,
               "Chams scope (RenderHooks#beginLiving/endLiving)",
               -1,
               method -> scope(
                     method,
                     list(new VarInsnNode(25, 1), hook("dev/slinky/client/hook/RenderHooks", "beginLiving", "(Lnet/minecraft/entity/EntityLivingBase;)V")),
                     () -> list(hook("dev/slinky/client/hook/RenderHooks", "endLiving", "()V"))
                  )
            )
            .edit("renderName", "(Lnet/minecraft/entity/EntityLivingBase;DDD)V", "Name Tags hides the vanilla name (cancellable head)", 1, method -> {
               InsnList entry = list(
                  new VarInsnNode(25, 1), hook("dev/slinky/client/hook/RenderHooks", "suppressVanillaName", "(Lnet/minecraft/entity/Entity;)Z")
               );
               entry.add(returnIfTrue());
               method.instructions.insert(entry);
               return 1;
            })
      );
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/renderer/entity/Render")
            .edit(
               "renderLivingLabel",
               "(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
               "Name Tags hides the vanilla label (cancellable head)",
               1,
               method -> {
                  InsnList entry = list(
                     new VarInsnNode(25, 1), hook("dev/slinky/client/hook/RenderHooks", "suppressVanillaName", "(Lnet/minecraft/entity/Entity;)Z")
                  );
                  entry.add(returnIfTrue());
                  method.instructions.insert(entry);
                  return 1;
               }
            )
      );
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/multiplayer/ChunkProviderClient")
            .edit(
               "unloadChunk",
               "(II)V",
               "afterUnloadChunk before return",
               -1,
               method -> beforeReturns(
                     method,
                     () -> list(new VarInsnNode(21, 1), new VarInsnNode(21, 2), hook("dev/slinky/client/hook/RenderHooks", "afterUnloadChunk", "(II)V"))
                  )
            )
      );
      String blockPos = "net/minecraft/util/BlockPos";
      String blockState = "net/minecraft/block/state/IBlockState";
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/world/World")
            .edit(
               "setBlockState",
               "(L" + blockPos + ";L" + blockState + ";I)Z",
               "beforeSetBlock at head",
               1,
               method -> {
                  method.instructions
                     .insert(
                        list(
                           new VarInsnNode(25, 0),
                           new VarInsnNode(25, 1),
                           new VarInsnNode(25, 2),
                           hook("dev/slinky/client/hook/RenderHooks", "beforeSetBlock", "(Lnet/minecraft/world/World;L" + blockPos + ";L" + blockState + ";)V")
                        )
                     );
                  return 1;
               }
            )
            .edit(
               "markBlockRangeForRenderUpdate",
               "(IIIIII)V",
               "afterInvalidate before return",
               -1,
               method -> beforeReturns(
                     method,
                     () -> list(
                           new VarInsnNode(25, 0),
                           new VarInsnNode(21, 1),
                           new VarInsnNode(21, 3),
                           hook("dev/slinky/client/hook/RenderHooks", "afterInvalidate", "(Lnet/minecraft/world/World;II)V")
                        )
                  )
            )
            .edit(
               "updateEntities",
               "()V",
               "beforeEntities/afterEntities/abortEntities",
               -1,
               method -> {
                  LabelNode start = new LabelNode();
                  LabelNode end = new LabelNode();
                  LabelNode failure = new LabelNode();
                  int returns = beforeReturns(
                     method, () -> list(new VarInsnNode(25, 0), hook("dev/slinky/client/hook/GameHooks", "afterEntities", "(Lnet/minecraft/world/World;)V"))
                  );
                  method.instructions
                     .insert(list(new VarInsnNode(25, 0), hook("dev/slinky/client/hook/GameHooks", "beforeEntities", "(Lnet/minecraft/world/World;)V"), start));
                  method.instructions.add(end);
                  method.instructions.add(failure);
                  method.instructions
                     .add(
                        list(
                           new VarInsnNode(25, 0),
                           hook("dev/slinky/client/hook/GameHooks", "abortEntities", "(Lnet/minecraft/world/World;)V"),
                           new InsnNode(191)
                        )
                     );
                  method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, failure, null));
                  return returns;
               }
            )
      );
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/renderer/EntityRenderer")
            .edit("renderWorldPass", "(IFJ)V", "GlStateManager.clear -> RenderHooks#clearWorld", 2, method -> {
               int count = 0;

               for (AbstractInsnNode insn : method.instructions.toArray()) {
                  if (isCall(insn, "net/minecraft/client/renderer/GlStateManager", "clear", "(I)V")) {
                     method.instructions.set(insn, hook("dev/slinky/client/hook/RenderHooks", "clearWorld", "(I)V"));
                     count++;
                  }
               }

               return count;
            })
            .edit("updateCameraAndRender", "(FJ)V", "preRender before Display.isActive, endRender scope", 1, method -> {
               int count = 0;

               for (AbstractInsnNode insn : method.instructions.toArray()) {
                  if (isExternalCall(insn, "org/lwjgl/opengl/Display", "isActive", "()Z")) {
                     method.instructions.insertBefore(insn, hook("dev/slinky/client/hook/GameHooks", "preRender", "()V"));
                     count++;
                  }
               }

               if (count == 0) {
                  return 0;
               } else {
                  scope(method, null, () -> list(hook("dev/slinky/client/hook/GameHooks", "endRender", "()V")));
                  return count;
               }
            })
            .edit(
               "orientCamera",
               "(F)V",
               "beginCamera/endCamera scope",
               -1,
               method -> scope(
                     method,
                     list(hook("dev/slinky/client/hook/GameHooks", "beginCamera", "()V")),
                     () -> list(hook("dev/slinky/client/hook/GameHooks", "endCamera", "()V"))
                  )
            )
            .edit(
               "getMouseOver",
               "(F)V",
               "beginRay/customRay, entity reach, block reach, hitbox border",
               -1,
               method -> {
                  int count = 0;

                  for (AbstractInsnNode insn : method.instructions.toArray()) {
                     if (insn instanceof LdcInsnNode && Double.valueOf(3.0).equals(((LdcInsnNode)insn).cst)) {
                        method.instructions.insert(insn, hook("dev/slinky/client/hook/GameHooks", "entityReach", "(D)D"));
                        count++;
                     } else if (isCall(insn, "net/minecraft/client/multiplayer/PlayerControllerMP", "getBlockReachDistance", "()F")) {
                        method.instructions.insert(insn, hook("dev/slinky/client/hook/GameHooks", "queryReach", "(F)F"));
                        count++;
                     } else if (isCall(insn, "net/minecraft/entity/Entity", "getCollisionBorderSize", "()F")) {
                        method.instructions.insert(insn, hook("dev/slinky/client/hook/GameHooks", "hitboxBorder", "(F)F"));
                        count++;
                     }
                  }

                  InsnList start = list(
                     new VarInsnNode(23, 1),
                     hook("dev/slinky/client/hook/GameHooks", "beginRay", "(F)V"),
                     hook("dev/slinky/client/hook/GameHooks", "customRay", "()Z")
                  );
                  start.add(returnIfTrue());
                  method.instructions.insert(start);
                  return count + 1;
               }
            )
      );
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/renderer/ActiveRenderInfo")
            .edit(
               "updateRenderInfo",
               "(Lnet/minecraft/entity/player/EntityPlayer;Z)V",
               "beginRenderInfo/endRenderInfo scope",
               -1,
               method -> scope(
                     method,
                     list(hook("dev/slinky/client/hook/GameHooks", "beginRenderInfo", "()V")),
                     () -> list(hook("dev/slinky/client/hook/GameHooks", "endRenderInfo", "()V"))
                  )
            )
      );
      SlinkyPatches.ClassEdits renderManager = new SlinkyPatches.ClassEdits("net/minecraft/client/renderer/entity/RenderManager");
      renderManager.everyMethodExpected = 6;
      renderManager.everyMethodLabel = "playerViewX/playerViewY writes (2 pitch, 4 yaw)";
      renderManager.everyMethod.add(method -> {
         int count = 0;
         String owner = cls("net/minecraft/client/renderer/entity/RenderManager");
         String pitch = Mappings.mapField("net/minecraft/client/renderer/entity/RenderManager", "playerViewX");
         String yaw = Mappings.mapField("net/minecraft/client/renderer/entity/RenderManager", "playerViewY");

         for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == 181) {
               FieldInsnNode field = (FieldInsnNode)insn;
               if (field.owner.equals(owner) && field.desc.equals("F")) {
                  String adapter = field.name.equals(pitch) ? "renderViewPitch" : (field.name.equals(yaw) ? "renderViewYaw" : null);
                  if (adapter != null) {
                     method.instructions.insertBefore(insn, hook("dev/slinky/client/hook/GameHooks", adapter, "(F)F"));
                     count++;
                  }
               }
            }
         }

         return count;
      });
      patches.add(renderManager);
      String worldClient = "net/minecraft/client/multiplayer/WorldClient";
      SlinkyPatches.ClassEdits minecraft = new SlinkyPatches.ClassEdits("net/minecraft/client/Minecraft")
         .edit("updateDisplay", "()V", "beforeDisplay before Display.update", 1, method -> {
            int count = 0;

            for (AbstractInsnNode insn : method.instructions.toArray()) {
               if (isExternalCall(insn, "org/lwjgl/opengl/Display", "update", "()V")) {
                  method.instructions.insertBefore(insn, hook("dev/slinky/client/hook/GameHooks", "beforeDisplay", "()V"));
                  count++;
               }
            }

            return count;
         })
         .edit("shutdownMinecraftApplet", "()V", "shutdown at head", 1, method -> {
            method.instructions.insert(hook("dev/slinky/client/hook/GameHooks", "shutdown", "()V"));
            return 1;
         })
         .edit("rightClickMouse", "()V", "beforeInteraction(true)/afterInteraction(true)", -1, method -> interaction(method, true))
         .edit("clickMouse", "()V", "beforeInteraction(false)/afterInteraction(false)", -1, method -> interaction(method, false))
         .edit("runTick", "()V", "ClientTickEvent START/END", -1, method -> {
            int returns = beforeReturns(method, () -> list(new InsnNode(3), hook("dev/slinky/client/ClientEventBridge", "clientTick", "(Z)V")));
            method.instructions.insert(list(new InsnNode(4), hook("dev/slinky/client/ClientEventBridge", "clientTick", "(Z)V")));
            return returns;
         })
         .edit("runGameLoop", "()V", "RenderTickEvent START/END around updateCameraAndRender", 1, method -> {
            int count = 0;

            for (AbstractInsnNode insn : method.instructions.toArray()) {
               if (isCall(insn, "net/minecraft/client/renderer/EntityRenderer", "updateCameraAndRender", "(FJ)V")) {
                  method.instructions.insertBefore(insn, list(new InsnNode(4), hook("dev/slinky/client/ClientEventBridge", "renderTick", "(Z)V")));
                  method.instructions.insert(insn, list(new InsnNode(3), hook("dev/slinky/client/ClientEventBridge", "renderTick", "(Z)V")));
                  count++;
               }
            }

            return count;
         })
         .edit(
            "loadWorld",
            "(L" + worldClient + ";Ljava/lang/String;)V",
            "WorldEvent.Unload at head",
            1,
            method -> {
               method.instructions
                  .insert(list(new VarInsnNode(25, 0), hook("dev/slinky/client/ClientEventBridge", "loadWorld", "(Lnet/minecraft/client/Minecraft;)V")));
               return 1;
            }
         );
      minecraft.everyMethodExpected = 2;
      minecraft.everyMethodLabel = "Keyboard.next/Mouse.next event-loop redirects (runTick, or OptiFine runInputTick)";
      minecraft.everyMethod
         .add(method -> redirectInputLoop(method, "org/lwjgl/input/Keyboard", "next", "()Z", "dev/slinky/client/ClientEventBridge", "keyboardNext"));
      minecraft.everyMethod
         .add(method -> redirectInputLoop(method, "org/lwjgl/input/Mouse", "next", "()Z", "dev/slinky/client/ClientEventBridge", "mouseNext"));
      patches.add(minecraft);
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/multiplayer/PlayerControllerMP")
            .edit(
               "updateController",
               "()V",
               "updateController before return",
               -1,
               method -> beforeReturns(method, () -> list(hook("dev/slinky/client/hook/GameHooks", "updateController", "()V")))
            )
            .edit("attackEntity", "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V", "HUD local attack attempt", 1, method -> {
               method.instructions.insert(list(new VarInsnNode(25, 1), new VarInsnNode(25, 2),
                  hook("dev/slinky/client/hook/GameHooks", "attack", "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V")));
               return 1;
            })
      );
      String packet = "net/minecraft/network/Packet";
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/network/NetworkManager")
            .edit(
               "sendPacket",
               "(L" + packet + ";)V",
               "beforeSend at head (cancellable)",
               1,
               method -> {
                  InsnList entry = list(
                     new VarInsnNode(25, 0),
                     new VarInsnNode(25, 1),
                     hook("dev/slinky/client/hook/GameHooks", "beforeSend", "(Lnet/minecraft/network/NetworkManager;L" + packet + ";)Z")
                  );
                  entry.add(returnIfTrue());
                  method.instructions.insert(entry);
                  return 1;
               }
            )
      );
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/entity/Entity")
            .edit(
               "setAngles",
               "(FF)V",
               "mouseAngles at head (cancellable)",
               1,
               method -> {
                  InsnList entry = list(
                     new VarInsnNode(25, 0),
                     new VarInsnNode(23, 1),
                     new VarInsnNode(23, 2),
                     hook("dev/slinky/client/hook/GameHooks", "mouseAngles", "(Lnet/minecraft/entity/Entity;FF)Z")
                  );
                  entry.add(returnIfTrue());
                  method.instructions.insert(entry);
                  return 1;
               }
            )
            .edit(
               "moveFlying",
               "(FFF)V",
               "beforeMoveFlying/afterMoveFlying",
               -1,
               method -> {
                  int local = method.maxLocals++;
                  InsnList entry = list(
                     new VarInsnNode(25, 0),
                     new VarInsnNode(23, 1),
                     new VarInsnNode(23, 2),
                     new VarInsnNode(23, 3),
                     hook("dev/slinky/client/hook/GameHooks", "beforeMoveFlying", "(Lnet/minecraft/entity/Entity;FFF)Ldev/slinky/client/SemanticEvent;"),
                     new VarInsnNode(58, local)
                  );
                  String[] fields = new String[]{"sideways", "forward", "factor"};

                  for (int i = 1; i <= 3; i++) {
                     entry.add(new VarInsnNode(25, local));
                     entry.add(new FieldInsnNode(180, "dev/slinky/client/SemanticEvent", fields[i - 1], "F"));
                     entry.add(new VarInsnNode(56, i));
                  }

                  int returns = beforeReturns(
                     method,
                     () -> list(
                           new VarInsnNode(25, 0),
                           new VarInsnNode(25, local),
                           hook("dev/slinky/client/hook/GameHooks", "afterMoveFlying", "(Lnet/minecraft/entity/Entity;Ldev/slinky/client/SemanticEvent;)V")
                        )
                  );
                  method.instructions.insert(entry);
                  return returns;
               }
            )
            .edit("moveEntity", "(DDD)V", "isSneaking -> GameHooks#safeWalk (first call)", 1, method -> {
               for (AbstractInsnNode insn : method.instructions.toArray()) {
                  if (isCall(insn, "net/minecraft/entity/Entity", "isSneaking", "()Z")) {
                     method.instructions.set(insn, hook("dev/slinky/client/hook/GameHooks", "safeWalk", "(Lnet/minecraft/entity/Entity;)Z"));
                     return 1;
                  }
               }

               return 0;
            })
      );
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/entity/EntityPlayerSP")
            .edit("swingItem", "()V", "return after super.swingItem() for GameHooks#baseSwing", 1, method -> {
               String name = Mappings.mapMethod("net/minecraft/client/entity/EntityPlayerSP", "swingItem", "()V");
               int count = 0;

               for (AbstractInsnNode insn : method.instructions.toArray()) {
                  if (insn.getOpcode() == 183 && ((MethodInsnNode)insn).name.equals(name) && ((MethodInsnNode)insn).desc.equals("()V")) {
                     InsnList after = list(hook("dev/slinky/client/hook/GameHooks", "baseSwingOnly", "()Z"));
                     after.add(returnIfTrue());
                     method.instructions.insert(insn, after);
                     count++;
                  }
               }

               return count;
            })
            .edit("sendChatMessage", "(Ljava/lang/String;)V", "client command at head (cancellable)", 1, method -> {
               InsnList entry = list(new VarInsnNode(25, 1), hook("dev/slinky/client/ClientEventBridge", "sendChatMessage", "(Ljava/lang/String;)Z"));
               entry.add(returnIfTrue());
               method.instructions.insert(entry);
               return 1;
            })
      );
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/gui/GuiScreen")
            .edit(
               "handleInput",
               "()V",
               "handleKeyboardInput -> ClientEventBridge#handleKeyboardInput",
               1,
               method -> redirectSelfCall(method, "handleKeyboardInput")
            )
            .edit("handleInput", "()V", "handleMouseInput -> ClientEventBridge#handleMouseInput", 1, method -> redirectSelfCall(method, "handleMouseInput"))
      );
      patches.add(
         new SlinkyPatches.ClassEdits("net/minecraft/client/network/NetHandlerPlayClient")
            .edit("onDisconnect", "(Lnet/minecraft/util/IChatComponent;)V", "disconnect at head", 1, method -> {
               method.instructions.insert(hook("dev/slinky/client/ClientEventBridge", "disconnect", "()V"));
               return 1;
            })
      );
      return patches;
   }

   private static int interaction(MethodNode method, boolean use) {
      int returns = beforeReturns(method, () -> list(new InsnNode(use ? 4 : 3), hook("dev/slinky/client/hook/GameHooks", "afterInteraction", "(Z)V")));
      InsnList start = list(new InsnNode(use ? 4 : 3), hook("dev/slinky/client/hook/GameHooks", "beforeInteraction", "(Z)Z"));
      start.add(returnIfTrue());
      method.instructions.insert(start);
      return returns;
   }

   private static int redirectSelfCall(MethodNode method, String name) {
      int count = 0;

      for (AbstractInsnNode insn : method.instructions.toArray()) {
         if (isCall(insn, "net/minecraft/client/gui/GuiScreen", name, "()V")) {
            method.instructions.set(insn, hook("dev/slinky/client/ClientEventBridge", name, "(Lnet/minecraft/client/gui/GuiScreen;)V"));
            count++;
         }
      }

      return count;
   }

   public static final class ClassEdits {
      public final String target;
      public final List<SlinkyPatches.MethodEdit> edits = new ArrayList<>();
      final List<SlinkyPatches.Edit> everyMethod = new ArrayList<>();
      int everyMethodExpected;
      String everyMethodLabel;

      ClassEdits(String target) {
         this.target = target;
      }

      public String getRuntimeTarget() {
         return Mappings.mapClass(this.target);
      }

      SlinkyPatches.ClassEdits edit(String name, String desc, String label, int expected, SlinkyPatches.Edit edit) {
         this.edits.add(new SlinkyPatches.MethodEdit(name, desc, label, expected, edit));
         return this;
      }

      public MethodNode find(ClassNode node, SlinkyPatches.MethodEdit edit) {
         String name = Mappings.mapMethod(this.target, edit.name, edit.desc);
         String desc = Mappings.mapDescriptor(edit.desc);

         for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
               return method;
            }
         }

         return null;
      }

      public int applyEveryMethod(ClassNode node) {
         int points = 0;

         for (SlinkyPatches.Edit edit : this.everyMethod) {
            for (MethodNode method : node.methods) {
               points += edit.apply(method);
            }
         }

         return points;
      }

      public int getEveryMethodExpected() {
         return this.everyMethodExpected;
      }

      public String getEveryMethodLabel() {
         return this.everyMethodLabel;
      }

      public boolean hasEveryMethodEdits() {
         return !this.everyMethod.isEmpty();
      }
   }

   public interface Edit {
      int apply(MethodNode var1);
   }

   interface InsnFactory {
      InsnList create();
   }

   public static final class MethodEdit {
      public final String name;
      public final String desc;
      public final String label;
      public final int expected;
      final SlinkyPatches.Edit edit;

      MethodEdit(String name, String desc, String label, int expected, SlinkyPatches.Edit edit) {
         this.name = name;
         this.desc = desc;
         this.label = label;
         this.expected = expected;
         this.edit = edit;
      }

      public int apply(MethodNode method) {
         return this.edit.apply(method);
      }

      public String describe() {
         return this.name + this.desc + " " + this.label;
      }
   }
}
