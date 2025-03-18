package grails.gorm.specs

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
