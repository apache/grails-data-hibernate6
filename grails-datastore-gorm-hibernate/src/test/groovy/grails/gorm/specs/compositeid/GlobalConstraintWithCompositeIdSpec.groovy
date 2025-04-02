package grails.gorm.specs.compositeid

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.jetbrains.annotations.NotNull
import spock.lang.Ignore
import spock.lang.Issue

/**
 * Created by graemerocher on 17/02/2017.
 */
class GlobalConstraintWithCompositeIdSpec extends HibernateGormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [ParentB,ChildB,DomainB]
    }


    Session configure() {
        ConfigObject grailsConfig = new ConfigObject()
        Map config = [
                'dataSource.url':"jdbc:tc:postgresql:latest:///dev_db",
                'dataSource.dbCreate': 'create-drop',
                'dataSource.formatSql': 'true',
                'dataSource.logSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
                'hibernate.type.descriptor.sql': 'true',
                'grails.gorm.default.constraints':{
                    '*'(nullable: true)
                }
        ]
        grailsConfig.putAll(config)
        setupClass.setup(((TEST_CLASSES + getDomainClasses()) as Set) as List, grailsConfig, true)
    }

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10457')
    void "test global constraints with composite id"() {
        when:
        ParentB parent = new ParentB(code:"AAA", desc: "BBB")
                                    .addToChildren(name:"Child A")
                                    .save(flush:true)

        then:
        ParentB.count == 1
        ChildB.count == 1
    }

    @Ignore("DDL not working for composite id")
    @Issue('https://github.com/grails/grails-data-mapping/issues/877')
    void "test global constraints with unique constraint"() {
        given:
        PersistentEntity entity = setupClass.hibernateDatastore.mappingContext.getPersistentEntity(DomainB.name)
        PropertyConfig nameProp = entity.getPropertyByName('name').mapping.mappedForm
        PropertyConfig someOtherConfig = entity.getPropertyByName('someOther').mapping.mappedForm
        expect:
        nameProp.unique
        someOtherConfig.unique
        !nameProp.uniquenessGroup.isEmpty()
        nameProp.uniquenessGroup.contains('domainB')
        someOtherConfig.uniquenessGroup.isEmpty()

    }
}


@Entity
class ParentB implements Serializable {

    String code
    String desc
    TreeSet<ChildB> children

    static hasMany = [children: ChildB]

    static constraints = {
    }

    static mapping = {
        id composite: ['code', 'desc']

        code column: 'COD'
        desc column: 'DSC'
    }
}

@Entity
class ChildB implements Serializable, Comparable<ChildB> {
    String name

    static belongsTo = [parent: ParentB]

    static constraints = {
    }

    static mapping = {
        id composite: ['name', 'parent']

        columns {
            parent {
                column name: 'COD'
                column name: 'DSC'
            }
        }
    }

    @Override
    int compareTo(@NotNull ChildB o) {
        this.name <=> o.name
    }
}

@Entity
class DomainB {

    String name

    String someOther

    static belongsTo = [domainB: DomainB]

    static constraints = {
        name nullable: false, blank: false, unique: "domainB"
        someOther nullable: false, blank: false, unique: true
    }
}
