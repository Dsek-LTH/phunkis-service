package se.dsek.phunkisservice.db

import java.io.Closeable
import javax.sql.DataSource

import io.getquill.{MysqlJdbcContext, NamingStrategy, SnakeCase}
import se.dsek.phunkisservice.model.Role

import scala.util.Try

trait RoleDAO {
  def allRoles(mastery: Option[String]): List[Role]

  def activeRoles(mastery: Option[String]): List[Role]

  def addRole(name: String,
              isCurrent: Boolean,
              mastery: String,
              term: String,
              description: String,
              maxPeople: Option[Int]): Option[Long]

  def getRole(id: Long): Option[Role]
}

private class RoleDAOImpl(val ctx: MysqlJdbcContext[SnakeCase])
    extends MysqlDAO[SnakeCase]
    with RoleDAO {

  import ctx._

  lazy private val roles = roleSchema
  lazy private val activeRoles = quote(roles.filter(_.isCurrent))

  override def activeRoles(mastery: Option[String]): List[Role] =
    mastery.fold(ctx.run(activeRoles))(m =>
      ctx.run(activeRoles.filter(_.mastery == lift(m))))

  override def allRoles(mastery: Option[String]): List[Role] =
    mastery.fold(ctx.run(roles))(m =>
      ctx.run(roles.filter(_.mastery == lift(m))))

  override def addRole(name: String,
                       isCurrent: Boolean,
                       mastery: String,
                       term: String,
                       description: String,
                       maxPeople: Option[Index]): Option[Long] =
    Try(
      ctx.run(
        roles
          .insert(lift(
            Role(0L, name, isCurrent, mastery, term, description, maxPeople)))
          .returning(_.uid)
      )).toOption

  override def getRole(id: Long): Option[Role] = Try(ctx.run(
    for {
      role <- roles if (role.uid == lift(id))
    } yield role
  ))
  .map(_.headOption)
  .toOption.flatten
}

object RoleDAO {
  def apply(dataSource: DataSource with Closeable): RoleDAO =
    new RoleDAOImpl(
      new MysqlJdbcContext(SnakeCase, dataSource)
    )

  private[db] lazy val createTable: String =
    """CREATE TABLE roles (
             uid INT unsigned NOT NULL primary key AUTO_INCREMENT,
             name VARCHAR(150) NOT NULL,
             is_current BOOLEAN NOT NULL,
             mastery VARCHAR(150) NOT NULL,
             term VARCHAR(30) NOT NULL,
             description VARCHAR(500) NOT NULL,
             max_people INT unsigned
             );"""
}
