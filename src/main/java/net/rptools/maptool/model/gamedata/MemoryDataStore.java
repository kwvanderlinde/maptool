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

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.gamedata.data.DataType;
import net.rptools.maptool.model.gamedata.data.DataValue;
import net.rptools.maptool.model.gamedata.data.DataValueFactory;
import net.rptools.maptool.model.gamedata.proto.GameDataDto;
import net.rptools.maptool.model.gamedata.proto.GameDataValueDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Class that implements the DataStore interface. */
public class MemoryDataStore implements DataStore {

  private record PropertyTypeNamespace(String propertyType, String namespace) {}

  /** Class used to cache definitions. */
  private final Map<String, Set<String>> propertyTypeNamespaceMap =
      Collections.synchronizedMap(new HashMap<>());

  private final Map<PropertyTypeNamespace, Map<String, DataValue>> namespaceDataMap =
      Collections.synchronizedMap(new HashMap<>());

  /** Class for logging. */
  private static final Logger log = LogManager.getLogger(MemoryDataStore.class);

  /** Creates a new MemoryDataStore. */
  MemoryDataStore() {}

  /**
   * Returns if the namespace exists for the property type.
   *
   * @return if the namespace exists for the property type.
   */
  private boolean checkPropertyNamespace(String propertyType, String namespace) {
    if (propertyTypeNamespaceMap.containsKey(propertyType)) {
      var propertyTypeNamespaces = propertyTypeNamespaceMap.get(propertyType);
      return propertyTypeNamespaces.contains(namespace);
    }
    return false;
  }

  @Override
  public CompletableFuture<Set<String>> getPropertyTypes() {
    return CompletableFuture.completedFuture(new HashSet<>(propertyTypeNamespaceMap.keySet()));
  }

