package se.dsek.phunkisservice.db

import java.io.Closeable
import java.util.Date
import javax.sql.DataSource

import io.getquill._
import se.dsek.phunkisservice.DBUtil
import se.dsek.phunkisservice.model.RoleInstance

import scala.util.Try

class RoleInstanceDAO[N](val ctx: MysqlJdbcContext[N]) extends DBUtil[N] {

  import ctx._

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
  def allWorkers(roleId: Long): List[RoleInstance] = ctx.run(roleInstanceSchema.filter(_.role == roleId))

  def allInstances(): List[RoleInstance] = ctx.run(roleInstanceSchema)

  def allCurrentInstances(): List[RoleInstance] = ctx.run(
    roleInstanceSchema.filter(_.endDate > lift(new Date()))
  )

  def insertInstance(roleInstance: RoleInstance): Try[RoleInstance] = Try(ctx.run(
    roleInstanceSchema.insert(lift(roleInstance)).returning(ri => ri)
  ))

  def relieveWorker(roleInstance: RoleInstance, date: Date): Boolean = Try(ctx.run(
    roleInstanceSchema.filter(ri => ri.role == lift(roleInstance.role) &&
      ri.user == lift(roleInstance.user) &&
      ri.startDate == lift(roleInstance.startDate) &&
      ri.endDate == lift(roleInstance.endDate)
    ).update(_.endDate -> lift(date))
  )).isSuccess
}

object RoleInstanceDAO {
  def apply(dataSource: DataSource with Closeable): RoleInstanceDAO[SnakeCase] = new RoleInstanceDAO(
    new MysqlJdbcContext(SnakeCase, dataSource)
  )
}
