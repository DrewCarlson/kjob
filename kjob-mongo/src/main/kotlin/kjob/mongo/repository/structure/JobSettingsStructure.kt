package kjob.mongo.repository.structure

internal enum class JobSettingsStructure(val key: String) {
    ID("_id"),
    NAME("name"),
    PROPERTIES("properties")
}