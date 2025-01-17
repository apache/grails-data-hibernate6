/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.orm;

import grails.gorm.DetachedCriteria;
import groovy.lang.GroovySystem;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.query.AbstractHibernateCriteriaBuilder;
import org.grails.orm.hibernate.query.AbstractHibernateQuery;
import org.grails.orm.hibernate.query.HibernateQuery;
import org.hibernate.SessionFactory;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.StandardBasicTypes;

import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.grails.datastore.mapping.query.api.QueryableCriteria;


import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * <p>Wraps the Hibernate Criteria API in a builder. The builder can be retrieved through the "createCriteria()" dynamic static
 * method of Grails domain classes (Example in Groovy):
 * <pre>
 *         def c = Account.createCriteria()
 *         def results = c {
 *             projections {
 *                 groupProperty("branch")
 *             }
 *             like("holderFirstName", "Fred%")
 *             and {
 *                 between("balance", 500, 1000)
 *                 eq("branch", "London")
 *             }
 *             maxResults(10)
 *             order("holderLastName", "desc")
 *         }
 * </pre>
 * <p>The builder can also be instantiated standalone with a SessionFactory and persistent Class instance:
 * <pre>
 *      new HibernateCriteriaBuilder(clazz, sessionFactory).list {
 *         eq("firstName", "Fred")
 *      }
 * </pre>
 *
 * @author Graeme Rocher
 */
public class HibernateCriteriaBuilder extends AbstractHibernateCriteriaBuilder {
    /*
     * Define constants which may be used inside of criteria queries
     * to refer to standard Hibernate Type instances.
     */
    public static final BasicTypeReference<Boolean> BOOLEAN = StandardBasicTypes.BOOLEAN;
    public static final BasicTypeReference<Boolean> YES_NO = StandardBasicTypes.YES_NO;
    public static final BasicTypeReference<Byte> BYTE = StandardBasicTypes.BYTE;
    public static final BasicTypeReference<Character> CHARACTER = StandardBasicTypes.CHARACTER;
    public static final BasicTypeReference<Short> SHORT = StandardBasicTypes.SHORT;
    public static final BasicTypeReference<Integer> INTEGER = StandardBasicTypes.INTEGER;
    public static final BasicTypeReference<Long> LONG = StandardBasicTypes.LONG;
    public static final BasicTypeReference<Float> FLOAT = StandardBasicTypes.FLOAT;
    public static final BasicTypeReference<Double> DOUBLE = StandardBasicTypes.DOUBLE;
    public static final BasicTypeReference<BigDecimal> BIG_DECIMAL = StandardBasicTypes.BIG_DECIMAL;
    public static final BasicTypeReference<BigInteger> BIG_INTEGER = StandardBasicTypes.BIG_INTEGER;
    public static final BasicTypeReference<String> STRING = StandardBasicTypes.STRING;
    public static final BasicTypeReference<Boolean> NUMERIC_BOOLEAN = StandardBasicTypes.NUMERIC_BOOLEAN;
    public static final BasicTypeReference<Boolean> TRUE_FALSE = StandardBasicTypes.TRUE_FALSE;
    public static final BasicTypeReference<java.net.URL> URL = StandardBasicTypes.URL;
    public static final BasicTypeReference<Date> TIME = StandardBasicTypes.TIME;
    public static final BasicTypeReference<Date> DATE = StandardBasicTypes.DATE;
    public static final BasicTypeReference<Date> TIMESTAMP = StandardBasicTypes.TIMESTAMP;
    public static final BasicTypeReference<Calendar> CALENDAR = StandardBasicTypes.CALENDAR;
    public static final BasicTypeReference<Calendar> CALENDAR_DATE = StandardBasicTypes.CALENDAR_DATE;
    public static final BasicTypeReference<Class> CLASS = StandardBasicTypes.CLASS;
    public static final BasicTypeReference<Locale> LOCALE = StandardBasicTypes.LOCALE;
    public static final BasicTypeReference<Currency> CURRENCY = StandardBasicTypes.CURRENCY;
    public static final BasicTypeReference<TimeZone> TIMEZONE = StandardBasicTypes.TIMEZONE;
    public static final BasicTypeReference<UUID> UUID_BINARY = StandardBasicTypes.UUID_BINARY;
    public static final BasicTypeReference<UUID> UUID_CHAR = StandardBasicTypes.UUID_CHAR;
    public static final BasicTypeReference<byte[]> BINARY = StandardBasicTypes.BINARY;
    public static final BasicTypeReference<Byte[]> WRAPPER_BINARY = StandardBasicTypes.WRAPPER_BINARY;
    public static final BasicTypeReference<byte[]> IMAGE = StandardBasicTypes.IMAGE;
    public static final BasicTypeReference<Blob> BLOB = StandardBasicTypes.BLOB;
    public static final BasicTypeReference<byte[]> MATERIALIZED_BLOB = StandardBasicTypes.MATERIALIZED_BLOB;
    public static final BasicTypeReference<char[]> CHAR_ARRAY = StandardBasicTypes.CHAR_ARRAY;
    public static final BasicTypeReference<Character[]> CHARACTER_ARRAY = StandardBasicTypes.CHARACTER_ARRAY;
    public static final BasicTypeReference<String> TEXT = StandardBasicTypes.TEXT;
    public static final BasicTypeReference<Clob> CLOB = StandardBasicTypes.CLOB;
    public static final BasicTypeReference<String> MATERIALIZED_CLOB = StandardBasicTypes.MATERIALIZED_CLOB;
    public static final BasicTypeReference<Serializable> SERIALIZABLE = StandardBasicTypes.SERIALIZABLE;