  @Override
  public CompletableFuture<Set<String>> getPropertyNamespaces(String type) {

    return CompletableFuture.supplyAsync(
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
  private DataValue getData(String type, String namespace, String name) {
    var propertyTypeNamespace = new PropertyTypeNamespace(type, namespace);
    var values = namespaceDataMap.get(propertyTypeNamespace);
    return values != null ? values.get(name) : null;
  }

  @Override
  public CompletableFuture<DataValue> getProperty(String type, String namespace, String name) {
    return CompletableFuture.supplyAsync(
        () -> {
          var prop = getData(type, namespace, name);
          return Objects.requireNonNullElseGet(prop, () -> DataValueFactory.undefined(name));
        });
  }

  @Override
  public CompletableFuture<Set<DataValue>> getProperties(String type, String namespace) {
    return CompletableFuture.supplyAsync(
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
   * @param type the property type.
   * @param namespace the property namespace.
   * @param value the data value.
   */
  private DataValue setData(String type, String namespace, DataValue value) {
    if (!checkPropertyNamespace(type, namespace)) {
      throw InvalidDataOperation.createNamespaceDoesNotExist(namespace, type);
    }

    DataValue setValue = value;
    var existing = getData(type, namespace, value.getName());
    // If no value exists we can put anything there, if a value exists we have to check type
    // is
    // correct
    var dataMap =
        namespaceDataMap.computeIfAbsent(
            new PropertyTypeNamespace(type, namespace), k -> new ConcurrentHashMap<>());
    if (existing == null || existing.getDataType() == DataType.UNDEFINED) {
      dataMap.put(value.getName(), value);
    } else {
      var newValue = DataType.convert(value, existing.getDataType());
      dataMap.put(newValue.getName(), newValue);
      setValue = newValue;
    }

    return setValue;
  }

  @Override
  public CompletableFuture<DataValue> setProperty(String type, String namespace, DataValue value) {
    return CompletableFuture.supplyAsync(() -> setData(type, namespace, value));
  }

  @Override
  public CompletableFuture<DataValue> setPropertyIfNotSet(
      String type, String namespace, DataValue value) {
    return CompletableFuture.supplyAsync(
        () -> {
          if (!checkPropertyNamespace(type, namespace)) {
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
    return CompletableFuture.supplyAsync(
        () -> {
          var key = new PropertyTypeNamespace(type, namespace);
          var dataMap = namespaceDataMap.get(key);
          if (dataMap != null) {
            dataMap.remove(name);
          }
          return null;
        });
  }

  /**
   * Creates a new namespace populated with the given data.
   *
   * @param propertyType the property type.
   * @param namespace the namespace.
   * @param initialData the initial data.
   */
  private void createDataNamespace(
      String propertyType, String namespace, Collection<DataValue> initialData) {

    Set<String> namespaces =
        propertyTypeNamespaceMap.computeIfAbsent(
            propertyType, k -> Collections.synchronizedSet(new HashSet<>()));

    namespaces.add(namespace);

    var dataMap =
        namespaceDataMap.computeIfAbsent(
            new PropertyTypeNamespace(propertyType, namespace), k -> new ConcurrentHashMap<>());

    for (var dataValue : initialData) {
      dataMap.put(dataValue.getName(), dataValue);
    }
  }

  @Override
  public CompletableFuture<Boolean> createNamespace(String propertyType, String namespace) {
    return CompletableFuture.supplyAsync(
        () -> {
          createDataNamespace(propertyType, namespace, List.of());
          return null;
        });
  }

  @Override
  public CompletableFuture<GameDataDto> toDto(String type, String namespace) {
    return CompletableFuture.supplyAsync(
        () -> {
          var builder = GameDataDto.newBuilder();
          builder.setType(type);
          builder.setNamespace(namespace);
          for (var data : getProperties(type, namespace).join()) {
            var dataDto = gameValueToDto(data);
            builder.addValues(dataDto);
          }
          return builder.build();
        });
  }

  @Override
  public CompletableFuture<GameDataValueDto> toDto(DataValue data) {
    return CompletableFuture.supplyAsync(() -> gameValueToDto(data));
  }

  /**
   * Converts a {@link DataValue} to a {@link GameDataValueDto}.
   *
   * @param data The {@link DataValue} to convert.
   * @return The converted {@link GameDataValueDto}.
   */
  private GameDataValueDto gameValueToDto(DataValue data) {
    var gson = new Gson();
    var dataBuilder = GameDataValueDto.newBuilder();
    dataBuilder.setName(data.getName());
    switch (data.getDataType()) {
      case LONG -> {
        if (data.isUndefined()) {
          dataBuilder.setUndefinedLongValue(true);
        } else {
          dataBuilder.setLongValue(data.asLong());
        }
      }
      case DOUBLE -> {
        if (data.isUndefined()) {
          dataBuilder.setUndefinedDoubleValue(true);
        } else {
          dataBuilder.setDoubleValue(data.asDouble());
        }
      }
      case BOOLEAN -> {
        if (data.isUndefined()) {
          dataBuilder.setUndefinedBooleanValue(true);
        } else {
          dataBuilder.setBooleanValue(data.asBoolean());
        }
      }
      case STRING -> {
        if (data.isUndefined()) {
          dataBuilder.setUndefinedStringValue(true);
        } else {
          dataBuilder.setStringValue(data.asString());
        }
      }
      case JSON_ARRAY -> {
        if (data.isUndefined()) {
          dataBuilder.setUndefinedJsonArrayValue(true);
        } else {
          dataBuilder.setJsonValue(gson.toJson(data.asJsonArray()));
        }
      }
      case JSON_OBJECT -> {
        if (data.isUndefined()) {
          dataBuilder.setUndefinedJsonObjectValue(true);
        } else {
          dataBuilder.setJsonValue(gson.toJson(data.asJsonObject()));
        }
      }
      case ASSET -> {
        if (data.isUndefined()) {
          dataBuilder.setUndefinedAssetValue(true);
        } else {
          dataBuilder.setAssetValue(data.asAsset().getMD5Key().toString());
        }
      }
      case UNDEFINED -> dataBuilder.setUndefinedValue(true);
    }
    return dataBuilder.build();
  }

  @Override
  public CompletableFuture<Set<MD5Key>> getAssets() {
    return CompletableFuture.supplyAsync(
        () ->
            namespaceDataMap.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(d -> d.getDataType() == DataType.ASSET)
                .map(a -> a.asAsset().getMD5Key())
                .collect(Collectors.toSet()));
  }

  @Override
  public void clear() {
    propertyTypeNamespaceMap.clear();
    namespaceDataMap.clear();
  }

  @Override
  public CompletableFuture<Void> clearNamespace(String propertyType, String namespace) {
    return CompletableFuture.supplyAsync(
        () -> {
          namespaceDataMap.remove(new PropertyTypeNamespace(propertyType, namespace));
          return null;
        });
  }
}
