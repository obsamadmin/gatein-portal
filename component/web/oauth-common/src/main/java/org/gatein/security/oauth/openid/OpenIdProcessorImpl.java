package org.gatein.security.oauth.openid;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.github.scribejava.core.model.OAuth2AccessToken;
import io.jsonwebtoken.*;
import org.apache.commons.lang.StringUtils;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.log.ExoLogger;
import org.gatein.security.oauth.common.OAuthConstants;
import org.gatein.security.oauth.exception.OAuthException;
import org.gatein.security.oauth.exception.OAuthExceptionCode;
import org.gatein.security.oauth.spi.InteractionState;
import org.gatein.security.oauth.spi.OAuthCodec;
import org.gatein.security.oauth.utils.HttpResponseContext;
import org.gatein.security.oauth.utils.OAuthPersistenceUtils;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.organization.UserProfile;
import org.exoplatform.web.security.security.SecureRandomService;
import org.gatein.security.oauth.utils.OAuthUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.picocontainer.Startable;

/**
 * {@inheritDoc}
 */
public class OpenIdProcessorImpl implements OpenIdProcessor, Startable {

  private static Log                  log    = ExoLogger.getLogger(OpenIdProcessorImpl.class);

  private final String                redirectURL;

  private String                      authenticationURL;

  private String                      accessTokenURL;

  private String                      userInfoURL;

  private final String                clientID;

  private final String                clientSecret;

  private final Set<String>           scopes = new HashSet<String>();

  private final String                accessType;

  private final String                wellKnownConfigurationUrl;

  private final String                applicationName;

  private String                      issuer;

  private final int                   chunkLength;


  private RemoteJwkSigningKeyResolver remoteJwkSigningKeyResolver;

  private final SecureRandomService   secureRandomService;

  public OpenIdProcessorImpl(ExoContainerContext context, InitParams params, SecureRandomService secureRandomService) {
    this.clientID = params.getValueParam("clientId").getValue();
    this.clientSecret = params.getValueParam("clientSecret").getValue();
    String redirectURLParam = params.getValueParam("redirectURL").getValue();
    this.wellKnownConfigurationUrl = params.getValueParam("wellKnownConfigurationUrl").getValue();
    String scope = params.getValueParam("scope").getValue();
    this.accessType = params.getValueParam("accessType").getValue();
    ValueParam appNameParam = params.getValueParam("applicationName");
    if (appNameParam != null && appNameParam.getValue() != null) {
      applicationName = appNameParam.getValue();
    } else {
      applicationName = "GateIn portal";
    }
    if (clientID == null || clientID.length() == 0 || clientID.trim().equals("<<to be replaced>>")) {
      throw new IllegalArgumentException("Property 'clientId' needs to be provided. The value should be "
          + "clientId of your OpenId application");
    }

    if (clientSecret == null || clientSecret.length() == 0 || clientSecret.trim().equals("<<to be replaced>>")) {
      throw new IllegalArgumentException("Property 'clientSecret' needs to be provided. The value should be "
          + "clientSecret of your OpenId application");
    }


    if (redirectURLParam == null || redirectURLParam.length() == 0) {
      this.redirectURL = "http://localhost:8080/" + context.getName() + OAuthConstants.OPEN_ID_AUTHENTICATION_URL_PATH;
    } else {
      this.redirectURL = redirectURLParam.replaceAll("@@portal.container.name@@", context.getName());
    }

    addScopesFromString(scope, this.scopes);

    this.chunkLength = OAuthPersistenceUtils.getChunkLength(params);

    if (log.isDebugEnabled()) {
      log.debug("configuration: clientId=" + clientID
          +
                    ", clientSecret=" + clientSecret +
                    ", redirectURL=" + redirectURL +
                    ", scope=" + scopes +
                    ", accessType=" + accessType +
                    ", applicationName=" + applicationName +
                    ", chunkLength=" + chunkLength);
    }

    this.secureRandomService = secureRandomService;
  }

  @Override
  public InteractionState<OpenIdAccessTokenContext> processOAuthInteraction(HttpServletRequest httpRequest,
                                                                            HttpServletResponse httpResponse) throws IOException,
                                                                                                              OAuthException {
    return processOAuthInteractionImpl(httpRequest, httpResponse, this.scopes);
  }

  @Override
  public InteractionState<OpenIdAccessTokenContext> processOAuthInteraction(HttpServletRequest httpRequest,
                                                                            HttpServletResponse httpResponse,
                                                                            String scope) throws IOException, OAuthException {
    Set<String> scopes = new HashSet<String>();
    addScopesFromString(scope, scopes);
    return processOAuthInteractionImpl(httpRequest, httpResponse, scopes);
  }

