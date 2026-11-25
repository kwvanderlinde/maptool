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
import net.rptools.maptool.model.gamedata.data.DataValue;
import net.rptools.maptool.model.gamedata.proto.GameDataDto;
import net.rptools.maptool.model.gamedata.proto.GameDataValueDto;

/**
 * Interface for a data store. Classes that implement this interfaces may be proxied by other
 * classes that are responsible for replicating the data to other clients, as such they should not
 * call any of their own public methods. or they risk sending superfluous data to other clients.
 */
public interface DataStore {

  /**
   * Returns the types of properties that are stored in this data store.
   *
   * @return the types of properties that are stored in this data store.
   */
  CompletableFuture<Set<String>> getPropertyTypes();

  /**
   * Returns the namespaces of properties that are stored in this data store for a specific
   * propertyType.
   *
   * @param type the propertyType of properties to get the namespaces of.
   * @return the namespaces of properties that are stored in this data store for a specific
   *     propertyType.
   */
  CompletableFuture<Set<String>> getPropertyNamespaces(String type);

  /**
   * Returns the value of a property.
   *
   * @param type the propertyType of the property.
   * @param namespace the namespace of the property.
   * @param name the name of the property.
   * @return the value of a property.
   */
  CompletableFuture<DataValue> getProperty(String type, String namespace, String name);

  /**
   * Returns all the properties for a type and namespace.
   *
   * @param type the propertyType of the property.
   * @param namespace the namespace of the property.
   * @return all the properties for a type and namespace.
   */
  CompletableFuture<Set<DataValue>> getProperties(String type, String namespace);

  /**
   * Sets the value of a property.
   *
   * @param type the propertyType of the property.
   * @param namespace the namespace of the property.
   * @param value the value of the property.
   * @return the value that was set.
   * @throws InvalidDataOperation exception if the value can not be converted to the properties'
   *     data propertyType.
   * @throws InvalidDataOperation exception if the namespace does not exist for the propertyType.
   */
  CompletableFuture<DataValue> setProperty(String type, String namespace, DataValue value);

  /**
   * Sets the value of a property.
   *
   * @param type the propertyType of the property.
   * @param namespace the namespace of the property.
   * @param value the value of the property.
   * @return the value that was set.
   * @throws InvalidDataOperation exception if the value can not be converted to the properties'
   *     data propertyType.
   * @throws InvalidDataOperation exception if the namespace does not exist for the propertyType.
   */
  CompletableFuture<DataValue> setPropertyIfNotSet(String type, String namespace, DataValue value);

  /**
   * Removes a property from the Data store.
   *
   * @param type the propertyType of the property.
   * @param namespace the namespace of the property.
   * @param name the name of the property.
   * @return a {@code Void} completable future.
   */
  CompletableFuture<Void> removeProperty(String type, String namespace, String name);

  /**
   * Ensures a namespace exists.
   *
   * <p>If called for an existing namespace, it will be left unchanged.
   *
   * @param propertyType the propertyType of the namespace.
   * @param namespace the namespace to create.
   * @return a completable future yield {@code true} if the namespace was created.
   */
  CompletableFuture<Boolean> createNamespace(String propertyType, String namespace);

  /**
   * Returns the data transfer object representation of the data in the namespace.
   *
   * @param type the propertyType of the namespace.
   * @param namespace the namespace to get the data for.
   * @return a {@code CompletableFuture} containing the data transfer object representation of the
   *     data.
   */
  CompletableFuture<GameDataDto> toDto(String type, String namespace);

  /**
   * Returns the data transfer object representation of the data in the namespace.
   *
   * @param value the data value to convert to a dto.
   * @return a {@code CompletableFuture} containing the data transfer object representation of the
   */
  CompletableFuture<GameDataValueDto> toDto(DataValue value);

  /**
   * Returns all of the {@link MD5Key} of assets in the DataStore.
   *
   * @return a {@code CompletableFuture} containing a list of all of the {@link MD5Key} of assets in
   *     the data store.
   */
  CompletableFuture<Set<MD5Key>> getAssets();

  /** Removes all the data in the DataStore. */
  void clear();

  /**
   * Removes the namespace from the DataStore.
   *
   * @param propertyType the propertyType of the namespace.
   * @param namespace the namespace to remove.
   */
  CompletableFuture<Void> clearNamespace(String propertyType, String namespace);
}
