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
package net.rptools.maptool.client;

import javax.annotation.Nullable;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Token;

/**
 * An identity that can be impersonated: either a token, or any name at all.
 *
 * <p>Instances are immutable. Note however that {@link #getToken()} and {@link #validToken()} are
 * not plain getters: they look the token up by its GUID each time, on the current map, so an
 * identity that resolved to a token when it was created can stop resolving later, or resolve
 * differently depending on which map is being viewed. With no map open, or no window at all (see
 * {@link MapToolClient#getCurrentZone()}), they find no token.
 *
 * <p>This was previously nested inside {@code CommandPanel}. It moved out alongside {@link
 * ImpersonationState}, so that code needing to know who is speaking does not depend on a Swing
 * component to do it. The behaviour is unchanged.
 */
public class TokenIdentity {

  /** The name of the identity. If null, nothing is impersonated. */
  private final String identityName;

  /** The GUID of the identity. */
  private final GUID identityGUID;

  /** Whether the player is allowed to set the token in the Impersonate panel. */
  private final boolean canImpersonate;

  /**
   * Primary constructor.
   *
   * @param identityName The name to display for the impersonation.
   * @param identityGUID The ID of the token being impersonated.
   * @param canImpersonate Whether this identify can be shown in the impersonation panel.
   */
  public TokenIdentity(
      @Nullable GUID identityGUID, @Nullable String identityName, boolean canImpersonate) {
    this.identityGUID = identityGUID;
    this.identityName = identityName;
    this.canImpersonate = canImpersonate;
  }

  /** Creates an empty identity (nothing impersonated). */
  public TokenIdentity() {
    this((GUID) null, (String) null, false);
  }

  /**
   * Creates an identity from a GUID. If there is no associated token, the identity uses the
   * specified backup name.
   *
   * @param tokenId the token GUID
   * @param backupName the backup name to impersonate if the token is null
   */
  public TokenIdentity(GUID tokenId, String backupName) {
    this(tokenId, backupName, true);
  }

  /**
   * Returns the text to show for this identity.
   *
   * <p>When nothing is impersonated this is the <em>player's</em> name, not an empty string. Chat
   * formatting relies on that, and so does {@code MacroManager}, which resolves this text to a
   * token when no GUID is available.
   *
   * @return a string representing the identity.
   */
  public String getIdentity() {
    if (identityName == null) {
      if (identityGUID == null) {
        return MapTool.getPlayer().getName();
      } else {
        return identityGUID.toString();
      }
    }
    return identityName;
  }

  /**
   * @return a string for the character label of the identity.
   */
  public String getCharacterLabel() {
    return hasName() ? identityName : "";
  }

  /**
   * @return the GUID of the identity.
   */
  public GUID getIdentityGUID() {
    return identityGUID;
  }

  /**
   * @return whether this identity may be shown in the Impersonate panel.
   */
  public boolean canImpersonate() {
    return canImpersonate;
  }

  /**
   * @return the token of the identity, looked up on the current map, or {@code null} if it is not
   *     there or no map is open.
   */
  public @Nullable Token getToken() {
    if (identityGUID == null) {
      return null;
    }

    var zone = MapTool.getClient().getCurrentZone();
    if (zone == null) {
      return null;
    }

    return zone.getToken(identityGUID);
  }

  /**
   * @return whether the identity has a name.
   */
  public boolean hasName() {
    return identityName != null;
  }

  /**
   * @return whether the token can still be found on the current map; {@code false} if no map is
   *     open.
   */
  public boolean validToken() {
    return getToken() != null;
  }
}
