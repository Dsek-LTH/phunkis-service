package se.dsek.phunkisservice.db

import java.io.{Closeable, PrintWriter}
import java.sql.{
  Blob,
  CallableStatement,
  Clob,
  Connection,
  DatabaseMetaData,
  NClob,
  PreparedStatement,
  SQLWarning,
  SQLXML,
  Savepoint,
  Statement,
  Struct
}
import java.time.Instant
import java.{sql, util}
import java.util.{Date, Properties}
import java.util.concurrent.Executor
import java.util.logging.Logger
import javax.sql.DataSource

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource
import com.typesafe.config.ConfigFactory
import io.getquill._
import sangria.ast
import sangria.schema._
import sangria.validation.{ValueCoercionViolation, Violation}
import se.dsek.phunkisservice.model.{Role, RoleInstance}

import scala.collection.mutable
import better.files.using
import org.slf4j.LoggerFactory
import sangria.marshalling.DateSupport

import scala.util.Try

trait DBUtil[N <: NamingStrategy] {
  protected val ctx: MysqlJdbcContext[N]
  import ctx._
  import DBUtil._

  implicit class DateQuotes(left: Date) {
    // scalastyle:off
    def >(right: Date) = quote(infix"$left > $right".as[Boolean])

    def <(right: Date) = quote(infix"$left < $right".as[Boolean])

    // scalastyle:on
  }

  val roleSchema = quote(querySchema[Role]("roles"))
  val roleInstanceSchema = quote(querySchema[RoleInstance]("role_instances"))
}

