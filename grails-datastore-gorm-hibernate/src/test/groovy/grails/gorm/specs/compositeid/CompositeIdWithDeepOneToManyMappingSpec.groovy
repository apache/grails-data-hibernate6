package grails.gorm.specs.compositeid

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import org.jetbrains.annotations.NotNull
import spock.lang.Ignore
import spock.lang.Issue

/**
 * Created by graemerocher on 26/01/2017.
 */
@Ignore("AUTO CREATE DDL NOT WORKING")
class CompositeIdWithDeepOneToManyMappingSpec extends HibernateGormDatastoreSpec {

    @Override
    List getDomainClasses() {
       [GrandParent, Parent, Child]
    }


    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/660')
    void 'test composite id with nested one-to-many mappings'() {
        when:
        def grandParent = new GrandParent(luckyNumber: 7, name: "Fred")
        def parent = new Parent(name: "Bob")
        grandParent.addToParents(parent)
        parent.addToChildren(new Child(name:"Chuck"))
        grandParent.save(flush:true)


        then:
        Parent.count == 1
        GrandParent.count == 1
        Child.count == 1
        GrandParent.list().first().parents.first().children.first().parent != null
    }
}

@Entity
class Child implements Serializable, Comparable<Child> {
    String name

    static belongsTo= [parent: Parent]

    static mapping = MappingBuilder.define {
        composite('parent', 'name')
    }

    @Override
    int compareTo(@NotNull Child o) {
        return this.name <=> o.name
    }
}

@Entity
class Parent implements Serializable, Comparable<Parent> {
    String name
    TreeSet<Child> children

    static belongsTo= [grandParent: GrandParent]
    static hasMany= [children: Child]

    static mapping= MappingBuilder.define {
        composite('grandParent', 'name') cascade('all')
    }

    @Override
    int compareTo(@NotNull Parent o) {
        return this.name <=> o.name
    }
}

@Entity
class GrandParent implements Serializable {
    String name
    Integer luckyNumber
    TreeSet<Parent> parents

    static hasMany= [parents: Parent]

    static mapping= MappingBuilder.define {
        composite('name', 'luckyNumber') cascade("all")

    }
}