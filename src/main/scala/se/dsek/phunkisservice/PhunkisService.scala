package se.dsek.phunkisservice

import java.io.{Closeable, PrintWriter}
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

import sangria.ast.Document
import sangria.execution.deferred.DeferredResolver
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.parser.{QueryParser, SyntaxError}
import sangria.parser.DeliveryScheme.Try
import sangria.marshalling.circe._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.parser._

import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import GraphQLRequestUnmarshaller._
import sangria.schema.Schema
import sangria.slowlog.SlowLog
import se.dsek.phunkisservice.db.{DBUtil, MysqlDAO, RoleDAO, RoleInstanceDAO}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.reflect.internal.util.NoPosition

object PhunkisService extends CorsSupport {
  import scala.concurrent.ExecutionContext.Implicits.global

  lazy private val logger = LoggerFactory.getLogger(PhunkisService.getClass)
  lazy val config = ConfigFactory.load()
  implicit val system: ActorSystem = ActorSystem("sangria-server")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {
    Class.forName("com.mysql.jdbc.Driver").getClass
    val db: DataSource with Closeable = DBUtil.makeDataSource(
      config.getString("mysql.url"),
      config.getString("mysql.user"),
      config.getString("mysql.password")
    )

    val route: Route =
      optionalHeaderValueByName("X-Apollo-Tracing") { tracing ⇒
        path("graphql") {
          get {
            explicitlyAccepts(`text/html`) {
              getFromResource("assets/playground.html")
            } ~
              unmarshallAndExecuteQuery(tracing,
                                        GQLSchema.roleInstanceSchema,
                                        RoleInstanceDAO(db))
          } ~ post {
            unmarshallAndExecuteQuery(tracing,
                                      GQLSchema.roleInstanceSchema,
                                      RoleInstanceDAO(db))
          }
        } ~ post {
          path("roles") {
            unmarshallAndExecuteQuery(tracing,
                                      GQLSchema.roleSchema,
                                      RoleDAO(db))
          } ~
            path("roleInstances") {
              unmarshallAndExecuteQuery(tracing,
                                        GQLSchema.roleInstanceSchema,
                                        RoleInstanceDAO(db))
            }
        }
      } ~
        (get & pathEndOrSingleSlash) {
          redirect("/graphql", PermanentRedirect)
        }

    val port = config.getInt("http.port")
    logger.info(s"Attempting to bind port $port")
    Http().bindAndHandle(corsHandler(route),
                         "0.0.0.0",
                          port)
      .onComplete({case Success(serverBinding) => logger.info("Successfully bound, serving content...")
      case e: Failure[_] => logger.trace("Failure when binding!", e.exception)})
  }

  def executeGraphQL[T, U](
      query: Document,
      operationName: Option[String],
      variables: Json,
      tracing: Boolean)(implicit schema: Schema[T, Unit], dao: T): Route =
    complete(
      Executor
        .execute(
          schema,
          query,
          dao,
          variables = if (variables.isNull) Json.obj() else variables,
          operationName = operationName,
          middleware =
            if (tracing) SlowLog.apolloTracing :: Nil
            else Nil
        )
        //      deferredResolver = DeferredResolver.fetchers(SchemaDefinition.characters))
        .map(OK → _)
        .recover {
          case error: QueryAnalysisError ⇒ BadRequest → error.resolveError
          case error: ErrorWithResolver ⇒
            InternalServerError → error.resolveError
        })

  def unmarshallAndExecuteQuery[T, U](implicit tracing: Option[String],
                                      schema: Schema[T, Unit],
                                      dao: T): Route =
    parameters('query.?, 'operationName.?, 'variables.?) {
      (queryParam, operationNameParam, variablesParam) ⇒
        entity(as[Json]) { body ⇒
          val query = queryParam orElse root.query.string.getOption(body)
          val operationName = operationNameParam orElse root.operationName.string
            .getOption(body)
          val variablesStr = variablesParam orElse root.variables.string
            .getOption(body)

          query.map(QueryParser.parse(_)) match {
            case Some(Success(ast)) ⇒
              variablesStr.map(parse) match {
                case Some(Left(error)) ⇒
                  complete(BadRequest, formatError(error))
                case Some(Right(json)) ⇒
                  executeGraphQL(ast, operationName, json, tracing.isDefined)
                case None ⇒
                  executeGraphQL(
                    ast,
                    operationName,
                    root.variables.json.getOption(body) getOrElse Json.obj(),
                    tracing.isDefined)
              }
            case Some(Failure(error)) ⇒ complete(BadRequest, formatError(error))
            case None ⇒ complete(BadRequest, formatError("No query to execute"))
          }
        } ~
          entity(as[Document]) { document ⇒
            variablesParam.map(parse) match {
              case Some(Left(error)) ⇒ complete(BadRequest, formatError(error))
              case Some(Right(json)) ⇒
                executeGraphQL(document,
                               operationNameParam,
                               json,
                               tracing.isDefined)
              case None ⇒
                executeGraphQL(document,
                               operationNameParam,
                               Json.obj(),
                               tracing.isDefined)
            }
          }
    }

  def formatError(error: Throwable): Json = error match {
    case syntaxError: SyntaxError ⇒
      Json.obj(
        "errors" → Json.arr(Json.obj(
          "message" → Json.fromString(syntaxError.getMessage),
          "locations" → Json.arr(Json.obj(
            "line" → Json.fromBigInt(syntaxError.originalError.position.line),
            "column" → Json.fromBigInt(
              syntaxError.originalError.position.column)))
        )))
    case NonFatal(e) ⇒
      formatError(e.getMessage)
    case e ⇒
      throw e
  }

  def formatError(message: String): Json =
    Json.obj(
      "errors" → Json.arr(Json.obj("message" → Json.fromString(message))))

}
