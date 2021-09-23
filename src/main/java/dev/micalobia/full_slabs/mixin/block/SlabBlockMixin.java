package dev.micalobia.full_slabs.mixin.block;

import dev.micalobia.full_slabs.FullSlabsMod;
import dev.micalobia.full_slabs.util.Utility;
import dev.micalobia.full_slabs.util.Utility.HitPart;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlabBlock.class)
public abstract class SlabBlockMixin extends Block implements Waterloggable {
	private static final EnumProperty<Axis> AXIS;

	@Shadow
	@Final
	public static EnumProperty<SlabType> TYPE;

	@Shadow
	@Final
	public static BooleanProperty WATERLOGGED;

	@Shadow
	@Final
	protected static VoxelShape TOP_SHAPE;

	@Shadow
	@Final
	protected static VoxelShape BOTTOM_SHAPE;

	static {
		AXIS = Properties.AXIS;
	}

	public SlabBlockMixin(Settings settings) {
		super(settings);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void includeAxisInDefaultState(Settings settings, CallbackInfo ci) {
		setDefaultState(getDefaultState().with(AXIS, Axis.Y));
	}

	@Inject(method = "appendProperties", at = @At("HEAD"))
	private void appendAxis(Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(AXIS);
	}

	@Override
	@Environment(EnvType.CLIENT)
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		SlabType type = state.get(TYPE);
		Direction direction;
		if(type == SlabType.DOUBLE) {
			Axis axis = state.get(AXIS);
			HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
			if(hitResult == null) return VoxelShapes.fullCube();
			direction = Utility.getDirection(axis, hitResult.getPos(), pos);
		} else direction = Utility.getDirection(state.get(TYPE), state.get(AXIS));
		return switch(direction) {
			case NORTH -> Utility.NORTH_OUTLINE_SHAPE;
			case EAST -> Utility.EAST_OUTLINE_SHAPE;
			case SOUTH -> Utility.SOUTH_OUTLINE_SHAPE;
			case WEST -> Utility.WEST_OUTLINE_SHAPE;
			case UP -> Utility.TOP_OUTLINE_SHAPE;
			case DOWN -> Utility.BOTTOM_OUTLINE_SHAPE;
		};
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		SlabType type = state.get(TYPE);
		if(type == SlabType.DOUBLE) return VoxelShapes.fullCube();
		Axis axis = state.get(AXIS);
		return switch(Utility.getDirection(type, axis)) {
			case UP -> Utility.TOP_COLLISION_SHAPE;
			case DOWN -> Utility.BOTTOM_COLLISION_SHAPE;
			case NORTH -> Utility.NORTH_COLLISION_SHAPE;
			case EAST -> Utility.EAST_COLLISION_SHAPE;
			case SOUTH -> Utility.SOUTH_COLLISION_SHAPE;
			case WEST -> Utility.WEST_COLLISION_SHAPE;
		};
	}

	@Redirect(method = "canReplace", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"))
	private boolean changeCanReplaceCondition(ItemStack stack, Item item) {
		Item stackItem = stack.getItem();
		if(!(stackItem instanceof BlockItem blockItem)) return false;
		return blockItem.getBlock() instanceof SlabBlock;
	}

	@Inject(method = "canReplace", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemPlacementContext;getHitPos()Lnet/minecraft/util/math/Vec3d;"), cancellable = true)
	private void changeCanReplaceMath(BlockState state, ItemPlacementContext context, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(Utility.insideSlab(state.getBlock(), context.getHitPos()));
	}

	@Inject(method = "getPlacementState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;"), cancellable = true)
	private void changePlacementRules(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
		BlockPos pos = ctx.getBlockPos();
		BlockState state = ctx.getWorld().getBlockState(pos);
		if(state.getBlock() instanceof SlabBlock) {
			Axis axis = state.get(AXIS);
			cir.setReturnValue(FullSlabsMod.FULL_SLAB_BLOCK.getDefaultState().with(AXIS, axis));
		} else {
			Direction hitSide = ctx.getSide();
			Direction facing = ctx.getPlayerFacing();
			HitPart hitPart = Utility.getHitPart(hitSide, facing, pos, ctx.getHitPos());
			FluidState fluidState = ctx.getWorld().getFluidState(pos);
			Direction slabDir = Utility.generateSlab(hitPart, hitSide, facing);
			cir.setReturnValue(getDefaultState()
					.with(TYPE, Utility.slabType(slabDir))
					.with(AXIS, slabDir.getAxis())
					.with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER));
		}
	}

	@Inject(method = "canReplace", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemPlacementContext;getHitPos()Lnet/minecraft/util/math/Vec3d;"), cancellable = true)
	private void changeReplacementRules(BlockState state, ItemPlacementContext context, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(Utility.insideSlab(state.getBlock(), context.getHitPos()));
	}
}
