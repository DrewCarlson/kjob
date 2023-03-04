rootProject.name = "kjob"
include(
    "kjob-core",
    "kjob-api",
    "kjob-kron",
    "kjob-mongo",
    "kjob-inmem",
    "kjob-example",
    "kjob-jdbi",
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
