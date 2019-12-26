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
package org.exoplatform.portal.jdbc.dao;

import org.exoplatform.application.registry.dao.NodeDAO;
import org.exoplatform.application.registry.entity.NodeEntity;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import java.util.List;

public class NodeDAOImpl extends GenericDAOJPAImpl<NodeEntity, Long> implements NodeDAO {

    @Override
    public NodeEntity create(NodeEntity entity) {
        // TODO Auto-generated method stub
        return super.create(entity);
    }

    @Override
    public List<NodeEntity> findAllByPage(Long pageId) {
        TypedQuery<NodeEntity> query = getEntityManager().createNamedQuery("NodeEntity.findByPage",
                NodeEntity.class);
        query.setParameter("pageId", pageId);

        return query.getResultList();
    }
}
