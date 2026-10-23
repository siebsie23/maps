package dev.cerus.maps.version;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.cerus.maps.api.ClientsideMap;
import dev.cerus.maps.api.Frame;
import dev.cerus.maps.api.version.PacketListener;
import dev.cerus.maps.api.version.VersionAdapter;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class VersionAdapter26_3 implements VersionAdapter {

    private Field connectionField;

    @Override
    public void spawnBarrierParticle(final Player player, final Location loc) {
        player.spawnParticle(Particle.BLOCK_MARKER, loc, 1, Material.BARRIER.createBlockData());
    }

    @Override
    public Object makeMapPacket(final boolean ignoreBounds, final ClientsideMap map) {
        final int x = ignoreBounds ? 0 : map.getX();
        final int y = ignoreBounds ? 0 : map.getY();
        final int w = ignoreBounds ? 128 : Math.max(1, map.getWidth());
        final int h = ignoreBounds ? 128 : Math.max(1, map.getHeight());

        final byte[] data;
        if (ignoreBounds) {
            data = map.getData();
        } else {
            data = new byte[w * h];
            for (int xx = 0; xx < w; ++xx) {
                for (int yy = 0; yy < h; ++yy) {
                    data[xx + yy * w] = map.getData()[x + xx + (y + yy) * 128];
                }
            }
        }

        return new ClientboundMapItemDataPacket(
                new MapId(map.getId()),
                (byte) 0,
                true,
                map.getMarkers().stream()
                        .map(cursor -> new MapDecoration(
                                BuiltInRegistries.MAP_DECORATION_TYPE.get(cursor.getType()).get(),
                                cursor.getCompressedX(),
                                cursor.getCompressedY(),
                                cursor.getDirection(),
                                !cursor.hasCaption() ? Optional.empty() : parse(cursor.getCaptionString())
                        ))
                        .collect(Collectors.toList()),
                new MapItemSavedData.MapPatch(
                        x,
                        y,
                        w,
                        h,
                        data
                )
        );
    }

    private Optional<Component> parse(String s) {
        if (s == null) {
            return Optional.empty();
        }
        JsonElement element = JsonParser.parseString(s);
        if (element == null) {
            return Optional.empty();
        }
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result();
    }

    @Override
    public Object makeFramePacket(final int frameId, final boolean visible, final ClientsideMap map) {
        final org.bukkit.inventory.ItemStack mapItem = new org.bukkit.inventory.ItemStack(Material.FILLED_MAP, 1);
        final MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        mapMeta.setMapId(map.getId());
        mapItem.setItemMeta(mapMeta);

        final List<SynchedEntityData.DataValue<?>> dwItems = Arrays.asList(
                new SynchedEntityData.DataValue<>(9, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(mapItem)),
                new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) (visible ? 0 : 0x20))
        );
        return new ClientboundSetEntityDataPacket(frameId, dwItems);
    }

    @Override
    public Object makeFrameSpawnPacket(final Frame frame) {
        return new ClientboundAddEntityPacket(
                frame.getEntityId(),
                UUID.randomUUID(),
                frame.getPosX(),
                frame.getPosY(),
                frame.getPosZ(),
                frame.getFacing() == BlockFace.DOWN ? 90 : frame.getFacing() == BlockFace.UP ? -90 : 0,
                switch (frame.getFacing()) {
                    case NORTH -> -180;
                    case EAST -> -90;
                    case WEST -> 90;
                    default -> 0;
                },
                frame.isGlowing() ? EntityTypes.GLOW_ITEM_FRAME : EntityTypes.ITEM_FRAME,
                switch (frame.getFacing()) {
                    case UP -> 1;
                    case NORTH -> 2;
                    case SOUTH -> 3;
                    case WEST -> 4;
                    case EAST -> 5;
                    default -> 0;
                },
                new Vec3(0, 0, 0),
                switch (frame.getFacing()) {
                    case NORTH -> -180;
                    case EAST -> -90;
                    case WEST -> 90;
                    default -> 0;
                }
        );
    }

    @Override
    public Object makeFrameDespawnPacket(final Frame frame) {
        return new ClientboundRemoveEntitiesPacket(frame.getEntityId());
    }

    @Override
    public void sendPacket(final Player player, final Object packet) {
        ((CraftPlayer) player).getHandle().connection.send((Packet<?>) packet);
    }

    @Override
    public void inject(final Player player, final PacketListener listener, final JavaPlugin plugin) {
        final Connection connection;
        try {
            connection = this.getConnection(((CraftPlayer) player).getHandle().connection);
        } catch (final IllegalAccessException | NoSuchFieldException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to inject packet handler into player %s".formatted(player.getName()), e);
            return;
        }
        connection.channel.pipeline().addBefore("packet_handler", "maps_listener", new PacketHandler26_3(player, listener, plugin));
    }

    private Connection getConnection(final ServerGamePacketListenerImpl listener) throws IllegalAccessException, NoSuchFieldException {
        if (this.connectionField == null) {
            this.connectionField = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
            this.connectionField.setAccessible(true);
        }
        return (Connection) this.connectionField.get(listener);
    }

}
