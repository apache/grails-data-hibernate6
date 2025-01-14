package org.grails.orm.hibernate.query;

import grails.gorm.MultiTenant;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.MissingMethodException;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.BuildableCriteria;
import org.grails.datastore.mapping.query.api.Criteria;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.hibernate.FetchMode;
import org.hibernate.HibernateException;
import org.hibernate.Metamodel;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.transform.ResultTransformer;
import org.hibernate.type.Type;
import org.springframework.beans.BeanUtils;
import org.springframework.core.convert.ConversionService;
import org.grails.datastore.mapping.query.api.Criteria;
import org.grails.datastore.mapping.query.api.ProjectionList;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract super class for sharing code between Hibernate 3 and 4 implementations of HibernateCriteriaBuilder
 *
 * @author Graeme Rocher
 * @since 3.0.7
 */
public abstract class AbstractHibernateCriteriaBuilder extends GroovyObjectSupport implements org.grails.datastore.mapping.query.api.BuildableCriteria, org.grails.datastore.mapping.query.api.ProjectionList  {

    public static final String AND = "and"; // builder
    public static final String IS_NULL = "isNull"; // builder
    public static final String IS_NOT_NULL = "isNotNull"; // builder
    public static final String NOT = "not";// builder
    public static final String OR = "or"; // builder
    public static final String ID_EQUALS = "idEq"; // builder
    public static final String IS_EMPTY = "isEmpty"; //builder
    public static final String IS_NOT_EMPTY = "isNotEmpty"; //builder
    public static final String RLIKE = "rlike";//method
    public static final String BETWEEN = "between";//method
    public static final String EQUALS = "eq";//method
    public static final String EQUALS_PROPERTY = "eqProperty";//method
    public static final String GREATER_THAN = "gt";//method
    public static final String GREATER_THAN_PROPERTY = "gtProperty";//method
    public static final String GREATER_THAN_OR_EQUAL = "ge";//method
    public static final String GREATER_THAN_OR_EQUAL_PROPERTY = "geProperty";//method
    public static final String ILIKE = "ilike";//method
    public static final String IN = "in";//method
    public static final String LESS_THAN = "lt"; //method
    public static final String LESS_THAN_PROPERTY = "ltProperty";//method
    public static final String LESS_THAN_OR_EQUAL = "le";//method
    public static final String LESS_THAN_OR_EQUAL_PROPERTY = "leProperty";//method
    public static final String LIKE = "like";//method
    public static final String NOT_EQUAL = "ne";//method
    public static final String NOT_EQUAL_PROPERTY = "neProperty";//method
    public static final String SIZE_EQUALS = "sizeEq"; //method
    public static final String ORDER_DESCENDING = "desc";
    public static final String ORDER_ASCENDING = "asc";
    protected static final String ROOT_DO_CALL = "doCall";
    protected static final String ROOT_CALL = "call";
    protected static final String LIST_CALL = "list";
    protected static final String LIST_DISTINCT_CALL = "listDistinct";
    protected static final String COUNT_CALL = "count";
    protected static final String GET_CALL = "get";
    protected static final String SCROLL_CALL = "scroll";
    protected static final String SET_RESULT_TRANSFORMER_CALL = "setResultTransformer";
    protected static final String PROJECTIONS = "projections";


    protected SessionFactory sessionFactory;
    protected Session hibernateSession;
    protected Class<?> targetClass;
    protected CriteriaQuery criteria;
    protected MetaClass criteriaMetaClass;
    protected boolean uniqueResult = false;
    protected List<LogicalExpression> logicalExpressionStack = new ArrayList<LogicalExpression>();
    protected List<String> associationStack = new ArrayList<String>();
    protected boolean participate;
    protected boolean scroll;
    protected boolean count;
    protected List<String> aliasStack = new ArrayList<String>();
    protected Map<String, String> aliasMap = new HashMap<String, String>();
    protected static final String ALIAS = "_alias";
    protected ResultTransformer resultTransformer;
    protected int aliasCount;
    protected boolean paginationEnabledList = false;
    protected ConversionService conversionService;
    protected int defaultFlushMode;
    protected AbstractHibernateDatastore datastore;
    protected HibernateCriteriaBuilder cb;
    protected Root root;

    @SuppressWarnings("rawtypes")
    public AbstractHibernateCriteriaBuilder(Class targetClass, SessionFactory sessionFactory) {
        this.targetClass = targetClass;
        this.sessionFactory = sessionFactory;
        this.cb = sessionFactory.getCriteriaBuilder();
        this.criteria = cb.createQuery(targetClass);
        this.root = criteria.from(targetClass);
    }

