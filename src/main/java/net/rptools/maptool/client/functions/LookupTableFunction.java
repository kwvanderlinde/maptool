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
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client.functions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.json.JSONMacroFunctions;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.LookupTable;
import net.rptools.maptool.model.LookupTable.LookupEntry;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;
import org.apache.commons.lang3.StringUtils;

public class LookupTableFunction extends AbstractFunction {

  public LookupTableFunction() {
    super(
        0,
        5,
        "tbl",
        "table",
        "tblImage",
        "tableImage",
        "getTableNames",
        "getTableRoll",
        "setTableRoll",
        "clearTable",
        "addTableEntry",
        "deleteTableEntry",
        "createTable",
        "deleteTable",
        "getTableVisible",
        "setTableVisible",
        "getTableAccess",
        "setTableAccess",
        "getTableImage",
        "setTableImage",
        "copyTable",
        "getTableEntry",
        "setTableEntry",
        "resetTablePicks",
        "getTablePickOnce",
        "setTablePickOnce",
        "getTablePicksLeft");
  }

  /** The singleton instance. */
  private static final LookupTableFunction instance = new LookupTableFunction();

  /**
   * Gets the instance of TableLookup.
   *
   * @return the TableLookup.
   */
  public static LookupTableFunction getInstance() {
    return instance;
  }

  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String function, List<Object> params)
      throws ParserException {

    if ("getTableNames".equalsIgnoreCase(function)) {

      FunctionUtil.checkNumberParam("getTableNames", params, 0, 1);
      String delim = ",";
      if (params.size() > 0) {
        delim = params.get(0).toString();
      }
      if ("json".equalsIgnoreCase(delim)) {
        JsonArray jsonArray = new JsonArray();
        getTableList(MapTool.getPlayer().isGM()).forEach(jsonArray::add);
        return jsonArray;
      }
      return StringUtils.join(getTableList(MapTool.getPlayer().isGM()), delim);

    } else if ("getTableVisible".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("getTableVisible", params, 1, 1);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      return lookupTable.getVisible() ? BigDecimal.ONE : BigDecimal.ZERO;

    } else if ("setTableVisible".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("setTableVisible", params, 2, 2);
      String name = params.get(0).toString();
      String visible = params.get(1).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      lookupTable.setVisible(FunctionUtil.getBooleanValue(visible));
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return lookupTable.getVisible() ? BigDecimal.ONE : BigDecimal.ZERO;

    } else if ("getTableAccess".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("getTableAccess", params, 1, 1);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      return lookupTable.getAllowLookup() ? BigDecimal.ONE : BigDecimal.ZERO;

    } else if ("setTableAccess".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("setTableAccess", params, 2, 2);
      String name = params.get(0).toString();
      String access = params.get(1).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      lookupTable.setAllowLookup(FunctionUtil.getBooleanValue(access));
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return lookupTable.getAllowLookup() ? BigDecimal.ONE : BigDecimal.ZERO;

    } else if ("getTableRoll".equalsIgnoreCase(function)) {

      FunctionUtil.checkNumberParam("getTableRoll", params, 1, 1);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      return lookupTable.getRoll();

    } else if ("setTableRoll".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("setTableRoll", params, 2, 2);
      String name = params.get(0).toString();
      String roll = params.get(1).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      lookupTable.setRoll(roll);
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return lookupTable.getRoll();

    } else if ("clearTable".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("clearTable", params, 1, 1);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      lookupTable.clearEntries();
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return "";

    } else if ("addTableEntry".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("addTableEntry", params, 4, 5);
      String name = params.get(0).toString();
      String min = params.get(1).toString();
      String max = params.get(2).toString();
      String value = params.get(3).toString();
      MD5Key asset = null;
      if (params.size() > 4) {
        asset = FunctionUtil.getAssetKeyFromString(params.get(4).toString());
      }
      LookupTable lookupTable = getMaptoolTable(name, function);
      lookupTable.addEntry(Integer.parseInt(min), Integer.parseInt(max), value, asset);
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return "";

    } else if ("deleteTableEntry".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("deleteTableEntry", params, 2, 2);
      String name = params.get(0).toString();
      String roll = params.get(1).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      LookupEntry entry = lookupTable.getLookup(roll);
      if (entry != null) {
        List<LookupEntry> oldlist = new ArrayList<>(lookupTable.getEntryList());
        lookupTable.clearEntries();
        oldlist.stream()
            .filter((e) -> (e != entry))
            .forEachOrdered(
                (e) -> lookupTable.addEntry(e.getMin(), e.getMax(), e.getValue(), e.getImageId()));
      }
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return "";

    } else if ("createTable".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("createTable", params, 3, 4);
      String name = params.get(0).toString();
      String visible = params.get(1).toString();
      String lookups = params.get(2).toString();
      MD5Key asset = null;
      if (params.size() > 3) {
        asset = FunctionUtil.getAssetKeyFromString(params.get(3).toString());
      }
      LookupTable lookupTable = new LookupTable();
      lookupTable.setName(name);
      lookupTable.setVisible(FunctionUtil.getBooleanValue(visible));
      lookupTable.setAllowLookup(FunctionUtil.getBooleanValue(lookups));
      if (asset != null) lookupTable.setTableImage(asset);
      MapTool.getCampaign().getLookupTableMap().put(name, lookupTable);
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return "";

    } else if ("deleteTable".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("deleteTable", params, 1, 1);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      MapTool.getCampaign().getLookupTableMap().remove(name);
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return "";

    } else if ("getTableImage".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("getTableImage", params, 1, 1);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      MD5Key img = lookupTable.getTableImage();
      // Returning null causes an NPE when output is dumped to chat.
      return Objects.requireNonNullElse(img, "");

    } else if ("setTableImage".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("setTableImage", params, 2, 2);
      String name = params.get(0).toString();
      MD5Key asset = FunctionUtil.getAssetKeyFromString(params.get(1).toString());
      LookupTable lookupTable = getMaptoolTable(name, function);
      lookupTable.setTableImage(asset);
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return "";

    } else if ("copyTable".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("copyTable", params, 2, 2);
      String oldName = params.get(0).toString();
      String newName = params.get(1).toString();
      LookupTable oldTable = getMaptoolTable(oldName, function);
      LookupTable newTable = new LookupTable(oldTable);
      newTable.setName(newName);
      MapTool.getCampaign().getLookupTableMap().put(newName, newTable);
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return "";

    } else if ("setTableEntry".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("setTableEntry", params, 3, 4);
      String name = params.get(0).toString();
      String roll = params.get(1).toString();
      String result = params.get(2).toString();
      MD5Key imageId = null;
      if (params.size() == 4) {
        imageId = FunctionUtil.getAssetKeyFromString(params.get(3).toString());
      }
      LookupTable lookupTable = getMaptoolTable(name, function);
      LookupEntry entry = lookupTable.getLookup(roll);
      if (entry == null) return 0; // no entry was found
      int rollInt = Integer.parseInt(roll);
      if (rollInt < entry.getMin() || rollInt > entry.getMax())
        return 0; // entry was found but doesn't match
      List<LookupEntry> oldlist = new ArrayList<>(lookupTable.getEntryList());
      lookupTable.clearEntries();
      for (LookupEntry e : oldlist)
        if (e != entry) {
          lookupTable.addEntry(e.getMin(), e.getMax(), e.getValue(), e.getImageId());
        } else {
          if (imageId == null) imageId = e.getImageId();

          lookupTable.addEntry(e.getMin(), e.getMax(), result, imageId);
        }
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return 1;
    } else if ("getTableEntry".equalsIgnoreCase(function)) {

      FunctionUtil.checkNumberParam(function, params, 2, 2);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      String roll = params.get(1).toString();
      LookupEntry entry = lookupTable.getLookupDirect(roll);
      if (entry == null) return ""; // no entry was found

      int rollInt = Integer.parseInt(roll);
      if (rollInt < entry.getMin() || rollInt > entry.getMax())
        return ""; // entry was found but doesn't match

      JsonObject entryDetails = new JsonObject();
      entryDetails.addProperty("min", entry.getMin());
      entryDetails.addProperty("max", entry.getMax());
      entryDetails.addProperty("value", entry.getValue());
      entryDetails.addProperty("picked", entry.getPicked());

      MD5Key imageId = entry.getImageId();
      if (imageId != null) {
        entryDetails.addProperty("assetid", "asset://" + imageId.toString());
      } else {
        entryDetails.addProperty("assetid", "");
      }
      return entryDetails;
    } else if ("resetTablePicks".equalsIgnoreCase(function)) {
      /*
       * resetTablePicks(tblName) - reset all entries on a table
       * resetTablePicks(tblName, entriesToReset) - reset specific entries from a String List with "," delim
       * resetTablePicks(tblName, entriesToReset, delim) - use custom delimiter
       * resetTablePicks(tblName, entriesToReset, "json") - entriesToReset is a JsonArray
       */
      checkTrusted(function);
      FunctionUtil.checkNumberParam(function, params, 1, 3);
      String tblName = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(tblName, function);
      if (params.size() > 1) {
        String delim = (params.size() > 2) ? params.get(2).toString() : ",";
        List<String> entriesToReset;
        if (delim.equalsIgnoreCase("json")) {
          JsonArray jsonArray = FunctionUtil.paramAsJsonArray(function, params, 1);
          entriesToReset =
              JSONMacroFunctions.getInstance()
                  .getJsonArrayFunctions()
                  .jsonArrayToListOfStrings(jsonArray);
        } else {
          entriesToReset = StrListFunctions.toList(params.get(1).toString(), delim);
        }
        lookupTable.reset(entriesToReset);
      } else {
        lookupTable.reset();
      }
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return "";
    } else if ("setTablePickOnce".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("setTablePickOnce", params, 2, 2);
      String name = params.get(0).toString();
      String pickonce = params.get(1).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      lookupTable.setPickOnce(FunctionUtil.getBooleanValue(pickonce));
      MapTool.serverCommand().updateCampaign(MapTool.getCampaign().getCampaignProperties());
      return lookupTable.getPickOnce() ? BigDecimal.ONE : BigDecimal.ZERO;

    } else if ("getTablePickOnce".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("getTablePickOnce", params, 1, 1);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      return lookupTable.getPickOnce() ? BigDecimal.ONE : BigDecimal.ZERO;

    } else if ("getTablePicksLeft".equalsIgnoreCase(function)) {

      checkTrusted(function);
      FunctionUtil.checkNumberParam("getTablePicksLeft", params, 1, 1);
      String name = params.get(0).toString();
      LookupTable lookupTable = getMaptoolTable(name, function);
      return lookupTable.getPicksLeft();

    } else { // if tbl, table, tblImage or tableImage
      FunctionUtil.checkNumberParam(function, params, 1, 3);
      String name = params.get(0).toString();

      String roll = null;
      if (params.size() > 1) {
        roll = params.get(1).toString().length() == 0 ? null : params.get(1).toString();
      }

      LookupTable lookupTable = MapTool.getCampaign().getLookupTableMap().get(name);
      if (!MapTool.getPlayer().isGM() && !lookupTable.getAllowLookup()) {
        if (lookupTable.getVisible()) {
          throw new ParserException(
              function + "(): " + I18N.getText("msg.error.tableUnknown") + name);
        } else {
          throw new ParserException(
              function + "(): " + I18N.getText("msg.error.tableAccessProhibited") + ": " + name);
        }
      }
      if (lookupTable == null) {
        throw new ParserException(
            I18N.getText("macro.function.LookupTableFunctions.unknownTable", function, name));
      }

      LookupEntry result = lookupTable.getLookup(roll);
      if (result == null) {
        return null;
      }

      if (result.getValue().equals(LookupTable.NO_PICKS_LEFT)) {
        return result.getValue();
      }

      if (function.equals("table") || function.equals("tbl")) {
        String val = result.getValue();
        try {
          BigDecimal bival = new BigDecimal(val);
          return bival;
        } catch (NumberFormatException nfe) {
          return val;
        }
      } else if ("tableImage".equalsIgnoreCase(function)
          || "tblImage"
              .equalsIgnoreCase(function)) { // We want the image URI through tblImage or tableImage

        if (result.getImageId() == null) {
          return ""; // empty string if no image is found (#538)
        }

        BigDecimal size = null;
        if (params.size() > 2) {
          if (params.get(2) instanceof BigDecimal) {
            size = (BigDecimal) params.get(2);
          } else {
            throw new ParserException(
                I18N.getText("macro.function.LookupTableFunctions.invalidSize", function));
          }
        }

        StringBuilder assetId = new StringBuilder("asset://");
        assetId.append(result.getImageId().toString());
        if (size != null) {
          int i = Math.max(size.intValue(), 1); // Constrain to a minimum of 1
          assetId.append("-");
          assetId.append(i);
        }
        return assetId.toString();
      } else {
        throw new ParserException(I18N.getText("macro.function.general.unknownFunction", function));
      }
    }
  }

  /**
   * Checks whether or not the function is trusted
   *
   * @param functionName Name of the macro function
   * @throws ParserException Returns trust error message and function name
   */
  private void checkTrusted(String functionName) throws ParserException {
    if (!MapTool.getParser().isMacroTrusted()) {
      throw new ParserException(I18N.getText("macro.function.general.noPerm", functionName));
    }
  }

  /**
   * * If GM return all tables Otherwise only return visible tables
   *
   * @param isGm boolean Does the calling function has GM privileges
   * @return a list of table names
   */
  private List<String> getTableList(boolean isGm) {
    List<String> tables = new ArrayList<>();
    if (isGm) tables.addAll(MapTool.getCampaign().getLookupTableMap().keySet());
    else
      MapTool.getCampaign().getLookupTableMap().values().stream()
          .filter(LookupTable::getVisible)
          .forEachOrdered((lt) -> tables.add(lt.getName()));
    return tables;
  }

  /**
   * Function to return a MapTool table.
   *
   * @param tableName String containing the name of the desired table
   * @param functionName String containing the name of the calling function, used by the error
   *     message.
   * @return LookupTable The desired MapTool table object
   * @throws ParserException if there were more or less parameters than allowed
   */
  private LookupTable getMaptoolTable(String tableName, String functionName)
      throws ParserException {

    LookupTable lookupTable = MapTool.getCampaign().getLookupTableMap().get(tableName);
    if (!MapTool.getPlayer().isGM() && !lookupTable.getAllowLookup()) {
      if (lookupTable.getVisible()) {
        throw new ParserException(
            functionName + "(): " + I18N.getText("msg.error.tableUnknown") + tableName);
      } else {
        throw new ParserException(
            functionName
                + "(): "
                + I18N.getText("msg.error.tableAccessProhibited")
                + ": "
                + tableName);
      }
    }
    if (lookupTable == null) {
      throw new ParserException(
          I18N.getText(
              "macro.function.LookupTableFunctions.unknownTable", functionName, tableName));
    }
    return lookupTable;
  }
}
