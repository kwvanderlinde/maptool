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
package net.rptools.maptool.model.library.addon;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.rptools.maptool.model.gamedata.DataStore;
import net.rptools.maptool.model.gamedata.DataStoreManager;
import net.rptools.maptool.model.gamedata.data.DataValue;
import net.rptools.maptool.model.gamedata.data.DataValueFactory;
import net.rptools.maptool.model.library.data.LibraryData;

/** Class to access the data for an AddOnLibrary. */
public class AddOnLibraryData implements LibraryData {

  /** The property name space for this library. */
  private static final String DATA_TYPE = "addon:";

  /** The AddOnLibrary this data is for. */
  private final AddOnLibrary addOnLibrary;

  /** The name space for the data this library. */
  private final String dataNameSpace;

  private final DataStore dataStore;

  /** If the library needs initialization. */
  private static final String NEEDS_INIT_KEY = "internal:needsInit";

  /**
   * Creates a new AddOnLibraryData.
   *
   * @param addOnLibrary The AddOnLibrary the data is for.
   * @param dataNameSpace The namespace for the data.
   */
  AddOnLibraryData(AddOnLibrary addOnLibrary, String dataNameSpace) {
    this.addOnLibrary = addOnLibrary;
    this.dataNameSpace = dataNameSpace;
    this.dataStore = new DataStoreManager().getDefaultDataStore();
  }

  @Override
  public CompletableFuture<Set<String>> getAllKeys() {
    return dataStore
        .getProperties(DATA_TYPE, dataNameSpace)
        .thenApply(p -> p.stream().map(DataValue::getName).collect(Collectors.toSet()));
  }

  @Override
  public CompletableFuture<DataValue> getValue(String key) {
    return getDataValue(key);
  }

  @Override
  public CompletableFuture<Void> setData(DataValue value) {
    return dataStore.setProperty(DATA_TYPE, dataNameSpace, value).thenAccept(p -> {});
  }

  @Override
  public CompletableFuture<DataValue> getStaticData(String path) {
    return addOnLibrary.readFile(path);
  }

  @Override
  public CompletableFuture<DataValue> getPublicStaticData(String path) {
    if (!addOnLibrary.allowsUriAccess() || !path.replaceFirst("^/", "").startsWith("public/")) {
      return CompletableFuture.completedFuture(DataValueFactory.undefined(path));
    }

    return getStaticData(path);
  }

  /**
   * Returns the data value for the given key.
   *
   * @param key The key to get the data value for.
   * @return The data value for the given key.
   */
  private CompletableFuture<DataValue> getDataValue(String key) {
    return dataStore.getProperty(DATA_TYPE, dataNameSpace, key);
  }

  /**
   * Initializes the data for this library, only needs to be called once but safe to call multiple
   * times.
   *
   * @return A future that completes when the data is initialized.
   */
  CompletableFuture<Void> initialize() {
    return dataStore
        .createNamespace(DATA_TYPE, dataNameSpace)
        .thenCompose(
            isNew -> {
              if (isNew) {
                return dataStore.setProperty(
                    DATA_TYPE, dataNameSpace, DataValueFactory.fromBoolean(NEEDS_INIT_KEY, true));
              } else {
                return dataStore.setPropertyIfNotSet(
                    DATA_TYPE, dataNameSpace, DataValueFactory.fromBoolean(NEEDS_INIT_KEY, false));
              }
            })
        .thenAccept(ignored -> {});
  }

  CompletableFuture<Boolean> needsInitialization() {
    return dataStore
        .getProperty(DATA_TYPE, dataNameSpace, NEEDS_INIT_KEY)
        .thenApply(DataValue::asBoolean);
  }

  public CompletableFuture<Void> setNeedsToBeInitialized(boolean needsToBeInitialized) {
    return dataStore
        .setProperty(
            DATA_TYPE,
            dataNameSpace,
            DataValueFactory.fromBoolean(NEEDS_INIT_KEY, needsToBeInitialized))
        .thenApply(v -> null);
  }
}
