package mrtjp.projectred.fabrication

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.packet.PacketCustom
import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.ProjectRedCore.log
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.world.World

import scala.collection.mutable
import scala.collection.mutable.Map as MMap

trait IICTileEditorNetwork
{
    def getIC:ICTileMapEditor
    def getEditorWorld:World

    def getICStreamOf(key:Int):MCDataOutput
    def getTileStream(pos:Point):MCDataOutput

    def isRemote:Boolean
    def markSave(): Unit 
}

trait TICTileEditorNetwork extends IICTileEditorNetwork
{
    private var editorStream:PacketCustom = null
    private var tileStream:PacketCustom = null

    def createTileStream():PacketCustom
    def sendTileStream(out:PacketCustom): Unit 
    override def getTileStream(pos:Point):MCDataOutput =
    {
        if tileStream == null then tileStream = createTileStream()

        val tile = getIC.getTile(pos)
        tileStream.writeByte(tile.id)
        tileStream.writeByte(pos.x).writeByte(pos.y)

        tileStream
    }
    def flushTileStream(): Unit =
    {
        if tileStream != null then {
            tileStream.writeByte(255)//terminator
            sendTileStream(tileStream.compress())
            tileStream = null
        }
    }
    def readTileStream(in:MCDataInput): Unit =
    {
        try {
            var id = in.readUByte()
            while id != 255 do {
                val p = Point(in.readUByte(), in.readUByte())
                var tile = getIC.getTile(p)
                if tile == null || tile.id != id then {
                    log.error("client tile stream couldnt find tile "+p)
                    tile = ICTile.createTile(id)
                }
                tile.read(in)
                id = in.readUByte()
            }
        }
        catch {
            case ex:IndexOutOfBoundsException =>
                log.error("tile stream failed to be read.")
                ex.printStackTrace()
        }
    }

    def createEditorStream():PacketCustom
    def sendEditorStream(out:PacketCustom): Unit 

    override def getICStreamOf(key:Int):MCDataOutput =
    {
        if editorStream == null then editorStream = createEditorStream()
        editorStream.writeByte(key)
        editorStream
    }
    def flushICStream(): Unit =
    {
        if editorStream != null then {
            editorStream.writeByte(255) //terminator
            sendEditorStream(editorStream.compress())
            editorStream = null
        }
    }
    def readICStream(in:MCDataInput): Unit =
    {
        try {
            var id = in.readUByte()
            while id != 255 do {
                getIC.read(in, id)
                id = in.readUByte()
            }
        } catch {
            case ex:IndexOutOfBoundsException =>
                log.error("Tile Map stream failed to be read")
        }
    }
}

class ICTileMapContainer extends ISETileMap
{
    override val tiles: mutable.Map[(Int, Int), ICTile] = mutable.Map[(Int, Int), ICTile]()

    var tilesLoadedDelegate = {() => ()}

    var name = "untitled"

    var size = Size.zeroSize

    def isEmpty = size == Size.zeroSize

    def nonEmpty = !isEmpty

    def assertCoords(x:Int, y:Int): Unit =
    {
        if !(0 until size.width contains x) || !(0 until size.height contains y) then
            throw new IndexOutOfBoundsException("Tile Map does not contain "+Point(x, y))
    }

    def saveTiles(tag:NBTTagCompound): Unit =
    {
        tag.setString("name", name)
        tag.setByte("sw", size.width.toByte)
        tag.setByte("sh", size.height.toByte)

        val tagList = new NBTTagList
        for tile <- tiles.values do {
            val tileTag = new NBTTagCompound
            tileTag.setByte("id", tile.id.toByte)
            tileTag.setByte("xpos", tile.pos.x.toByte)
            tileTag.setByte("ypos", tile.pos.y.toByte)
            tile.save(tileTag)
            tagList.appendTag(tileTag)
        }
        tag.setTag("tiles", tagList)
    }

    def loadTiles(tag:NBTTagCompound): Unit =
    {
        name = tag.getString("name")
        size = Size(tag.getByte("sw")&0xFF, tag.getByte("sh")&0xFF)

        val tileList = tag.getTagList("tiles", 10)
        for i <- 0 until tileList.tagCount do {
            val tileTag = tileList.getCompoundTagAt(i)
            val tile = ICTile.createTile(tileTag.getByte("id")&0xFF)
            val x = tileTag.getByte("xpos")&0xFF
            val y = tileTag.getByte("ypos")&0xFF
            tile.bindTileMap(this)
            tile.bindPos(Point(x, y))
            tiles += (x, y) -> tile
            tile.load(tileTag)
        }

        tilesLoadedDelegate()
    }

