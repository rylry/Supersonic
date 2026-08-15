package dev.rylry.supersonic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.sugar.Local;

import dev.rylry.supersonic.chunk.SupersonicChunkConfig;
import dev.rylry.supersonic.movement.SupersonicMovement;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
  // Removes the "moved too quickly" behavior when players meet certain conditions
  // so players can go as fast as they want
  @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"), require = 1)
  private boolean supersonic$disableMovedTooQuickly(
      ServerGamePacketListenerImpl instance,
      Operation<Boolean> original,

      @Local(ordinal = 0) ServerLevel level,
      @Local(ordinal = 0) double clientX,
      @Local(ordinal = 1) double clientY,
      @Local(ordinal = 2) double clientZ,
      @Local(ordinal = 3) double serverX,
      @Local(ordinal = 4) double serverY,
      @Local(ordinal = 5) double serverZ) {
    Vec3 start = new Vec3(serverX, serverY, serverZ);
    Vec3 end = new Vec3(clientX, clientY, clientZ);
    double horizontalDistance = Math.hypot(end.x - start.x, end.z - start.z);
    if (horizontalDistance >= SupersonicChunkConfig.get().directMovementMinSpeed()
        && SupersonicMovement.canUseDirectMovement(level, start, end)) {
      return true;
    } else {
      return original.call(instance);
    }
  }

  // The vanilla movement code allows a maximum of 5 move packets to be sent in a
  // single server tick, which doesn't work out super well with high pings. This
  // changes the maximum to 30. There is also a check verifying the total number
  // of packets makes sense.
  @ModifyConstant(method = "handleMovePlayer", constant = @Constant(intValue = 5))
  private int supersonic$increasePacketLimit(int original) {
    return SupersonicChunkConfig.get().movementPacketLimit();
  }
}
