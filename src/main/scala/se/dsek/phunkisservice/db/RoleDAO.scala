package se.dsek.phunkisservice.db

import java.io.Closeable
import javax.sql.DataSource

import io.getquill.{MysqlJdbcContext, SnakeCase}
import se.dsek.phunkisservice.DBUtil
import se.dsek.phunkisservice.model.Role

import scala.util.Try

class RoleDAO[N](val ctx: MysqlJdbcContext[N]) extends DBUtil[N] {

  import ctx._

  lazy private val roles = roleSchema
  lazy private val activeRoles = roles.filter(_.isCurrent)

  def allRoles(mastery: Option[String]): List[Role] = ctx.run(
    filterMaybeMastery(mastery, roles)
  )

  def activeRoles(mastery: Option[String]): List[Role] = ctx.run(filterMaybeMastery(mastery, activeRoles))

  def addRole(name: String, isCurrent: Boolean, mastery: String, term: String, description: String,
              maxPeople: Option[Int]): Option[Long] = Try(ctx.run(
    roles.insert(lift(Role(0L, name, isCurrent, mastery, term, description, maxPeople)))
      .returning(_.uid)
  )).toOption

  @inline
  private def filterMaybeMastery(mastery: Option[String], query: Quoted[Query[Role]]) = {
    mastery.fold(query)(m => query.filter(_.mastery == lift(m)))
  }
}

object RoleDAO {
  def apply(dataSource: DataSource with Closeable): RoleDAO[SnakeCase] = new RoleDAO(
    new MysqlJdbcContext(SnakeCase, dataSource)
  )
}