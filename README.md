# Prerequisites
 - You need a MySQL database you can connect to.
 - You need [SBT](https://www.scala-sbt.org/) 1.0+ installed. (0.13.5 *might* also work)
 
# Setup
 - Set environment variables JDBC_URL, MYSQL_USERNAME, MYSQL_PASSWORD and HTTP_PORT or use the defaults defined in [src/main/resources/application.conf](src/main/resources/application.conf)
 - Run `sbt init` to initialize database tables
 - You can now start the service locally with `sbt run`
 
# Deployment
This will most likely be done by making SBT package a fat JAR to run on the server.
Update this section when we get there.

# Development
After you've started the service it will (for now) host a GraphQL playground at localhost:8080.

We use [Scapegoat](https://github.com/sksamuel/scapegoat) and [Scalastyle](http://www.scalastyle.org/) to help enforce high code quality. 
They can be run separately as `sbt scapegoat` and `sbt scalastyle`, or together as `sbt lint`.
Run them before each commit, and fix any issues. If you disagree with the warnings, either disable them locally,
or raise the discussion with DWWW of whether to remove that rule from the config.
To autoformat the code, simply run `sbt phunkis-service/scalafmt`.

When you run the linters they will output warnings in the console, however Scapegoat will also output
a handy HTML report in [target/scala-2.12/scapegoat-report/scapegoat.html](target/scala-2.12/scapegoat-report/scapegoat.html), if you find it easier to read that way.

## Major libraries
This service uses [Akka HTTP](https://doc.akka.io/docs/akka-http/current/introduction.html) for HTTP routing, although it could be relevant to look into switching to [Play](https://www.playframework.com/).
The endpoints serve JSON over GraphQL, with the help of [Sangria](https://sangria-graphql.org/). [Quill](https://github.com/getquill/quill) is used together with JDBC for database access. 
When you compile the project Quill will output a bunch of macro-generated SQL-statements at you.