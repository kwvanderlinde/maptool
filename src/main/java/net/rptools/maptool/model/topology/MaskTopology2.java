package net.rptools.maptool.model.topology;

import net.rptools.maptool.model.Zone;
import org.locationtech.jts.geom.Polygon;

import java.util.EnumSet;

public class MaskTopology2 {
    public static final class Chunk {
        public Polygon polygon;
        public EnumSet<Zone.TopologyType> types;
    }
}
