rootProject.name = "kjob"
include(
    "kjob-core",
    "kjob-api",
    "kjob-dashboard",
    "kjob-kron",
    "kjob-mongo",
    "kjob-inmem",
    "kjob-example",
    "kjob-jdbi",
)

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
