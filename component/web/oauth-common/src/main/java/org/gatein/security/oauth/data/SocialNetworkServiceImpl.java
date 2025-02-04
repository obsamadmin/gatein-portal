/*
 * JBoss, a division of Red Hat
 * Copyright 2013, Red Hat Middleware, LLC, and individual
 * contributors as indicated by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.gatein.security.oauth.data;

import java.lang.reflect.Method;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.component.ComponentRequestLifecycle;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.organization.*;
import org.exoplatform.web.security.codec.AbstractCodec;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;
import org.gatein.security.oauth.spi.AccessTokenContext;
import org.gatein.security.oauth.exception.OAuthException;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.log.ExoLogger;
import org.gatein.security.oauth.spi.OAuthCodec;
import org.gatein.security.oauth.spi.OAuthProviderProcessor;
import org.gatein.security.oauth.spi.OAuthProviderType;
import org.gatein.security.oauth.exception.OAuthExceptionCode;
import org.gatein.security.oauth.spi.SocialNetworkService;

/**
 * {@inheritDoc}
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class SocialNetworkServiceImpl implements SocialNetworkService, OAuthCodec {

  private static Log          log = ExoLogger.getLogger(SocialNetworkServiceImpl.class);

    private OrganizationService orgService;
    private AbstractCodec codec;

    public SocialNetworkServiceImpl(OrganizationService orgService, CodecInitializer codecInitializer) throws TokenServiceInitializationException {
        this.orgService = orgService;
        this.codec = codecInitializer.getCodec();
    }

    @Override
    public User findUserByOAuthProviderUsername(OAuthProviderType oauthProviderType, String oauthProviderUsername) {
        try {
            begin();

            UserHandler userHandler = orgService.getUserHandler();
            try {
                // TODO: Ugly, but it's used due to OrganizationService API limitations because it doesn't allow to find user by unique userProfile attribute
                Method m = userHandler.getClass().getMethod("findUserByUniqueAttribute", String.class, String.class, UserStatus.class);
                return (User)m.invoke(userHandler, oauthProviderType.getUserNameAttrName(), oauthProviderUsername, UserStatus.ANY);
            } catch (NoSuchMethodException e) {
                String error = "Method findUserByUniqueAttribute(String, String, boolean) is not available on userHandler object " + userHandler +
                        "of class " + userHandler.getClass();
                log.error(error);
                throw new OAuthException(OAuthExceptionCode.PERSISTENCE_ERROR, error, e);
            } catch (Exception e) {
                throw new OAuthException(OAuthExceptionCode.PERSISTENCE_ERROR, e);
            }

        } finally {
            end();
        }
    }

    @Override
    public <T extends AccessTokenContext> void updateOAuthAccessToken(OAuthProviderType<T> oauthProviderType, String username, T accessToken) {
        try {
            begin();

            UserProfileHandler userProfileHandler = orgService.getUserProfileHandler();
            UserProfile userProfile = userProfileHandler.findUserProfileByName(username);
            if(userProfile == null) {
                userProfile = userProfileHandler.createUserProfileInstance(username);
            }

            OAuthProviderProcessor<T> oauthProviderProcessor = oauthProviderType.getOauthProviderProcessor();
            oauthProviderProcessor.saveAccessTokenAttributesToUserProfile(userProfile, this, accessToken);

            userProfileHandler.saveUserProfile(userProfile, true);
        } catch (OAuthException oauthEx) {
            throw oauthEx;
        } catch (Exception e) {
            throw new OAuthException(OAuthExceptionCode.PERSISTENCE_ERROR, e);
        } finally {
            end();
        }
    }

    @Override
    public <T extends AccessTokenContext> T getOAuthAccessToken(OAuthProviderType<T> oauthProviderType, String username) {
        try {
            begin();
            UserProfileHandler userProfileHandler = orgService.getUserProfileHandler();
            UserProfile userProfile = userProfileHandler.findUserProfileByName(username);
            if(userProfile == null) {
                //If use have not profile, he also have not OauthAccessToken
                return null;
            }

            OAuthProviderProcessor<T> oauthProviderProcessor = oauthProviderType.getOauthProviderProcessor();
            return oauthProviderProcessor.getAccessTokenFromUserProfile(userProfile, this);
        } catch (Exception e) {
            throw new OAuthException(OAuthExceptionCode.PERSISTENCE_ERROR, e);
        } finally {
            end();
        }
    }

    @Override
    public <T extends AccessTokenContext> void removeOAuthAccessToken(OAuthProviderType<T> oauthProviderType, String username) {
        try {
            begin();
            UserProfileHandler userProfileHandler = orgService.getUserProfileHandler();
            UserProfile userProfile = userProfileHandler.findUserProfileByName(username);
            if(userProfile == null) {
                //Don't need to remove OauthAccessToken if user-profile does not exists
                return;
            }

            OAuthProviderProcessor<T> oauthProviderProcessor = oauthProviderType.getOauthProviderProcessor();
            oauthProviderProcessor.removeAccessTokenFromUserProfile(userProfile);

            userProfileHandler.saveUserProfile(userProfile, true);
        } catch (Exception e) {
            throw new OAuthException(OAuthExceptionCode.PERSISTENCE_ERROR, e);
        } finally {
            end();
        }
    }

  @Override
  public User findUserByEmail(String email) {
    try {
      begin();
      UserHandler userHandler = orgService.getUserHandler();
      Query queryByEmail = new Query();
      queryByEmail.setEmail(email);
      ListAccess<User> users = userHandler.findUsersByQuery(queryByEmail);
      if(users == null || users.getSize() == 0 || users.getSize() > 1) {
        return null;
      } else if(users.getSize() == 1) {
        return users.load(0, 1)[0];
      }
    } catch (OAuthException oauthEx) {
      throw oauthEx;
    } catch (Exception e) {
      throw new OAuthException(OAuthExceptionCode.PERSISTENCE_ERROR, e);
    } finally {
      end();
    }
    return null;
  }

  @Override
    public <T extends AccessTokenContext> void updateOAuthInfo(OAuthProviderType<T> oauthProviderType, String username, String oauthUsername, T accessToken) {
        try {
            begin();
            UserProfileHandler userProfileHandler = orgService.getUserProfileHandler();
            UserProfile userProfile = userProfileHandler.findUserProfileByName(username);
            if(userProfile == null) {
                userProfile = userProfileHandler.createUserProfileInstance(username);
            }

            userProfile.setAttribute(oauthProviderType.getUserNameAttrName(), oauthUsername);

            OAuthProviderProcessor<T> oauthProviderProcessor = oauthProviderType.getOauthProviderProcessor();
            oauthProviderProcessor.saveAccessTokenAttributesToUserProfile(userProfile, this, accessToken);
            userProfileHandler.saveUserProfile(userProfile, true);
        } catch (OAuthException oauthEx) {
            throw oauthEx;
        } catch (Exception e) {
            throw new OAuthException(OAuthExceptionCode.PERSISTENCE_ERROR, e);
        } finally {
            end();
        }
    }

    @Override
    public String encodeString(String input) {
        if (input == null) {
            return null;
        } else {
            return codec.encode(input);
        }
    }

    @Override
    public String decodeString(String input) {
        if (input == null) {
            return null;
        } else {
            return codec.decode(input);
        }
    }

    void begin() {
        if (orgService instanceof ComponentRequestLifecycle) {
            RequestLifeCycle.begin((ComponentRequestLifecycle) orgService);
        }
    }
    
    void end() {
        if (orgService instanceof ComponentRequestLifecycle) {
            RequestLifeCycle.end();
        }
    }

}
