dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly("net.citizensnpcs:citizensapi:${rootProject.property("citizensVersion")}")
    compileOnly("me.clip:placeholderapi:${rootProject.property("placeholderApiVersion")}")
    // MythicMobs and ItemsAdder: no reliable Maven artifacts either — drop the matching jars from
    // the live servers' plugins/ folder into libs/ before building (same pattern as yeowool-life's
    // AddCook/MCPets jars).
    compileOnly(files("libs/MythicMobs.jar"))
    compileOnly(files("libs/ItemsAdder.jar"))
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation(project(":yeowool-core"))
}
