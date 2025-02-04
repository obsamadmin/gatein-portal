package org.exoplatform.portal.mop.jdbc.service;

import java.util.List;

import org.exoplatform.services.log.Log;
import org.exoplatform.services.log.ExoLogger;

import org.exoplatform.commons.api.persistence.DataInitializer;
import org.exoplatform.portal.mop.*;
import org.exoplatform.portal.mop.jdbc.dao.*;
import org.exoplatform.portal.mop.page.*;
import org.exoplatform.services.listener.ListenerService;

public class PageServiceWrapper implements PageService {

  /** . */
  private static final Log      log = ExoLogger.getLogger(PageServiceWrapper.class);

  /** . */
  private final PageServiceImpl service;

  /** . */
  private final ListenerService listenerService;

  public PageServiceWrapper(ListenerService listenerService,
                            PageDAO pageDAO,
                            ContainerDAO containerDAO,
                            WindowDAO windowDAO,
                            PermissionDAO permissionDAO,
                            SiteDAO siteDAO,
                            DataInitializer initializer) {
    this.service = new PageServiceImpl(pageDAO, containerDAO, windowDAO, permissionDAO, siteDAO);
    this.listenerService = listenerService;
  }

  @Override
  public PageContext loadPage(PageKey key) {
    return service.loadPage(key);
  }

  public List<PageContext> loadPages(SiteKey siteKey) throws NullPointerException, PageServiceException {
    return service.loadPages(siteKey);
  }

  @Override
  public boolean savePage(PageContext page) {
    boolean created = service.savePage(page);

    //
    if (created) {
      notify(EventType.PAGE_CREATED, page.getKey());
    } else {
      notify(EventType.PAGE_UPDATED, page.getKey());
    }

    //
    return created;
  }

  @Override
  public boolean destroyPage(PageKey key) {
    boolean destroyed = service.destroyPage(key);

    //
    if (destroyed) {
      notify(EventType.PAGE_DESTROYED, key);
    }

    //
    return destroyed;
  }

  @Override
  public PageContext clone(PageKey src, PageKey dst) {
    PageContext pageContext = service.clone(src, dst);
    notify(EventType.PAGE_CREATED, dst);
    return pageContext;
  }

  @Override
  public QueryResult<PageContext> findPages(int offset,
                                            int limit,
                                            SiteType siteType,
                                            String siteName,
                                            String pageName,
                                            String pageTitle) {
    return service.findPages(offset, limit, siteType, siteName, pageName, pageTitle);
  }

  private void notify(String name, PageKey key) {
    try {
      listenerService.broadcast(name, this, key);
    } catch (Exception e) {
      log.error("Error when delivering notification " + name + " for page " + key, e);
    }
  }
}
