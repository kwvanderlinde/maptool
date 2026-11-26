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
package net.rptools.maptool.client.script.javascript;

import java.util.*;
import java.util.List;
import javax.script.*;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.MacroJavaScriptBridge;
import net.rptools.maptool.client.script.javascript.api.*;
import net.rptools.maptool.language.I18N;
import net.rptools.parser.ParserException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.*;
import org.reflections.Reflections;

public class JSScriptEngine {
  private static final Logger log = LogManager.getLogger(JSScriptEngine.class);
  private static List<ApiDefinition> apiDefinitions = null;

  private static final JSScriptEngine mainEngine = new JSScriptEngine();
  private static final JSScriptEngine addOnEngine = new JSScriptEngine();

  public static JSScriptEngine getMainEngine() {
    return mainEngine;
  }

  public static JSScriptEngine getAddOnEngine() {
    return addOnEngine;
  }

  private static List<ApiDefinition> getApiDefinitions() {
    if (apiDefinitions == null) {
      Reflections reflections = new Reflections("net.rptools.maptool.client.script.javascript.api");
      Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(MapToolJSAPIDefinition.class);

      var bindings = new ArrayList<ApiDefinition>();
      for (Class<?> apiClass : annotated) {
        Class<? extends MapToolJSAPIInterface> casted;
        try {
          casted = apiClass.asSubclass(MapToolJSAPIInterface.class);
        } catch (ClassCastException e) {
          log.error(
              "Could not load API class {} since it does not implement {}",
              apiClass,
              MapToolJSAPIInterface.class);
          continue;
        }

        var annotation = casted.getAnnotation(MapToolJSAPIDefinition.class);
        if (annotation == null) {
          log.error(
              "Unexpected condition: class {} does not have annotation {}",
              casted,
              MapToolJSAPIDefinition.class);
          continue;
        }

        bindings.add(new ApiDefinition(annotation, casted));
      }

      apiDefinitions = List.copyOf(bindings);
    }
    return apiDefinitions;
  }

  private final HostAccess hostAccess;
  private final Map<String, JSContext> contexts = new HashMap<>();
  private final Stack<JSContext> contextStack = new Stack<>();

  private JSScriptEngine() {
    {
      HostAccess.Builder habuilder = HostAccess.newBuilder();
      habuilder.allowAccessAnnotatedBy(HostAccess.Export.class);
      habuilder.allowArrayAccess(true);
      habuilder.allowListAccess(true);
      habuilder.allowMapAccess(true);
      habuilder.targetTypeMapping(
          Value.class, Object.class, (v) -> v.hasArrayElements(), (v) -> v.as(List.class));
      hostAccess = habuilder.build();
    }
  }

  public JSContext getCurrentContext() {
    return contextStack.peek();
  }

  public boolean inTrustedContext() {
    if (contextStack.empty()) {
      return false;
    }
    return contextStack.peek().trusted();
  }

  public JSContext registerContext(String name, boolean trusted, boolean makeTrusted)
      throws ParserException {
    if (!trusted) {
      JSContext jc = contexts.get(name);
      if (jc != null) {
        throw new ParserException("Context " + name + " already exists");
      }
    }
    if (!trusted && makeTrusted) {
      throw new ParserException("Cannot make a trusted JS context from an untrusted context");
    }

    JSContext c = makeContext(name, makeTrusted);
    contexts.put(name, c);
    return c;
  }

  public void removeContext(String name, boolean trusted) throws ParserException {
    if (trusted) {
      contexts.remove(name);
      return;
    }
    JSContext c = contexts.get(name);
    if (c == null || c.trusted()) {
      throw new ParserException(I18N.getText("macro.function.general.noPermJS", name));
    }
    contexts.remove(name);
  }

  public void resetContexts() {
    contexts.clear();
  }

  public Collection<JSContext> getContexts() {
    return List.copyOf(contexts.values());
  }

  private JSContext makeContext(String name, boolean trusted) {
    Context graalContext =
        Context.newBuilder("js")
            .allowHostAccess(hostAccess)
            .option("js.ecmascript-version", "2021")
            .build();
    Value bindings = graalContext.getBindings("js");

    for (var definition : getApiDefinitions()) {
      try {
        MapToolJSAPIInterface apiObject = definition.type().getDeclaredConstructor().newInstance();
        bindings.putMember(definition.annotation().javaScriptVariableName(), apiObject);
      } catch (Exception e) {
        log.error("Could not add API object {}", definition.type().getName(), e);
      }
    }

    return new JSContext(trusted, graalContext, name);
  }

  public Value evalScript(String contextName, String script)
      throws ScriptException, ParserException {
    return evalScript(contextName, script, MapTool.getParser().isMacroTrusted());
  }

  public Value evalScript(String contextName, String script, boolean trusted)
      throws ScriptException, ParserException {
    if (contextName == null) {
      return evalAnonymous(script);
    }
    JSContext jc = contexts.get(contextName);
    if (jc == null) {
      jc = registerContext(contextName, trusted, trusted);
    }

    return evalScript(jc, script, trusted);
  }

  public Value evalScript(JSContext context, String script, boolean trusted)
      throws ScriptException, ParserException {

    if (context.trusted() && !trusted) {
      throw new ParserException(I18N.getText("macro.function.general.noPermJS", context.name()));
    }
    contextStack.push(context);
    try {
      return context.context().eval("js", script);
    } finally {
      contextStack.pop();
    }
  }

  public Object applyFunction(JSAPIRegisteredMacro macro, Object[] args) {
    contextStack.push(macro.context);
    Object[] modifiedArgs = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      modifiedArgs[i] = MacroJavaScriptBridge.getInstance().HostObjectToJavaScriptType(args[i]);
    }
    try {
      return macro.callable.apply(modifiedArgs);
    } finally {
      contextStack.pop();
    }
  }

  public Value evalAnonymous(String script) throws ScriptException {

    StringBuilder wrapped = new StringBuilder();
    wrapped
        .append("(function() { var args = MTScript.getMTScriptCallingArgs(); ")
        .append(script)
        .append("})();");
    JSContext jc = makeContext("<anonymous>", MapTool.getParser().isMacroTrusted());
    contextStack.push(jc);
    try {
      return jc.context().eval("js", wrapped.toString());
    } finally {
      contextStack.pop();
    }
  }

  private record ApiDefinition(
      MapToolJSAPIDefinition annotation, Class<? extends MapToolJSAPIInterface> type) {}
}
