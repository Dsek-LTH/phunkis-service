# Prerequisites
 - You need [SBT](https://www.scala-sbt.org/) 1.0+ installed. (0.13.5 *might* also work)
 
# Setup
## With provided database
Has a faster code, compile, refresh cycle than the docker solution. Handy for quick iteration when developing.

### Prerequisites
 - A MySQL database connection

### Steps
 - Set environment variables JDBC\_URL, MYSQL\_USERNAME, MYSQL\_PASSWORD and HTTP\_PORT or use the defaults defined in [src/main/resources/application.conf](src/main/resources/application.conf)
 - Run `sbt init` to initialize database tables
 - Start the service locally with `sbt run`. You can now access the GraphQL playground at localhost:8080.
 
## In Docker containers
Docker compose offers a "one-click" install and run solution for the whole service and dependecies, including database. Very useful for running integration tests on a CI server and for making deployment easier.

### Prerequisites
 - You need [Docker](https://www.docker.com/get-started) and [Docker Compose](https://docs.docker.com/compose/) installed
 
### Steps
 - Run `sbt` in the root project directory
 - Run `dockerComposeUp` to spin up containers with the server and MySQL database running. You can now access the GraphQL playground at localhost:8080.
 - Run `dockerComposeStop` to stop the containers.
 - Run `reload` if you make any changes to the SBT config while SBT is running.
 - TODO: Tests that can run in the docker environment.

# Deployment
This will most likely be done by making SBT package a dockerfile to deploy on the server.
Update this section when we get there.
Set environment variables JDBC\_URL, MYSQL\_USERNAME, MYSQL\_PASSWORD and HTTP\_PORT

# Development
After you've started the service it will (for now) host a GraphQL playground at localhost:8080.

We use [Scapegoat](https://github.com/sksamuel/scapegoat) and [Scalastyle](http://www.scalastyle.org/) to help enforce high code quality. 
They can be run separately as `sbt scapegoat` and `sbt scalastyle`, or together as `sbt lint`.
Run them before each commit, and fix any issues. If you disagree with the warnings, either disable them locally,
or raise the discussion with DWWW of whether to remove that rule from the config.
To autoformat the code, simply run `sbt phunkis-service/scalafmt`.

When you run the linters they will output warnings in the console, however Scapegoat will also output
a handy HTML report in [target/scala-2.12/scapegoat-report/scapegoat.html](target/scala-2.12/scapegoat-report/scapegoat.html), if you find it easier to read that way.

TODO: Set up consistent configs between linters and autoformatter. Currently scalastyle wants to have braces on all
if expressions, while scalafmt removes them on one-liners.

## Major libraries
This service uses [Akka HTTP](https://doc.akka.io/docs/akka-http/current/introduction.html) for HTTP routing, although it could be relevant to look into switching to [Play](https://www.playframework.com/).
The endpoints serve JSON over GraphQL, with the help of [Sangria](https://sangria-graphql.org/). [Quill](https://github.com/getquill/quill) is used together with JDBC for database access. 
When you compile the project Quill will output a bunch of macro-generated SQL-statements at you.

## Docker
The configuration file that specifies how the docker containers are to be setup is [docker\_compose.yml](./docker_compose.yml).
The MySQL container is configured to glorp all SQL files in database\_init/ and run them in alphabetical order on startup. To make the run order easier to follow we start each file name with the order number (it is assumed we won't have more than 9 SQL scripts for this service). These files both setup the database tables with the expected schema and fill them with some initial data.

Accessing the ports of the docker containers from outside the container has proven troublesome on Windows machines, but should work without any extra setup on Linux.

To troubleshoot the containerized services (including the db), `docker logs <container_id>` is a very useful command.
Note that it takes a while (should be less than a minute) from starting the containers to the db accepting connections, which in turn means that the Scala service does not accept any connections during this time.
