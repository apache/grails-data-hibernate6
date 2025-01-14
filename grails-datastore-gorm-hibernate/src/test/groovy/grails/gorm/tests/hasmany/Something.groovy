package grails.gorm.tests.hasmany

class Something {

    public static void main(String[] args) {
        Book book = new Book(title:"Name")
        book.class.declaredFields.each{ field ->
            def find = book.properties.find { property ->
                property.key == field.getName()
            }
            println find
        }
    }
}