    @SuppressWarnings("rawtypes")
    public AbstractHibernateCriteriaBuilder(Class targetClass, SessionFactory sessionFactory, boolean uniqueResult) {
        this.targetClass = targetClass;
        this.sessionFactory = sessionFactory;
        this.uniqueResult = uniqueResult;
        this.cb = sessionFactory.getCriteriaBuilder();
        this.criteria = cb.createQuery(targetClass);
        this.root = criteria.from(targetClass);
    }

    public void setDatastore(AbstractHibernateDatastore datastore) {
        this.datastore = datastore;
        if(MultiTenant.class.isAssignableFrom(targetClass) && datastore.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR ) {
            datastore.enableMultiTenancyFilter();
        }
    }

    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * A projection that selects a property name
     * @param propertyName The name of the property
     */
    public ProjectionList property(String propertyName) {
        return property(propertyName, null);
    }

    /**
     * A projection that selects a property name
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList property(String propertyName, String alias) {
        criteria.select(root.get(propertyName));
        return this;
    }

    /**
     * A projection that selects a distince property name
     * @param propertyName The property name
     */
    public ProjectionList distinct(String propertyName) {
        distinct(propertyName, null);
        return this;
    }

    /**
     * A projection that selects a distince property name
     * @param propertyName The property name
     * @param alias The alias to use
     */
    public ProjectionList distinct(String propertyName, String alias) {
        criteria.select(root.get(propertyName)).distinct(true);
        return this;
    }


