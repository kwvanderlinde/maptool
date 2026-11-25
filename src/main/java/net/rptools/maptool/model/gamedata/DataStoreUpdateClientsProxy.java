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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.gamedata.data.DataValue;
import net.rptools.maptool.model.gamedata.proto.GameDataDto;
import net.rptools.maptool.model.gamedata.proto.GameDataValueDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class DataStoreUpdateClientsProxy implements DataStore {

  /** Class for logging. */
  private static final Logger log = LogManager.getLogger(MemoryDataStore.class);

  @Override
  public CompletableFuture<Set<String>> getPropertyTypes() {
    return dataStore.getPropertyTypes();
  }

  @Override
  public CompletableFuture<Set<String>> getPropertyNamespaces(String type) {
    return dataStore.getPropertyNamespaces(type);
  }

  @Override
  public CompletableFuture<Boolean> hasPropertyNamespace(String type, String namespace) {
    return dataStore.hasPropertyNamespace(type, namespace);
  }

  @Override
  public CompletableFuture<Boolean> hasProperty(String type, String namespace, String name) {
    return dataStore.hasProperty(type, namespace, name);
  }

  @Override
  public CompletableFuture<Boolean> isPropertyDefined(String type, String namespace, String name) {
    return dataStore.isPropertyDefined(type, namespace, name);
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
    try {
      MapTool.serverCommand().updateData(type, namespace, toDto(data).get());
    } catch (InterruptedException | ExecutionException e) {
      log.error(I18N.getText("data.error.sendingUpdate"));
      throw new CompletionException(e.getCause());
    }
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
  public CompletableFuture<Void> removeProperty(String type, String namespace, String name) {
    MapTool.serverCommand().removeData(type, namespace, name);
    return dataStore.removeProperty(type, namespace, name);
  }

  @Override
  public CompletableFuture<Void> createNamespace(String propertyType, String namespace) {
    return dataStore
        .createNamespace(propertyType, namespace)
        .thenRun(
            () -> {
              try {
                MapTool.serverCommand().updateDataNamespace(toDto(propertyType, namespace).get());
              } catch (InterruptedException | ExecutionException e) {
                log.error(I18N.getText("data.error.sendingUpdate"));
                throw new CompletionException(e.getCause());
              }
            });
  }

  @Override
  public CompletableFuture<GameDataDto> toDto(String type, String namespace) {
    return dataStore.toDto(type, namespace);
  }

  @Override
  public CompletableFuture<Set<MD5Key>> getAssets() {
    return dataStore.getAssets();
  }

  @Override
  public void clear() {
    dataStore.clear();
    MapTool.serverCommand().removeDataStore();
  }

  @Override
  public CompletableFuture<Void> clearNamespace(String propertyType, String namespace) {
    MapTool.serverCommand().removeDataNamespace(propertyType, namespace);
    return dataStore.clearNamespace(propertyType, namespace);
  }

  @Override
  public CompletableFuture<GameDataValueDto> toDto(DataValue value) {
    return dataStore.toDto(value);
  }

  private final DataStore dataStore;

  public DataStoreUpdateClientsProxy(DataStore delegate) {
    this.dataStore = delegate;
  }
}
