package mrtjp.projectred.transportation;

import java.util.Random;

import mrtjp.projectred.core.PRColors;
import mrtjp.projectred.core.fx.CoreParticle;
import mrtjp.projectred.core.fx.ParticleLogicApproachPoint;
import mrtjp.projectred.core.fx.ParticleLogicArcToEntity;
import mrtjp.projectred.core.fx.ParticleLogicFade;
import mrtjp.projectred.core.fx.ParticleLogicIconShift;
import mrtjp.projectred.core.fx.ParticleLogicOrbitPoint;
import mrtjp.projectred.core.fx.ParticleLogicScale;
import mrtjp.projectred.core.fx.ParticleLogicStatic;
import mrtjp.projectred.core.fx.ParticleLogicTrail;
import mrtjp.projectred.core.fx.ParticleLogicTrail.IParticleBuilder;
import mrtjp.projectred.core.fx.ParticleManagement;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.vec.BlockCoord;
import codechicken.lib.vec.Vector3;

public class RouteFX
{
    private static final Random rand = new Random();

    public static final int color_receive = PRColors.ORANGE.ordinal();
    public static final int color_send = PRColors.PURPLE.ordinal();

    public static final int color_relay = PRColors.CYAN.ordinal();
    public static final int color_routeLost = PRColors.MAGENTA.ordinal();

    public static final int color_route = PRColors.RED.ordinal();
    public static final int color_sync = PRColors.LIGHT_BLUE.ordinal();

    public static final int color_request = PRColors.PINK.ordinal();

    public static final int color_checkInv = PRColors.WHITE.ordinal();
    
    public static final int color_linked = PRColors.LIME.ordinal();
    public static final int color_unlinked = PRColors.RED.ordinal();
    public static final int color_rand = PRColors.LIGHT_GREY.ordinal();


    public static void spawnType1(int color, int count, BlockCoord bc, World world)
    {
        if (!world.isRemote)
        {
            packetType1(color, count, bc, world);
            return;
        }
        
        PRColors c = PRColors.get(color);
        for (int i = 0; i < count; i++)
            spawnType1(c, bc, world);
    }
    
    public static void spawnType2(int color, int count, int dir, BlockCoord bc, World world)
    {
        if (!world.isRemote)
        {
            packetType2(color, count, dir, bc, world);
            return;
        }
        
        PRColors c = PRColors.get(color);
        for (int i = 0; i < count; i++)
            spawnType2(c, dir, bc, world);

    }
    
    private static void spawnType2(final PRColors color, int dir, BlockCoord bc, World world)
    {
        if (!world.isRemote)
            return;

        double x = bc.x + 0.5;
        double y = bc.y + 0.5;
        double z = bc.z + 0.5;

        double x1 = bc.x + 0.5 + MathHelper.getRandomDoubleInRange(rand, -1/16d, 1/16d);
        double y1 = bc.y + 0.5 + MathHelper.getRandomDoubleInRange(rand, -1/16d, 1/16d);
        double z1 = bc.z + 0.5 + MathHelper.getRandomDoubleInRange(rand, -1/16d, 1/16d);
        
        double shift = MathHelper.getRandomDoubleInRange(rand, 0.8D, 1.0D);
        switch (dir) 
        {
            case 0: y1 = bc.y + 0.5 - shift; break;
            case 1: y1 = bc.y + 0.5 + shift; break;
            case 2: z1 = bc.z + 0.5 - shift; break;
            case 3: z1 = bc.z + 0.5 + shift; break;
            case 4: x1 = bc.x + 0.5 - shift; break;
            case 5: x1 = bc.x + 0.5 + shift; break;
        }

        CoreParticle c = ParticleManagement.instance.spawn(world, "box", x, y, z);
        if (c != null)
        {
            IParticleBuilder build = new IParticleBuilder() {
                
                @Override
                public void build(CoreParticle c2)
                {
                    ParticleLogicStatic stay = new ParticleLogicStatic();
                    
                    ParticleLogicIconShift iconshift = new ParticleLogicIconShift();
                    iconshift.addIcon("flutter1");
                    iconshift.addIcon("flutter2");
                    iconshift.addIcon("flutter3");
                    iconshift.addIcon("flutter4");
                    iconshift.setTicksBetweenChange(3);
                    
                    ParticleLogicScale scale = new ParticleLogicScale();
                    scale.setRate(-0.001F, -0.001F * rand.nextFloat());
                    scale.setTerminate(true);

                    c2.setIgnoreMaxAge(true);
                    c2.setScale(0.05f + 0.02f * rand.nextFloat());
                    c2.setPRColor(color);
                    
                    c2.addLogic(stay).addLogic(iconshift).addLogic(scale);
                }
            };
            
            ParticleLogicTrail trail = new ParticleLogicTrail("box", build);
            
            double speed = MathHelper.getRandomDoubleInRange(rand, 0.1D, 0.2D);
            ParticleLogicApproachPoint approach = new ParticleLogicApproachPoint(new Vector3(x1,y1,z1), speed, 0.1D);
            approach.setTerminate(true);
            
            ParticleLogicScale scale = new ParticleLogicScale();
            scale.setRate(-0.0001F, -0.0001F);
            scale.setTerminate(true);

            c.noClip = true;
            c.setIgnoreMaxAge(true);
            c.setScale(0.05f + 0.075f * rand.nextFloat());
            c.setPRColor(color);
            
            c.addLogic(trail).addLogic(scale).addLogic(approach);
        }
    }
    