    /**
     * Adds a projection that allows the criteria to return the property average value
     *
     * @param propertyName The name of the property
     */
    public ProjectionList avg(String propertyName) {
        return avg(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to return the property average value
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList avg(String propertyName, String alias) {
        return this;
    }

    /**
     * Use a join query
     *
     * @param associationPath The path of the association
     */
    public BuildableCriteria join(String associationPath) {
        return this;
    }

    public BuildableCriteria join(String property, JoinType joinType) {
        return this;
    }


    /**
     * Whether a pessimistic lock should be obtained.
     *
     * @param shouldLock True if it should
     */
    public void lock(boolean shouldLock) {
    }

    /**
     * Use a select query
     *
     * @param associationPath The path of the association
     */
    public BuildableCriteria select(String associationPath) {
        return this;
    }

    /**
     * Whether to use the query cache
     * @param shouldCache True if the query should be cached
     */
    public BuildableCriteria cache(boolean shouldCache) {
        return this;
    }

    /**
     * Whether to check for changes on the objects loaded
     * @param readOnly True to disable dirty checking
     */
    public BuildableCriteria readOnly(boolean readOnly) {
        return this;
    }

    /**
     * Calculates the property name including any alias paths
     *
     * @param propertyName The property name
     * @return The calculated property name
     */
    protected String calculatePropertyName(String propertyName) {
        String lastAlias = getLastAlias();
        if (lastAlias != null) {
            return lastAlias +'.'+propertyName;
        }

        return propertyName;
    }

    private String getLastAlias() {
        if (aliasStack.size() > 0) {
            return aliasStack.get(aliasStack.size() - 1).toString();
        }
        return null;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }

    /**
     * Calculates the property value, converting GStrings if necessary
     *
     * @param propertyValue The property value
     * @return The calculated property value
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object calculatePropertyValue(Object propertyValue) {
        if (propertyValue instanceof CharSequence) {
            return propertyValue.toString();
        }
        return propertyValue;
    }


    /**
     * Adds a projection that allows the criteria to return the property count
     *
     * @param propertyName The name of the property
     */
    public void count(String propertyName) {
        count(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to return the property count
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public void count(String propertyName, String alias) {

    }

    public ProjectionList id() {
        return this;
    }

    public ProjectionList count() {
        return rowCount();
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     */
    public ProjectionList countDistinct(String propertyName) {
        return countDistinct(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     */
    public ProjectionList groupProperty(String propertyName) {
        groupProperty(propertyName, null);
        return this;
    }

    public ProjectionList distinct() {
        return this;
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList countDistinct(String propertyName, String alias) {
        return this;
    }


    /**
     * Adds a projection that allows the criteria's result to be grouped by a property
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList groupProperty(String propertyName, String alias) {
        return this;
    }

    /**
     * Adds a projection that allows the criteria to retrieve a  maximum property value
     *
     * @param propertyName The name of the property
     */
    public ProjectionList max(String propertyName) {
        return max(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to retrieve a  maximum property value
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList max(String propertyName, String alias) {
        return this;
    }

    /**
     * Adds a projection that allows the criteria to retrieve a  minimum property value
     *
     * @param propertyName The name of the property
     */
    public ProjectionList min(String propertyName) {
        return min(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to retrieve a  minimum property value
     *
     * @param alias The alias to use
     */
    public ProjectionList min(String propertyName, String alias) {
        return this;
    }

    /**
     * Adds a projection that allows the criteria to return the row count
     *
     */
    public ProjectionList rowCount() {
        return rowCount(null);
    }

    /**
     * Adds a projection that allows the criteria to return the row count
     *
     * @param alias The alias to use
     */
    public ProjectionList rowCount(String alias) {
        return this;
    }

    /**
     * Adds a projection that allows the criteria to retrieve the sum of the results of a property
     *
     * @param propertyName The name of the property
     */
    public ProjectionList sum(String propertyName) {
        return sum(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to retrieve the sum of the results of a property
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList sum(String propertyName, String alias) {
        return this;
    }

    /**
     * Sets the fetch mode of an associated path
     *
     * @param associationPath The name of the associated path
     * @param fetchMode The fetch mode to set
     */
    public void fetchMode(String associationPath, FetchMode fetchMode) {
    }

    /**
     * Sets the resultTransformer.
     * @param transformer The result transformer to use.
     */
    public void resultTransformer(ResultTransformer transformer) {
        resultTransformer = transformer;
    }


    /**
     * Creates a Criterion that compares to class properties for equality
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria eqProperty(String propertyName, String otherPropertyName) {
        return this;
    }

    /**
     * Creates a Criterion that compares to class properties for !equality
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria neProperty(String propertyName, String otherPropertyName) {
        return this;
    }

    /**
     * Creates a Criterion that tests if the first property is greater than the second property
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria gtProperty(String propertyName, String otherPropertyName) {
        return this;
    }

    /**
     * Creates a Criterion that tests if the first property is greater than or equal to the second property
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria geProperty(String propertyName, String otherPropertyName) {
        return this;
    }

    /**
     * Creates a Criterion that tests if the first property is less than the second property
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria ltProperty(String propertyName, String otherPropertyName) {
        return this;
    }

    /**
     * Creates a Criterion that tests if the first property is less than or equal to the second property
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria leProperty(String propertyName, String otherPropertyName) {
        return this;
    }

    @Override
    public Criteria allEq(Map<String, Object> propertyValues) {
        return this;
    }


    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria eqAll(String propertyName, Closure<?> propertyValue) {
        return eqAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }


    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria gtAll(String propertyName, Closure<?> propertyValue) {
        return gtAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria ltAll(String propertyName, Closure<?> propertyValue) {
        return ltAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria geAll(String propertyName, Closure<?> propertyValue) {
        return geAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria leAll(String propertyName, Closure<?> propertyValue) {
        return leAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria eqAll(String propertyName,
                                                                 @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        return this;
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria gtAll(String propertyName,
                                                                 @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        return this;
    }

    @Override
    public Criteria gtSome(String propertyName, QueryableCriteria propertyValue) {
        return this;
    }

    @Override
    public Criteria gtSome(String propertyName, Closure<?> propertyValue) {
        return gtSome(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    @Override
    public Criteria geSome(String propertyName, QueryableCriteria propertyValue) {
        return this;
    }

    @Override
    public Criteria geSome(String propertyName, Closure<?> propertyValue) {
        return geSome(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    @Override
    public Criteria ltSome(String propertyName, QueryableCriteria propertyValue) {
        return this;
    }

    @Override
    public Criteria ltSome(String propertyName, Closure<?> propertyValue) {
        return ltSome(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    @Override
    public Criteria leSome(String propertyName, QueryableCriteria propertyValue) {
        return this;
    }

    @Override
    public Criteria leSome(String propertyName, Closure<?> propertyValue) {
        return leSome(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    @Override
    public Criteria in(String propertyName, QueryableCriteria<?> subquery) {
        return inList(propertyName, subquery);
    }

    @Override
    public Criteria inList(String propertyName, QueryableCriteria<?> subquery) {
        return this;
    }

    @Override
    public Criteria in(String propertyName, Closure<?> subquery) {
        return inList(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(subquery));
    }

    @Override
    public Criteria inList(String propertyName, Closure<?> subquery) {
        return inList(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(subquery));
    }

    @Override
    public Criteria notIn(String propertyName, QueryableCriteria<?> subquery) {
        return this;
    }

    @Override
    public Criteria notIn(String propertyName, Closure<?> subquery) {
        return notIn(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(subquery));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria ltAll(String propertyName,
                                                                 @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        return this;

    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria geAll(String propertyName,
                                                                 @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        return this;

    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria leAll(String propertyName,
                                                                 @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        return this;

    }

    /**
     * Creates a "greater than" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria gt(String propertyName, Object propertyValue) {
        return this;
    }

    public Criteria lte(String s, Object o) {
        return le(s,o);
    }

    /**
     * Creates a "greater than or equal to" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria ge(String propertyName, Object propertyValue) {
        return this;
    }

    /**
     * Creates a "less than" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria lt(String propertyName, Object propertyValue) {
        return this;
    }

    /**
     * Creates a "less than or equal to" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria le(String propertyName, Object propertyValue) {
        return this;
    }

    public Criteria idEquals(Object o) {
        return idEq(o);
    }

    @Override
    public Criteria exists(QueryableCriteria<?> subquery) {
        return this;
    }

    @Override
    public Criteria notExists(QueryableCriteria<?> subquery) {
        return this;
    }

    public Criteria isEmpty(String property) {
        String propertyName = calculatePropertyName(property);
        return this;
    }

    public Criteria isNotEmpty(String property) {
        String propertyName = calculatePropertyName(property);
        return this;
    }

    public Criteria isNull(String property) {
        String propertyName = calculatePropertyName(property);
        return this;
    }

    public Criteria isNotNull(String property) {
        String propertyName = calculatePropertyName(property);
        return this;
    }

    @Override
    public Criteria and(Closure callable) {
        return executeLogicalExpression(callable, AND);
    }

    @Override
    public Criteria or(Closure callable) {
        return executeLogicalExpression(callable, OR);
    }

    @Override
    public Criteria not(Closure callable) {
        return executeLogicalExpression(callable, NOT);
    }

    protected Criteria executeLogicalExpression(Closure callable, String logicalOperator) {
        logicalExpressionStack.add(new LogicalExpression(logicalOperator));
        try {
            invokeClosureNode(callable);
        } finally {
            LogicalExpression logicalExpression = logicalExpressionStack.remove(logicalExpressionStack.size()-1);
        }

        return this;
    }

    /**
     * Creates an "equals" Criterion based on the specified property name and value. Case-sensitive.
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria eq(String propertyName, Object propertyValue) {
        return eq(propertyName, propertyValue, Collections.emptyMap());
    }

    public Criteria idEq(Object o) {
        return eq("id", o);
    }

    /**
     * Groovy moves the map to the first parameter if using the idiomatic form, e.g.
     * <code>eq 'firstName', 'Fred', ignoreCase: true</code>.
     * @param params optional map with customization parameters; currently only 'ignoreCase' is supported.
     * @param propertyName
     * @param propertyValue
     * @return A Criterion instance
     */
    @SuppressWarnings("rawtypes")
    public Criteria eq(Map params, String propertyName, Object propertyValue) {
        return eq(propertyName, propertyValue, params);
    }

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     * Supports case-insensitive search if the <code>params</code> map contains <code>true</code>
     * under the 'ignoreCase' key.
     * @param propertyName The property name
     * @param propertyValue The property value
     * @param params optional map with customization parameters; currently only 'ignoreCase' is supported.
     *
     * @return A Criterion instance
     */
    @SuppressWarnings("rawtypes")
    public Criteria eq(String propertyName, Object propertyValue, Map params) {
        return this;
    }

    /**
     * Applies a sql restriction to the results to allow something like:
     *
     * @param sqlRestriction the sql restriction
     * @return a Criteria instance
     */
    public Criteria sqlRestriction(String sqlRestriction) {
        return sqlRestriction(sqlRestriction, Collections.EMPTY_LIST);
    }

    /**
     * Applies a sql restriction to the results to allow something like:
     *
     * @param sqlRestriction the sql restriction
     * @param values jdbc parameters
     * @return a Criteria instance
     */
    public Criteria sqlRestriction(String sqlRestriction, List<?> values) {
        return this;
    }

    /**
     * Creates a Criterion with from the specified property name and "like" expression
     * @param propertyName The property name
     * @param propertyValue The like value
     *
     * @return A Criterion instance
     */
    public Criteria like(String propertyName, Object propertyValue) {
        return this;
    }



    /**
     * Creates a Criterion with from the specified property name and "ilike" (a case sensitive version of "like") expression
     * @param propertyName The property name
     * @param propertyValue The ilike value
     *
     * @return A Criterion instance
     */
    public Criteria ilike(String propertyName, Object propertyValue) {

        return this;
    }

    /**
     * Applys a "in" contrain on the specified property
     * @param propertyName The property name
     * @param values A collection of values
     *
     * @return A Criterion instance
     */
    @SuppressWarnings("rawtypes")
    public Criteria in(String propertyName, Collection values) {
        return this;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected List convertArgumentList(List argList) {
        List convertedList = new ArrayList(argList.size());
        for (Object item : argList) {
            if(item instanceof CharSequence) {
                item = item.toString();
            }
            convertedList.add(item);
        }
        return convertedList;
    }
    /**
     * Delegates to in as in is a Groovy keyword
     */
    @SuppressWarnings("rawtypes")
    public Criteria inList(String propertyName, Collection values) {
        return in(propertyName, values);
    }

    /**
     * Delegates to in as in is a Groovy keyword
     */
    public Criteria inList(String propertyName, Object[] values) {
        return in(propertyName, values);
    }

    /**
     * Applys a "in" contrain on the specified property
     * @param propertyName The property name
     * @param values A collection of values
     *
     * @return A Criterion instance
     */
    public Criteria in(String propertyName, Object[] values) {
        return this;
    }

    /**
     * Orders by the specified property name (defaults to ascending)
     *
     * @param propertyName The property name to order by
     * @return A Order instance
     */
    public Criteria order(String propertyName) {
        return this;
    }



    @Override
    public Criteria order(Query.Order o) {
        return this;
    }




    /**
     * Orders by the specified property name and direction
     *
     * @param propertyName The property name to order by
     * @param direction Either "asc" for ascending or "desc" for descending
     *
     * @return A Order instance
     */
    public Criteria order(String propertyName, String direction) {
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property by size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeEq(String propertyName, int size) {
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be greater than the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeGt(String propertyName, int size) {
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be greater than or equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeGe(String propertyName, int size) {
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than or equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeLe(String propertyName, int size) {
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeLt(String propertyName, int size) {
        return this;
    }

    /**
     * Creates a Criterion with from the specified property name and "rlike" (a regular expression version of "like") expression
     *
     * @param propertyName  The property name
     * @param propertyValue The ilike value
     * @return A Criterion instance
     */
    public org.grails.datastore.mapping.query.api.Criteria rlike(String propertyName, Object propertyValue) {
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be not equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeNe(String propertyName, int size) {
        return this;
    }

    /**
     * Creates a "not equal" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return The criterion object
     */
    public Criteria ne(String propertyName, Object propertyValue) {
        return this;
    }


    /**
     * Creates a "between" Criterion based on the property name and specified lo and hi values
     * @param propertyName The property name
     * @param lo The low value
     * @param hi The high value
     * @return A Criterion instance
     */
    public Criteria between(String propertyName, Object lo, Object hi) {
        return this;
    }

    public Criteria gte(String s, Object o) {
        return ge(s, o);
    }

    protected boolean validateSimpleExpression() {
        return criteria != null;
    }

    @Override
    public Object list(@DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(LIST_CALL, new Object[]{c});
    }

    @Override
    public Object list(Map params, @DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(LIST_CALL, new Object[]{params, c});
    }
    
    @Override
    public Object listDistinct(@DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(LIST_DISTINCT_CALL, new Object[]{c});
    }

    @Override
    public Object get(@DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(GET_CALL, new Object[]{c});
    }
    
    @Override
    public Object scroll(@DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(SCROLL_CALL, new Object[]{c});
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public Object invokeMethod(String name, Object obj) {
        Object[] args = obj.getClass().isArray() ? (Object[])obj : new Object[]{obj};
//
//        if (paginationEnabledList && SET_RESULT_TRANSFORMER_CALL.equals(name) && args.length == 1 &&
//                args[0] instanceof ResultTransformer) {
//            resultTransformer = (ResultTransformer) args[0];
//            return null;
//        }
//
//        if (isCriteriaConstructionMethod(name, args)) {
//            if (criteria != null) {
//                throwRuntimeException(new IllegalArgumentException("call to [" + name + "] not supported here"));
//            }
//
//            if (name.equals(GET_CALL)) {
//                uniqueResult = true;
//            }
//            else if (name.equals(SCROLL_CALL)) {
//                scroll = true;
//            }
//            else if (name.equals(COUNT_CALL)) {
//                count = true;
//            }
////            else if (name.equals(LIST_DISTINCT_CALL)) {
////                resultTransformer = CriteriaSpecification.DISTINCT_ROOT_ENTITY;
////            }
//
//            createCriteriaInstance();
//
//            // Check for pagination params
//            if (name.equals(LIST_CALL) && args.length == 2) {
//                paginationEnabledList = true;
//                invokeClosureNode(args[1]);
//            }
//            else {
//                invokeClosureNode(args[0]);
//            }
//
////            if (resultTransformer != null) {
////                criteria.setResultTransformer(resultTransformer);
////            }
//            Object result;
//            if (!uniqueResult) {
//                if (scroll) {
//
//                    result = hibernateSession.createQuery(criteria).scroll();
//                }
//                else if (count) {
//                    criteria.select(cb.count(root));
//                    result = hibernateSession.createQuery(criteria).getSingleResult();
//                }
//                else if (paginationEnabledList) {
//                    // Calculate how many results there are in total. This has been
//                    // moved to before the 'list()' invocation to avoid any "ORDER
//                    // BY" clause added by 'populateArgumentsForCriteria()', otherwise
//                    // an exception is thrown for non-string sort fields (GRAILS-2690).
//                    criteria.setFirstResult(0);
//                    criteria.setMaxResults(Integer.MAX_VALUE);
//
//                    // Restore the previous projection, add settings for the pagination parameters,
//                    // and then execute the query.
//                    boolean isProjection = (projectionList != null && projectionList.getLength() > 0);
//                    criteria.setProjection(isProjection ? projectionList : null);
//
//                    for (Order orderEntry : orderEntries) {
//                        criteria.addOrder(orderEntry);
//                    }
//                    if (resultTransformer == null) {
//                        // GRAILS-9644 - Use projection transformer
//                        criteria.setResultTransformer( isProjection ?
//                                        CriteriaSpecification.PROJECTION :
//                                        CriteriaSpecification.ROOT_ENTITY
//                        );
//                    }
//                    else if (paginationEnabledList) {
//                        // relevant to GRAILS-5692
//                        criteria.setResultTransformer(resultTransformer);
//                    }
//                    // GRAILS-7324 look if we already have association to sort by
//                    Map argMap = (Map)args[0];
//                    final String sort = (String) argMap.get(HibernateQueryConstants.ARGUMENT_SORT);
//                    if (sort != null) {
//                        boolean ignoreCase = true;
//                        Object caseArg = argMap.get(HibernateQueryConstants.ARGUMENT_IGNORE_CASE);
//                        if (caseArg instanceof Boolean) {
//                            ignoreCase = (Boolean) caseArg;
//                        }
//                        final String orderParam = (String) argMap.get(HibernateQueryConstants.ARGUMENT_ORDER);
//                        final String order = HibernateQueryConstants.ORDER_DESC.equalsIgnoreCase(orderParam) ?
//                                HibernateQueryConstants.ORDER_DESC : HibernateQueryConstants.ORDER_ASC;
//                        int lastPropertyPos = sort.lastIndexOf('.');
//                        String associationForOrdering = lastPropertyPos >= 0 ? sort.substring(0, lastPropertyPos) : null;
//                        if (associationForOrdering != null && aliasMap.containsKey(associationForOrdering)) {
//                            addOrder(criteria, aliasMap.get(associationForOrdering) + "." + sort.substring(lastPropertyPos + 1),
//                                    order, ignoreCase);
//                            // remove sort from arguments map to exclude from default processing.
//                            @SuppressWarnings("unchecked") Map argMap2 = new HashMap(argMap);
//                            argMap2.remove(HibernateQueryConstants.ARGUMENT_SORT);
//                            argMap = argMap2;
//                        }
//                    }
//                    result = createPagedResultList(argMap);
//                }
//                else {
//                    result = criteria.list();
//                }
//            }
//            else {
//                result = executeUniqueResultWithProxyUnwrap();
//            }
//            if (!participate) {
//                closeSession();
//            }
//            return result;
//        }
//
//        if (criteria == null) createCriteriaInstance();
//
//        MetaMethod metaMethod = getMetaClass().getMetaMethod(name, args);
//        if (metaMethod != null) {
//            return metaMethod.invoke(this, args);
//        }
//
//        metaMethod = criteriaMetaClass.getMetaMethod(name, args);
//        if (metaMethod != null) {
//            return metaMethod.invoke(criteria, args);
//        }
//        metaMethod = criteriaMetaClass.getMetaMethod(NameUtils.getSetterName(name), args);
//        if (metaMethod != null) {
//            return metaMethod.invoke(criteria, args);
//        }
//
//        if (isAssociationQueryMethod(args) || isAssociationQueryWithJoinSpecificationMethod(args)) {
//            final boolean hasMoreThanOneArg = args.length > 1;
//            Object callable = hasMoreThanOneArg ? args[1] : args[0];
//            int joinType = hasMoreThanOneArg ? (Integer)args[0] : org.hibernate.sql.JoinType.INNER_JOIN.getJoinTypeValue();
//
//            if (name.equals(AND) || name.equals(OR) || name.equals(NOT)) {
//                if (criteria == null) {
//                    throwRuntimeException(new IllegalArgumentException("call to [" + name + "] not supported here"));
//                }
//
//                logicalExpressionStack.add(new LogicalExpression(name));
//                invokeClosureNode(callable);
//
//                LogicalExpression logicalExpression = logicalExpressionStack.remove(logicalExpressionStack.size()-1);
//                addToCriteria(logicalExpression.toCriterion());
//
//                return name;
//            }
//
//            if (name.equals(PROJECTIONS) && args.length == 1 && (args[0] instanceof Closure)) {
//                if (criteria == null) {
//                    throwRuntimeException(new IllegalArgumentException("call to [" + name + "] not supported here"));
//                }
//
//                projectionList = Projections.projectionList();
//                invokeClosureNode(callable);
//
//                if (projectionList != null && projectionList.getLength() > 0) {
//                    criteria.setProjection(projectionList);
//                }
//
//                return name;
//            }
//
//            final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(targetClass, name);
//            if (pd != null && pd.getReadMethod() != null) {
//                final Metamodel metamodel = sessionFactory.getMetamodel();
//                final EntityType<?> entityType = metamodel.entity(targetClass);
//                final Attribute<?, ?> attribute = entityType.getAttribute(name);
//
//                if (attribute.isAssociation()) {
//                    Class oldTargetClass = targetClass;
//                    targetClass = getClassForAssociationType(attribute);
//                    if (targetClass.equals(oldTargetClass) && !hasMoreThanOneArg) {
//                        joinType = org.hibernate.sql.JoinType.LEFT_OUTER_JOIN.getJoinTypeValue(); // default to left join if joining on the same table
//                    }
//                    associationStack.add(name);
//                    final String associationPath = getAssociationPath();
//                    createAliasIfNeccessary(name, associationPath,joinType);
//                    // the criteria within an association node are grouped with an implicit AND
//                    logicalExpressionStack.add(new LogicalExpression(AND));
//                    invokeClosureNode(callable);
//                    aliasStack.remove(aliasStack.size() - 1);
//                    if (!aliasInstanceStack.isEmpty()) {
//                        aliasInstanceStack.remove(aliasInstanceStack.size() - 1);
//                    }
//                    LogicalExpression logicalExpression = logicalExpressionStack.remove(logicalExpressionStack.size()-1);
//                    if (!logicalExpression.args.isEmpty()) {
//                        addToCriteria(logicalExpression.toCriterion());
//                    }
//                    associationStack.remove(associationStack.size()-1);
//                    targetClass = oldTargetClass;
//
//                    return name;
//                }
//                if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
//                    associationStack.add(name);
//                    logicalExpressionStack.add(new LogicalExpression(AND));
//                    Class oldTargetClass = targetClass;
//                    targetClass = pd.getPropertyType();
//                    invokeClosureNode(callable);
//                    targetClass = oldTargetClass;
//                    LogicalExpression logicalExpression = logicalExpressionStack.remove(logicalExpressionStack.size()-1);
//                    if (!logicalExpression.args.isEmpty()) {
//                        addToCriteria(logicalExpression.toCriterion());
//                    }
//                    associationStack.remove(associationStack.size()-1);
//                    return name;
//                }
//            }
//        }
//        else if (args.length == 1 && args[0] != null) {
//            if (criteria == null) {
//                throwRuntimeException(new IllegalArgumentException("call to [" + name + "] not supported here"));
//            }
//
//            Object value = args[0];
//            Criterion c = null;
//            if (name.equals(ID_EQUALS)) {
//                return eq("id", value);
//            }
//
//            if (name.equals(IS_NULL) ||
//                    name.equals(IS_NOT_NULL) ||
//                    name.equals(IS_EMPTY) ||
//                    name.equals(IS_NOT_EMPTY)) {
//                if (!(value instanceof String)) {
//                    throwRuntimeException(new IllegalArgumentException("call to [" + name + "] with value [" +
//                            value + "] requires a String value."));
//                }
//                String propertyName = calculatePropertyName((String)value);
//                if (name.equals(IS_NULL)) {
//                    c = Restrictions.isNull(propertyName);
//                }
//                else if (name.equals(IS_NOT_NULL)) {
//                    c = Restrictions.isNotNull(propertyName);
//                }
//                else if (name.equals(IS_EMPTY)) {
//                    c = Restrictions.isEmpty(propertyName);
//                }
//                else if (name.equals(IS_NOT_EMPTY)) {
//                    c = Restrictions.isNotEmpty(propertyName);
//                }
//            }
//
//            if (c != null) {
//                return addToCriteria(c);
//            }
//        }

        throw new MissingMethodException(name, getClass(), args);
    }



    protected abstract List createPagedResultList(Map args);


    private boolean isAssociationQueryMethod(Object[] args) {
        return args.length == 1 && args[0] instanceof Closure;
    }

    private boolean isAssociationQueryWithJoinSpecificationMethod(Object[] args) {
        return args.length == 2 && (args[0] instanceof Number) && (args[1] instanceof Closure);
    }




    private String getAssociationPath() {
        StringBuilder fullPath = new StringBuilder();
        for (Object anAssociationStack : associationStack) {
            String propertyName = (String) anAssociationStack;
            if (fullPath.length() > 0) fullPath.append(".");
            fullPath.append(propertyName);
        }
        return fullPath.toString();
    }

    private boolean isCriteriaConstructionMethod(String name, Object[] args) {
        return (name.equals(LIST_CALL) && args.length == 2 && args[0] instanceof Map && args[1] instanceof Closure) ||
                (name.equals(ROOT_CALL) ||
                        name.equals(ROOT_DO_CALL) ||
                        name.equals(LIST_CALL) ||
                        name.equals(LIST_DISTINCT_CALL) ||
                        name.equals(GET_CALL) ||
                        name.equals(COUNT_CALL) ||
                        name.equals(SCROLL_CALL) && args.length == 1 && args[0] instanceof Closure);
    }

//    public Criteria buildCriteria(Closure<?> criteriaClosure) {
//        createCriteriaInstance();
//        criteriaClosure.setDelegate(this);
//        criteriaClosure.call();
//        return criteria;
//    }


    protected abstract void createCriteriaInstance();

    protected abstract void cacheCriteriaMapping();


    private void invokeClosureNode(Object args) {
        Closure<?> callable = (Closure<?>)args;
        callable.setDelegate(this);
        callable.setResolveStrategy(Closure.DELEGATE_FIRST);
        callable.call();
    }






    /**
     * Returns the criteria instance
     * @return The criteria instance
     */
    public CriteriaQuery getInstance() {
        return criteria;
    }

    /**
     * Set whether a unique result should be returned
     * @param uniqueResult True if a unique result should be returned
     */
    public void setUniqueResult(boolean uniqueResult) {
        this.uniqueResult = uniqueResult;
    }



    protected abstract Class getClassForAssociationType(Attribute<?, ?> type);


    /**
     * instances of this class are pushed onto the logicalExpressionStack
     * to represent all the unfinished "and", "or", and "not" expressions.
     */
    protected class LogicalExpression {
        public final Object name;

        public LogicalExpression(Object name) {
            this.name = name;
        }



    }
    /**
     * Throws a runtime exception where necessary to ensure the session gets closed
     */
    protected void throwRuntimeException(RuntimeException t) {
        closeSessionFollowingException();
        throw t;
    }

    private void closeSessionFollowingException() {
        closeSession();
        criteria = null;
    }

    /**
     * Closes the session if it is copen
     */
    protected void closeSession() {
        if (hibernateSession != null && hibernateSession.isOpen() && !participate) {
            hibernateSession.close();
        }
    }

    public int getDefaultFlushMode() {
        return defaultFlushMode;
    }

    public void setDefaultFlushMode(int defaultFlushMode) {
        this.defaultFlushMode = defaultFlushMode;
    }
}
