package org.exoplatform.portal.mop.rest;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.*;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.Visibility;
import org.exoplatform.portal.mop.navigation.Scope;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.mop.user.HttpUserPortalContext;
import org.exoplatform.portal.mop.user.UserNavigation;
import org.exoplatform.portal.mop.user.UserNode;
import org.exoplatform.portal.mop.user.UserNodeFilterConfig;
import org.exoplatform.portal.mop.user.UserPortal;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.ws.frameworks.json.impl.JsonParserImpl;

@Path("/v1/navigations")
@Tag(name = "v1/navigations", description = "Retrieve sites navigations")
public class NavigationRest implements ResourceContainer {

  private static final Log                  LOG                  = ExoLogger.getLogger(NavigationRest.class);

  private static final Visibility[]         DEFAULT_VISIBILITIES = Visibility.values();

  private static final UserNodeFilterConfig USER_FILTER_CONFIG   = getUserFilterConfig(DEFAULT_VISIBILITIES);

  private UserPortalConfigService           portalConfigService;

  private NavigationCategoryService         navigationCategoryService;

  public NavigationRest(UserPortalConfigService portalConfigService, NavigationCategoryService navigationCategoryService) {
    this.portalConfigService = portalConfigService;
    this.navigationCategoryService = navigationCategoryService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
          summary = "Gets navigations",
          description = "Gets navigations",
          method = "GET")
  @ApiResponses(value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Navigation does not exist"),
          @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response getSiteNavigation(@Context HttpServletRequest request) {
    HttpUserPortalContext userPortalContext = new HttpUserPortalContext(request);
    String portalName = portalConfigService.getDefaultPortal();

    ConversationState state = ConversationState.getCurrent();
    Identity userIdentity = state == null ? null : state.getIdentity();
    String username = userIdentity == null ? null : userIdentity.getUserId();
    if (StringUtils.equals(username, IdentityConstants.ANONIM)) {
      username = null;
    }

    try {
      UserPortalConfig userPortalConfig = portalConfigService.getUserPortalConfig(portalName, username, userPortalContext);
      List<ResultUserNavigation> allNavs = userPortalConfig.getUserPortal()
                                                           .getNavigations()
                                                           .stream()
                                                           .filter(userNavigation -> !userNavigation.getKey()
                                                                                                    .getName()
                                                                                                    .equals("global"))
                                                           .map(ResultUserNavigation::new)
                                                           .collect(Collectors.toList());
      return Response.ok(allNavs).build();

    } catch (Exception e) {
      LOG.error("Error retrieving navigations", e);
      return Response.status(500).build();
    }

  }

  @Path("/{siteType}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(
          summary = "Gets navigations of one or multiple site navigations",
          description = "Gets navigations of one or multiple site navigations",
          method = "GET")
  @ApiResponses(value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Navigation does not exist"),
          @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response getSiteTypeNavigations(@Context HttpServletRequest request,
                                         @Parameter(description = "Portal site type, possible values: PORTAL, GROUP or USER", required = true) @PathParam("siteType") String siteTypeName,
                                         @Parameter(description = "Site names regex to exclude from results", required = false) @QueryParam("exclude") String excludedSiteName,
                                         @Parameter(description = "Portal site name", required = true) @QueryParam("siteName") String siteName,
                                         @Parameter(description = "Scope of navigations tree to retrieve, possible values: ALL, CHILDREN, GRANDCHILDREN, SINGLE", required = false)
                                         @Schema(defaultValue = "ALL") @QueryParam("scope") String scopeName,
                                         @Parameter(description = "Multivalued visibilities of navigation nodes to retrieve, possible values: DISPLAYED, HIDDEN, SYSTEM or TEMPORAL. If empty, all visibilities will be used.", required = false)
                                         @Schema(defaultValue = "All possible values combined") @QueryParam("visibility") List<String> visibilityNames) {
    // this function return nodes and not navigations
    if (StringUtils.isBlank(siteTypeName)) {
      return Response.status(400).build();
    }

    return getNavigations(request, siteTypeName, siteName, excludedSiteName, scopeName, visibilityNames);
  }

  @Path("/categories")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Gets navigations categories for UI",
      description = "Gets navigations categories for UI",
      method = "GET")
  @ApiResponses(
      value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
      }
  )
  public Response getNavigationCategories() {
    try {
      JSONObject object = new JSONObject();
      object.put("navs", navigationCategoryService.getNavigationCategories());
      object.put("categoriesOrder", navigationCategoryService.getNavigationCategoriesOrder());
      object.put("urisOrder", navigationCategoryService.getNavigationUriOrder());
      return Response.ok(object.toString()).build();
    } catch (JSONException e) {
      LOG.warn("Error parsing navigation categories result", e);
      return Response.serverError().build();
    }
  }