object DBUtil {
  private val logger = LoggerFactory.getLogger(DBUtil.getClass)

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    Class.forName("com.mysql.jdbc.Driver").getClass
    val db: DataSource with Closeable = DBUtil.makeDataSource(
      config.getString("mysql.url"),
      config.getString("mysql.user"),
      config.getString("mysql.password")
    )
    using(db.getConnection) { conn =>
      logger.info("Creating role table...")
      val pstmt = conn.prepareStatement(RoleDAO.createTable)
      pstmt.execute()
      logger.info("Done!")
      logger.info("Creating role instance table...")
      val pstmt2 = conn.prepareStatement(RoleInstanceDAO.createTable)
      pstmt2.execute()
      logger.info("Done!")
    }
  }

  implicit val encodeDate: MappedEncoding[Date, Long] =
    MappedEncoding[Date, Long](_.toInstant.getEpochSecond)
  implicit val decodeDate: MappedEncoding[Long, Date] =
    MappedEncoding[Long, Date](l => Date.from(Instant.ofEpochSecond(l)))

  private lazy val yyyymmddFormatter =
    new java.text.SimpleDateFormat("yyyy-MM-dd")
  private def coerceDateString(s: String): Either[Violation, Date] =
    Try(yyyymmddFormatter.parse(s)).toEither.left.map(_ =>
      DateCoercionViolation)

  case object DateCoercionViolation
      extends ValueCoercionViolation(
        "Date string did not match format yyyy-MM-dd")
  implicit val DateType: ScalarType[Date] = ScalarType[Date](
    "Date",
    coerceOutput = (d, caps) ⇒
      if (caps.contains(DateSupport)) d
      else yyyymmddFormatter.format(d),
    coerceUserInput = {
      case s: String ⇒ coerceDateString(s)
      case _ ⇒ Left(DateCoercionViolation)
    },
    coerceInput = {
      case ast.StringValue(s, _, _, _, _) ⇒ coerceDateString(s)
      case _ ⇒ Left(DateCoercionViolation)
    }
  )

  def makeDataSource(url: String,
                     user: String,
                     password: String): DataSource with Closeable = {
    val ds = new MysqlDataSource()
    ds.setURL(url)
    ds.setUser(user)
    ds.setPassword(password)
    val conn = ds.getConnection
    logger.info("testing connection...")
    logger.debug(s"connection: $conn")
    logger.debug(s"close: ${conn.close()}")
    logger.info("connection opened and closed without errors")
    new CloseableMySQLDataSource(ds)
  }

  class CloseableMySQLDataSource(ds: DataSource)
      extends DataSource
      with Closeable {
    private val conns = mutable.Set.empty[Connection]

    var open: Boolean = true

    override def getConnection: Connection = {
      if (!open) {
        throw new IllegalStateException(
          "Tried getting new connection from closed data source")
      }
      val conn = new CentrallyCloseableConnection(ds.getConnection, this)
      conns += conn
      conn
    }

    override def getConnection(username: String,
                               password: String): Connection = {
      if (!open) {
        throw new IllegalStateException(
          "Tried getting new connection from closed data source")
      }
      val conn = new CentrallyCloseableConnection(
        ds.getConnection(username, password),
        this)
      conns += conn
      conn
    }

    override def unwrap[T](iface: Class[T]): T = ds.unwrap(iface)

    override def isWrapperFor(iface: Class[_]): Boolean = ds.isWrapperFor(iface)

    override def setLoginTimeout(seconds: Int): Unit =
      ds.setLoginTimeout(seconds)

    override def setLogWriter(out: PrintWriter): Unit = ds.setLogWriter(out)

    override def getParentLogger: Logger = ds.getParentLogger

    override def getLoginTimeout: Int = ds.getLoginTimeout

    override def getLogWriter: PrintWriter = ds.getLogWriter

    override def close(): Unit = {
      open = false
      for (conn <- conns) conn.close()
    }

    def removeConnection(conn: Connection): Unit = conns -= conn
  }

  // scalastyle:off
  class CentrallyCloseableConnection(conn: Connection,
                                     central: CloseableMySQLDataSource)
      extends Connection {
    override def commit(): Unit = conn.commit()

    override def getHoldability: Int = conn.getHoldability

    override def setCatalog(catalog: String): Unit = conn.setCatalog(catalog)

    override def setHoldability(holdability: Int): Unit =
      conn.setHoldability(holdability)

    override def prepareStatement(sql: String): PreparedStatement =
      conn.prepareStatement(sql)

    override def prepareStatement(
        sql: String,
        resultSetType: Int,
        resultSetConcurrency: Int): PreparedStatement =
      conn.prepareStatement(sql, resultSetType, resultSetConcurrency)

    override def prepareStatement(
        sql: String,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int): PreparedStatement =
      conn.prepareStatement(sql,
                            resultSetType,
                            resultSetConcurrency,
                            resultSetHoldability)

    override def prepareStatement(sql: String,
                                  autoGeneratedKeys: Int): PreparedStatement =
      conn.prepareStatement(sql, autoGeneratedKeys)

    override def prepareStatement(
        sql: String,
        columnIndexes: Array[Int]): PreparedStatement =
      conn.prepareStatement(sql, columnIndexes)

    override def prepareStatement(
        sql: String,
        columnNames: Array[String]): PreparedStatement =
      conn.prepareStatement(sql, columnNames)

    override def createClob(): Clob = conn.createClob()

    override def setSchema(schema: String): Unit = conn.setSchema(schema)

    override def setClientInfo(name: String, value: String): Unit =
      conn.setClientInfo(name, value)

    override def setClientInfo(properties: Properties): Unit =
      conn.setClientInfo(properties)

    override def createSQLXML(): SQLXML = conn.createSQLXML()

    override def getCatalog: String = conn.getCatalog

    override def createBlob(): Blob = conn.createBlob()

    override def createStatement(): Statement = conn.createStatement()

    override def createStatement(resultSetType: Int,
                                 resultSetConcurrency: Int): Statement =
      conn.createStatement(resultSetType, resultSetConcurrency)

    override def createStatement(resultSetType: Int,
                                 resultSetConcurrency: Int,
                                 resultSetHoldability: Int): Statement =
      conn.createStatement(resultSetType,
                           resultSetConcurrency,
                           resultSetHoldability)

    override def abort(executor: Executor): Unit = conn.abort(executor)

    override def setAutoCommit(autoCommit: Boolean): Unit =
      conn.setAutoCommit(autoCommit)

    override def getMetaData: DatabaseMetaData = conn.getMetaData

    override def setReadOnly(readOnly: Boolean): Unit =
      conn.setReadOnly(readOnly)

    override def prepareCall(sql: String): CallableStatement =
      conn.prepareCall(sql)

    override def prepareCall(sql: String,
                             resultSetType: Int,
                             resultSetConcurrency: Int): CallableStatement =
      conn.prepareCall(sql, resultSetType, resultSetConcurrency)

    override def prepareCall(sql: String,
                             resultSetType: Int,
                             resultSetConcurrency: Int,
                             resultSetHoldability: Int): CallableStatement =
      conn.prepareCall(sql,
                       resultSetType,
                       resultSetConcurrency,
                       resultSetHoldability)

    override def setTransactionIsolation(level: Int): Unit =
      conn.setTransactionIsolation(level)

    override def getWarnings: SQLWarning = conn.getWarnings

    override def releaseSavepoint(savepoint: Savepoint): Unit =
      conn.releaseSavepoint(savepoint)

    override def nativeSQL(sql: String): String = conn.nativeSQL(sql)

    override def isReadOnly: Boolean = conn.isReadOnly

    override def createArrayOf(typeName: String,
                               elements: Array[AnyRef]): sql.Array =
      conn.createArrayOf(typeName, elements)

    override def setSavepoint(): Savepoint = conn.setSavepoint()

    override def setSavepoint(name: String): Savepoint = conn.setSavepoint(name)

    override def close(): Unit = {
      central.removeConnection(this)
      conn.close()
    }

    override def createNClob(): NClob = conn.createNClob()

    override def rollback(): Unit = conn.rollback()

    override def rollback(savepoint: Savepoint): Unit = conn.rollback(savepoint)

    override def setNetworkTimeout(executor: Executor,
                                   milliseconds: Int): Unit =
      conn.setNetworkTimeout(executor, milliseconds)

    override def setTypeMap(map: util.Map[String, Class[_]]): Unit =
      conn.setTypeMap(map)

    override def isValid(timeout: Int): Boolean = conn.isValid(timeout)

    override def getAutoCommit: Boolean = conn.getAutoCommit

    override def clearWarnings(): Unit = conn.clearWarnings()

    override def getSchema: String = conn.getSchema

    override def getNetworkTimeout: Int = conn.getNetworkTimeout

    override def isClosed: Boolean = conn.isClosed

    override def getTransactionIsolation: Int = conn.getTransactionIsolation

    override def createStruct(typeName: String,
                              attributes: Array[AnyRef]): Struct =
      conn.createStruct(typeName, attributes)

    override def getClientInfo(name: String): String = conn.getClientInfo(name)

    override def getClientInfo: Properties = conn.getClientInfo

    override def getTypeMap: util.Map[String, Class[_]] = conn.getTypeMap

    override def unwrap[T](iface: Class[T]): T = conn.unwrap(iface)

    override def isWrapperFor(iface: Class[_]): Boolean =
      conn.isWrapperFor(iface)
  }

  // scalastyle:on
}
