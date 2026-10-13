/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.block.property.unlisted.UnlistedIntegerProperty
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.model.bakery.generation.IBakery
import codechicken.lib.model.bakery.{IBakeryProvider, ModelBakery, SimpleBlockRenderer}
import codechicken.lib.render.buffer.BakingVertexBuffer
import codechicken.lib.render.particle.CustomParticleHandler
import codechicken.lib.render.{CCModel, CCRenderState}
import codechicken.lib.util.VertexDataUtils
import codechicken.lib.vec.uv.{MultiIconTransformation, UVTransformation}
import codechicken.lib.vec.{Cuboid6, Rotation, Vector3}
import com.google.common.collect.ImmutableList
import mrtjp.core.block.*
import mrtjp.projectred.ProjectRedFabrication
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockStateContainer.Builder
import net.minecraft.block.state.{BlockFaceShape, BlockStateContainer, IBlockState}
import net.minecraft.client.particle.ParticleManager
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.{BlockPos, RayTraceResult}
import net.minecraft.util.{EnumFacing, ResourceLocation}
import net.minecraft.world.{IBlockAccess, World}
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import java.util.{List as JList}

/**
  * Shell-only ports of the high-version Plotting/Lithography/Packaging Tables.
  * Registration, tile entity, rotation, and rendering only -- no crafting,
  * power, or GUI logic yet. See MIGRATION.md.
  *
  * These are "domed machines": a 10/16-tall box, not a full cube. The side
  * textures carry 6 rows of transparent padding at the top, so they must be
  * UV-cropped to v in [6,16] -- exactly what the high version does with
  * `"uv": [0, 6, 16, 16]` on a 10/16-tall model.
  */
class BlockFabricationMachine(bakery:IBakery) extends MultiTileBlock(Material.ROCK) with IBakeryProvider
:
    setHardness(2)
    setCreativeTab(ProjectRedFabrication.tabFabrication)

    override def createBlockState(): BlockStateContainer = new Builder(this).add(MultiTileBlock.TILE_INDEX)
            .add(BlockFabricationMachine.UNLISTED_ROTATION_PROPERTY)
            .build()

    override def getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos) = ModelBakery.handleExtendedState(state.asInstanceOf[IExtendedBlockState], world, pos)

    override def getBakery:IBakery = bakery

    @SideOnly(Side.CLIENT)
    override def addHitEffects(state:IBlockState, world:World, target:RayTraceResult, manager:ParticleManager):Boolean =
        CustomParticleHandler.handleHitEffects(state, world, target, manager)

    @SideOnly(Side.CLIENT)
    override def addDestroyEffects(world:World, pos:BlockPos, manager:ParticleManager):Boolean =
        CustomParticleHandler.handleDestroyEffects(world, pos, manager)

object BlockFabricationMachine
:
    val UNLISTED_ROTATION_PROPERTY = new UnlistedIntegerProperty("rotation")

abstract class TileFabricationMachine extends MTBlockTile with TTileOrient
:
    override def getBlock = ProjectRedFabrication.fabricationMachineBlock

    override def onBlockPlaced(side:Int, player:EntityPlayer, stack:ItemStack): Unit =
        setSide(0)
        setRotation((Rotation.getSidedRotation(player, side)+2)%4)

    override def writeDesc(out:MCDataOutput): Unit =
        super.writeDesc(out)
        out.writeByte(orientation)

    override def readDesc(in:MCDataInput): Unit =
        super.readDesc(in)
        orientation = in.readByte

    override def save(tag:NBTTagCompound): Unit =
        tag.setByte("rot", orientation)

    override def load(tag:NBTTagCompound): Unit =
        orientation = tag.getByte("rot")

    //Not a full cube, so no face is solid. Same treatment as TileICPrinter.
    override def getBlockFaceShape(side:Int): BlockFaceShape = BlockFaceShape.UNDEFINED

class TilePlottingTable extends TileFabricationMachine