    @SuppressWarnings("rawtypes")
    public HibernateCriteriaBuilder(Class targetClass, SessionFactory sessionFactory) {
        super(targetClass, sessionFactory);
        setDefaultFlushMode(GrailsHibernateTemplate.FLUSH_AUTO);
    }

    @SuppressWarnings("rawtypes")
    public HibernateCriteriaBuilder(Class targetClass, SessionFactory sessionFactory, boolean uniqueResult) {
        super(targetClass, sessionFactory, uniqueResult);
        setDefaultFlushMode(GrailsHibernateTemplate.FLUSH_AUTO);
    }



    protected Class getClassForAssociationType(Attribute<?, ?> type) {
        if (type instanceof PluralAttribute) {
            return ((PluralAttribute)type).getElementType().getJavaType();
        }
        return type.getJavaType();
    }

    @Override
    protected List createPagedResultList(Map args) {
        GrailsHibernateTemplate ght = new GrailsHibernateTemplate(sessionFactory, (HibernateDatastore) datastore, getDefaultFlushMode());
        return null;
    }



    @Override
    protected void createCriteriaInstance() {
        {
            if (TransactionSynchronizationManager.hasResource(sessionFactory)) {
                participate = true;
                hibernateSession = ((SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory)).getSession();
            }
            else {
                hibernateSession = sessionFactory.openSession();
            }
            criteriaQuery = hibernateSession.getCriteriaBuilder().createQuery(targetClass);
            root = criteriaQuery.from(targetClass);
            cacheCriteriaMapping();
            criteriaMetaClass = GroovySystem.getMetaClassRegistry().getMetaClass(criteriaQuery.getClass());
        }
    }

    protected  DetachedCriteria convertToHibernateCriteria(QueryableCriteria<?> queryableCriteria) {
        return getHibernateDetachedCriteria(new HibernateQuery(null, queryableCriteria.getPersistentEntity()), queryableCriteria);
    }

    public static DetachedCriteria getHibernateDetachedCriteria(AbstractHibernateQuery query, QueryableCriteria<?> queryableCriteria) {
        String alias = queryableCriteria.getAlias();
        return getHibernateDetachedCriteria(query, queryableCriteria, alias);
    }

    public static DetachedCriteria getHibernateDetachedCriteria(AbstractHibernateQuery query, QueryableCriteria<?> queryableCriteria, String alias) {
        PersistentEntity persistentEntity = queryableCriteria.getPersistentEntity();
        DetachedCriteria detachedCriteria = new DetachedCriteria(persistentEntity.getJavaClass());

        populateHibernateDetachedCriteria(new HibernateQuery(null,persistentEntity), detachedCriteria, queryableCriteria);
        return detachedCriteria;
    }

    private static void populateHibernateDetachedCriteria(AbstractHibernateQuery query, DetachedCriteria detachedCriteria, QueryableCriteria<?> queryableCriteria) {
        List<org.grails.datastore.mapping.query.Query.Criterion> criteriaList = queryableCriteria.getCriteria();
        for (org.grails.datastore.mapping.query.Query.Criterion criterion : criteriaList) {
                detachedCriteria.add(criterion);
            }
        }

    protected void cacheCriteriaMapping() {

    }


    /**
     * Closes the session if it is copen
     */
    @Override
    protected void closeSession() {
        if (hibernateSession != null && hibernateSession.isOpen() && !participate) {
            hibernateSession.close();
        }
    }


}
