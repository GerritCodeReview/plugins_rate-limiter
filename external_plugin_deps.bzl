load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:2.23.4",
        sha1 = "a35b6f8ffcfa786771eac7d7d903429e790fdf3f",
        deps = [
            "@byte-buddy//jar",
            "@objenesis//jar",
        ],
    )

    maven_jar(
        name = "byte-buddy",
        artifact = "net.bytebuddy:byte-buddy:1.7.4",
        sha1 = "d0e77888485e1683057f8399f916eda6049c4acf",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:2.6",
        sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
    )
