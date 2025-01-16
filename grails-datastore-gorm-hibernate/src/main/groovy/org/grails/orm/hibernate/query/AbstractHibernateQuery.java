/* Copyright (C) 2011 SpringSource
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

import groovy.util.logging.Slf4j;
import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.AbstractHibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.hibernate.SessionFactory;
import org.hibernate.persister.entity.PropertyMapping;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaPredicate;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
@Slf4j
public abstract class AbstractHibernateQuery extends Query {

    public static final String SIZE_CONSTRAINT_PREFIX = "Size";

    protected static final String ALIAS = "_alias";
    protected static ConversionService conversionService = new DefaultConversionService();

    private static final Map<String, Boolean> JOIN_STATUS_CACHE = new ConcurrentHashMap<String, Boolean>();
    protected Root root;

    protected CriteriaQuery criteriaQuery;
    protected String alias;
    protected int aliasCount;
    protected Map<String, CriteriaAndAlias> createdAssociationPaths = new HashMap<String, CriteriaAndAlias>();
    protected LinkedList<String> aliasStack = new LinkedList<String>();
    protected LinkedList<PersistentEntity> entityStack = new LinkedList<PersistentEntity>();
    protected LinkedList<Association> associationStack = new LinkedList<Association>();
    protected LinkedList aliasInstanceStack = new LinkedList();
    private boolean hasJoins = false;


    protected AbstractHibernateQuery(CriteriaQuery criteriaQuery, AbstractHibernateSession session, PersistentEntity entity) {
        super(session, entity);
        if(entity != null) {
            initializeJoinStatus();
        }
    }



    @Override
    protected Object resolveIdIfEntity(Object value) {
        // for Hibernate queries, the object itself is used in queries, not the id
        return value;
    }

    protected void initializeJoinStatus() {
        Boolean cachedStatus = JOIN_STATUS_CACHE.get(entity.getName());
        if(cachedStatus != null) hasJoins = cachedStatus;
        else {
            for(Association a : entity.getAssociations()) {
                if( a.getFetchStrategy() == FetchType.EAGER ) hasJoins = true;
            }
        }
    }

    protected AbstractHibernateQuery(CriteriaQuery subCriteria, AbstractHibernateSession session, PersistentEntity associatedEntity, String newAlias) {
        this(subCriteria, session, associatedEntity);
        alias = newAlias;
    }

    @Override
    public Query isEmpty(String property) {
        return this;
    }

    @Override
    public Query isNotEmpty(String property) {
        return this;
    }

    @Override
    public Query isNull(String property) {
        return this;
    }

    @Override
    public Query isNotNull(String property) {
        return this;
    }




    @Override
    public PersistentEntity getEntity() {
        if (!entityStack.isEmpty()) {
            return entityStack.getLast();
        }
        return super.getEntity();
    }

    protected String getAssociationPath(String propertyName) {
        if(propertyName.indexOf('.') > -1) {
            return propertyName;
        }
        else {

            StringBuilder fullPath = new StringBuilder();
            for (Association association : associationStack) {
                fullPath.append(association.getName());
                fullPath.append('.');
            }
            fullPath.append(propertyName);
            return fullPath.toString();
        }
    }

    protected String getCurrentAlias() {
        if (alias != null) {
            return alias;
        }

        if (aliasStack.isEmpty()) {
            return null;
        }

        return aliasStack.getLast();
    }

    @SuppressWarnings("unchecked")
    static void doTypeConversionIfNeccessary(PersistentEntity entity, PropertyCriterion pc) {
        // ignore Size related constraints
        if (pc.getClass().getSimpleName().startsWith(SIZE_CONSTRAINT_PREFIX)) {
            return;
        }

        String property = pc.getProperty();
        Object value = pc.getValue();
        PersistentProperty p = entity.getPropertyByName(property);
        if (p != null && !p.getType().isInstance(value)) {
            pc.setValue(conversionService.convert(value, p.getType()));
        }
    }




    protected abstract PropertyMapping getEntityPersister(String name, SessionFactory sessionFactory);



    @Override
    public Junction disjunction() {
        return null;
    }

    @Override
    public Junction negation() {
        return null;
    }

    @Override
    public Query eq(String property, Object value) {
        return this;
    }

    @Override
    public Query idEq(Object value) {
        return this;
    }

    @Override
    public Query gt(String property, Object value) {
        return this;
    }

    @Override
    public Query and(Criterion a, Criterion b) {
        return this;
    }

    @Override
    public Query or(Criterion a, Criterion b) {
           return this;
    }

    @Override
    public Query allEq(Map<String, Object> values) {
        return this;
    }

    @Override
    public Query ge(String property, Object value) {
        return this;
    }

    @Override
    public Query le(String property, Object value) {
        return this;
    }

    @Override
    public Query gte(String property, Object value) {
        return this;
    }

    @Override
    public Query lte(String property, Object value) {
        return this;
    }

    @Override
    public Query lt(String property, Object value) {
        return this;
    }

    @Override
    public Query in(String property, List values) {
        return this;
    }

    @Override
    public Query between(String property, Object start, Object end) {
        return this;
    }

    @Override
    public Query like(String property, String expr) {
        return this;
    }

    @Override
    public Query ilike(String property, String expr) {
        return this;
    }

    @Override
    public Query rlike(String property, String expr) {
        return this;
    }

    @Override
    public AssociationQuery createQuery(String associationName) {
        final PersistentProperty property = entity.getPropertyByName(calculatePropertyName(associationName));
        if (property != null && (property instanceof Association)) {
            String alias = generateAlias(associationName);
            CriteriaAndAlias subCriteria = getOrCreateAlias(associationName, alias);

            Association association = (Association) property;
            if(subCriteria.criteria != null) {
                return new HibernateAssociationQuery(subCriteria.criteria, (AbstractHibernateSession) getSession(), association.getAssociatedEntity(), association, alias);
            }
        }
        throw new InvalidDataAccessApiUsageException("Cannot query association [" + calculatePropertyName(associationName) + "] of entity [" + entity + "]. Property is not an association!");
    }

    protected CriteriaAndAlias getCriteriaAndAlias(DetachedAssociationCriteria associationCriteria) {
        String associationPath = associationCriteria.getAssociationPath();
        String alias = associationCriteria.getAlias();

        if(associationPath == null) {
            associationPath = associationCriteria.getAssociation().getName();
        }
        return getOrCreateAlias(associationPath, alias);
    }

    protected CriteriaAndAlias getOrCreateAlias(String associationName, String alias) {
        CriteriaAndAlias subCriteria = null;
        String associationPath = getAssociationPath(associationName);
        CriteriaQuery parentCriteria = criteriaQuery;
        if(alias == null) {
            alias = generateAlias(associationName);
        }
        else {
            CriteriaAndAlias criteriaAndAlias = createdAssociationPaths.get(alias);
            if(criteriaAndAlias != null) {
                parentCriteria = criteriaAndAlias.criteria;
                if(parentCriteria != null) {

                    alias = associationName + '_' + alias;
                    associationPath = criteriaAndAlias.associationPath + '.' + associationPath;
                }
            }
        }
        if (createdAssociationPaths.containsKey(associationName)) {
            subCriteria = createdAssociationPaths.get(associationName);
        }
        else {
            JoinType joinType = joinTypes.get(associationName);
            if(parentCriteria != null) {
//                Criteria sc = parentCriteria.createAlias(associationPath, alias, resolveJoinType(joinType));
//                subCriteria = new CriteriaAndAlias(sc, alias, associationPath);
            }
            if(subCriteria != null) {

                createdAssociationPaths.put(associationPath,subCriteria);
                createdAssociationPaths.put(alias,subCriteria);
            }
        }
        return subCriteria;
    }

    private SqmJoinType resolveJoinType(JoinType joinType) {
        if(joinType  == null) {
            return SqmJoinType.INNER;
        }
        switch (joinType) {
            case LEFT:
                return SqmJoinType.LEFT;
            case RIGHT:
                return SqmJoinType.RIGHT;
            default:
                return SqmJoinType.INNER;
        }
    }

    @Override
    public Query firstResult(int offset) {
        offset(offset);
        return this;
    }

    @Override
    public Query cache(boolean cache) {
        return super.cache(cache);
    }

    @Override
    public Query lock(boolean lock) {
        return super.lock(lock);
    }

    @Override
    public Query order(Order order) {
        super.order(order);
        return this;
    }

    @Override
    public Query join(String property) {
        this.hasJoins = true;
        root.join(property);
        return this;
    }

    @Override
    public Query select(String property) {
        this.hasJoins = true;
        this.criteriaQuery.select(root.get(property));
        return this;
    }

    @Override
    public List list() {
        return getQuery().getResultList();
    }



    @Override
    protected void flushBeforeQuery() {
        // do nothing
    }

    @Override
    public Object singleResult() {
        return  getQuery().getSingleResult();
    }

    private final Predicate<Projection> countProjectionPredicate = projection -> projection instanceof CountProjection;

    @SuppressWarnings("unchecked")
    Predicate<Projection>[] projectionPredicates = new Predicate[] {countProjectionPredicate} ;

    @SafeVarargs
    private static <T> Predicate<T> combinePredicates(Predicate<T>... predicates) {
        return Arrays.stream(predicates)
                .reduce(Predicate::and)
                .orElse(x -> true);
    }

    private org.hibernate.query.Query getQuery() {
        HibernateCriteriaBuilder cb = getCriteriaBuilder();
        List<Projection> projections = projections()
                .getProjectionList()
                .stream()
                .filter(combinePredicates(projectionPredicates)).toList();
        if (projections.size() == 1  && projections.get(0) instanceof CountProjection) {
            criteriaQuery = cb.createQuery(Long.class);
            root = criteriaQuery.from(entity.getJavaClass());
            criteriaQuery.select(cb.count(root));
        } else {
            criteriaQuery = cb.createQuery(entity.getJavaClass());
            root = criteriaQuery.from(entity.getJavaClass());
            criteriaQuery.select(root);
        }

        List<JpaPredicate> predicates = this.criteria.getCriteria().stream().
            map(criterion -> {
                if (criterion instanceof IsNotNull c) {
                    return cb.isNotNull(root.get(c.getProperty()));
                } else if (criterion instanceof Equals c ) {
                    return cb.equal(root.get(c.getProperty()),c.getValue());
                }
                return null;
            }).filter(Objects::nonNull).toList();

        criteriaQuery.where(cb.and(predicates.toArray(new JpaPredicate[0])));
        return getSessionFactory()
                .getCurrentSession()
                .createQuery(criteriaQuery)
                .setMaxResults(this.max)
                .setFirstResult(this.offset);
    }

    private SessionFactory getSessionFactory() {
        return ((IHibernateTemplate) session.getNativeInterface()).getSessionFactory();
    }

    private HibernateCriteriaBuilder getCriteriaBuilder() {
        return getSessionFactory().getCriteriaBuilder();
    }


    @Override
    protected List executeQuery(PersistentEntity entity, Junction criteria) {
        return list();
    }




    protected String calculatePropertyName(String property) {
        if (alias == null) {
            return property;
        }
        return alias + '.' + property;
    }

    protected String generateAlias(String associationName) {
        return calculatePropertyName(associationName) + calculatePropertyName(ALIAS) + aliasCount++;
    }


    protected class HibernateAssociationQuery extends AssociationQuery {

        protected String alias;
        protected CriteriaQuery assocationCriteria;

        public HibernateAssociationQuery(CriteriaQuery criteria, AbstractHibernateSession session, PersistentEntity associatedEntity, Association association, String alias) {
            super(session, associatedEntity, association);
            this.alias = alias;
            assocationCriteria = criteria;
        }



        @Override
        public Query order(Order order) {
            return this;
        }

        @Override
        public Query isEmpty(String property) {
            return this;
        }


        @Override
        public Query isNotEmpty(String property) {
            return this;
        }

        @Override
        public Query isNull(String property) {
            return this;
        }

        @Override
        public Query isNotNull(String property) {
            return this;
        }

        @Override
        public void add(Criterion criterion) {

        }

        @Override
        public Junction disjunction() {
            return null;
        }

        @Override
        public Junction negation() {
            return null;
        }

        @Override
        public Query eq(String property, Object value) {
            return this;
        }

        @Override
        public Query idEq(Object value) {
            return this;
        }

        @Override
        public Query gt(String property, Object value) {
            return this;
        }

        @Override
        public Query and(Criterion a, Criterion b) {
              return this;
        }

        @Override
        public Query or(Criterion a, Criterion b) {
            return this;
        }

        @Override
        public Query allEq(Map<String, Object> values) {
            return this;
        }

        @Override
        public Query ge(String property, Object value) {
            return this;
        }

        @Override
        public Query le(String property, Object value) {
            return this;
        }

        @Override
        public Query gte(String property, Object value) {
            return this;
        }

        @Override
        public Query lte(String property, Object value) {
            return this;
        }

        @Override
        public Query lt(String property, Object value) {
            return this;
        }

        @Override
        public Query in(String property, List values) {
            return this;
        }

        @Override
        public Query between(String property, Object start, Object end) {
            return this;
        }

        @Override
        public Query like(String property, String expr) {
            return this;
        }

        @Override
        public Query ilike(String property, String expr) {
            return this;
        }

        @Override
        public Query rlike(String property, String expr) {
            return this;
        }
    }

    protected class CriteriaAndAlias {
        protected CriteriaQuery criteria;
        protected String alias;
        protected String associationPath;


        public CriteriaAndAlias(CriteriaQuery criteria, String alias, String associationPath) {
            this.criteria = criteria;
            this.alias = alias;
            this.associationPath = associationPath;
        }
    }
}