  private Response getNavigations(HttpServletRequest request,
                                  String siteTypeName,
                                  String siteName,
                                  String excludedSiteName,
                                  String scopeName,
                                  List<String> visibilityNames) {
    ConversationState state = ConversationState.getCurrent();
    Identity userIdentity = state == null ? null : state.getIdentity();
    String username = userIdentity == null ? null : userIdentity.getUserId();
    if (StringUtils.equals(username, IdentityConstants.ANONIM)) {
      username = null;
    }

    SiteType siteType = null;
    try {
      siteType = SiteType.valueOf(StringUtils.upperCase(siteTypeName));
    } catch (Exception e) {
      return Response.status(400).entity("Bad site type: " + siteTypeName).build();
    }

    Scope scope = null;
    try {
      scope = getScope(scopeName);
    } catch (Exception e) {
      return Response.status(400).entity(e.getMessage()).build();
    }

    Visibility[] visibilities = null;
    try {
      visibilities = convertVisibilities(visibilityNames);
    } catch (Exception e) {
      return Response.status(400)
                     .entity("Bad visibility names: " + visibilityNames + ". should be one of " + DEFAULT_VISIBILITIES)
                     .build();
    }

    UserNodeFilterConfig userFilterConfig = USER_FILTER_CONFIG;
    if (visibilities.length > 0) {
      userFilterConfig = getUserFilterConfig(visibilities);
    }

    String portalName = siteName;
    if (siteType != SiteType.PORTAL || StringUtils.isBlank(siteName)) {
      portalName = portalConfigService.getDefaultPortal();
    }

    try {
      HttpUserPortalContext userPortalContext = new HttpUserPortalContext(request);
      UserPortalConfig userPortalConfig = portalConfigService.getUserPortalConfig(portalName,
                                                                                  username,
                                                                                  userPortalContext);
      if (userPortalConfig == null) {
        return Response.status(404).build();
      }

      UserPortal userPortal = userPortalConfig.getUserPortal();
      Collection<UserNode> nodes = null;
      if (siteType == SiteType.PORTAL || StringUtils.isBlank(siteName)) {
        nodes = userPortal.getNodes(siteType, excludedSiteName, scope, userFilterConfig);
      } else {
        UserNavigation navigation = userPortal.getNavigation(new SiteKey(siteType, siteName));
        if (navigation == null) {
          return Response.status(404).build();
        }
        UserNode rootNode = userPortal.getNode(navigation, scope, userFilterConfig, null);
        nodes = rootNode.getChildren();
      }
      List<ResultUserNode> resultNodes = convertNodes(nodes);
      return Response.ok(resultNodes).build();
    } catch (Exception e) {
      LOG.error("Error retrieving ");
      return Response.status(500).build();
    }
  }

  private List<ResultUserNode> convertNodes(Collection<UserNode> nodes) {
    if (nodes == null) {
      return Collections.emptyList();
    }
    List<ResultUserNode> result = new ArrayList<>();
    for (UserNode userNode : nodes) {
      if (userNode == null) {
        continue;
      }
      ResultUserNode resultNode = new ResultUserNode(userNode);
      resultNode.setChildren(convertNodes(userNode.getChildren()));
      result.add(resultNode);
    }
    return result;
  }

