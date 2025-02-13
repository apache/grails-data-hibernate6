package org.grails.orm.hibernate.query;

import groovy.util.logging.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;
import org.hibernate.Session;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaInPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmInListPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class PredicateGenerator {
    private static final Logger log = LoggerFactory.getLogger(PredicateGenerator.class);

    public static Predicate[] getPredicates(HibernateCriteriaBuilder cb, CriteriaQuery criteriaQuery, From root_, List<Query.Criterion> criteriaList, EntityManager entityManager) {


        return criteriaList.stream().
                map(criterion -> {
                    if (criterion instanceof Query.Disjunction) {
                        List<Query.Criterion> criterionList = ((Query.Disjunction) criterion).getCriteria();
                        return cb.or(getPredicates(cb, criteriaQuery, root_, criterionList, entityManager));
                    } else if (criterion instanceof Query.Conjunction) {
                        List<Query.Criterion> criterionList = ((Query.Conjunction) criterion).getCriteria();
                        return cb.and(getPredicates(cb, criteriaQuery, root_, criterionList, entityManager));
                    } else if (criterion instanceof Query.Negation) {
                        List<Query.Criterion> criterionList = ((Query.Negation) criterion).getCriteria();
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, root_, criterionList, entityManager);
                        if (predicates.length != 1) {
                            log.error("Must have a single predicate behind a not");
                            throw new RuntimeException("Must have a single predicate behind a not");
                        }
                        return cb.not(predicates[0]);
                    } else if (criterion instanceof Query.IsNull c) {
                        return cb.isNull(root_.get(c.getProperty()));
                    } else if (criterion instanceof Query.IsNotNull c) {
                        return cb.isNotNull(root_.get(c.getProperty()));
                    } else if (criterion instanceof Query.IsEmpty c) {
                        return cb.isEmpty(root_.get(c.getProperty()));
                    } else if (criterion instanceof Query.Equals c) {
                        return cb.equal(root_.get(c.getProperty()), c.getValue());
                    } else if (criterion instanceof Query.NotEquals c) {
                        return cb.notEqual(root_.get(c.getProperty()), c.getValue());
                    } else if (criterion instanceof Query.EqualsProperty c) {
                        return cb.equal(root_.get(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.NotEqualsProperty c) {
                        return cb.notEqual(root_.get(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.LessThanEqualsProperty c) {
                        return cb.le(root_.get(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.LessThanProperty c) {
                        return cb.lt(root_.get(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.GreaterThanEqualsProperty c) {
                        return cb.ge(root_.get(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.GreaterThanProperty c) {
                        return cb.gt(root_.get(c.getProperty()), root_.get(c.getOtherProperty()));
                    } else if (criterion instanceof Query.IdEquals c) {
                        return cb.equal(root_.get("id"), c.getValue());
                    } else if (criterion instanceof Query.GreaterThan c) {
                        return cb.gt(root_.get(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof Query.GreaterThanEquals c) {
                        return cb.ge(root_.get(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof Query.LessThan c) {
                        return cb.lt(root_.get(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof Query.LessThanEquals c) {
                        return cb.le(root_.get(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof Query.Between c) {
                        if (c.getFrom() instanceof String && c.getTo() instanceof String) {
                            return cb.between(root_.get(c.getProperty()), (String) c.getFrom(), (String) c.getTo());
                        } else if (c.getFrom() instanceof Short && c.getTo() instanceof Short) {
                            return cb.between(root_.get(c.getProperty()), (Short) c.getFrom(), (Short) c.getTo());
                        } else if (c.getFrom() instanceof Integer && c.getTo() instanceof Integer) {
                            return cb.between(root_.get(c.getProperty()), (Integer) c.getFrom(), (Integer) c.getTo());
                        } else if (c.getFrom() instanceof Long && c.getTo() instanceof Long) {
                            return cb.between(root_.get(c.getProperty()), (Long) c.getFrom(), (Long) c.getTo());
                        } else if (c.getFrom() instanceof Date && c.getTo() instanceof Date) {
                            return cb.between(root_.get(c.getProperty()), (Date) c.getFrom(), (Date) c.getTo());
                        } else if (c.getFrom() instanceof Instant && c.getTo() instanceof Instant) {
                            return cb.between(root_.get(c.getProperty()), (Instant) c.getFrom(), (Instant) c.getTo());
                        } else if (c.getFrom() instanceof LocalDate && c.getTo() instanceof LocalDate) {
                            return cb.between(root_.get(c.getProperty()), (LocalDate) c.getFrom(), (LocalDate) c.getTo());
                        } else if (c.getFrom() instanceof LocalDateTime && c.getTo() instanceof LocalDateTime) {
                            return cb.between(root_.get(c.getProperty()), (LocalDateTime) c.getFrom(), (LocalDateTime) c.getTo());
                        } else if (c.getFrom() instanceof OffsetDateTime && c.getTo() instanceof OffsetDateTime) {
                            return cb.between(root_.get(c.getProperty()), (OffsetDateTime) c.getFrom(), (OffsetDateTime) c.getTo());
                        } else if (c.getFrom() instanceof ZonedDateTime && c.getTo() instanceof ZonedDateTime) {
                            return cb.between(root_.get(c.getProperty()), (ZonedDateTime) c.getFrom(), (ZonedDateTime) c.getTo());
                        }
                    } else if (criterion instanceof Query.ILike c) {
                        return cb.ilike(root_.get(c.getProperty()), c.getValue().toString());
                    } else if (criterion instanceof Query.RLike c) {
                        return cb.like(root_.get(c.getProperty()), c.getPattern(), '\\');
                    } else if (criterion instanceof Query.Like c) {
                        return cb.like(root_.get(c.getProperty()), c.getValue().toString());
                    } else if (criterion instanceof Query.SizeEquals c) {
                        return cb.equal(cb.size(root_.get(c.getProperty())),c.getValue());
                    } else if (criterion instanceof Query.SizeGreaterThan c) {
                        return cb.gt(cb.size(root_.get(c.getProperty())),(Number) c.getValue());
                    } else if (criterion instanceof Query.SizeGreaterThanEquals c) {
                        return cb.ge(cb.size(root_.get(c.getProperty())),(Number) c.getValue());
                    } else if (criterion instanceof Query.SizeLessThan c) {
                        return cb.lt(cb.size(root_.get(c.getProperty())),(Number) c.getValue());
                    } else if (criterion instanceof Query.LessThanEquals c) {
                        return cb.le(root_.get(c.getProperty()), (Number) c.getValue());
                    } else if (criterion instanceof DetachedAssociationCriteria c
                    ) {

//                        Subquery subquery = criteriaQuery.subquery(Object.class);
//                        Root from = subquery.from(c.getPersistentEntity().getJavaClass());
//                        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, c.getCriteria(), entityManager);
//
//                        EntityType entity = entityManager.getMetamodel().entity(root_.getJavaType());
//                        PluralAttribute pluralAttribute = (PluralAttribute )entity.getPluralAttributes().stream().toList().get(0);
////                        subquery.select(from.get(root_.)).where(cb.and(predicates)).alias(c.getAlias());
//                        String associationPath = c.getAssociationPath();
//                        return cb.isMember(Optional.ofNullable(root_.get(associationPath)), from.get(pluralAttribute));
                    } else if (criterion instanceof Query.In c
                            && Objects.nonNull(c.getSubquery())
                            && !c.getSubquery().getProjections().isEmpty()
                            && c.getSubquery().getProjections().get(0) instanceof Query.PropertyProjection
                    ) {
                        JpaInPredicate in = cb.in(root_.get(c.getProperty()));
                        Query.PropertyProjection projection = (Query.PropertyProjection) c.getSubquery().getProjections().get(0);
                        boolean distinct = projection instanceof Query.DistinctPropertyProjection;
                        Subquery subquery = criteriaQuery.subquery(getJavaTypeOfInClause((SqmInListPredicate) in));
                        Root from = subquery.from(c.getSubquery().getPersistentEntity().getJavaClass());
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, c.getSubquery().getCriteria(), entityManager);
                        subquery.select(from.get(projection.getPropertyName())).distinct(distinct).where(cb.and(predicates));
                        return in.value(subquery);
                    } else if (criterion instanceof Query.In c
                            && Objects.nonNull(c.getSubquery())
                            && !c.getSubquery().getProjections().isEmpty()
                            && c.getSubquery().getProjections().get(0) instanceof Query.IdProjection
                    ) {
                        JpaInPredicate in = cb.in(root_.get("id"));
                        Subquery subquery = criteriaQuery.subquery(getJavaTypeOfInClause((SqmInListPredicate) in));
                        Root from = subquery.from(c.getSubquery().getPersistentEntity().getJavaClass());
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, c.getSubquery().getCriteria(), entityManager);
                        subquery.select(from).where(cb.and(predicates));
                        return in.value(subquery);
                    } else if (criterion instanceof Query.In c && !c.getValues().isEmpty()
                    ) {
                        return cb.in(root_.get(c.getProperty()), c.getValues());
                    } else if (criterion instanceof Query.Exists c) {
                        Subquery subquery = criteriaQuery.subquery(Object.class);
                        Root from = subquery.from(c.getSubquery().getPersistentEntity().getJavaClass());
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, c.getSubquery().getCriteria(), entityManager);
                        subquery.select(cb.literal(1)).where(cb.and(predicates));
                        return cb.exists(subquery);
                    } else if (criterion instanceof Query.NotExists c) {
                        Subquery subquery = criteriaQuery.subquery(Object.class);
                        Root from = subquery.from(c.getSubquery().getPersistentEntity().getJavaClass());
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, c.getSubquery().getCriteria(), entityManager);
                        subquery.select(cb.literal(1)).where(cb.and(predicates));
                        return cb.not(cb.exists(subquery));
                    }
                    else if (criterion instanceof Query.SubqueryCriterion c) {
                        Subquery subquery = criteriaQuery.subquery(Number.class);
                        Root from = subquery.from(c.getValue().getPersistentEntity().getJavaClass());
                        Predicate[] predicates = getPredicates(cb, criteriaQuery, from, c.getValue().getCriteria(), entityManager);
                        if (c instanceof Query.GreaterThanEqualsAll sc ) {
                            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.and(predicates));
                            return cb.greaterThanOrEqualTo(root_.get(sc.getProperty()),subquery);
                        } else if (c instanceof Query.GreaterThanAll sc ) {
                            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.and(predicates));
                            return cb.greaterThan(root_.get(sc.getProperty()),subquery);
                        } else if (c instanceof Query.LessThanEqualsAll sc ) {
                            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.and(predicates));
                            return cb.lessThanOrEqualTo(root_.get(sc.getProperty()),subquery);
                        } else if (c instanceof Query.LessThanAll sc ) {
                            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.and(predicates));
                            return cb.lessThan(root_.get(sc.getProperty()),subquery);
                        } else if (c instanceof Query.EqualsAll sc) {
                            subquery.select(from.get(c.getProperty())).where(cb.and(predicates));
                            return cb.equal(root_.get(sc.getProperty()),subquery);
                        } else if (c instanceof Query.GreaterThanEqualsSome sc ) {
                            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.or(predicates));
                            return cb.greaterThanOrEqualTo(root_.get(sc.getProperty()),subquery);
                        } else if (c instanceof Query.GreaterThanSome sc ) {
                            subquery.select(cb.max(from.get(c.getProperty()))).where(cb.or(predicates));
                            return cb.greaterThan(root_.get(sc.getProperty()),subquery);
                        } else if (c instanceof Query.LessThanEqualsSome sc ) {
                            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.or(predicates));
                            return cb.lessThanOrEqualTo(root_.get(sc.getProperty()),subquery);
                        } else if (c instanceof Query.LessThanSome sc ) {
                            subquery.select(cb.min(from.get(c.getProperty()))).where(cb.or(predicates));
                            return cb.lessThan(root_.get(sc.getProperty()), subquery);
                        } else if (criterion instanceof Query.NotIn sc
                                && Objects.nonNull(sc.getSubquery())
                                && !sc.getSubquery().getProjections().isEmpty()
                                && sc.getSubquery().getProjections().get(0) instanceof Query.PropertyProjection
                        ) {
                            Query.PropertyProjection projection = (Query.PropertyProjection) sc.getSubquery().getProjections().get(0);
                            boolean distinct = projection instanceof Query.DistinctPropertyProjection;
                            subquery.select(from.get(projection.getPropertyName())).distinct(distinct).where(cb.and(predicates));
                            return cb.in(root_.get(sc.getProperty())).value(subquery);
                        } else if (criterion instanceof Query.NotIn sc
                                && Objects.nonNull(sc.getSubquery())
                                && !sc.getSubquery().getProjections().isEmpty()
                                && sc.getSubquery().getProjections().get(0) instanceof Query.IdProjection
                        ) {
                            subquery.select(from).where(cb.and(predicates));
                            return cb.in(root_.get("id")).value(subquery);
                        }
                    }
                    return null;
                }).filter(Objects::nonNull).toList().toArray(new Predicate[0]);
    }

    private static Class getJavaTypeOfInClause(SqmInListPredicate in) {
        Class javaTypeOfInClause = in.getTestExpression().getExpressible().getExpressibleJavaType().getJavaTypeClass();
        return javaTypeOfInClause;
    }
}