class TileLithographyTable extends TileFabricationMachine

class TilePackagingTable extends TileFabricationMachine

class RenderFabricationTable(spriteFolder:String) extends SimpleBlockRenderer
:
    import java.lang.{Integer as JInt}

    import org.apache.commons.lang3.tuple.Triple

    /**
      * A 10/16-tall box, pre-rotated for each of the 4 rotations.
      * `generateBlock` uv-maps "the same as a minecraft block with the same
      * bounds", so the side faces automatically land on v in [1-10/16, 1] =
      * sprite rows 6..16, and top/bottom on the full sprite. That reproduces
      * the high-version `domed_machine` UV windows without hard-coding them.
      */
    val lowerBoxes =
        val array = new Array[CCModel](4)
        val box = CCModel.quadModel(24).generateBlock(0, new Cuboid6(0, 0, 0, 1, 10/16D, 1))
        for r <- 0 until 4 do
            val m = box.copy.apply(Rotation.quarterRotations(r).at(Vector3.center))
            m.computeNormals()
            m.shrinkUVs(0.0005)
            array(r) = m
        array

    var bottom:TextureAtlasSprite = scala.compiletime.uninitialized
    var top:TextureAtlasSprite = scala.compiletime.uninitialized
    var side:TextureAtlasSprite = scala.compiletime.uninitialized
    var front:TextureAtlasSprite = scala.compiletime.uninitialized

    var iconT:UVTransformation = scala.compiletime.uninitialized

    override def handleState(state:IExtendedBlockState, world:IBlockAccess, pos:BlockPos):IExtendedBlockState = world.getTileEntity(pos) match
        case t:TileFabricationMachine =>
            state.withProperty(BlockFabricationMachine.UNLISTED_ROTATION_PROPERTY, t.rotation.asInstanceOf[JInt])
        case _ => state

    override def getWorldTransforms(state:IExtendedBlockState) =
        val rot = state.getValue(BlockFabricationMachine.UNLISTED_ROTATION_PROPERTY)
        Triple.of(0, rot, iconT)

    override def getItemTransforms(stack:ItemStack) = Triple.of(0, 0, iconT)

    override def shouldCull() = false

    override def getParticleTexture(state:IExtendedBlockState) = bottom

    override def bakeQuads(face:EnumFacing, state:IExtendedBlockState):JList[BakedQuad] =
        bake(face, getWorldTransforms(state))

    override def bakeItemQuads(face:EnumFacing, stack:ItemStack):JList[BakedQuad] =
        bake(face, getItemTransforms(stack))

    private def bake(face:EnumFacing, transforms:Triple[Integer, Integer, UVTransformation]):JList[BakedQuad] =
        val buffer = BakingVertexBuffer.create
        val ccrs = CCRenderState.instance

        ccrs.reset()
        ccrs.startDrawing(0x7, DefaultVertexFormats.ITEM, buffer)
        lowerBoxes(transforms.getMiddle).render(ccrs, transforms.getRight)
        buffer.finishDrawing()

        val quads = buffer.bake
        if face == null && !shouldCull() then quads
        else if face != null then VertexDataUtils.sortFaceData(quads).get(face)
        else ImmutableList.of()

    override def registerIcons(reg:TextureMap): Unit =
        def register(s:String) = reg.registerSprite(new ResourceLocation(s"projectred:blocks/fabrication/$spriteFolder/$s"))

        bottom = register("bottom")
        top = register("top")
        side = register("side")
        front = register("front")

        //tex order is (bottom, top, north, south, west, east). The front goes on
        //south so that it ends up facing the placing player, matching high version.
        iconT = new MultiIconTransformation(bottom, top, side, front, side, side)

object RenderPlottingTable extends RenderFabricationTable("plottingtable")

object RenderLithographyTable extends RenderFabricationTable("lithographytable")

object RenderPackagingTable extends RenderFabricationTable("packagingtable")
