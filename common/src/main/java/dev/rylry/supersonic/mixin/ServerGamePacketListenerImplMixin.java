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

import dev.rylry.supersonic.movement.SupersonicMovement;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
  // Removes the "moved too quickly" check above highest block in a chunk so
  // players can go as fast as they want
  @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;shouldCheckPlayerMovement(Z)Z"), require = 1)
  private boolean supersonic$disableMovedTooQuickly(
      ServerGamePacketListenerImpl instance,
      boolean isFallFlying,
      Operation<Boolean> original,

      @Local(name = "level") ServerLevel level,
      @Local(name = "targetX") double clientX,
      @Local(name = "targetY") double clientY,
      @Local(name = "targetZ") double clientZ,
      @Local(name = "startX") double serverX,
      @Local(name = "startY") double serverY,
      @Local(name = "startZ") double serverZ) {
    if (SupersonicMovement.canUseDirectMovement(level, new Vec3(serverX, serverY, serverZ),
        new Vec3(clientX, clientY, clientZ))) {
      return false;
    } else {
      return original.call(instance, isFallFlying);
    }
  }

  // The vanilla movement code allows a maximum of 5 move packets to be sent in a
  // single server tick, which doesn't work out super well with high pings. This
  // changes the maximum to 30. There is also a check verifying the total number
  // of packets makes sense.
  @ModifyConstant(method = "handleMovePlayer", constant = @Constant(intValue = 5))
  private int supersonic$increasePacketLimit(int original) {
    return 30;
  }
}