    def getTile(x:Int, y:Int):ICTile = tiles.getOrElse((x, y), null)

    def getTile(p:Point):ICTile = getTile(p.x, p.y)
}

class ICTileMapEditor(val network:IICTileEditorNetwork) extends IICSimEngineContainerDelegate
{
    val tileMapContainer = new ICTileMapContainer

    var simEngineContainer = new ICSimEngineContainer
    var simNeedsRefresh = true

    var worldTimeOffset = -1L //number of ticks that the simulation is behind the total world time
    var lastWorldTime = -1L

    private var scheduledTicks = MMap[Point, Long]()

    tileMapContainer.tilesLoadedDelegate = {() =>
        simNeedsRefresh = true
        for tile <- tileMapContainer.tiles.values do
            tile.bindEditor(this)
    }

    def size = tileMapContainer.size
    def name = tileMapContainer.name

    def save(tag:NBTTagCompound): Unit =
    {
        tileMapContainer.saveTiles(tag)
        simEngineContainer.saveSimState(tag)
    }

    def load(tag:NBTTagCompound): Unit =
    {
        clear()
        tileMapContainer.loadTiles(tag)

        recompileSchematic()
        simEngineContainer.loadSimState(tag)
    }

    def writeDesc(out:MCDataOutput): Unit =
    {
        out.writeString(tileMapContainer.name)
        out.writeByte(tileMapContainer.size.width).writeByte(tileMapContainer.size.height)
        for i <- 0 until 4 do out.writeInt(simEngineContainer.iostate(i))
        simEngineContainer.logger.writeLog(out)

        for ((x, y), tile) <- tileMapContainer.tiles do {
            out.writeByte(tile.id)
            out.writeByte(x).writeByte(y)
            tile.writeDesc(out)
        }
        out.writeByte(255)
    }

    def readDesc(in:MCDataInput): Unit =
    {
        clear()
        tileMapContainer.name = in.readString()
        tileMapContainer.size = Size(in.readUByte(), in.readUByte())
        for i <- 0 until 4 do simEngineContainer.iostate(i) = in.readInt()
        simEngineContainer.logger.readLog(in)

        var id = in.readUByte()
        while id != 255 do {
            val tile = ICTile.createTile(id)
            setTile_do(Point(in.readUByte(), in.readUByte()), tile)
            tile.readDesc(in)
            id = in.readUByte()
        }
    }

    def read(in:MCDataInput, key:Int) = key match
    {
        case 0 => readDesc(in)
        case 1 =>
            val tile = ICTile.createTile(in.readUByte())
            setTile_do(Point(in.readUByte(), in.readUByte()), tile)
            tile.readDesc(in)
        case 2 => removeTile_do(Point(in.readUByte(), in.readUByte()))
        case 3 => TileEditorOp.getOperation(in.readUByte()).readOp(this, in)
        case 4 => getTile(Point(in.readUByte(), in.readUByte())) match {
            case g:TClientNetICTile => g.readClientPacket(in)
            case _ => log.error("Server IC stream received invalid client packet")
        }
        case 5 =>
            for r <- 0 until 4 do
                simEngineContainer.iostate(r) = in.readInt()
        case 6 => simEngineContainer.setInput(in.readUByte(), in.readShort())//TODO remove? not used...
        case 7 => simEngineContainer.setOutput(in.readUByte(), in.readShort()) //TODO remove? not used...
        case 8 => simEngineContainer.logger.readLog(in)
        case 9 => worldTimeOffset = in.readLong()
        case _ =>
    }

    def sendTileAdded(tile:ICTile): Unit =
    {
        val out = network.getICStreamOf(1)
        out.writeByte(tile.id)
        out.writeByte(tile.pos.x).writeByte(tile.pos.y)
        tile.writeDesc(out)
    }

    def sendRemoveTile(pos:Point): Unit =
    {
        network.getICStreamOf(2).writeByte(pos.x).writeByte(pos.y)
    }

    def sendOpUse(op:TileEditorOp, start:Point, end:Point) =
    {
        if op.checkOp(this, start, end) then {
            op.writeOp(this, start, end, network.getICStreamOf(3).writeByte(op.id))
            true
        }
        else false
    }

    def sendClientPacket(tile:TClientNetICTile, writer:MCDataOutput => Unit): Unit =
    {
        val s = network.getICStreamOf(4).writeByte(tile.pos.x).writeByte(tile.pos.y)
        writer(s)
    }

    def sendIOUpdate(): Unit =
    {
        val stream = network.getICStreamOf(5)
            for r <- 0 until 4 do
                stream.writeInt(simEngineContainer.iostate(r))
    }

