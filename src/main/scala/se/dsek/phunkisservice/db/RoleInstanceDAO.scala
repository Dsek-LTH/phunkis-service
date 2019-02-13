package se.dsek.phunkisservice.db

import java.io.Closeable
import java.util.Date
import javax.sql.DataSource

import io.getquill._
import se.dsek.phunkisservice.model.RoleInstance

import scala.util.Try

trait RoleInstanceDAO {
  def currentRoles(userId: String): List[Long]
  def allRoles(userId: String): List[RoleInstance]
  def currentWorkers(roleId: Long): List[String]
  def allWorkers(roleId: Long): List[RoleInstance]
  def allInstances(): List[RoleInstance]
  def allCurrentInstances(): List[RoleInstance]
  def insertInstance(roleInstance: RoleInstance): Try[RoleInstance]
  def relieveWorker(roleInstance: RoleInstance, date: Date): Boolean
}

class RoleInstanceDAOImpl(val ctx: MysqlJdbcContext[SnakeCase])
    extends RoleInstanceDAO
    with MysqlDAO[SnakeCase] {

  import ctx._
  //import DBUtil._

  def currentRoles(userId: String): List[Long] = ctx.run(
    roleInstanceSchema
      .filter(_.user == lift(userId))
      .filter(_.endDate > lift(new Date()))
      .map(_.role)
  )
  def allRoles(userId: String): List[RoleInstance] = ctx.run(
    roleInstanceSchema.filter(_.user == lift(userId))
  )
  def currentWorkers(roleId: Long): List[String] = ctx.run(
    roleInstanceSchema
      .filter(_.role == lift(roleId))
      .filter(_.endDate > lift(new Date()))
      .map(_.user)
  )
  def allWorkers(roleId: Long): List[RoleInstance] = ctx.run(
    roleInstanceSchema.filter(_.role == lift(roleId))
  )

  def allInstances(): List[RoleInstance] = ctx.run(roleInstanceSchema)

  def allCurrentInstances(): List[RoleInstance] = ctx.run(
    roleInstanceSchema.filter(_.endDate > lift(new Date()))
  )

  def insertInstance(roleInstance: RoleInstance): Try[RoleInstance] =
    Try(
      ctx.run(
        roleInstanceSchema.insert(lift(roleInstance))
      ))
      .map(_ => roleInstance) // shouldn't have to do this... dirty fix for now

  def relieveWorker(roleInstance: RoleInstance, date: Date): Boolean =
    Try(
      ctx.run(
        roleInstanceSchema
          .filter(
            ri =>
              ri.role == lift(roleInstance.role) &&
                ri.user == lift(roleInstance.user) &&
                ri.startDate == lift(roleInstance.startDate) &&
                ri.endDate == lift(roleInstance.endDate))
          .update(_.endDate -> lift(date))
      )).isSuccess
}

object RoleInstanceDAO {
  def apply(dataSource: DataSource with Closeable): RoleInstanceDAO =
    new RoleInstanceDAOImpl(
      new MysqlJdbcContext(SnakeCase, dataSource)
    )

  private[db] lazy val createTable =
    """CREATE TABLE role_instances (
      |role BIGINT unsigned NOT NULL,
      |user VARCHAR(20) NOT NULL,
      |start_date TIMESTAMP NOT NULL,
      |end_date TIMESTAMP NOT NULL
      |);
    """.stripMargin
}
