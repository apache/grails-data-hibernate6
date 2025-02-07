package grails.gorm.tests.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.tests.Person
import grails.gorm.tests.Pet
import grails.persistence.Entity
import jakarta.persistence.criteria.JoinType
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.AbstractHibernateSession
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.query.HibernateQuery
import spock.lang.Ignore


class HibernateQuerySpec extends HibernateGormDatastoreSpec {


    HibernateQuery hibernateQuery
    HibernateQuery petHibernateQuery

    Person oldBob

    def setup() {
        HibernateDatastore hibernateDatastore = setupClass.hibernateDatastore
        AbstractHibernateSession session = hibernateDatastore.connect() as AbstractHibernateSession
        hibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName))
        petHibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(Pet.typeName))
        oldBob = new Person(firstName: "Bob", lastName: "Builder", age: 50).save(flush: true)
    }

    List getDomainClasses() {
        [Person]
    }

    def equals() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.eq("age", 50)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    @Ignore("Need better implementation of Predicate")
    def idEq() {
        given:
        Person oldFred = new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.idEq(oldFred.id)
        when:
        def newFred = hibernateQuery.singleResult()
        then:
        oldFred == newFred
    }

    def gt() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        hibernateQuery.gt("age", 49)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def ge() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        hibernateQuery.ge("age", 50)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def le() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.le("age", 50)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def lt() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.lt("age", 51)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def like() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.like("firstName", "Bo%")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def ilike() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.ilike("firstName", "BO%")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    @Ignore("Must add custom functionality")
    def rlike() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.rlike("firstName", "/Bob*/")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def and() {
        given:
        new Person(firstName: "Bob", lastName: "Builder", age: 51).save(flush: true)
        Query.Criterion lastName = new Query.Equals("lastName", "Builder")
        Query.Criterion age = new Query.Equals("age", 50)
        hibernateQuery.and(lastName, age)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def or() {
        given:
        new Person(firstName: "Bob", lastName: "Builder", age: 51).save(flush: true)
        Query.Criterion lastNameWrong = new Query.Equals("lastName", "Rogers")
        Query.Criterion ageCorrect = new Query.Equals("age", 50)
        hibernateQuery.or(lastNameWrong, ageCorrect)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def not() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        Query.Criterion lastNameWrong = new Query.Equals("lastName", "Rogers")
        Query.Criterion ageIncorrect = new Query.Equals("age", 51)
        hibernateQuery.not(lastNameWrong, ageIncorrect)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def isEmpty() {
        given:
        hibernateQuery.isEmpty("pets")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def isNotEmpty() {
        Pet pet = new Pet(name: "Lucky")
        oldBob.addToPets(pet)
        oldBob.save(flush: true)
        given:
        hibernateQuery.isNotEmpty("pets")
                .join("pets")

        when:
        Person newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
        oldBob.pets == newBob.pets
    }

    def isNull() {
        oldBob.lastName = null
        oldBob.save(flush: true)
        given:
        hibernateQuery.isNull("lastName")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def isNotNull() {
        new Person(firstName: "Fred", age: 52).save(flush: true)
        given:
        hibernateQuery.isNotNull("lastName")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def allEq() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.allEq(["firstName": "Bob", "lastName": "Builder"])
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def inSubQuery() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        def oldPet = new Pet(name: "Lucky")
        oldBob.addToPets(oldPet)
        oldBob.save(flush: true)
        petHibernateQuery.inList("owner",
            new DetachedCriteria(Person).eq("lastName", "Builder")
        )
        when:
        def newPet = petHibernateQuery.singleResult()
        then:
        oldPet == newPet
    }

    def greaterThanAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.gtAll("age", new DetachedCriteria(Person).eq("age",48).property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def lessThanEqualsAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.leAll("age", new DetachedCriteria(Person).eq("age",50).property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def lessThanAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.ltAll("age", new DetachedCriteria(Person).eq("age",48).property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def greaterThanEqualsAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.geAll("age", new DetachedCriteria(Person).eq("age",50).property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }



    def inList() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.in("age", [50, 51])
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def between() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.between("age", 49, 51)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def joinWithProjection() {
        given:
        oldBob.addToPets(new Pet(name:"Lucky")).save(flush:true)
        hibernateQuery.join("pets").projections().property("pets.name").property("lastName")
        when:
        def answers = hibernateQuery.singleResult()
        then:
        answers[0] == "Lucky"
        answers[1] == "Builder"

    }

    def leftJoin() {
        given:
        hibernateQuery.join("pets", JoinType.LEFT)
        when:
        Person newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
        oldBob.pets == newBob.pets
    }

    def makeLazy() {
        given:
        def eagerOwner= new EagerOwner( pets :[new Pet(name:"Lucky")])
        hibernateQuery.join("pets", JoinType.LEFT)
        when:
        Person newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
        oldBob.pets == newBob.pets
    }

    def orderByAge() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.order(new Query.Order("age", Query.Order.Direction.DESC))
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
        oldBob == bobs[0]
    }

    def projectionProperty() {
        given:
        hibernateQuery.projections().property("lastName")
        when:
        def lastName = hibernateQuery.singleResult()
        then:
        lastName == "Builder"
    }

    def projectionId() {
        given:
        hibernateQuery.projections().id()
        when:
        def id = hibernateQuery.singleResult()
        then:
        id == oldBob.id
    }

    def count() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.projections().count()
        when:
        def count = hibernateQuery.singleResult()
        then:
        count == 2
    }

    def max() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.projections().max("age")
        when:
        def age = hibernateQuery.singleResult()
        then:
        age == 50
    }

    def min() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.projections().min("age")
        when:
        def age = hibernateQuery.singleResult()
        then:
        age == 50
    }

    def sum() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.projections().sum("age")
        when:
        def age = hibernateQuery.singleResult()
        then:
        age == 102
    }

    def avg() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.projections().avg("age")
        when:
        def age = hibernateQuery.singleResult()
        then:
        age == 51
    }

    def groupByLastNameAverageAge() {
        new Person(firstName: "Fred", lastName: "Builder", age: 52).save(flush: true)
        given:
        hibernateQuery.projections().groupProperty("lastName").avg("age")
        when:
        def result = hibernateQuery.singleResult()
        then:
        result[0] == "Builder"
        result[1] == 51
    }

}

@Entity
class EagerOwner implements Serializable {
    Set<Pet> pets = [] as Set
    static hasMany = [pets: Pet]
    static mapping = {
        pets lazy : false
    }
}
