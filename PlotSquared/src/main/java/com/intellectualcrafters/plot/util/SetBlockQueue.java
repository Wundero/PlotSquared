package com.intellectualcrafters.plot.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import com.intellectualcrafters.plot.PlotSquared;
import com.intellectualcrafters.plot.generator.AugmentedPopulator;
import com.intellectualcrafters.plot.object.PlotBlock;

public class SetBlockQueue {
    
    private volatile static HashMap<ChunkWrapper, PlotBlock[][]> blocks;
    private volatile static int allocate = 25;
    private volatile static boolean running = false;
    private volatile static boolean locked = false;
    private volatile static HashSet<Runnable> runnables;
    
    public synchronized static void allocate(int t) {
        allocate = t;
    }
    
    public static int getAllocate() {
        return allocate;
    }
    
    public synchronized static void addNotify(Runnable whenDone) {
        if (runnables == null) {
            TaskManager.runTask(whenDone);
        }
        else {
            runnables.add(whenDone);
        }
    }
    
    public synchronized static void init() {
        if (blocks == null) {
            if (AugmentedPopulator.x_loc == null) {
                AugmentedPopulator.initCache();
            }
            blocks = new HashMap<>();
            runnables = new HashSet<>();
        }
        if (!running) {
            TaskManager.index.increment();
            final int current = TaskManager.index.intValue();
            int task = TaskManager.runTaskRepeat(new Runnable() {
                @Override
                public void run() {
                    if (locked) {
                        return;
                    }
                    if (blocks.size() == 0) {
                        PlotSquared.TASK.cancelTask(TaskManager.tasks.get(current));
                        for (Runnable runnable : runnables) {
                            TaskManager.runTask(runnable);
                        }
                        runnables = null;
                        blocks = null;
                        running = false;
                        return;
                    }
                    long start = System.currentTimeMillis() + allocate;
                    Iterator<Entry<ChunkWrapper, PlotBlock[][]>> i = blocks.entrySet().iterator();
                    while (System.currentTimeMillis() < start && i.hasNext()) {
                        if (locked) {
                            return;
                        }
                        Entry<ChunkWrapper, PlotBlock[][]> n = i.next();
                        i.remove();
                        ChunkWrapper chunk = n.getKey();
                        int X = chunk.x << 4;
                        int Z = chunk.z << 4;
                        PlotBlock[][] blocks = n.getValue();
                        String world = chunk.world;
//                        ChunkManager.manager.setChunk(chunk, blocks);
                        for (int j = 0; j < blocks.length; j++) {
                            PlotBlock[] blocksj = blocks[j];
                            if (blocksj != null) {
                                for (int k = 0; k < blocksj.length; k++) {
                                    PlotBlock block = blocksj[k];
                                    if (block != null) {
                                        int x = AugmentedPopulator.x_loc[j][k];
                                        int y = AugmentedPopulator.y_loc[j][k];
                                        int z = AugmentedPopulator.z_loc[j][k];
                                        BlockManager.manager.functionSetBlock(world, X + x, y, Z + z, block.id, block.data);
                                    }
                                }
                            }
                        }
                    }
                }
            }, 2);
            TaskManager.tasks.put(current, task);
            running = true;
        }
    }
     
    public static void setBlock(final String world, int x, final int y, int z, final PlotBlock block) {
        locked = true;
        if (!running) {
            init();
        }
        int X = x >> 4;
        int Z = z >> 4;
        x -= X << 4;
        z -= Z << 4;
        
        ChunkWrapper wrap = new ChunkWrapper(world, X, Z);
        PlotBlock[][] result = blocks.get(wrap);
        if (!blocks.containsKey(wrap)) {
            result = new PlotBlock[16][];
            blocks.put(wrap, result);
        }
        if (result[y >> 4] == null) {
            result[y >> 4] = new PlotBlock[4096];
        }
        result[y >> 4][((y & 0xF) << 8) | (z << 4) | x] = block;
        locked = false;
    }
    
    private static int lastInt = 0;
    private static PlotBlock lastBlock = new PlotBlock((short) 0, (byte) 0);
    
    public static void setBlock(final String world, int x, final int y, int z, final int id) {
        locked = true;
        if (!running) {
            init();
        }
        int X = x >> 4;
        int Z = z >> 4;
        x -= X << 4;
        z -= Z << 4;
        
        ChunkWrapper wrap = new ChunkWrapper(world, X, Z);
        PlotBlock[][] result = blocks.get(wrap);
        if (!blocks.containsKey(wrap)) {
            result = new PlotBlock[16][];
            blocks.put(wrap, result);
        }
        if (result[y >> 4] == null) {
            result[y >> 4] = new PlotBlock[4096];
        }
        if (id == lastInt) {
            result[y >> 4][((y & 0xF) << 8) | (z << 4) | x] = lastBlock;
        }
        else {
            lastInt = id;
            lastBlock = new PlotBlock((short) id, (byte) 0);
        }
        result[y >> 4][((y & 0xF) << 8) | (z << 4) | x] = lastBlock;
        locked = false;
    }
    
    public static class ChunkWrapper {
        public final int x; 
        public final int z;
        public final String world;
        
        public ChunkWrapper(String world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
        
        @Override
        public int hashCode() {
            int result;
            if (this.x >= 0) {
                if (this.z >= 0) {
                    result = (this.x * this.x) + (3 * this.x) + (2 * this.x * this.z) + this.z + (this.z * this.z);
                } else {
                    final int y1 = -this.z;
                    result = (this.x * this.x) + (3 * this.x) + (2 * this.x * y1) + y1 + (y1 * y1) + 1;
                }
            } else {
                final int x1 = -this.x;
                if (this.z >= 0) {
                    result = -((x1 * x1) + (3 * x1) + (2 * x1 * this.z) + this.z + (this.z * this.z));
                } else {
                    final int y1 = -this.z;
                    result = -((x1 * x1) + (3 * x1) + (2 * x1 * y1) + y1 + (y1 * y1) + 1);
                }
            }
            result = result * 31 + world.hashCode();
            return result;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ChunkWrapper other = (ChunkWrapper) obj;
            return ((this.x == other.x) && (this.z == other.z) && (this.world.equals(other.world)));
        }
    }
}
