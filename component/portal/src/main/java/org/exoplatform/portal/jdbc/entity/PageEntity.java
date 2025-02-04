/*
 * Copyright (C) 2016 eXo Platform SAS.
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
package org.exoplatform.portal.jdbc.entity;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.exoplatform.commons.api.persistence.ExoEntity;
import org.exoplatform.portal.mop.SiteType;

@ExoEntity
@Entity(name = "GateInPage")
@SequenceGenerator(name = "SEQ_GTN_ID_GENERATOR", sequenceName = "SEQ_GTN_PAGE_ID", allocationSize = 1)
@Table(name = "PORTAL_PAGES")
@NamedQueries({
    @NamedQuery(name = "PageEntity.deleteByOwner", query = "DELETE GateInPage p WHERE p.owner.id = :ownerId"),
    @NamedQuery(name = "PageEntity.findByKey", query = "SELECT p FROM GateInPage p WHERE p.name = :name AND p.owner.siteType = :ownerType AND p.owner.name = :ownerId") })
public class PageEntity extends ComponentEntity implements Serializable {

  private static final long     serialVersionUID = -6195451978995765259L;

  @Id
  @SequenceGenerator(name = "SEQ_PAGE_ID_GENERATOR", sequenceName = "SEQ_PAGE_ID_GENERATOR", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_PAGE_ID_GENERATOR")
  @Column(name = "ID")
  protected Long                id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "SITE_ID")
  private SiteEntity            owner;

  @Column(name = "SHOW_MAX_WINDOW")
  private boolean               showMaxWindow;

  @Column(name = "DISPLAY_NAME", length = 200)
  private String                displayName;

  @Column(name = "NAME", length = 200)
  private String                name;

  @Column(name = "DESCRIPTION", length = 2000)
  private String                description;

  @Column(name = "FACTORY_ID", length = 200)
  private String                factoryId;

  @Column(name = "PAGE_BODY", length = 5000)
  private String                pageBody         = getJSONString(new JSONArray());

  @Transient
  private List<ComponentEntity> children         = new LinkedList<>();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public SiteEntity getOwner() {
    return owner;
  }

  public void setOwner(SiteEntity owner) {
    this.owner = owner;
  }

  public boolean isShowMaxWindow() {
    return showMaxWindow;
  }

  public SiteType getOwnerType() {
    if (getOwner() != null) {
      return getOwner().getSiteType();
    } else {
      return null;
    }
  }

  public String getOwnerId() {
    if (getOwner() != null) {
      return getOwner().getName();
    } else {
      return null;
    }
  }

  public void setShowMaxWindow(boolean showMaxWindow) {
    this.showMaxWindow = showMaxWindow;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getPageBody() {
    return pageBody;
  }

  public void setPageBody(String pageBody) {
    this.pageBody = pageBody;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getFactoryId() {
    return factoryId;
  }

  public void setFactoryId(String factoryId) {
    this.factoryId = factoryId;
  }

  public List<ComponentEntity> getChildren() {
    return children;
  }

  public void setChildren(List<ComponentEntity> children) {
    this.children = children;
  }

  @Override
  public JSONObject toJSON() {
    JSONObject obj = super.toJSON();

    JSONArray jChildren = new JSONArray();
    for (ComponentEntity child : getChildren()) {
      jChildren.add(child.toJSON());
    }
    obj.put("children", jChildren);
    return obj;
  }

  @Override
  public TYPE getType() {
    return TYPE.PAGE;
  }

}
