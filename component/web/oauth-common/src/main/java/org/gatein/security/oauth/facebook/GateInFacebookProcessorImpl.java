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

package org.gatein.security.oauth.facebook;

import java.io.IOException;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.organization.UserProfile;
import org.exoplatform.web.security.security.SecureRandomService;
import org.gatein.security.oauth.spi.InteractionState;
import org.gatein.security.oauth.exception.OAuthException;
import org.gatein.security.oauth.exception.OAuthExceptionCode;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.log.ExoLogger;
import org.gatein.security.oauth.spi.OAuthCodec;
import org.gatein.security.oauth.common.OAuthConstants;
import org.gatein.security.oauth.social.FacebookPrincipal;
import org.gatein.security.oauth.social.FacebookProcessor;
import org.gatein.security.oauth.utils.OAuthPersistenceUtils;

/**
 * {@inheritDoc}
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class GateInFacebookProcessorImpl implements GateInFacebookProcessor {

  private static Log                log = ExoLogger.getLogger(GateInFacebookProcessorImpl.class);

    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String redirectURL;
    private final FacebookProcessor facebookProcessor;
    private final SecureRandomService secureRandomService;
    private final int chunkLength;

    public GateInFacebookProcessorImpl(ExoContainerContext context, InitParams params, SecureRandomService secureRandomService) {
        this.clientId = params.getValueParam("clientId").getValue();
        this.clientSecret = params.getValueParam("clientSecret").getValue();
        String scope = params.getValueParam("scope").getValue();
        String redirectURL = params.getValueParam("redirectURL").getValue();

        if (clientId == null || clientId.length() == 0 || clientId.trim().equals("<<to be replaced>>")) {
            throw new IllegalArgumentException("Property 'clientId' needs to be provided. The value should be " +
                    "clientId of your Facebook application");
        }

        if (clientSecret == null || clientSecret.length() == 0 || clientSecret.trim().equals("<<to be replaced>>")) {
            throw new IllegalArgumentException("Property 'clientSecret' needs to be provided. The value should be " +
                    "clientSecret of your Facebook application");
        }

        this.scope = scope == null ? "email" : scope;

        if (redirectURL == null || redirectURL.length() == 0) {
            this.redirectURL = "http://localhost:8080/" + context.getName() + OAuthConstants.FACEBOOK_AUTHENTICATION_URL_PATH;
        }  else {
            this.redirectURL = redirectURL.replaceAll("@@portal.container.name@@", context.getName());
        }

        this.chunkLength = OAuthPersistenceUtils.getChunkLength(params);

        if (log.isDebugEnabled()) {
            log.debug("configuration: clientId=" + this.clientId +
                    ", clientSecret=" + clientSecret +
                    ", scope=" + this.scope +
                    ", redirectURL=" + this.redirectURL +
                    ", chunkLength=" + this.chunkLength);
        }

        // Use empty rolesList because we don't need rolesList for GateIn integration
        this.facebookProcessor = new FacebookProcessor(this.clientId , this.clientSecret, this.scope, this.redirectURL);
        this.secureRandomService = secureRandomService;
    }

    @Override
    public InteractionState<FacebookAccessTokenContext> processOAuthInteraction(HttpServletRequest httpRequest, HttpServletResponse httpResponse, String scope) throws IOException {
        return processOAuthInteractionImpl(httpRequest, httpResponse, new FacebookProcessor(this.clientId, this.clientSecret, scope, this.redirectURL));
    }


    @Override
    public InteractionState<FacebookAccessTokenContext> processOAuthInteraction(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        return processOAuthInteractionImpl(httpRequest, httpResponse, this.facebookProcessor);
    }


    protected InteractionState<FacebookAccessTokenContext> processOAuthInteractionImpl(HttpServletRequest httpRequest, HttpServletResponse httpResponse, FacebookProcessor facebookProcessor) throws IOException {
        HttpSession session = httpRequest.getSession();
        String state = (String) session.getAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE);

        if (log.isTraceEnabled()) {
            log.trace("state=" + state);
        }

        // Very initial request to portal
        if (state == null || state.isEmpty()) {
            String verificationState = String.valueOf(secureRandomService.getSecureRandom().nextLong());
            facebookProcessor.initialInteraction(httpRequest, httpResponse, verificationState);
            state = InteractionState.State.AUTH.name();
            session.setAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE, state);
            session.setAttribute(OAuthConstants.ATTRIBUTE_VERIFICATION_STATE, verificationState);
            return new InteractionState<FacebookAccessTokenContext>(InteractionState.State.valueOf(state), null);
        }

        // We are authenticated in Facebook and our app is authorized. Finish OAuth handshake by obtaining accessToken and initial info
        if (state.equals(InteractionState.State.AUTH.name())) {
            String accessToken = facebookProcessor.getAccessToken(httpRequest, httpResponse);

            if (accessToken == null) {
                throw new OAuthException(OAuthExceptionCode.FACEBOOK_ERROR, "AccessToken was null");
            } else {
                Set<String> scopes = facebookProcessor.getScopes(accessToken);
                state = InteractionState.State.FINISH.name();

                // Clear session attributes
                session.removeAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE);
                session.removeAttribute(OAuthConstants.ATTRIBUTE_VERIFICATION_STATE);

                FacebookAccessTokenContext accessTokenContext = new FacebookAccessTokenContext(accessToken, scopes);
                return new InteractionState<FacebookAccessTokenContext>(InteractionState.State.valueOf(state), accessTokenContext);
            }
        }

        // Likely shouldn't happen...
        return new InteractionState<FacebookAccessTokenContext>(InteractionState.State.valueOf(state), null);
    }

    @Override
    public FacebookPrincipal getPrincipal(FacebookAccessTokenContext accessTokenContext) {
        String accessToken = accessTokenContext.getAccessToken();
        return facebookProcessor.getPrincipal(accessToken);
    }

    @Override
    public String getAvatar(FacebookAccessTokenContext accessTokenContext) {
        String accessToken = accessTokenContext.getAccessToken();
        return facebookProcessor.getUserAvatarURL(accessToken);
    }

  @Override
    public void saveAccessTokenAttributesToUserProfile(UserProfile userProfile, OAuthCodec codec, FacebookAccessTokenContext accessTokenContext) {
        String realAccessToken = accessTokenContext.getAccessToken();
        String encodedAccessToken = codec.encodeString(realAccessToken);

        // Encoded accessToken could be longer than 255 characters. So we need to split it
        OAuthPersistenceUtils.saveLongAttribute(encodedAccessToken, userProfile, OAuthConstants.PROFILE_FACEBOOK_ACCESS_TOKEN,
                true, chunkLength);

        userProfile.setAttribute(OAuthConstants.PROFILE_FACEBOOK_SCOPE, accessTokenContext.getScopesAsString());
    }

    @Override
    public FacebookAccessTokenContext getAccessTokenFromUserProfile(UserProfile userProfile, OAuthCodec codec) {
        String encodedAccessToken = OAuthPersistenceUtils.getLongAttribute(userProfile, OAuthConstants.PROFILE_FACEBOOK_ACCESS_TOKEN, true);

        // We don't have token in userProfile
        if (encodedAccessToken == null) {
            return null;
        }

        String accessToken = codec.decodeString(encodedAccessToken);
        String scopesAsString = userProfile.getAttribute(OAuthConstants.PROFILE_FACEBOOK_SCOPE);
        return new FacebookAccessTokenContext(accessToken, scopesAsString);
    }

    @Override
    public void removeAccessTokenFromUserProfile(UserProfile userProfile) {
        OAuthPersistenceUtils.removeLongAttribute(userProfile, OAuthConstants.PROFILE_FACEBOOK_ACCESS_TOKEN, true);
    }

    @Override
    public void revokeToken(FacebookAccessTokenContext accessToken) {
        String realAccessToken = accessToken.getAccessToken();
        facebookProcessor.revokeToken(realAccessToken);
    }

    @Override
    public FacebookAccessTokenContext validateTokenAndUpdateScopes(FacebookAccessTokenContext accessToken) throws OAuthException {
        Set<String> scopes = facebookProcessor.getScopes(accessToken.getAccessToken());
        return new FacebookAccessTokenContext(accessToken.getAccessToken(), scopes);
    }

    @Override
    public <C> C getAuthorizedSocialApiObject(FacebookAccessTokenContext accessToken, Class<C> socialApiObjectType) {
        if (log.isDebugEnabled()) {
            log.debug("Class '" + socialApiObjectType + "' not supported by this processor");
        }
        return null;
    }
}
