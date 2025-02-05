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

import grails.gorm.DetachedCriteria;
import groovy.lang.Closure;
import groovy.util.logging.Slf4j;
import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.Restrictions;
import org.grails.orm.hibernate.AbstractHibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.hibernate.SessionFactory;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

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

    protected String alias;
    protected int aliasCount;
    protected Map<String, CriteriaAndAlias> createdAssociationPaths = new HashMap<String, CriteriaAndAlias>();
    protected LinkedList<String> aliasStack = new LinkedList<String>();
    protected LinkedList<PersistentEntity> entityStack = new LinkedList<PersistentEntity>();
    protected LinkedList<Association> associationStack = new LinkedList<Association>();
    protected LinkedList aliasInstanceStack = new LinkedList();
    private boolean hasJoins = false;
    private DetachedCriteria detachedCriteria;

    protected AbstractHibernateQuery(AbstractHibernateSession session, PersistentEntity entity) {
        super(session, entity);
        this.detachedCriteria = new DetachedCriteria(entity.getJavaClass());
    }

    public void setDetachedCriteria(DetachedCriteria detachedCriteria) {
        this.detachedCriteria = detachedCriteria;
    }


    @Override
    protected Object resolveIdIfEntity(Object value) {
        // for Hibernate queries, the object itself is used in queries, not the id
        return value;
    }


    @Override
    public Query isEmpty(String property) {
        detachedCriteria.isEmpty(property);
        return this;
    }

    @Override
    public Query isNotEmpty(String property) {
        detachedCriteria.isNotEmpty(property);
        return this;
    }

    @Override
    public Query isNull(String property) {
        detachedCriteria.isNull(property);
        return this;
    }

    @Override
    public Query isNotNull(String property) {
        detachedCriteria.isNotNull(property);
        return this;
    }


    @Override
    public PersistentEntity getEntity() {
        if (!entityStack.isEmpty()) {
            return entityStack.getLast();
        }
        return super.getEntity();
    }


    private String getAssociationPath(String propertyName) {
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
        detachedCriteria.eq(property, value);
        return this;
    }

    @Override
    public Query idEq(Object value) {
        detachedCriteria.idEq(value);
        return this;
    }

    @Override
    public Query gt(String property, Object value) {
        detachedCriteria.gt(property, value);
        return this;
    }

    @Override
    public Query and(Criterion a, Criterion b) {
        Closure addClosure = new Closure(this) {
            public void doCall() {
                DetachedCriteria owner = (DetachedCriteria) getDelegate();
                owner.add(Restrictions.and(a,b));
            }
        };
        detachedCriteria.and(addClosure);
        return this;
    }

    @Override
    public Query or(Criterion a, Criterion b) {
        Closure orClosure = new Closure(this) {
            public void doCall() {
                DetachedCriteria owner = (DetachedCriteria) getDelegate();
                owner.add(Restrictions.or(a,b));
            }
        };
        detachedCriteria.and(orClosure);
           return this;
    }

    @Override
    public Query allEq(Map<String, Object> values) {
        values.forEach((key, value) -> {
            detachedCriteria.eq(key,value);
        });
        return this;
    }

    @Override
    public Query ge(String property, Object value) {
        detachedCriteria.ge(property, value);
        return this;
    }

    @Override
    public Query le(String property, Object value) {
        detachedCriteria.le(property, value);
        return this;
    }

    @Override
    public Query gte(String property, Object value) {
        detachedCriteria.gte(property, value);
        return this;
    }

    @Override
    public Query lte(String property, Object value) {
        detachedCriteria.lte(property, value);
        return this;
    }

    @Override
    public Query lt(String property, Object value) {
        detachedCriteria.lt(property, value);
        return this;
    }

    @Override
    public Query in(String property, List values) {
        detachedCriteria.in(property,values);
        return this;
    }

    @Override
    public Query between(String property, Object start, Object end) {
        detachedCriteria.between(property,start,end);
        return this;
    }

    @Override
    public Query like(String property, String expr) {
        detachedCriteria.like(property, expr);
        return this;
    }

    @Override
    public Query ilike(String property, String expr) {
        detachedCriteria.ilike(property, expr);
        return this;
    }

    @Override
    public Query rlike(String property, String expr) {
        detachedCriteria.rlike(property, expr);
        return this;
    }


    //TODO THIS IS USED BY AbstractCriteriaBuilder which is not the parent of
    // AbstractHibernateCriteriaBuilder
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


    private CriteriaAndAlias getOrCreateAlias(String associationName, String alias) {
        CriteriaAndAlias subCriteria = null;
        String associationPath = getAssociationPath(associationName);
        CriteriaQuery parentCriteria = getCriteriaBuilder().createQuery(entity.getJavaClass());
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
        detachedCriteria.order(order);
        return this;
    }

    @Override
    public Query join(String property) {
        detachedCriteria.join(property);
        return this;
    }

    @Override
    public Query select(String property) {
        detachedCriteria.select(property);
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
        try {
            return getQuery().getSingleResult();
        }
        catch (jakarta.persistence.NoResultException e) {
           return null;
        }
    }

    private final Predicate<Projection> idProjectionPredicate = projection -> projection instanceof IdProjection;
    private final Predicate<Projection> countProjectionPredicate = projection -> projection instanceof CountProjection;
    private final Predicate<Projection> countDistinctProjection = projection -> projection instanceof CountDistinctProjection;
    private final Predicate<Projection> maxProjectionPredicate = projection -> projection instanceof MaxProjection;
    private final Predicate<Projection> minProjectionPredicate = projection -> projection instanceof MinProjection;
    private final Predicate<Projection> sumProjectionPredicate = projection -> projection instanceof SumProjection;
    private final Predicate<Projection> avgProjectionPredicate = projection -> projection instanceof AvgProjection;
    private final Predicate<Projection> propertyProjectionPredicate = projection -> projection instanceof PropertyProjection;

    @SuppressWarnings("unchecked")
    Predicate<Projection>[] projectionPredicates = new Predicate[] {
            idProjectionPredicate
            , propertyProjectionPredicate
            , countProjectionPredicate
            , countDistinctProjection
            , maxProjectionPredicate
            , minProjectionPredicate
            , sumProjectionPredicate
            , avgProjectionPredicate
    } ;

    @SafeVarargs
    private static <T> Predicate<T> combinePredicates(Predicate<T>... predicates) {
        return Arrays.stream(predicates)
                .reduce(Predicate::or)
                .orElse(x -> true);
    }

    private org.hibernate.query.Query getQuery() {
        HibernateCriteriaBuilder cb = getCriteriaBuilder();
        List<Projection> projectionList = projections().getProjectionList();
        List<Projection> projections = projectionList
                .stream()
                .filter(combinePredicates(projectionPredicates)).toList();
        List<GroupPropertyProjection> groupProjections = projectionList
                .stream()
                .filter(GroupPropertyProjection.class::isInstance)
                .map(GroupPropertyProjection.class::cast)
                .toList();
        List<String> joinColumns = ((Map<String, FetchType>) detachedCriteria.getFetchStrategies())
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(FetchType.EAGER))
                .map(Map.Entry::getKey)
                .toList();


        CriteriaQuery cq;
        Root root;
        if (!groupProjections.isEmpty() && !projections.isEmpty()) {
            cq = cb.createQuery(Object[].class);
            root = cq.from(entity.getJavaClass());
            List<Expression> groupByPaths = groupProjections
                    .stream()
                    .map(groupPropertyProjection -> root.get(groupPropertyProjection.getPropertyName()))
                    .map(Expression.class::cast)
                    .toList();
            List<Expression> projectionExpressions = projections
                    .stream()
                    .map(projectionToJpaExpression(cb, root))
                    .filter(Objects::nonNull)
                    .map(Expression.class::cast)
                    .toList();
            cq.multiselect(projectionExpressions);
            cq.groupBy(groupByPaths);
        } else if (groupProjections.isEmpty() && !projections.isEmpty())  {
            cq = cb.createQuery(Object[].class);
            root = cq.from(entity.getJavaClass());
            List<Expression> projectionExpressions = projections
                    .stream()
                    .map(projectionToJpaExpression(cb, root))
                    .filter(Objects::nonNull)
                    .map(Expression.class::cast)
                    .toList();
            if (projectionExpressions.size() == 1) {
                cq.select(projectionExpressions.get(0));
            } else {
                cq.multiselect(projectionExpressions);
            }
        } else if (!groupProjections.isEmpty() && projections.isEmpty()) {
            throw new RuntimeException("Group By without projections");
        } else {
            cq = cb.createQuery(entity.getJavaClass());
            root = cq.from(entity.getJavaClass());
            cq.select(root);
            if (joinColumns.size() > 0) {

                Map<String, JoinType> joinTypes = detachedCriteria.getJoinTypes();
                joinColumns.forEach( joinColumn ->{
                    JoinType joinType =  joinTypes.entrySet()
                            .stream()
                            .filter(entry -> entry.getKey().equals(joinColumn))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(JoinType.INNER);
                    root.join(joinColumn, joinType);
                });
            }
        }
        List<Order> orders = detachedCriteria.getOrders();
        if (!orders.isEmpty()) {
            cq.orderBy(orders
                    .stream()
                    .map(order -> {
                        if (order.getDirection().equals(Order.Direction.ASC)) {
                            return cb.asc(root.get(order.getProperty()));
                        } else {
                            return cb.desc(root.get(order.getProperty()));
                        }
                    })
                    .toList()
            );
        }

        List<Query.Criterion>  criteriaList = (List<Query.Criterion>)detachedCriteria.getCriteria();
        if (!criteriaList.isEmpty()) {
            cq.where(cb.and(PredicateGenerator.getPredicates(cb, cq, root, criteriaList)));
        }

        org.hibernate.query.Query query = getSessionFactory()
                .getCurrentSession()
                .createQuery(cq)
                .setFirstResult(this.offset)
                .setHint("org.hibernate.cacheable", queryCache);;
        if (this.max > -1) {
            query.setMaxResults(this.max);
        }
        if (Objects.nonNull(lockResult)) {
            query.setLockMode(lockResult);
        }
        return query;
    }

    private Function<Projection, JpaExpression> projectionToJpaExpression(HibernateCriteriaBuilder cb, Root root) {
        return projection -> {
            if (countProjectionPredicate.test(projection)) {
                return cb.count(root);
            } else if (countDistinctProjection.test(projection)) {
                return cb.countDistinct(root);
            } else if (maxProjectionPredicate.test(projection)) {
                Path path = root.get(((PropertyProjection) projection).getPropertyName());
                return cb.max(path);
            } else if (minProjectionPredicate.test(projection)) {
                Path path = root.get(((PropertyProjection) projection).getPropertyName());
                return cb.min(path);
            } else if (avgProjectionPredicate.test(projection)) {
                Path path = root.get(((PropertyProjection) projection).getPropertyName());
                return cb.avg(path);
            } else if (sumProjectionPredicate.test(projection)) {
                Path path = root.get(((PropertyProjection) projection).getPropertyName());
                return cb.sum(path);
            } else if (idProjectionPredicate.test(projection)) {
               return (JpaExpression) root.get("id");
            } else if (propertyProjectionPredicate.test(projection)) { // keep this last!!!
                return (JpaExpression)root.get(((PropertyProjection) projection).getPropertyName());
            }
            return null;
        };
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
