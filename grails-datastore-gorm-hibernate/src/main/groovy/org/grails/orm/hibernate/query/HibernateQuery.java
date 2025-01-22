/*
 * Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query;



import jakarta.persistence.criteria.CriteriaQuery;
import org.grails.orm.hibernate.AbstractHibernateSession;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateSession;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.PropertyMapping;
import org.hibernate.query.criteria.JpaCriteriaQuery;

import java.util.Iterator;

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class HibernateQuery extends AbstractHibernateQuery {


    public HibernateQuery(CriteriaQuery criteria, AbstractHibernateSession session, PersistentEntity entity) {
        super(criteria, session, entity);
    }

    public HibernateQuery(CriteriaQuery criteria, PersistentEntity entity) {
        super(criteria, null, entity);
    }

    public HibernateQuery(CriteriaQuery subCriteria, AbstractHibernateSession session, PersistentEntity associatedEntity, String newAlias) {
        super(subCriteria, session, associatedEntity, newAlias);
    }

    protected PropertyMapping getEntityPersister(String name, SessionFactory sessionFactory) {
        return (PropertyMapping) ((SharedSessionContractImplementor) sessionFactory).getEntityPersister(name,this.entity);
    }

    /**
     * @return The hibernate criteria
     */
    public CriteriaQuery getHibernateCriteria() {
        return this.criteriaQuery;
    }

    @Override
    public Object clone() {
        final HibernateSession hibernateSession = (HibernateSession) getSession();
        final GrailsHibernateTemplate hibernateTemplate = (GrailsHibernateTemplate) hibernateSession.getNativeInterface();
        return hibernateTemplate.execute((GrailsHibernateTemplate.HibernateCallback<Object>) session -> {
            JpaCriteriaQuery newCriteria = session.getCriteriaBuilder().createQuery(entity.getJavaClass());
            HibernateQuery hibernateQuery = new HibernateQuery(newCriteria, hibernateSession, entity);
            hibernateQuery.max(this.max);
            hibernateQuery.offset(this.offset);
            this.projections.getProjectionList().forEach(projection -> {hibernateQuery.projections().add(projection);});;
            getCriteria().getCriteria().forEach(hibernateQuery::add);
            return hibernateQuery;
        });
    }

}