  private static UserNodeFilterConfig getUserFilterConfig(Visibility[] visibilities) {
    UserNodeFilterConfig.Builder builder = UserNodeFilterConfig.builder();
    builder.withReadWriteCheck()
           .withVisibility(visibilities)
           .withTemporalCheck()
           .withReadCheck();
    return builder.build();
  }

  private Visibility[] convertVisibilities(List<String> visibilityNames) {
    if (visibilityNames == null) {
      return new Visibility[0];
    }
    return visibilityNames.stream()
                          .map(visibilityName -> Visibility.valueOf(StringUtils.upperCase(visibilityName)))
                          .collect(Collectors.toList())
                          .toArray(new Visibility[0]);
  }

  private Scope getScope(String scopeName) {
    Scope scope = null;
    if (StringUtils.isBlank(scopeName)) {
      scope = Scope.ALL;
    } else {
      switch (StringUtils.upperCase(scopeName)) {
      case "SINGLE":
        scope = Scope.SINGLE;
        break;
      case "CHILDREN":
        scope = Scope.CHILDREN;
        break;
      case "GRANDCHILDREN":
        scope = Scope.GRANDCHILDREN;
        break;
      case "ALL":
        scope = Scope.ALL;
        break;
      default:
        throw new IllegalStateException("Bad scope name: " + scopeName);
      }
    }
    return scope;
  }

  /**
   * A class to retrieve {@link UserNode} attributes by avoiding cyclic JSON
   * parsing of instance when retrieving {@link UserNode#getChildren()} and
   * {@link UserNode#getParent()} attributes using {@link JsonParserImpl}
   */
  public static final class ResultUserNode {
    private UserNode             userNode;

    private List<ResultUserNode> subNodes;

    public ResultUserNode(UserNode userNode) {
      this.userNode = userNode;
    }

    public void setChildren(List<ResultUserNode> subNodes) {
      this.subNodes = subNodes;
    }

    public List<ResultUserNode> getChildren() {
      return subNodes;
    }

    public String getLabel() {
      return userNode.getResolvedLabel();
    }

    public String getLabelKey() {
      return userNode.getLabel();
    }

    public String getIcon() {
      return userNode.getIcon();
    }

    public String getId() {
      return userNode.getId();
    }

    public String getUri() {
      return userNode.getURI();
    }

    public Visibility getVisibility() {
      return userNode.getVisibility();
    }

    public String getName() {
      return userNode.getName();
    }

    public long getStartPublicationTime() {
      return userNode.getStartPublicationTime();
    }

    public long getEndPublicationTime() {
      return userNode.getEndPublicationTime();
    }

    public SiteKey getSiteKey() {
      return userNode.getNavigation().getKey();
    }

    public PageKey getPageKey() {
      return userNode.getPageRef();
    }
  }

  /**
   * A class to retrieve {@link UserNavigation} attributes by avoiding cyclic JSON
   * parsing of instance when retrieving {@link UserNavigation#getBundle()}
   * attributes using {@link JsonParserImpl}
   */
  public static final class ResultUserNavigation {
    public SiteKey getKey() {
      return key;
    }

    public String getLabel() {
      return label;
    }

    private SiteKey key;

    private String  label;

    public ResultUserNavigation(UserNavigation userNavigation) {
      key = userNavigation.getKey();
      if (key.getType().equals(SiteType.GROUP)) {
        try {
          OrganizationService orgService = ExoContainerContext.getCurrentContainer()
                                                              .getComponentInstanceOfType(OrganizationService.class);
          Group group = orgService.getGroupHandler().findGroupById(key.getName());
          if (group != null) {
            label = group.getLabel();
          }
        } catch (Exception e) {
          LOG.error("Error when getting group label {}", key.getName(), e);
        }
      } else {
        label = key.getName();
      }
    }

  }

}
