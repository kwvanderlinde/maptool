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
package net.rptools.maptool.client.macro.impl;

import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.MapToolVariableResolver;
import net.rptools.maptool.language.I18N;

public abstract class AbstractRollMacro extends AbstractMacro {

  protected String roll(String roll) {

    try {
      String text =
          roll + " => " + MapTool.getParser().expandRoll(new MapToolVariableResolver(null), roll);

      return text;
    } catch (Exception e) {
      MapTool.addLocalMessage("<b>" + I18N.getText("roll.general.unknown", roll) + "</b>");
      return null;
    }
  }
}
