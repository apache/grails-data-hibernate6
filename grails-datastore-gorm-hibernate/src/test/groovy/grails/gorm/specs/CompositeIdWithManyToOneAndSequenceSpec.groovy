package grails.gorm.specs

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.jetbrains.annotations.NotNull
import spock.lang.Issue

/**
 * Created by graemerocher on 26/01/2017.
 */
class CompositeIdWithManyToOneAndSequenceSpec extends HibernateGormDatastoreSpec {

    List getDomainClasses() {
        [Tooth, ToothDisease]
    }



    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/835')
    void "Test composite id many to one and sequence"() {

        when:"a many to one association is created"
        ToothDisease td = new ToothDisease(nrVersion: 1).save()

        def tooth = new Tooth()
        tooth.toothDisease << td
        tooth.save(flush:true)

        then:"The object was saved"
        Tooth.count() == 1
        Tooth.list().first().toothDisease != null
    }

}


@Entity
class Tooth {
    Integer id
    TreeSet<ToothDisease> toothDisease = new TreeSet<>()
    static mapping = {
        table name: 'AK_TOOTH'
        id generator: 'sequence', params: [sequence: 'SEQ_AK_TOOTH']
        toothDisease {
            column name: 'FK_AK_TOOTH_ID'
            column name: 'FK_AK_TOOTH_NR_VERSION'
        }
    }
}

@Entity
class ToothDisease implements Serializable,Comparable<ToothDisease> {
    Integer idColumn
    Integer nrVersion
    static mapping = {
        table name: 'AK_TOOTH_DISEASE'
        idColumn column: 'ID', generator: 'sequence', params: [sequence: 'SEQ_AK_TOOTH_DISEASE']
        nrVersion column: 'NR_VERSION'
        id composite: ['idColumn', 'nrVersion']
    }

    @Override
    int compareTo(@NotNull ToothDisease o) {
        return idColumn <=> ((ToothDisease)o).idColumn
    }
}