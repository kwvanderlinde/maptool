package net.rptools.maptool.client.ui.zone.viewmodel;

import com.google.common.eventbus.Subscribe;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.zone.ZoneView;
import net.rptools.maptool.client.ui.zone.vbl.AreaTree;
import net.rptools.maptool.events.MapToolEventBus;
import net.rptools.maptool.model.AttachedLightSource;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.LightSource;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.zones.TokensAdded;
import net.rptools.maptool.model.zones.TokensChanged;
import net.rptools.maptool.model.zones.TokensRemoved;
import net.rptools.maptool.model.zones.TopologyChanged;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.geom.Area;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a zone's topology, including token topology.
 */
public class TopologyModel {
    private static final Logger log = LogManager.getLogger(TopologyModel.class);

    private final Zone zone;
    private final Map<Zone.TopologyType, Area> topologyAreas = new EnumMap<>(Zone.TopologyType.class);
    private final Map<Zone.TopologyType, AreaTree> topologyTrees = new EnumMap<>(Zone.TopologyType.class);

    public TopologyModel(Zone zone) {
        this.zone = zone;

        new MapToolEventBus().getMainEventBus().register(this);
    }

    /**
     * Get the map and token topology of the requested type.
     *
     * <p>The topology is cached and should only regenerate when not yet present, which should happen
     * on flush calls.
     *
     * @param topologyType The type of topology tree to get.
     * @return the area of the topology.
     */
    // TODO It scares me that this needs to be `synchronized`.
    public synchronized Area getTopology(Zone.TopologyType topologyType) {
        var topology = topologyAreas.get(topologyType);

        if (topology == null) {
            log.debug("ZoneView topology area for {} is null, generating...", topologyType.name());

            topology = new Area(zone.getTopology(topologyType));
            List<Token> topologyTokens = zone.getTokensWithTopology(topologyType);
            for (Token topologyToken : topologyTokens) {
                topology.add(topologyToken.getTransformedTopology(topologyType));
            }

            topologyAreas.put(topologyType, topology);
        }

        return topology;
    }

    /**
     * Get the topology tree of the requested type.
     *
     * <p>The topology tree is cached and should only regenerate when the tree is not present, which
     * should happen on flush calls.
     *
     * <p>This method is equivalent to building an AreaTree from the results of getTopology(), but the
     * results are cached.
     *
     * @param topologyType The type of topology tree to get.
     * @return the AreaTree (topology tree).
     */
    // TODO It scares me that this needs to be `synchronized`.
    public synchronized AreaTree getTopologyTree(Zone.TopologyType topologyType) {
        var topologyTree = topologyTrees.get(topologyType);

        if (topologyTree == null) {
            log.debug("ZoneView topology tree for {} is null, generating...", topologyType.name());

            var topology = getTopology(topologyType);
            topologyTree = new AreaTree(topology);
            topologyTrees.put(topologyType, topologyTree);
        }

        return topologyTree;
    }

    @Subscribe
    private void onTopologyChanged(TopologyChanged event) {
        if (event.zone() != this.zone) {
            return;
        }

        topologyAreas.clear();
        topologyTrees.clear();
    }

    @Subscribe
    private void onTokensAdded(TokensAdded event) {
        if (event.zone() != zone) {
            return;
        }

        boolean topologyChanged = event.tokens().stream().anyMatch(Token::hasAnyTopology);
        if (topologyChanged) {
            topologyAreas.clear();
            topologyTrees.clear();
        }
    }

    @Subscribe
    private void onTokensRemoved(TokensRemoved event) {
        if (event.zone() != zone) {
            return;
        }

        boolean topologyChanged = event.tokens().stream().anyMatch(Token::hasAnyTopology);
        if (topologyChanged) {
            topologyAreas.clear();
            topologyTrees.clear();
        }
    }

    @Subscribe
    private void onTokensChanged(TokensChanged event) {
        if (event.zone() != zone) {
            return;
        }

        // TODO This check works for add and remove, but does not work for changes. The only reason
        //  we don't break is because the Edit Token dialog and other places unconditionally fire a
        //  TopologyChanged effect, which we respond to in the same way.
        boolean topologyChanged = event.tokens().stream().anyMatch(Token::hasAnyTopology);
        if (topologyChanged) {
            topologyAreas.clear();
            topologyTrees.clear();
        }
    }
}
