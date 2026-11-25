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

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.rptools.maptool.model.gamedata.data.DataType;
import net.rptools.maptool.model.gamedata.data.DataValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryDataStoreTest {

  private MemoryDataStore mds1;
  private MemoryDataStore mds2;

  private MemoryDataStore emptyMds;

  @BeforeEach
  void setUp() throws ExecutionException, InterruptedException {
    mds1 = new MemoryDataStore();
    mds1.createNamespace("testType", "testNamespace").get();

    mds2 = new MemoryDataStore();
    mds2.createNamespace("testType2", "testNamespace").get();
    mds2.createNamespace("testType2", "testNamespace2").get();
    mds2.createNamespace("testType3", "testNamespace3").get();

    emptyMds = new MemoryDataStore();
  }

  @Test
  void getPropertyTypes() {
    assertAll(
        () -> assertEquals(1, mds1.getPropertyTypes().get().size()),
        () -> assertEquals(2, mds2.getPropertyTypes().get().size()),
        () -> assertEquals(0, emptyMds.getPropertyTypes().get().size()),
        () -> assertTrue(mds1.getPropertyTypes().get().contains("testType")),
        () -> assertTrue(mds2.getPropertyTypes().get().contains("testType2")),
        () -> assertTrue(mds2.getPropertyTypes().get().contains("testType3")));
  }

  @Test
  void getPropertyNamespaces() throws Exception {
    assertAll(
        () -> assertEquals(1, mds1.getPropertyNamespaces("testType").get().size()),
        () -> assertEquals(2, mds2.getPropertyNamespaces("testType2").get().size()),
        () -> assertEquals(1, mds2.getPropertyNamespaces("testType3").get().size()),
        () -> assertEquals(0, emptyMds.getPropertyNamespaces("testType").get().size()),
        () -> assertTrue(mds1.getPropertyNamespaces("testType").get().contains("testNamespace")),
        () -> assertTrue(mds2.getPropertyNamespaces("testType2").get().contains("testNamespace")),
        () -> assertTrue(mds2.getPropertyNamespaces("testType2").get().contains("testNamespace2")));
  }

  @Test
  void hasPropertyNamespace() {
    assertAll(
        () -> assertTrue(mds1.hasPropertyNamespace("testType", "testNamespace").get()),
        () -> assertTrue(mds2.hasPropertyNamespace("testType2", "testNamespace").get()),
        () -> assertTrue(mds2.hasPropertyNamespace("testType2", "testNamespace2").get()),
        () -> assertFalse(emptyMds.hasPropertyNamespace("testType", "testNamespace").get()));
  }

  @Test
  void hasProperty() throws ExecutionException, InterruptedException {
    var cf1 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.fromBoolean("boolean1", true));
    var cf2 = mds1.setProperty("testType", "testNamespace", DataValueFactory.fromLong("long1", 1L));
    var cf3 =
        mds1.setProperty("testType", "testNamespace", DataValueFactory.fromDouble("double1", 1.0));
    var cf4 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.fromString("string1", "test"));
    var cf5 =
        mds1.setProperty(
            "testType",
            "testNamespace",
            DataValueFactory.fromJsonArray("jsonArray1", new JsonArray()));
    var cf6 =
        mds1.setProperty(
            "testType",
            "testNamespace",
            DataValueFactory.fromJsonObject("jsonObject1", new JsonObject()));
    var cf7 =
        mds1.setProperty("testType", "testNamespace", DataValueFactory.undefined("undefined"));
    var cf8 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.undefined("ulong", DataType.LONG));
    var cf9 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.undefined("udouble", DataType.DOUBLE));
    var cf10 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.undefined("ustring", DataType.STRING));
    var cf11 =
        mds1.setProperty(
            "testType",
            "testNamespace",
            DataValueFactory.undefined("ujsonArray", DataType.JSON_ARRAY));
    var cf12 =
        mds1.setProperty(
            "testType",
            "testNamespace",
            DataValueFactory.undefined("ujsonObject", DataType.JSON_OBJECT));

    CompletableFuture.allOf(cf1, cf2, cf3, cf4, cf5, cf6, cf7, cf8, cf9, cf10, cf11, cf12).get();

    assertAll(
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "boolean1").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "long1").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "double1").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "string1").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "jsonArray1").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "jsonObject1").get()),
        () -> assertFalse(mds1.hasProperty("testType", "testNamespace", "invalid").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "undefined").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "ulong").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "udouble").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "ustring").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "ujsonArray").get()),
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "ujsonObject").get()));
  }

  @Test
  void isPropertyDefined() throws ExecutionException, InterruptedException {
    var cf1 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.fromBoolean("boolean1", true));
    var cf2 = mds1.setProperty("testType", "testNamespace", DataValueFactory.fromLong("long1", 1L));
    var cf3 =
        mds1.setProperty("testType", "testNamespace", DataValueFactory.fromDouble("double1", 1.0));
    var cf4 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.fromString("string1", "test"));
    var cf5 =
        mds1.setProperty(
            "testType",
            "testNamespace",
            DataValueFactory.fromJsonArray("jsonArray1", new JsonArray()));
    var cf6 =
        mds1.setProperty(
            "testType",
            "testNamespace",
            DataValueFactory.fromJsonObject("jsonObject1", new JsonObject()));
    var cf7 =
        mds1.setProperty("testType", "testNamespace", DataValueFactory.undefined("undefined"));
    var cf8 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.undefined("ulong", DataType.LONG));
    var cf9 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.undefined("udouble", DataType.DOUBLE));
    var cf10 =
        mds1.setProperty(
            "testType", "testNamespace", DataValueFactory.undefined("ustring", DataType.STRING));
    var cf11 =
        mds1.setProperty(
            "testType",
            "testNamespace",
            DataValueFactory.undefined("ujsonArray", DataType.JSON_ARRAY));
    var cf12 =
        mds1.setProperty(
            "testType",
            "testNamespace",
            DataValueFactory.undefined("ujsonObject", DataType.JSON_OBJECT));

    CompletableFuture.allOf(cf1, cf2, cf3, cf4, cf5, cf6, cf7, cf8, cf9, cf10, cf11, cf12).get();

    assertAll(
        () -> assertTrue(mds1.hasProperty("testType", "testNamespace", "boolean1").get()),
        () -> assertTrue(mds1.isPropertyDefined("testType", "testNamespace", "long1").get()),
        () -> assertTrue(mds1.isPropertyDefined("testType", "testNamespace", "double1").get()),
        () -> assertTrue(mds1.isPropertyDefined("testType", "testNamespace", "string1").get()),
        () -> assertTrue(mds1.isPropertyDefined("testType", "testNamespace", "jsonArray1").get()),
        () -> assertTrue(mds1.isPropertyDefined("testType", "testNamespace", "jsonObject1").get()),
        () -> assertFalse(mds1.isPropertyDefined("testType", "testNamespace", "invalid").get()),
        () -> assertFalse(mds1.isPropertyDefined("testType", "testNamespace", "undefined").get()),
        () -> assertFalse(mds1.isPropertyDefined("testType", "testNamespace", "ulong").get()),
        () -> assertFalse(mds1.isPropertyDefined("testType", "testNamespace", "udouble").get()),
        () -> assertFalse(mds1.isPropertyDefined("testType", "testNamespace", "ustring").get()),
        () -> assertFalse(mds1.isPropertyDefined("testType", "testNamespace", "ujsonArray").get()),
        () ->
            assertFalse(mds1.isPropertyDefined("testType", "testNamespace", "ujsonObject").get()));
  }

  @Test
  void createNamespace() throws ExecutionException, InterruptedException {
    emptyMds.createNamespace("testType", "testNamespace").get();
    emptyMds.createNamespace("testType", "testNamespace2").get();
    emptyMds.createNamespace("testType2", "testNamespace3").get();
    emptyMds.createNamespace("testType2", "testNamespace4").get();
    emptyMds.createNamespace("testType2", "testNamespace5").get();

    assertAll(
        () -> assertEquals(2, emptyMds.getPropertyNamespaces("testType").get().size()),
        () -> assertEquals(3, emptyMds.getPropertyNamespaces("testType2").get().size()),
        () ->
            assertTrue(emptyMds.getPropertyNamespaces("testType").get().contains("testNamespace")),
        () ->
            assertTrue(emptyMds.getPropertyNamespaces("testType").get().contains("testNamespace2")),
        () ->
            assertTrue(
                emptyMds.getPropertyNamespaces("testType2").get().contains("testNamespace3")),
        () ->
            assertTrue(
                emptyMds.getPropertyNamespaces("testType2").get().contains("testNamespace4")),
        () ->
            assertTrue(
                emptyMds.getPropertyNamespaces("testType2").get().contains("testNamespace5")),
        () ->
            assertFalse(
                emptyMds.getPropertyNamespaces("testType").get().contains("testNamespace5")));
  }

  @Test
  void emptyValueCanBeUpdated() throws ExecutionException, InterruptedException {
    final String PROPERTY_TYPE = "pt";
    final String NAMESPACE = "ns";
    final String PROPERTY_NAME = "a";

    var mdsWithUndefined = new MemoryDataStore();
    mdsWithUndefined
        .createNamespace(PROPERTY_TYPE, NAMESPACE)
        .thenCompose(
            v ->
                mdsWithUndefined.setProperty(
                    PROPERTY_TYPE, NAMESPACE, DataValueFactory.undefined(PROPERTY_NAME)))
        .get();

    assertEquals(
        DataType.UNDEFINED,
        mdsWithUndefined.getProperty(PROPERTY_TYPE, NAMESPACE, PROPERTY_NAME).get().getDataType());

    mdsWithUndefined
        .setProperty(PROPERTY_TYPE, NAMESPACE, DataValueFactory.fromString(PROPERTY_NAME, "1"))
        .get();

    assertEquals(
        DataType.STRING,
        mdsWithUndefined.getProperty(PROPERTY_TYPE, NAMESPACE, PROPERTY_NAME).get().getDataType());
    assertEquals(
        "1",
        mdsWithUndefined.getProperty(PROPERTY_TYPE, NAMESPACE, PROPERTY_NAME).get().asString());
  }
}
