package net.rptools.maptool.client.ui.zone.rendering;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class RenderLayerManager {
    private static final Logger log = LogManager.getLogger(RenderLayerManager.class);

    private final Map<Class<?>, RenderLayer> layers = new HashMap<>();

    public <T extends RenderLayer> void addLayer(Class<T> type, T layer) {
        final var previous = layers.put(type, layer);
        if (previous != null) {
            log.info("Ejecting old layer of type {}", type);
        }
    }

    public <T extends RenderLayer> T getLayer(Class<T> type) {
        final var object = layers.get(type);
        if (object != null) {
            return type.cast(object);
        }
        return null;
    }

    public Stream<RenderLayer> getAllLayers() {
        return layers.values().stream();
    }

    public Stream<RenderLayer> getMatchingLayers(Class<?> ... types) {
        return Arrays.stream(types).map(layers::get).filter(Objects::nonNull);
    }
}
