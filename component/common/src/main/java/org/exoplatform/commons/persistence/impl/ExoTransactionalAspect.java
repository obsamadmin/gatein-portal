/*
 * Copyright (C) 2015 eXo Platform SAS.
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
package org.exoplatform.commons.persistence.impl;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Process the Transactional annotation. The only propagation implemented yet is
 * REQUIRED. Support a current transaction, create a new one if none exists.
 * Analogous to EJB or Spring transaction attribute of the same name.
 *
 * @see ExoTransactional
 * @author <a href="bdechateauvieux@exoplatform.org">Benoit de Chateauvieux</a>
 * @version $Revision$
 */
@Aspect
public class ExoTransactionalAspect {
  private static final Log LOG = ExoLogger.getLogger(ExoTransactionalAspect.class);

  @Around("execution(* *(..)) && @annotation(org.exoplatform.commons.api.persistence.ExoTransactional)")
  public Object around(ProceedingJoinPoint point) throws Throwable {
    EntityManager entityManager;
    boolean begunTx = false;
    boolean emStarted = false;

    // Do we need to start EntityManager ?
    EntityManagerService service = ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(EntityManagerService.class);
    if (service == null) {
      throw new IllegalStateException("Cannot find EntityManagerService instance. This may happen when ExoContainerContext.getCurrentContainer is not set in current thread.");
    }
    entityManager = service.getEntityManager();
    if (entityManager == null) {
      LOG.debug("Injecting new PersistenceContext");
      entityManager = service.createEntityManager();
      emStarted = true;
    } else {
      LOG.debug("Using existing PersistenceContext");
    }

    // Do we need to start Transaction ?
    EntityTransaction tx = entityManager.getTransaction();
    if (tx.isActive()) {
      LOG.debug("Using current transaction");
    } else {
      LOG.debug("Starting new transaction");
      try {
        tx.begin();
      } catch (PersistenceException e) {
        LOG.warn("An error occured while starting JPA transaction. The transaction will be restarted.", e);
        try {
          service.closeEntityManager();
        } catch (Exception e1) {
          LOG.warn("An error occured while closing current JPA entity manager. The transaction will be recreated.", e);
        }
        entityManager = service.createEntityManager();
        tx = entityManager.getTransaction();
        tx.begin();
      }
      begunTx = true;
    }
    try {
      Object result = point.proceed();
      return result;
    } catch (RuntimeException e) {
        LOG.error("Error while processing transactional method.", e);
        throw e;
    } finally {
      // Do we need to end Transaction ?
      try {
        if ((begunTx) && (tx.isActive())) {
          if (!tx.getRollbackOnly()) {
            LOG.debug("Committing current transaction");
            tx.commit();
          } else {
            LOG.debug("Rollback current transaction set as RollbackOnly");
            tx.rollback();
          }
        }
      } catch (RuntimeException ex) {
        try {
          if (tx != null && tx.isActive()) {
            tx.rollback();
          }
        } catch (RuntimeException rbEx) {
          LOG.error("Could not roll back transaction", rbEx);
        }
        throw ex;
      } finally {
        // Do we need to close EntityManager ?
        if (emStarted && (entityManager != null)) {
          service.closeEntityManager();
        }
      }

    }
  }

}
