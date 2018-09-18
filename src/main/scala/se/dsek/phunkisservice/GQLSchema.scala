package se.dsek.phunkisservice

import java.util.Date

import sangria.execution.deferred.{Fetcher, HasId}
import sangria.schema._
import sangria.macros.derive._
import se.dsek.phunkisservice.db.{RoleChangeDAO, RoleDAO}
import se.dsek.phunkisservice.model.{Role, RoleChange, RoleInstance}

import scala.concurrent.Future


object GQLSchema {

//  val roles = Fetcher.caching((ctx: RoleDAO, ))
  implicit val roleType = deriveObjectType[Unit, Role](
    ObjectTypeDescription("Represents a role/position members can hold within the guild")
  )
  implicit val roleChangeType = deriveObjectType[Unit, RoleChange](
    ObjectTypeDescription("Represents a guild member getting elected to a role, finishing their term, or getting relieved")
  )
  implicit val roleInstanceType = deriveObjectType[Unit, RoleInstance](
    ObjectTypeDescription("Represents a guild member holding a position for some length of time")
  )

  val userId = Argument("user", StringType)

  val roleId = Argument("role", LongType)

  val roleInstanceQueryType = ObjectType("Query", fields[RoleChangeDAO, Unit](
    Field("currentRoles", ListType(roleType),
      description = Some("Returns all roles the user currently holds"),
      arguments = userId :: Nil,
      resolve = c => c.ctx.currentRoles(c.arg(userId))
    ),
    Field("allRoles", ListType(roleInstanceType),
      description = Some("Returns all roles the user holds or has held"),
      arguments = userId :: Nil,
      resolve = c => c.ctx.allRoles(c.arg(userId))
    ),
    Field("currentUsers", ListType(StringType),
      description = Some("Returns all users currently holding this role"),
      arguments = roleId :: Nil,
      resolve = c => c.ctx.currentWorkers(c.arg(roleId))
    ),
    Field("allUsers", ListType(roleInstanceType),
      description = Some("Returns all users that have held this role"),
      arguments = roleId :: Nil,
      resolve = c => c.ctx.allWorkers(c.arg(roleId))
    ),
  ))

  val mastery = Argument("mastery", OptionInputType(StringType))

  val roleQueryType = ObjectType("Query", fields[RoleDAO, Unit](
    Field("allRoles", ListType(roleType),
      description = Some("All roles that have ever existed, optionally filtered by mastery"),
      arguments = mastery :: Nil,
      resolve = c => c.ctx.allRoles(c.arg(mastery))
    ),
    Field("activeRoles", ListType(roleType),
      description = Some("All roles that currently exist within the guild, or in a mastery"),
      arguments = mastery :: Nil,
      resolve = c => c.ctx.activeRoles(c.arg(mastery))
    ),
  ))

  val roleSchema = Schema(roleQueryType)
  val roleInstanceSchema = Schema(roleInstanceQueryType)
}
