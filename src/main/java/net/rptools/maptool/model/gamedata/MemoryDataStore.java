/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.model.gamedata;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.gamedata.data.DataType;
import net.rptools.maptool.model.gamedata.data.DataValue;
import net.rptools.maptool.model.gamedata.data.DataValueFactory;
import net.rptools.maptool.model.gamedata.proto.DataStoreDto;
import net.rptools.maptool.model.gamedata.proto.GameDataDto;

/** Class that implements the DataStore interface. */
public class MemoryDataStore implements DataStore {
  /**
   * Persisted tag for the MemoryDataStore, as its the only data store currently supported it will
   * be the default, this may change in the future.
   */
  private static final String DATA_STORE_TYPE_NAME = "default";

  private record PropertyTypeNamespace(String propertyType, String namespace) {}

  // Task queue used to read and write the store.
  private final ExecutorService queue =
      Executors.newSingleThreadExecutor(
          new ThreadFactoryBuilder().setNameFormat("MemoryDataStore-thread-%d").build());

  /** Class used to cache definitions. */
  private final Map<String, Set<String>> propertyTypeNamespaceMap = new HashMap<>();

  private final Map<PropertyTypeNamespace, Map<String, DataValue>> namespaceDataMap =
      new HashMap<>();

  /** Creates a new MemoryDataStore. */
  MemoryDataStore() {}

