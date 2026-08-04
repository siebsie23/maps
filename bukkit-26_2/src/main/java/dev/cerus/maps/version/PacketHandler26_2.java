package dev.cerus.maps.version;

import dev.cerus.maps.api.version.PacketListener;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PacketHandler26_2 extends ChannelDuplexHandler {

    private final Player player;
    private final PacketListener listener;
    private final JavaPlugin plugin;

    public PacketHandler26_2(final Player player, final PacketListener listener, final JavaPlugin plugin) {
        this.player = player;
        this.listener = listener;
        this.plugin = plugin;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if ((((msg instanceof final ServerboundUseItemOnPacket useItem && useItem.getHand() != InteractionHand.OFF_HAND) || msg instanceof ServerboundUseItemPacket) && this.listener.handlePlayerRightClick(this.player))
            || (msg instanceof final ServerboundInteractPacket interact && interact.hand() != InteractionHand.OFF_HAND && this.listener.handlePlayerRightClick(this.player))
            || (msg instanceof ServerboundPlayerActionPacket && this.listener.handlePlayerLeftClick(this.player))) {
            if (msg instanceof final ServerboundPlayerActionPacket dig) {
                // To prevent de-syncs we need to tell the client that the block has not changed
                final Location location = new Location(
                        this.player.getWorld(),
                        dig.getPos().getX(),
                        dig.getPos().getY(),
                        dig.getPos().getZ()
                );
                // If we don't acknowledge the client's block change it won't accept further block change packets
                ((CraftPlayer) this.player).getHandle().connection.send(new ClientboundBlockChangedAckPacket(dig.getSequence()));
                Bukkit.getScheduler().runTask(this.plugin, () -> this.player.sendBlockChange(location, location.getBlock().getBlockData()));
            }
            if (msg instanceof final ServerboundUseItemOnPacket useItem) {
                // To prevent de-syncs we need to tell the client that the block has not changed
                final BlockHitResult pos = useItem.getHitResult();
                if (pos != null && pos.getType() == HitResult.Type.BLOCK) {
                    final Location location = new Location(
                            this.player.getWorld(),
                            pos.getBlockPos().getX(),
                            pos.getBlockPos().getY(),
                            pos.getBlockPos().getZ()
                    ).getBlock().getRelative(CraftBlock.notchToBlockFace(pos.getDirection())).getLocation();
                    // If we don't acknowledge the client's block change it won't accept further block change packets
                    ((CraftPlayer) this.player).getHandle().connection.send(new ClientboundBlockChangedAckPacket(useItem.getSequence()));
                    Bukkit.getScheduler().runTask(this.plugin, () -> this.player.sendBlockChange(location, location.getBlock().getBlockData()));
                }
            }
            return;
        }
        super.channelRead(ctx, msg);
    }

}
