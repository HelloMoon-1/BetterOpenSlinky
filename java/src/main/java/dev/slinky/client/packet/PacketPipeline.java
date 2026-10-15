package dev.slinky.client.packet;

import dev.slinky.client.SemanticEvent;
import dev.slinky.module.ModuleManager;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.Packet;

public final class PacketPipeline extends ChannelDuplexHandler implements Runnable {
   private static final AtomicInteger COUNTER = new AtomicInteger();
   private static final Set<PacketPipeline> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());
   public static final String NAME = "slinky_semantic_packets";
   private final ArrayDeque<PacketPipeline.Entry> inbound = new ArrayDeque<>();
   private final ArrayDeque<PacketPipeline.Entry> outbound = new ArrayDeque<>();
   private final PacketPipeline.Clock clock;
   private final PacketPipeline.Dispatch dispatch;
   private final boolean automaticPolling;
   private Future<?> polling;
   private ChannelHandlerContext owner;

   public PacketPipeline(final ModuleManager modules) {
      this(new PacketPipeline.Clock() {
         @Override
         public long millis() {
            return System.currentTimeMillis();
         }
      }, new PacketPipeline.Dispatch() {
         @Override
         public int classify(Object packet, boolean outbound, long age, int sequence) {
            if (!(packet instanceof Packet)) {
               return 0;
            } else {
               SemanticEvent e = new SemanticEvent(outbound ? 3 : 2);
               e.packet = (Packet<?>)packet;
               e.packetAgeMillis = age;
               e.packetSequence = sequence;
               modules.dispatch(e);
               return e.dispatchMode;
            }
         }
      }, true);
   }

   public PacketPipeline(PacketPipeline.Clock clock, PacketPipeline.Dispatch dispatch) {
      this(clock, dispatch, false);
   }

   private PacketPipeline(PacketPipeline.Clock clock, PacketPipeline.Dispatch dispatch, boolean automaticPolling) {
      this.clock = clock;
      this.dispatch = dispatch;
      this.automaticPolling = automaticPolling;
   }

   public void handlerAdded(ChannelHandlerContext context) {
      this.owner = context;
      ACTIVE.add(this);
      if (this.automaticPolling) {
         this.polling = context.executor().scheduleWithFixedDelay(this, 4L, 4L, TimeUnit.MILLISECONDS);
      }
   }

   public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) {
      this.accept(context, packet, promise, true);
   }

   public void channelRead(ChannelHandlerContext context, Object packet) {
      this.accept(context, packet, null, false);
   }

   private void accept(ChannelHandlerContext context, Object packet, ChannelPromise promise, boolean out) {
      int sequence = COUNTER.getAndIncrement();
      int mode = this.dispatch.classify(packet, out, 0L, sequence);
      if (mode == 2) {
         discard(packet, promise);
      } else {
         ArrayDeque<PacketPipeline.Entry> queue = out ? this.outbound : this.inbound;
         if (mode != 1 && (mode != 0 || queue.isEmpty())) {
            if (out) {
               context.write(packet, promise);
            } else {
               context.fireChannelRead(packet);
            }
         } else {
            queue.add(new PacketPipeline.Entry(context, packet, promise, this.clock.millis(), sequence));
         }
      }
   }

   @Override
   public void run() {
      long now = this.clock.millis();
      this.poll(this.inbound, false, now);
      this.poll(this.outbound, true, now);
   }

   private void poll(ArrayDeque<PacketPipeline.Entry> queue, boolean out, long now) {
      ChannelHandlerContext flushed = null;

      while (!queue.isEmpty()) {
         PacketPipeline.Entry entry = queue.peek();
         int mode = this.dispatch.classify(entry.packet, out, now - entry.added, entry.sequence);
         if (mode == 1) {
            break;
         }

         if (mode == 2) {
            queue.removeFirst();
            discard(entry.packet, entry.promise);
            return;
         }

         if (out) {
            entry.context.write(entry.packet, entry.promise);
            flushed = entry.context;
         } else {
            entry.context.fireChannelRead(entry.packet);
         }

         queue.removeFirst();
      }

      if (flushed != null) {
         flushed.flush();
      }
   }

   private static void discard(Object packet, ChannelPromise promise) {
      ReferenceCountUtil.release(packet);
   }

   public void handlerRemoved(ChannelHandlerContext context) {
      ACTIVE.remove(this);
      if (this.polling != null) {
         this.polling.cancel(false);
         this.polling = null;
      }

      this.drain(context, this.inbound, false);
      this.drain(context, this.outbound, true);
      context.flush();
      this.owner = null;
   }

   private void drain(ChannelHandlerContext context, ArrayDeque<PacketPipeline.Entry> queue, boolean out) {
      while (!queue.isEmpty()) {
         PacketPipeline.Entry entry = queue.removeFirst();
         if (!context.channel().isOpen()) {
            ReferenceCountUtil.release(entry.packet);
            if (entry.promise != null) {
               entry.promise.tryFailure(new ClosedChannelException());
            }
         } else if (out) {
            context.write(entry.packet, entry.promise);
         } else {
            context.fireChannelRead(entry.packet);
         }
      }
   }

   public void channelInactive(ChannelHandlerContext context) throws Exception {
      ACTIVE.remove(this);
      if (this.polling != null) {
         this.polling.cancel(false);
         this.polling = null;
      }

      this.drain(context, this.inbound, false);
      this.drain(context, this.outbound, true);
      super.channelInactive(context);
   }

   public static void releaseAll(final boolean removeHandler) {
      for (final PacketPipeline handler : ACTIVE) {
         final ChannelHandlerContext context = handler.owner;
         if (context != null) {
            context.executor().execute(new Runnable() {
               @Override
               public void run() {
                  if (removeHandler) {
                     if (context.pipeline().context(handler) != null) {
                        context.pipeline().remove(handler);
                     }
                  } else {
                     handler.drain(context, handler.inbound, false);
                     handler.drain(context, handler.outbound, true);
                     context.flush();
                  }
               }
            });
         }
      }
   }

   public int retained(boolean out) {
      return (out ? this.outbound : this.inbound).size();
   }

   public interface Clock {
      long millis();
   }

   public interface Dispatch {
      int classify(Object var1, boolean var2, long var3, int var5);
   }

   private static final class Entry {
      final ChannelHandlerContext context;
      final Object packet;
      final ChannelPromise promise;
      final long added;
      final int sequence;

      Entry(ChannelHandlerContext context, Object packet, ChannelPromise promise, long added, int sequence) {
         this.context = context;
         this.packet = packet;
         this.promise = promise;
         this.added = added;
         this.sequence = sequence;
      }
   }
}
