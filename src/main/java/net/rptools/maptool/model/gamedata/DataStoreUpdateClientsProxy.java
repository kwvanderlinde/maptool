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

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.gamedata.data.DataValue;
import net.rptools.maptool.model.gamedata.proto.DataStoreDto;
import net.rptools.maptool.model.gamedata.proto.GameDataDto;

class DataStoreUpdateClientsProxy implements DataStore {
  private final DataStore dataStore;

  public DataStoreUpdateClientsProxy(DataStore delegate) {
    this.dataStore = delegate;
  }

  @Override
  public CompletableFuture<Set<String>> getPropertyTypes() {
    return dataStore.getPropertyTypes();
  }

  @Override
  public CompletableFuture<Set<String>> getPropertyNamespaces(String type) {
    return dataStore.getPropertyNamespaces(type);
  }

  @Override
  public CompletableFuture<DataValue> getProperty(String type, String namespace, String name) {
    return dataStore.getProperty(type, namespace, name);
  }

  @Override
  public CompletableFuture<Set<DataValue>> getProperties(String type, String namespace) {
    return dataStore.getProperties(type, namespace);
  }

  /**
   * Notifies remote clients of a change to a property.
   *
   * @param type the type of the property
   * @param namespace the namespace of the property
   * @param data the data value of the property
   */
  private void notifyClientsOfDataUpdate(String type, String namespace, DataValue data) {
    MapTool.serverCommand().updateData(type, namespace, data.toDto());
  }

  @Override
  public CompletableFuture<DataValue> setProperty(String type, String namespace, DataValue value) {
    return dataStore
        .setProperty(type, namespace, value)
        .thenApply(
            v -> {
              notifyClientsOfDataUpdate(type, namespace, v);
              return v;
            });
  }

  @Override
  public CompletableFuture<DataValue> setPropertyIfNotSet(
      String type, String namespace, DataValue value) {
    return dataStore
        .setPropertyIfNotSet(type, namespace, value)
        .thenApply(
            v -> {
              notifyClientsOfDataUpdate(type, namespace, v);
              return v;
            });
  }

  @Override
  public CompletableFuture<Void> removeProperty(String type, String namespace, String name) {
    return dataStore
        .removeProperty(type, namespace, name)
        .thenRun(() -> MapTool.serverCommand().removeData(type, namespace, name));
  }

  @Override
  public CompletableFuture<Boolean> createNamespace(String propertyType, String namespace) {
    return dataStore
        .createNamespace(propertyType, namespace)
        .thenApply(
            isNew -> {
              if (isNew) {
                var builder = GameDataDto.newBuilder();
                builder.setType(propertyType);
                builder.setNamespace(namespace);
                MapTool.serverCommand().updateDataNamespace(builder.build());
              }
              return isNew;
            });
  }

  @Override
  public CompletableFuture<DataStoreDto> toDto() {
    return dataStore.toDto();
  }

  @Override
  public CompletableFuture<Set<MD5Key>> getAssets() {
    return dataStore.getAssets();
  }

  @Override
  public CompletableFuture<Void> clear() {
    return dataStore.clear().thenRun(() -> MapTool.serverCommand().removeDataStore());
  }

  @Override
  public CompletableFuture<Void> clearNamespace(String propertyType, String namespace) {
    return dataStore
        .clearNamespace(propertyType, namespace)
        .thenRun(() -> MapTool.serverCommand().removeDataNamespace(propertyType, namespace));
  }
}