    def sendInputUpdate(r:Int): Unit = //TODO Remove?
    {
        network.getICStreamOf(6).writeByte(r).writeShort(simEngineContainer.iostate(r)&0xFFFF)
    }

    def sendOutputUpdate(r:Int): Unit = //TODO Remove?
    {
        network.getICStreamOf(7).writeByte(r).writeShort(simEngineContainer.iostate(r)>>>16)
    }

    def sendCompileLog(): Unit =
    {
        simEngineContainer.logger.writeLog(network.getICStreamOf(8))
    }

    def sendWorldTimeOffset(): Unit =
    {
        network.getICStreamOf(9).writeLong(worldTimeOffset)
    }

    def clear(): Unit =
    {
        tileMapContainer.tiles.values.foreach{_.unbind()}//remove references
        tileMapContainer.tiles.clear()
        scheduledTicks = MMap()
        tileMapContainer.name = "untitled"
        tileMapContainer.size = Size.zeroSize
        for i <- 0 until 4 do simEngineContainer.iostate(i) = 0
        simNeedsRefresh = true
    }

    def getTotalSimTimeClient = network.getEditorWorld.getTotalWorldTime-worldTimeOffset

    def isEmpty = tileMapContainer.isEmpty
    def nonEmpty = tileMapContainer.nonEmpty

    def tick(): Unit =
    {
        //Update tiles as needed
        val t = network.getEditorWorld.getTotalWorldTime
        var rem = Seq.newBuilder[Point]
        for (p, st) <- scheduledTicks do if st >= t then {
            getTile(p).scheduledTick()
            rem += p
        }
        rem.result().foreach(scheduledTicks.remove)

        //Tick tiles
        for tile <- tileMapContainer.tiles.values do tile.update()

        //Rebuild circuit if needed
        if simNeedsRefresh then {
            recompileSchematic()
            worldTimeOffset = network.getEditorWorld.getTotalWorldTime
            sendWorldTimeOffset()
        }

        //Tick Simulation time
        simEngineContainer.advanceTime(if lastWorldTime >= 0 then t-lastWorldTime else 1) //if first tick, advance 1 tick only
        simEngineContainer.repropagate()
        lastWorldTime = t
    }

    def setTile(pos:Point, tile:ICTile): Unit =
    {
        assert(!network.isRemote, "Tiles can only be added server-side")
        setTile_do(pos, tile)

        sendTileAdded(tile)
        network.markSave()
        markSchematicChanged()
    }

    private def setTile_do(pos:Point, tile:ICTile): Unit =
    {
        tileMapContainer.assertCoords(pos.x, pos.y)
        tile.bindPos(pos)
        tile.bindEditor(this)
        tileMapContainer.tiles += (pos.x, pos.y) -> tile
        tile.onAdded()
    }

    def getTile(pos:Point):ICTile = tileMapContainer.getTile(pos.x, pos.y)

    def removeTile(pos:Point): Unit =
    {
        assert(!network.isRemote, "Tiles can only be removed server-side")
        if removeTile_do(pos) then {
            sendRemoveTile(pos)
            network.markSave()
            markSchematicChanged()
        }
    }

    private def removeTile_do(pos:Point):Boolean =
    {
        tileMapContainer.assertCoords(pos.x, pos.y)
        val tile = getTile(pos)
        if tile == null then
            return false

        tileMapContainer.tiles.remove((pos.x, pos.y))
        tile.onRemoved()
        tile.unbind()
        true
    }

    def notifyNeighbor(pos:Point): Unit =
    {
        val tile = getTile(pos)
        if tile != null then tile.onNeighborChanged()
    }

    def notifyNeighbors(pos:Point, mask:Int): Unit =
    {
        for r <- 0 until 4 do if (mask&1<<r) != 0 then {
            val tile = getTile(pos.offset(r))
            if tile != null then tile.onNeighborChanged()
        }
    }

    def scheduleTick(pos:Point, ticks:Int): Unit ={scheduledTicks += pos -> (network.getEditorWorld.getTotalWorldTime+ticks)}

    def markSchematicChanged(): Unit =
    {
        simNeedsRefresh = true
    }

    def recompileSchematic(): Unit =
    {
        simNeedsRefresh = false
        simEngineContainer.delegate = this
        simEngineContainer.recompileSimulation(tileMapContainer)
    }

    override def registersDidChange(registers:Set[Int]): Unit =
    {
        for tile <- tileMapContainer.tiles.values do
            tile.onRegistersChanged(registers)
    }

    override def ioRegistersDidChange(): Unit =
    {
        sendIOUpdate()
    }

    override def logDidChange(): Unit =
    {
        sendCompileLog()
    }
}