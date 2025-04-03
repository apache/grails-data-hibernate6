package grails.gorm.specs

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType

import javax.sql.DataSource
import java.sql.ResultSet

/**
 * Created by graemerocher on 16/11/16.
 */
class IdentityEnumTypeSpec extends HibernateGormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [EnumEntityDomain, FooWithEnum]
    }


    @Rollback
    void "test identity enum type"() {
        when:
        new EnumEntityDomain(status: EnumEntityDomain.Status.FOO).save(flush:true)
        DataSource ds = setupClass.hibernateDatastore.connectionSources.defaultConnectionSource.dataSource
        ResultSet resultSet = ds.getConnection().prepareStatement('select status from enum_entity_domain').executeQuery()

        then:
        resultSet.next()
        resultSet.getString(1) == 'FOO'
        EnumEntityDomain.first().status == EnumEntityDomain.Status.FOO
    }

    @Rollback
    void "test identity enum type 2"() {
        when:
        new FooWithEnum(name: "blah", mySuperValue: XEnum.X__TWO).save(flush:true)
        DataSource ds = setupClass.hibernateDatastore.connectionSources.defaultConnectionSource.dataSource
        ResultSet resultSet = ds.getConnection().prepareStatement('select my_super_value from foo_with_enum').executeQuery()

        then:
        resultSet.next()
        resultSet.getString(1) == "X__TWO"
        FooWithEnum.first().mySuperValue == XEnum.X__TWO
    }
}

@Entity
class EnumEntityDomain {
    @Enumerated(EnumType.STRING)
    Status status

    static mapping = {
        status(enumType: "string")
    }

    enum Status {
        FOO("F"), BAR("B")
        String id
        Status(String id) { this.id = id }
    }
}

@Entity
class FooWithEnum {
    long id
    String name
    @Enumerated(EnumType.STRING)
    XEnum mySuperValue

    static mapping = {
        version false
        mySuperValue enumType:"string"
    }
}

enum XEnum {
    X__ONE (000, "x.one"),
    X__TWO (100, "x.two"),
    X__THREE (200, "x.three")

    final int id
    final String name

    private XEnum(int id, String name) {
        this.id = id
        this.name = name
    }

    String toString() {
        name
    }
}
