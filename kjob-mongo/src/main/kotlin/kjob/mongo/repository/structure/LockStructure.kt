package kjob.mongo.repository.structure

internal enum class LockStructure(val key: String) {
    ID("_id"),
    UPDATED_AT("updated_at")
}