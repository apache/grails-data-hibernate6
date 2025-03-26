package grails.gorm.specs.proxy

import grails.gorm.specs.entities.Club
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.specs.entities.Team
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import spock.lang.Ignore
import spock.lang.PendingFeatureIf

/**
 * Contains misc proxy tests using Hibenrate defaults, which is ByteBuddy.
 * These should all be passing for Gorm to be operating correctly with Groovy.
 */
class ByteBuddyProxySpec extends HibernateGormDatastoreSpec {
    static HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    //to show test that fail that should succeed set this to true. or uncomment the
    // testImplementation "org.yakworks:hibernate-groovy-proxy:$yakworksHibernateGroovyProxy" to see pass
    boolean runPending = ClassUtils.isPresent("yakworks.hibernate.proxy.ByteBuddyGroovyInterceptor")

    @Override
    List getDomainClasses() {
        [Team, Club]
    }

    Team createATeam(){
        Club c = new Club(name: "DOOM Club").save(failOnError:true)
        Team team = new Team(name: "The A-Team", club: c).save(failOnError:true, flush:true)
        return team
    }

    @Ignore("StaticTestUtil.team_id_asserts assert !Hibernate.isInitialized(team) fails")
    void "getId and id property checks dont initialize proxy if in a CompileStatic method"() {
        when:
        Team team = createATeam()
        session.clear()
        //TODO load is broken
//        team = Team.load(team.id)
        team = Team.list().find { it.id == team.id }

        then:"The asserts on getId and id should not initialize proxy when statically compiled"
        StaticTestUtil.team_id_asserts(team)
        !proxyHandler.isInitialized(team)

        StaticTestUtil.club_id_asserts(team)
        !proxyHandler.isInitialized(team.club)
    }

    @PendingFeatureIf({ !instance.runPending })
    void "getId and id dont initialize proxy"() {
        when:"load proxy"
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on getId and id should not initialize proxy"
        proxyHandler.isProxy(team)
        team.getId()
        !proxyHandler.isInitialized(team)

        team.id
        !proxyHandler.isInitialized(team)

        and: "the getAt check for id should not initialize"
        team['id']
        !proxyHandler.isInitialized(team)
    }

    @PendingFeatureIf({ !instance.runPending })
    void "truthy check on instance should not initialize proxy"() {
        when:"load proxy"
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on the intance should not init proxy"
        team
        !proxyHandler.isInitialized(team)

        and: "truthy check on association should not initialize"
        team.club
        !proxyHandler.isInitialized(team.club)
    }

    @PendingFeatureIf({ !instance.runPending })
    void "id checks on association should not initialize its proxy"() {
        when:"load instance"
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on the intance should not init proxy"
        !proxyHandler.isInitialized(team.club)

        team.club.getId()
        !proxyHandler.isInitialized(team.club)

        team.club.id
        !proxyHandler.isInitialized(team.club)

        team.clubId
        !proxyHandler.isInitialized(team.club)

        and: "the getAt check for id should not initialize"
        team.club['id']
        !proxyHandler.isInitialized(team.club)
    }

    void "isDirty should not intialize the association proxy"() {
        when:"load instance"
        Team team = createATeam()
        session.clear()
        team = Team.load(team.id)

        then:"The asserts on the intance should not init proxy"
        !proxyHandler.isInitialized(team)

        //isDirty will init the proxy. should make changes for this.
        !team.isDirty()
        proxyHandler.isInitialized(team)
        //it should not have initialized the association
        !proxyHandler.isInitialized(team.club)

        when: "its made dirty"
        team.name = "B-Team"

        then:
        team.isDirty()
        //still should not have initialized it.
        !proxyHandler.isInitialized(team.club)
    }

}
