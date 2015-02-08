package net.CyanWool.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import net.CyanWool.api.CyanWool;
import net.CyanWool.api.io.ChunkIOService;
import net.CyanWool.api.io.RegionFile;
import net.CyanWool.api.world.World;
import net.CyanWool.api.world.chunks.Chunk;
import net.CyanWool.world.CyanChunk;

import org.spacehq.mc.protocol.data.game.NibbleArray3d;
import org.spacehq.mc.protocol.data.game.ShortArray3d;
import org.spacehq.opennbt.NBTIO;
import org.spacehq.opennbt.tag.builtin.ByteArrayTag;
import org.spacehq.opennbt.tag.builtin.ByteTag;
import org.spacehq.opennbt.tag.builtin.CompoundTag;
import org.spacehq.opennbt.tag.builtin.ListTag;
import org.spacehq.opennbt.tag.builtin.Tag;


public class CyanChunkIOService implements ChunkIOService {

    private final World world;
    private final File dir;

    public CyanChunkIOService(World world) {
        this.world = world;
        this.dir = new File(world.getPath());
    }

    @Override
    public Chunk readChunk(int x, int z) {
        CyanChunk chunk = new CyanChunk(world, x, z);
        RegionFile region;
        region = RegionFileCache.getRegionFile(dir, x, z);
        int nX = x & (32-1);
        int nZ = z & (32-1);
        if (!region.hasChunk(nX, nZ)) {
            return null;
        }
        DataInputStream in = region.getChunkDataInputStream(nX, nZ);
        if (in != null) {
            CompoundTag compoundTag = null;
            try {
                compoundTag = (CompoundTag) NBTIO.readTag(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (compoundTag == null)
                return null;

            CompoundTag level = compoundTag.get("Level");
            ListTag sectionsList = level.get("Sections");
            org.spacehq.mc.protocol.data.game.Chunk[] sections = new org.spacehq.mc.protocol.data.game.Chunk[16];
            for (int i = 0; i < sectionsList.size(); i++) {
                CompoundTag chunkz = sectionsList.get(i);
                ByteTag y = chunkz.get("Y");
                ByteArrayTag blocks = chunkz.get("Blocks");
                ByteArrayTag blockLight = chunkz.get("BlockLight");
                ByteArrayTag skyLight = chunkz.get("SkyLight");
                ByteArrayTag data = chunkz.get("Data");
                ByteArrayTag add = chunkz.get("Add");
                ShortArray3d block = new ShortArray3d(4096);
                for (int cX = 0; cX < 16; cX++)
                    for (int cY = 0; cY < 16; cY++)
                        for (int cZ = 0; cZ < 16; cZ++) {
                            int index = 256 * cY + 16 * cZ + cX;
                            int id = blocks.getValue(index);
                            block.setBlockAndData(cX, cY, cZ, id + (id < 0 ? 256 : 0), getValue(data, index));
                        }
                sections[y.getValue()] = new org.spacehq.mc.protocol.data.game.Chunk(block, new NibbleArray3d(blockLight.getValue()), new NibbleArray3d(skyLight.getValue()));
            }
     
            // from Glowstone
            chunk.initializeSections(sections);

            // TODO: Init entities...
            CyanWool.getLogger().info("Chunk loaded!");
            return chunk;
        } else {}
        // todo: generate chunk
        org.spacehq.mc.protocol.data.game.Chunk[] sections = new org.spacehq.mc.protocol.data.game.Chunk[16];
        CyanWool.getLogger().info("Not found NBT sections! Generate new sections...");
        for (int i = 0; i < 16; i++) {
            sections[i] = new org.spacehq.mc.protocol.data.game.Chunk(true); 
        }
        return chunk;
    }

    private int getValue(ByteArrayTag array, int index) {
        return (index%2 == 0 ? array.getValue(index/2) : array.getValue(index/2) >> 4)&0x0F;
    }
    
    @Override
    public void saveChunk(Chunk chunk) {
        int x = chunk.getX(), z = chunk.getZ();
        RegionFile region = RegionFileCache.getRegionFile(dir, x, z);
        int regionX = x & (32- 1);
        int regionZ = z & (32 - 1);

        DataOutputStream out = region.getChunkDataOutputStream(regionX, regionZ);
        DataInputStream in = region.getChunkDataInputStream(regionX, regionZ);

        if (in != null && out != null) {
            CompoundTag compoundTag = null;
            try {
                compoundTag = (CompoundTag) NBTIO.readTag(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (compoundTag == null)
                return;
            CompoundTag level = compoundTag.get("Level");
            ListTag sections = level.get("Sections");
            CyanChunk c = (CyanChunk) chunk;
            org.spacehq.mc.protocol.data.game.Chunk[] chunks = c.getSections();
            Map<String, Tag> values = compoundTag.getValue();
            Map<String, Tag> levelInfo = level.getValue();
            ArrayList<Tag> newSections = new ArrayList<Tag>();
            for (int i = 0; i < sections.size(); i++) {
                CompoundTag chunkz = sections.get(i);
                Map<String, Tag> cv = chunkz.getValue();
                ByteTag y = chunkz.get("Y");
                ByteArrayTag blocks = chunkz.get("Blocks");
                ByteArrayTag blockLight = chunkz.get("BlockLight");
                ByteArrayTag skyLight = chunkz.get("SkyLight");
                ByteArrayTag data = chunkz.get("Data");
                // ByteArrayTag add = chunkz.get("Add");
                if (chunks[i] != null) {
                    if (chunks[i].getBlockLight() != null)
                        blockLight.setValue(chunks[i].getBlockLight().getData());
                    if (chunks[i].getSkyLight() != null)
                        skyLight.setValue(chunks[i].getSkyLight().getData());
                    if (chunks[i].getBlocks() != null)
                        for (int cX = 0; cX < 16; cX++)
                            for (int cY = 0; cY < 16; cY++)
                                for (int cZ = 0; cZ < 16; cZ++) {
                                    int index = 256 * cY + 16 * cZ + cX;
                                    int id = chunks[i].getBlocks().getBlock(cX, cY, cZ);
                                    if (id > 128)
                                        id -= 256;
                                    blocks.setValue(index, Byte.parseByte(id + ""));
                                    if (index % 2 == 0)
                                        data.setValue(index / 2, (byte) ((getValue(data, index + 1) << 4) | chunks[i].getBlocks().getData(cX, cY, cZ)));
                                    else data.setValue(index / 2, (byte) ((chunks[i].getBlocks().getData(cX, cY, cZ) << 4) | getValue(data, index - 1)));
                                }
                }
                cv.put("Y", y);
                cv.put("Blocks", blocks);
                cv.put("BlockLight", blockLight);
                cv.put("SkyLight", skyLight);
                cv.put("Data", data);
                // cv.put("Add", add);//How to calculate from value if even
                // needed
                chunkz.setValue(cv);
                newSections.add(chunkz);
            }
            sections.setValue(newSections);
            levelInfo.put("Sections", sections);
            level.setValue(levelInfo);
            values.put("Level", level);
            compoundTag.setValue(values);
            try {
                NBTIO.writeTag(out, compoundTag);
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}