  protected InteractionState<OpenIdAccessTokenContext> processOAuthInteractionImpl(HttpServletRequest request,
                                                                                   HttpServletResponse response,
                                                                                   Set<String> scopes) throws IOException {
    HttpSession session = request.getSession();
    String state = (String) session.getAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE);

    // Very initial request to portal
    if (state == null || state.isEmpty()) {

      return initialInteraction(request, response);
    } else if (state.equals(InteractionState.State.AUTH.name())) {
      OAuth2AccessToken tokenResponse = obtainAccessToken(request);
      OpenIdAccessTokenContext accessTokenContext = validateTokenAndUpdateScopes(new OpenIdAccessTokenContext(tokenResponse));
      // // Clear session attributes
      session.removeAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE);
      session.removeAttribute(OAuthConstants.ATTRIBUTE_VERIFICATION_STATE);

      return new InteractionState<>(InteractionState.State.FINISH, accessTokenContext);
    }

    // Likely shouldn't happen...
    return new InteractionState<>(InteractionState.State.valueOf(state), null);
  }

  //
  protected InteractionState<OpenIdAccessTokenContext> initialInteraction(HttpServletRequest request,
                                                                          HttpServletResponse response) throws IOException {
    String verificationState = String.valueOf(secureRandomService.getSecureRandom().nextLong());
    String authorizeUrl = this.authenticationURL + "?" + "response_type=code" + "&client_id=" + this.clientID + "&scope="
        + this.scopes.stream().collect(Collectors.joining(" ")) + "&redirect_uri=" + this.redirectURL + "&state="
        + verificationState;

    if (log.isTraceEnabled()) {
      log.trace("Starting OAuth2 interaction with OpenId");
      log.trace("URL to send to OpenId: " + authorizeUrl);
    }

    HttpSession session = request.getSession();
    session.setAttribute(OAuthConstants.ATTRIBUTE_VERIFICATION_STATE, verificationState);
    session.setAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE, InteractionState.State.AUTH.name());
    response.sendRedirect(authorizeUrl);
    return new InteractionState<OpenIdAccessTokenContext>(InteractionState.State.AUTH, null);
  }

  protected OAuth2AccessToken obtainAccessToken(HttpServletRequest request) throws IOException {
    HttpSession session = request.getSession();
    String stateFromSession = (String) session.getAttribute(OAuthConstants.ATTRIBUTE_VERIFICATION_STATE);
    String stateFromRequest = request.getParameter(OAuthConstants.STATE_PARAMETER);
    if (stateFromSession == null || stateFromRequest == null || !stateFromSession.equals(stateFromRequest)) {
      throw new OAuthException(OAuthExceptionCode.INVALID_STATE,
                               "Validation of state parameter failed. stateFromSession=" + stateFromSession
                                   + ", stateFromRequest=" + stateFromRequest);
    }

    // Check if user didn't permit scope
    String error = request.getParameter(OAuthConstants.ERROR_PARAMETER);
    if (error != null) {
      if (OAuthConstants.ERROR_ACCESS_DENIED.equals(error)) {
        throw new OAuthException(OAuthExceptionCode.USER_DENIED_SCOPE, error);
      } else {
        throw new OAuthException(OAuthExceptionCode.UNKNOWN_ERROR, error);
      }
    } else {
      String code = request.getParameter(OAuthConstants.CODE_PARAMETER);

      Map<String, String> params = new HashMap<String, String>();
      params.put(OAuthConstants.CODE_PARAMETER, code);
      params.put(OAuthConstants.CLIENT_ID_PARAMETER, clientID);
      params.put(OAuthConstants.CLIENT_SECRET_PARAMETER, clientSecret);
      params.put(OAuthConstants.REDIRECT_URI_PARAMETER, redirectURL);
      params.put("grant_type", "authorization_code");


      OAuth2AccessToken tokenResponse = new OpenIdRequest<OAuth2AccessToken>() {

        @Override
        protected URL createURL() throws IOException {
          return sendAccessTokenRequest();
        }

        @Override
        protected OAuth2AccessToken invokeRequest(Map<String, String> params) throws IOException, JSONException {
          URL url = createURL();
          HttpURLConnection conn = (HttpURLConnection) url.openConnection();
          conn.setRequestMethod("POST");
          conn.setDoOutput(true);
          String urlParameters = params.keySet().stream().map(s -> s + "=" + params.get(s)).collect(Collectors.joining("&"));
          conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
          conn.setRequestProperty("charset", "utf-8");
          conn.setRequestProperty("Content-Length", Integer.toString(urlParameters.getBytes().length));
          conn.setUseCaches(false);
          try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
            wr.write(urlParameters.getBytes());
          }
          HttpResponseContext httpResponse = OAuthUtils.readUrlContent(conn);
          if (httpResponse.getResponseCode() == 200) {
            return parseResponse(httpResponse.getResponse());
          } else if (httpResponse.getResponseCode() == 400) {
            String errorMessage = "Error when obtaining content from OpenId. Error details: " + httpResponse.getResponse();
            log.warn(errorMessage);
            throw new OAuthException(OAuthExceptionCode.ACCESS_TOKEN_ERROR, errorMessage);
          } else {
            String errorMessage = "Unspecified IO error. Http response code: " + httpResponse.getResponseCode() + ", details: "
                + httpResponse.getResponse();
            log.warn(errorMessage);
            throw new OAuthException(OAuthExceptionCode.IO_ERROR, errorMessage);
          }
        }


        @Override
        protected OAuth2AccessToken parseResponse(String httpResponse) throws JSONException {
          JSONObject json = new JSONObject(httpResponse);
          String accessToken = json.getString(OAuthConstants.ACCESS_TOKEN_PARAMETER);
          if (log.isTraceEnabled())
            log.trace("Access Token=" + accessToken);

          return new OAuth2AccessToken(accessToken,
                                       json.getString(OAuthConstants.TOKEN_TYPE_PARAMETER),
                                       json.getInt(OAuthConstants.EXPIRES_IN_PARAMETER),
                                       null,
                                       json.has(OAuthConstants.SCOPE_PARAMETER) ? json.getString(OAuthConstants.SCOPE_PARAMETER)
                                                                                : null,
                                       json.toString());

        }

      }.executeRequest(params);

      if (log.isTraceEnabled()) {
        log.trace("Successfully obtained accessToken from openid: " + tokenResponse);
      }

      return tokenResponse;
    }
  }


  @Override
  public OpenIdAccessTokenContext validateTokenAndUpdateScopes(OpenIdAccessTokenContext accessToken) throws OAuthException {
    String scope = accessToken.getTokenData().getScope();
    Claims customClaims;
    try {
      String tokenId = new JSONObject(accessToken.accessToken.getRawResponse()).get("id_token").toString();
      customClaims = Jwts.parserBuilder()
                         .setSigningKeyResolver(remoteJwkSigningKeyResolver)
                         .requireIssuer(this.issuer)
                         .requireAudience(this.clientID)
                         .setAllowedClockSkewSeconds(60)
                         .build()
                         .parseClaimsJws(tokenId)
                         .getBody();
    } catch (IncorrectClaimException e) {
      log.error("Issuer or audience have not the correct value in the token", e);
      throw new OAuthException(OAuthExceptionCode.TOKEN_VALIDATION_ERROR,
                               "Issuer or audience have not the correct value in the token",
                               e);
    } catch (MissingClaimException e) {
      log.error("Required claims (issuer or audience) are missing in JWT Token", e);
      throw new OAuthException(OAuthExceptionCode.TOKEN_VALIDATION_ERROR,
                               "Required claims (issuer or audience) are missing in JWT Token",
                               e);
    } catch (JSONException e) {
      log.error("Unable to parse the accessToken");
      throw new OAuthException(OAuthExceptionCode.ACCESS_TOKEN_ERROR, "Unable to parse the accessToken", e);
    }
    OpenIdAccessTokenContext accessTokenContext = new OpenIdAccessTokenContext(accessToken.getTokenData(), scope);
    if (customClaims != null) {
      accessTokenContext.addCustomClaims(customClaims.entrySet()
                                                     .stream()
                                                     .filter(element -> element.getKey().equals("given_name")
                                                         || element.getKey().equals("family_name")
                                                         || element.getKey().equals("email"))
                                                     .collect(Collectors.toMap(Map.Entry::getKey, x -> x.getValue().toString())));
    }
    return accessTokenContext;
  }

  @Override
  public <C> C getAuthorizedSocialApiObject(OpenIdAccessTokenContext accessToken, Class<C> socialApiObjectType) {
    return null;
  }

  @Override
  public void saveAccessTokenAttributesToUserProfile(UserProfile userProfile,
                                                     OAuthCodec codec,
                                                     OpenIdAccessTokenContext accessToken) {
    String encodedAccessToken = codec.encodeString(accessToken.accessToken.getAccessToken());
    OAuthPersistenceUtils.saveLongAttribute(encodedAccessToken,
                                            userProfile,
                                            OAuthConstants.PROFILE_OPEN_ID_ACCESS_TOKEN,
                                            false,
                                            chunkLength);

  }

  @Override
  public OpenIdAccessTokenContext getAccessTokenFromUserProfile(UserProfile userProfile, OAuthCodec codec) {
    String encodedAccessToken = OAuthPersistenceUtils.getLongAttribute(userProfile,
                                                                       OAuthConstants.PROFILE_OPEN_ID_ACCESS_TOKEN,
                                                                       false);
    String encodedAccessTokenSecret = OAuthPersistenceUtils.getLongAttribute(userProfile,
                                                                             OAuthConstants.PROFILE_OPEN_ID_ACCESS_TOKEN_SECRET,
                                                                             false);
    String decodedAccessToken = codec.decodeString(encodedAccessToken);
    String decodedAccessTokenSecret = codec.decodeString(encodedAccessTokenSecret);

    if (decodedAccessToken == null || decodedAccessTokenSecret == null) {
      return null;
    } else {
      OAuth2AccessToken token = new OAuth2AccessToken(decodedAccessToken, decodedAccessTokenSecret);
      return new OpenIdAccessTokenContext(token);
    }
  }

  @Override
  public void removeAccessTokenFromUserProfile(UserProfile userProfile) {
    OAuthPersistenceUtils.removeLongAttribute(userProfile, OAuthConstants.PROFILE_OPEN_ID_ACCESS_TOKEN, false);
    OAuthPersistenceUtils.removeLongAttribute(userProfile, OAuthConstants.PROFILE_OPEN_ID_ACCESS_TOKEN_SECRET, false);
  }

  @Override
  public JSONObject obtainUserInfo(OpenIdAccessTokenContext accessTokenContext) {
    Map<String, String> params = new HashMap<>();
    params.put(OAuthConstants.ACCESS_TOKEN_PARAMETER, accessTokenContext.getAccessToken());
    OpenIdRequest<JSONObject> userInfoRequest = new OpenIdRequest<JSONObject>() {

      @Override
      protected URL createURL() throws IOException {
        return getUserInfoURL();
      }

      @Override
      protected JSONObject invokeRequest(Map<String, String> params) throws IOException, JSONException {
        URL url = createURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + params.get(OAuthConstants.ACCESS_TOKEN_PARAMETER));
        HttpResponseContext httpResponse = OAuthUtils.readUrlContent(conn);
        if (httpResponse.getResponseCode() == 200) {
          return parseResponse(httpResponse.getResponse());
        } else {
          String errorMessage = "Unable to read OpenId userInfo endpoint. Http response code: " + httpResponse.getResponseCode()
              + ", details: " + httpResponse.getResponse() + ". We will use claims in id_token";
          log.info(errorMessage);
        }
        return new JSONObject();
      }

      @Override
      protected JSONObject parseResponse(String httpResponse) throws JSONException {
        JSONObject json = new JSONObject(httpResponse);
        return json;
      }

    };
    JSONObject uinfo = userInfoRequest.executeRequest(params);

    if (log.isTraceEnabled()) {
      log.trace("Successfully obtained userInfo from openid: " + uinfo);
    }

    return uinfo;
  }

  @Override
  public void revokeToken(OpenIdAccessTokenContext accessToken) throws OAuthException {

  }

  // Parse given String and add all scopes from it to given Set
  private void addScopesFromString(String scope, Set<String> scopes) {
    String[] scopes2 = scope.split(" ");
    for (String current : scopes2) {
      scopes.add(current);
    }
  }

  protected URL sendAccessTokenRequest() throws IOException {
    if (log.isTraceEnabled())
      log.trace("AccessToken Request=" + this.accessTokenURL);
    return new URL(this.accessTokenURL);
  }

  protected URL getUserInfoURL() throws IOException {
    if (log.isTraceEnabled())
      log.trace("UserInfo Request=" + this.userInfoURL);
    return new URL(this.userInfoURL);
  }

  @Override
  public void start() {
    if (StringUtils.isBlank(this.wellKnownConfigurationUrl)) {
      log.error("wellKnownConfigurationUrl is not configured");
      return;
    }
    try {
      String wellKnownConfigurationContent = readUrl(new URL(this.wellKnownConfigurationUrl));
      if (wellKnownConfigurationContent != null) {
        JSONObject json = new JSONObject(wellKnownConfigurationContent);
        this.authenticationURL = json.getString("authorization_endpoint");
        this.accessTokenURL = json.getString("token_endpoint");
        this.userInfoURL = json.getString("userinfo_endpoint");
        this.issuer = json.getString("issuer");
        this.remoteJwkSigningKeyResolver = new RemoteJwkSigningKeyResolver(this.wellKnownConfigurationUrl);
      }
    } catch (JSONException e) {
      log.error("Unable to read webKnownUrl content : " + this.wellKnownConfigurationUrl);
    } catch (MalformedURLException e) {
      log.error("WellKnowConfigurationUrl malformed : url" + this.wellKnownConfigurationUrl);
    }
  }

  @Override
  public void stop() {
    // Nothing to stop
  }

  private static String readUrl(URL url) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
      StringBuilder buffer = new StringBuilder();
      int read;
      char[] chars = new char[1024];
      while ((read = reader.read(chars)) != -1)
        buffer.append(chars, 0, read);

      return buffer.toString();
    } catch (IOException e) {
      log.error(e.getMessage());
    }
    return null;
  }
}
