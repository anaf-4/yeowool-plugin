dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly("me.clip:placeholderapi:${rootProject.property("placeholderApiVersion")}")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:${rootProject.property("itemsAdderApiVersion")}")
    compileOnly("net.citizensnpcs:citizensapi:${rootProject.property("citizensVersion")}")
    // isTransitive = false: citizens-main's POM pulls in libraries that aren't resolvable from
    // our repos (e.g. net.byteflux:libby-bukkit) - harmless since this is compileOnly (for
    // SkinTrait) and Citizens itself is what actually runs at server startup, not this jar.
    compileOnly("net.citizensnpcs:citizens-main:${rootProject.property("citizensVersion")}") {
        isTransitive = false
    }
    // No public Maven artifact for NuVotifier — copy the lobby's plugins/nuvotifier.jar into libs/
    // locally before building (see .gitignore).
    compileOnly(files("libs/nuvotifier.jar"))
}
