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

import java.math.BigDecimal;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.ParserException;

public class GeneratedSupport {
  public interface Converter<T> {
    T fromObject(String functionName, Object o) throws ParserException;

    Object toObject(String functionName, T t) throws ParserException;
  }

  public interface ConverterWithContext<T, Context> extends Converter<T> {
    T fromObject(String functionName, Context context, Object o) throws ParserException;
  }

  public static final ConverterWithContext<Token, Zone> tokenConverter =
      new ConverterWithContext<>() {
        // TODO Anything that uses this must be trusted. Need some kind of indicator for that.

        @Override
        public Token fromObject(String functionName, Object o) {
          return fromObject(functionName, MapTool.getFrame().getCurrentZoneRenderer().getZone(), o);
        }

        @Override
        public Token fromObject(String functionName, Zone zone, Object o) {
          var identifier = o.toString();
          Token token = zone.resolveToken(identifier);
          return token;
        }

        @Override
        public Object toObject(String functionName, Token token) {
          return token.getId().toString();
        }
      };

  public static final Converter<Object> identityConverter =
      new Converter<>() {
        @Override
        public Object fromObject(String functionName, Object o) {
          return o;
        }

        @Override
        public Object toObject(String functionName, Object o) {
          return o;
        }
      };

  public static final Converter<Boolean> booleanConverter =
      new Converter<>() {
        @Override
        public Boolean fromObject(String functionName, Object o) {
          return FunctionUtil.getBooleanValue(o);
        }

        @Override
        public Object toObject(String functionName, Boolean t) {
          return FunctionUtil.getDecimalForBoolean(t);
        }
      };

  public static final Converter<BigDecimal> bigDecimalConverter =
      new Converter<>() {
        @Override
        public BigDecimal fromObject(String functionName, Object o) throws ParserException {
          try {
            return new BigDecimal(o.toString());
          } catch (NumberFormatException e) {
            throw new ParserException(e);
          }
        }

        @Override
        public Object toObject(String functionName, BigDecimal t) {
          return t;
        }
      };

  public static final Converter<String> stringConverter =
      new Converter<>() {
        @Override
        public String fromObject(String functionName, Object o) {
          return o.toString();
        }

        @Override
        public Object toObject(String functionName, String s) {
          return s;
        }
      };

  public static final Converter<Zone> zoneConverter =
      new Converter<>() {
        @Override
        public Zone fromObject(String functionName, Object o) throws ParserException {
          return FunctionUtil.getZoneRenderer(functionName, o.toString()).getZone();
        }

        @Override
        public Object toObject(String functionName, Zone zone) {
          // TODO For some functions, it might be the name. Will have to see what is common :shrug:.
          return zone.getId();
        }
      };
}
