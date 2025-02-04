#
# GRAKN.AI - THE KNOWLEDGE GRAPH
# Copyright (C) 2019 Grakn Labs Ltd
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

load("@graknlabs_build_tools//checkstyle:rules.bzl", "checkstyle_test")

java_test(
    name = "session-it",
    test_class = "grakn.core.server.session.SessionIT",
    srcs = ["SessionIT.java"],
    deps = [
        "//concept:concept",
        "//server:server",
        "//test-integration/rule:grakn-test-server"
    ],
    classpath_resources = ["//test-integration/resources:logback-test"]
)

java_test(
    name = "transaction-oltp-it",
    size = "large",
    srcs = ["TransactionOLTPIT.java"],
    classpath_resources = ["//test-integration/resources:logback-test"],
    test_class = "grakn.core.server.session.TransactionOLTPIT",
    deps = [
        "//common",
        "//concept",
        "//dependencies/maven/artifacts/com/google/guava:guava",
        "//dependencies/maven/artifacts/org/apache/tinkerpop:gremlin-core",
        "//dependencies/maven/artifacts/org/hamcrest:hamcrest-library",
        "//dependencies/maven/artifacts/org/janusgraph:janusgraph-core",
        "//server",
        "//test-integration/rule:grakn-test-server",
        "//test-integration/util:graql-test-util",
        "@graknlabs_graql//java:graql",
    ],
)


java_test(
    name = "keyspace-schema-cache-it",
    test_class = "grakn.core.server.session.cache.KeyspaceSchemaCacheIT",
    srcs = ["cache/KeyspaceSchemaCacheIT.java"],
    deps = ["//server", "//test-integration/rule:grakn-test-server", "@graknlabs_client_java//:client-java", "//concept", "@graknlabs_graql//java:graql"],
    classpath_resources = ["//test-integration/resources:logback-test"]
)

java_test(
    name = "transaction-cache-it",
    test_class = "grakn.core.server.session.cache.TransactionCacheIT",
    srcs = ["cache/TransactionCacheIT.java"],
    deps = [
        "//server",
        "//test-integration/rule:grakn-test-server",
        "//concept",
        "//dependencies/maven/artifacts/org/hamcrest:hamcrest-library",
        "@graknlabs_graql//java:graql",
        "//dependencies/maven/artifacts/org/apache/tinkerpop:gremlin-core",
    ],
    classpath_resources = ["//test-integration/resources:logback-test"],
    data = ["//test-integration/resources:grakn-small-tx-cache"]
)

checkstyle_test(
    name = "checkstyle",
    targets = [
        ":session-it",
        ":transaction-oltp-it",
        ":keyspace-schema-cache-it",
        ":transaction-cache-it"
    ],
)
