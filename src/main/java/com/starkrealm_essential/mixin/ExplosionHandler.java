package com.starkrealm_essential.mixin;

import com.starkrealm_essential.RegionProtectionCommand;
import net.minecraft.world.explosion.ExplosionBehavior; // Use the concrete implementation
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

@Mixin(ExplosionBehavior.class) // Target the concrete implementation
public class ExplosionHandler {
}