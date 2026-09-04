dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly(project(":yeowool-economy"))
    compileOnly("net.citizensnpcs:citizensapi:${rootProject.property("citizensVersion")}")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:${rootProject.property("itemsAdderApiVersion")}")
}