  private <T> CompletableFuture<T> enqueue(Callable<T> task) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return task.call();
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        },
        queue);
  }

  @Override
  public CompletableFuture<Set<String>> getPropertyTypes() {
    return enqueue(() -> new HashSet<>(propertyTypeNamespaceMap.keySet()));
  }

  @Override
  public CompletableFuture<Set<String>> getPropertyNamespaces(String type) {
    return enqueue(
        () -> {
          var propertyTypeNamespace = propertyTypeNamespaceMap.get(type);
          if (propertyTypeNamespace != null) {
            return new HashSet<>(propertyTypeNamespace);
          } else {
            return new HashSet<>();
          }
        });
  }

  /**
   * Returns the data value for the given property type, namespace and name. This will return null
   * if the data does not exist.
   *
   * @param type the property type.
   * @param namespace the property namespace.
   * @param name the property name.
   * @return the data value.
   */
  private CompletableFuture<DataValue> getData(String type, String namespace, String name) {
    return enqueue(
        () -> {
          var propertyTypeNamespace = new PropertyTypeNamespace(type, namespace);
          var values = namespaceDataMap.get(propertyTypeNamespace);
          return values != null ? values.get(name) : null;
        });
  }

  @Override
  public CompletableFuture<DataValue> getProperty(String type, String namespace, String name) {
    return getData(type, namespace, name)
        .thenApply(
            prop -> Objects.requireNonNullElseGet(prop, () -> DataValueFactory.undefined(name)));
  }

  @Override
  public CompletableFuture<Set<DataValue>> getProperties(String type, String namespace) {
    return enqueue(
        () -> {
          var values = namespaceDataMap.get(new PropertyTypeNamespace(type, namespace));
          if (values != null) {
            return Set.copyOf(values.values());
          } else {
            return Set.of();
          }
        });
  }

  /**
   * Method to set the data value for the given property type, namespace and name.
   *
   * <p>If no there is no existing value, then {@code value} is used as-is to set the value and the
   * data type. Otherwise, {@code value} is converted to the existing data type.
   *
   * @param type the property type.
   * @param namespace the property namespace.
   * @param value the data value.
   * @return A future yielding the newly set value, which may be different from {@code value} if
   *     type conversions are done.
   */
  private CompletableFuture<DataValue> setData(String type, String namespace, DataValue value) {
    return enqueue(
        () -> {
          var propertyTypeNamespaces = propertyTypeNamespaceMap.get(type);
          if (propertyTypeNamespaces == null || !propertyTypeNamespaces.contains(namespace)) {
            throw InvalidDataOperation.createNamespaceDoesNotExist(namespace, type);
          }

          var dataMap =
              namespaceDataMap.computeIfAbsent(
                  new PropertyTypeNamespace(type, namespace), k -> new ConcurrentHashMap<>());

          var existing = dataMap.get(value.getName());
          if (existing == null || existing.getDataType() == DataType.UNDEFINED) {
            dataMap.put(value.getName(), value);
            return value;
          } else {
            var newValue = DataType.convert(value, existing.getDataType());
            dataMap.put(newValue.getName(), newValue);
            return newValue;
          }
        });
  }

  @Override
  public CompletableFuture<DataValue> setProperty(String type, String namespace, DataValue value) {
    return setData(type, namespace, value);
  }

  @Override
  public CompletableFuture<DataValue> setPropertyIfNotSet(
      String type, String namespace, DataValue value) {
    return enqueue(
        () -> {
          var propertyTypeNamespaces = propertyTypeNamespaceMap.get(type);
          if (propertyTypeNamespaces == null || !propertyTypeNamespaces.contains(namespace)) {
            throw InvalidDataOperation.createNamespaceDoesNotExist(namespace, type);
          }

          var dataMap =
              namespaceDataMap.computeIfAbsent(
                  new PropertyTypeNamespace(type, namespace), k -> new ConcurrentHashMap<>());

          var existing = dataMap.get(value.getName());
          if (existing == null || existing.getDataType() == DataType.UNDEFINED) {
            dataMap.put(value.getName(), value);
            return value;
          }
          return existing;
        });
  }

  @Override
  public CompletableFuture<Void> removeProperty(String type, String namespace, String name) {
    return enqueue(
        () -> {
          var key = new PropertyTypeNamespace(type, namespace);
          var dataMap = namespaceDataMap.get(key);
          if (dataMap != null) {
            dataMap.remove(name);
          }
          return null;
        });
  }

  @Override
  public CompletableFuture<Boolean> createNamespace(String propertyType, String namespace) {
    return enqueue(
        () -> {
          Set<String> namespaces =
              propertyTypeNamespaceMap.computeIfAbsent(
                  propertyType, k -> Collections.synchronizedSet(new HashSet<>()));
          namespaces.add(namespace);

          var key = new PropertyTypeNamespace(propertyType, namespace);
          var existing = namespaceDataMap.get(key);
          if (existing == null) {
            namespaceDataMap.put(key, new ConcurrentHashMap<>());
            return true;
          }
          return false;
        });
  }

  @Override
  public CompletableFuture<DataStoreDto> toDto() {
    return enqueue(
        () -> {
          var builder = DataStoreDto.newBuilder();
          builder.setDataStoreType(DATA_STORE_TYPE_NAME);

          for (var entry : namespaceDataMap.entrySet()) {
            var entryBuilder = GameDataDto.newBuilder();
            entryBuilder.setType(entry.getKey().propertyType());
            entryBuilder.setNamespace(entry.getKey().namespace());
            for (var data : entry.getValue().values()) {
              entryBuilder.addValues(data.toDto());
            }
            builder.addData(entryBuilder);
          }

          return builder.build();
        });
  }

  @Override
  public CompletableFuture<Set<MD5Key>> getAssets() {
    return enqueue(
        () ->
            namespaceDataMap.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(d -> d.getDataType() == DataType.ASSET)
                .map(a -> a.asAsset().getMD5Key())
                .collect(Collectors.toSet()));
  }

  @Override
  public CompletableFuture<Void> clear() {
    return enqueue(
        () -> {
          propertyTypeNamespaceMap.clear();
          namespaceDataMap.clear();
          return null;
        });
  }

  @Override
  public CompletableFuture<Void> clearNamespace(String propertyType, String namespace) {
    return enqueue(
        () -> {
          namespaceDataMap.remove(new PropertyTypeNamespace(propertyType, namespace));
          return null;
        });
  }
}
