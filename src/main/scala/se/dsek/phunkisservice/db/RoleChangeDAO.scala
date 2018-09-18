package se.dsek.phunkisservice.db

import java.io.Closeable
import java.util.Date
import javax.sql.DataSource

import io.getquill._
import se.dsek.phunkisservice.DBUtil
import se.dsek.phunkisservice.model.RoleInstance

class RoleChangeDAO[N](val ctx: MysqlJdbcContext[N]) extends DBUtil[N] {

  import ctx._

  def currentRoles(userId: String): List[Long] = ctx.run(
    roleInstanceSchema
      .filter(_.user == userId)
      .filter(_.endDate > new Date())
      .map(_.role)
  )
  def allRoles(userId: String): List[RoleInstance] = ctx.run(
    roleInstanceSchema.filter(_.user == userId)
  )
  def currentWorkers(roleId: Long): List[String] = ctx.run(
    roleInstanceSchema
      .filter(_.role == roleId)
      .filter(_.endDate > new Date())
      .map(_.user)
  )
  def allWorkers(roleId: Long): List[RoleInstance] = ctx.run(roleInstanceSchema.filter(_.role == roleId))

  def allInstances(): List[RoleInstance] = ctx.run(roleInstanceSchema)

  def allCurrentInstances(): List[RoleInstance] = ctx.run(
    roleInstanceSchema.filter(_.endDate > new Date())
  )
}

object RoleChangeDAO {
  def apply(dataSource: DataSource with Closeable): RoleChangeDAO[SnakeCase] = new RoleChangeDAO(
    new MysqlJdbcContext(SnakeCase, dataSource)
  )
}
