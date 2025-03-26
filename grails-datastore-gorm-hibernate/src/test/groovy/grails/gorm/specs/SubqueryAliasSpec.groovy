package grails.gorm.specs

import grails.gorm.specs.entities.Club
import grails.gorm.specs.entities.Team
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

/**
 * Created by graemerocher on 01/03/2017.
 */
@ApplyDetachedCriteriaTransform
class SubqueryAliasSpec extends HibernateGormDatastoreSpec {

    List getDomainClasses() {
        [Club, Team]
    }


    void "Test subquery with root alias"() {
        given:
        Club c = new Club(name: "Manchester United").save()
        new Team(name: "First Team", club: c).save(flush:true)

        when:
        Team t = Team.where {
            def t = Team
            name == "First Team"
            exists(
                    Club.where {
                        id == t.club
                    }.property('name')
            )
        }.find()

        then:
        t != null
    }
}
