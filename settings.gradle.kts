rootProject.name = "kjob"
include(
    "kjob-core",
    "kjob-kron",
    "kjob-mongo",
    "kjob-inmem",
    "kjob-example",
)

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
