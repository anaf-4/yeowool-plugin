dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly("com.github.LoneDev6:API-ItemsAdder:${rootProject.property("itemsAdderApiVersion")}")
    compileOnly("net.momirealms:custom-fishing:2.3.24")
    compileOnly("net.momirealms:custom-crops:3.6.52")
    // No public Maven artifact for AddCook (paid plugin) — drop the matching jar from
    // plugins/AddCook-*.jar into libs/ locally before building (see .gitignore).
    compileOnly(files("libs/AddCook-3.8.2.jar"))
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation(project(":yeowool-core"))
}
