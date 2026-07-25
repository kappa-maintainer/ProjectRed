/*
 * Copyright (c) 2014.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.relocation

import java.util.{List as JList}

import codechicken.lib.vec.{Cuboid6, Vector3}
import net.minecraft.client.particle.ParticleManager
import net.minecraft.util.math.{BlockPos, RayTraceResult}
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import mrtjp.core.block.{MTBlockTile, MultiTileBlock}
import mrtjp.projectred.ProjectRedRelocation
import net.minecraft.block.SoundType
import net.minecraft.block.material.Material
import net.minecraft.block.state.{BlockFaceShape, IBlockState}
import net.minecraft.entity.{Entity, MoverType}
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.world.World

import scala.jdk.CollectionConverters.*

class BlockMovingRow extends MultiTileBlock(Material.ROCK)
:
    setBlockUnbreakable()
    setSoundType(SoundType.STONE)
    setCreativeTab(null)

    override def getRenderType(state:IBlockState):EnumBlockRenderType = EnumBlockRenderType.INVISIBLE

    @SideOnly(Side.CLIENT)
    override def addHitEffects(state:IBlockState, world:World, target:RayTraceResult, manager:ParticleManager):Boolean = true

    @SideOnly(Side.CLIENT)
    override def addDestroyEffects(world:World, pos:BlockPos, manager:ParticleManager):Boolean = true

object TileMovingRow
:
    private var isCalculatingBB = false

    def setBlockForRow(w:World, r:BlockRow): Unit =
        w.setBlockState(r.pos, ProjectRedRelocation.blockMovingRow.getDefaultState, 0)

    def getBoxFor(w:World, r:BlockRow, progress:Double):Cuboid6 =
        if isCalculatingBB then
            return Cuboid6.full.copy()

        val p = r.pos.offset(r.moveDir.getOpposite)
        val bl = w.getBlockState(p)

        isCalculatingBB = true
        val box = bl.getCollisionBoundingBox(w, p) match
            case aabb:AxisAlignedBB => new Cuboid6(aabb).subtract(Vector3.fromBlockPos(r.pos))
                    .add(Vector3.fromVec3i(r.moveDir.getDirectionVec).multiply(progress))
            case null => Cuboid6.full.copy
        isCalculatingBB = false

        box

class TileMovingRow extends MTBlockTile
{
    var prevProg = 0.0

    override def updateServer():Unit =
        if !MovementManager.isMoving(world, pos) then world.setBlockToAir(pos)

    override def getBlock:BlockMovingRow = ProjectRedRelocation.blockMovingRow

    override def getBlockBounds:Cuboid6 =
        val s = MovementManager.getEnclosedStructure(world, pos)
        if s != null then
            val r = s.rows.find(_.contains(pos)).get
            TileMovingRow.getBoxFor(world, r, s.progress)
        else Cuboid6.full

    override def getCollisionBounds:Cuboid6 = getBlockBounds

    override def getBlockFaceShape(side:Int) = BlockFaceShape.UNDEFINED

    def pushEntities(r:BlockRow, progress:Double): Unit =
        val box = Cuboid6.full.copy.add(Vector3.fromBlockPos(r.preMoveBlocks.head))
                .add(Vector3.fromVec3i(r.moveDir.getDirectionVec).multiply(progress))
        val boxBounds = box.aabb()

        val dp = (if progress >= 1.0 then progress + 0.1 else progress) - prevProg
        val d = Vector3.fromVec3i(r.moveDir.getDirectionVec).multiply(dp)
        world.getEntitiesWithinAABBExcludingEntity(null, boxBounds) match
            case list:JList[_] =>
                for e <- list.asScala do
                    e.move(MoverType.PISTON, d.x, d.y*4 max 0, d.z) //TODO find better way to do this
            case null =>

        prevProg = progress
}