    private static void spawnType1(PRColors color, BlockCoord bc, World world)
    {
        if (!world.isRemote)
            return;

        double x = bc.x + 0.5 + MathHelper.getRandomDoubleInRange(rand, -1 / 4d, 1 / 4d);
        double y = bc.y + 0.5 + MathHelper.getRandomDoubleInRange(rand, -1 / 8d, 1 / 8d);
        double z = bc.z + 0.5 + MathHelper.getRandomDoubleInRange(rand, -1 / 4d, 1 / 4d);

        double x1 = bc.x + 0.5;
        double y1 = bc.y + 0.5 + 1;
        double z1 = bc.z + 0.5;

        CoreParticle c = ParticleManagement.instance.spawn(world, "flutter1", x, y, z);
        if (c != null)
        {
            ParticleLogicOrbitPoint orbit = new ParticleLogicOrbitPoint(new Vector3(x1, y1, z1));
            orbit.setOrbitSpeed(0.5f * rand.nextDouble()).setTargetDistance(0.3D);
            orbit.setShrinkingOrbit(0.01, 0.01);

            ParticleLogicScale scale = new ParticleLogicScale();
            scale.setRate(-0.001F, -0.0001F * rand.nextFloat());
            scale.setTerminate(true);

            ParticleLogicIconShift iconshift = new ParticleLogicIconShift();
            iconshift.addIcon("flutter1");
            iconshift.addIcon("flutter2");
            iconshift.addIcon("flutter3");
            iconshift.addIcon("flutter4");
            iconshift.setTicksBetweenChange(3);

            c.setIgnoreMaxAge(true);
            c.setScale(0.05f + 0.02f * rand.nextFloat());
            c.setPRColor(color);

            c.addLogic(orbit);
            c.addLogic(scale);
            c.addLogic(iconshift);
        }
    }

    public static void handleClientPacket(MCDataInput in, World world)
    {
        int type = in.readUByte();
        int color = in.readUByte();
        int count = in.readUByte();
        int dir = type == 2 ? in.readUByte() : -1;
        
        BlockCoord bc = in.readCoord();
    
        if (type == 1)
            spawnType1(count, color, bc, world);
        else if (type == 2)
            spawnType2(count, color, dir, bc, world);
    }

    private static void packetType1(int color, int count, BlockCoord bc, World world)
    {
        if (world.isRemote)
            return;
    
        PacketCustom packet = new PacketCustom(TransportationSPH.channel, NetConstants.particle_Spawn);
    
        packet.writeByte(1);
        packet.writeByte(color).writeByte(count).writeCoord(bc);
        packet.sendPacketToAllAround(bc.x, bc.y, bc.z, 64.0D, world.provider.dimensionId);
    }

    private static void packetType2(int color, int count, int dir, BlockCoord bc, World world)
    {
        if (world.isRemote)
            return;
    
        PacketCustom packet = new PacketCustom(TransportationSPH.channel, NetConstants.particle_Spawn);
        
        packet.writeByte(2);
        packet.writeByte(color).writeByte(count).writeCoord(bc).writeByte(dir);
        packet.sendPacketToAllAround(bc.x, bc.y, bc.z, 64.0D, world.provider.dimensionId);
    }
}