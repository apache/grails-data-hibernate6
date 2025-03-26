package grails.gorm.specs.entities

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity

@Entity
class Club implements HibernateEntity<Club> {
    String name

    @Override
    String toString() {
        name
    }
}
