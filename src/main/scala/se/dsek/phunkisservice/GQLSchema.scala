package se.dsek.phunkisservice

import java.time.Instant
import java.util.Date

import org.slf4j.LoggerFactory
import sangria.schema._
import sangria.macros.derive._
import se.dsek.phunkisservice.db.{RoleDAO, RoleInstanceDAO}
import se.dsek.phunkisservice.model.{Role, RoleInstance}

object GQLSchema {
  private val logger = LoggerFactory.getLogger(GQLSchema.getClass)
  import db.DBUtil._

  //implicit val enc = encodeDate
  //implicit val dec = decodeDate

  implicit val roleType: ObjectType[Unit, Role] = deriveObjectType[Unit, Role](
    ObjectTypeDescription(
      "Represents a role/position members can hold within the guild")
  )

  implicit val roleInstanceType: ObjectType[Unit, RoleInstance] =
    deriveObjectType[Unit, RoleInstance](
      ObjectTypeDescription(
        "Represents a guild member holding a position for some length of time")
    )

  private val userId = Argument("user", StringType)

  private val roleId = Argument("role", LongType)

  private val startDate = Argument("startDate", LongType)

  private val endDate = Argument("endDate", LongType)
  private val endDate2 = Argument("relieveDate", LongType)

  val roleInstanceQueryType: ObjectType[RoleInstanceDAO[_], Unit] = ObjectType(
    "Query",
    fields[RoleInstanceDAO[_], Unit](
      Field(
        "currentRoles",
        ListType(LongType),
        description = Some("Returns all roles the user currently holds"),
        arguments = userId :: Nil,
        resolve = c => c.ctx.currentRoles(c.arg(userId))
      ),
      Field(
        "allRoles",
        ListType(roleInstanceType),
        description = Some("Returns all roles the user holds or has held"),
        arguments = userId :: Nil,
        resolve = c => c.ctx.allRoles(c.arg(userId))
      ),
      Field(
        "currentUsers",
        ListType(StringType),
        description = Some("Returns all users currently holding this role"),
        arguments = roleId :: Nil,
        resolve = c => c.ctx.currentWorkers(c.arg(roleId))
      ),
      Field(
        "allUsers",
        ListType(roleInstanceType),
        description = Some("Returns all users that have held this role"),
        arguments = roleId :: Nil,
        resolve = c => c.ctx.allWorkers(c.arg(roleId))
      )
    )
  )

  val roleInstanceMutationType: ObjectType[RoleInstanceDAO[_], Unit] =
    ObjectType(
      "Mutation",
      fields[RoleInstanceDAO[_], Unit](
        Field(
          "elect",
          OptionType(roleInstanceType),
          description = Some(
            "Make a user have a role that they were elected to hold. Returns the role instance if successful."),
          arguments = startDate :: endDate :: roleId :: userId :: Nil,
          resolve = c => {
            logger.debug(
              s"roleInstanceElect called with arguments ${c.args.raw}")
            val o = c.ctx.insertInstance(
              RoleInstance(c.arg(roleId),
                c.arg(userId),
                Date.from(Instant.ofEpochSecond(c.arg(startDate))),
                Date.from(Instant.ofEpochSecond(c.arg(endDate))))
            )
            logger.debug(s"result: $o")
            o.toOption
          }
        ),
        Field(
          "relieve",
          BooleanType,
          description = Some(
            "Remove a user from role that they were relived of. Returns true if successful."),
          arguments = endDate2 :: startDate :: endDate :: roleId :: userId :: Nil,
          resolve = c =>
            c.ctx.relieveWorker(
              RoleInstance(c.arg(roleId),
                c.arg(userId),
                Date.from(Instant.ofEpochSecond(c.arg(startDate))),
                Date.from(Instant.ofEpochSecond(c.arg(endDate)))),
              Date.from(Instant.ofEpochSecond(c.arg(endDate2)))
            )
        )
      )
    )

  private val maybeMastery = Argument("mastery", OptionInputType(StringType))

  private val roleQueryType = ObjectType(
    "Query",
    fields[RoleDAO[_], Unit](
      Field(
        "allRoles",
        ListType(roleType),
        description = Some(
          "All roles that have ever existed, optionally filtered by mastery"),
        arguments = maybeMastery :: Nil,
        resolve = c => c.ctx.allRoles(c.arg(maybeMastery))
      ),
      Field(
        "activeRoles",
        ListType(roleType),
        description = Some(
          "All roles that currently exist within the guild, or in a mastery"),
        arguments = maybeMastery :: Nil,
        resolve = c => c.ctx.activeRoles(c.arg(maybeMastery))
      )
    )
  )

  private val name = Argument("name", StringType)
  private val isCurrent = Argument("isCurrent", BooleanType)
  private val mastery = Argument("mastery", StringType)
  private val term = Argument("term", StringType)
  private val description = Argument("description", StringType)
  private val maxPeople = Argument("maxPeople", OptionInputType(IntType))

  private val roleMutationType = ObjectType(
    "Mutation",
    fields[RoleDAO[_], Unit](
      Field(
        "addRole",
        OptionType(LongType),
        description =
          Some("Add a new role. Returns generated role uid if successful."),
        arguments = name :: isCurrent :: mastery :: term :: description :: maxPeople :: Nil,
        resolve = c => {
          logger.debug(s"addRole called with arguments: ${c.args.raw}")
          c.ctx.addRole(c.arg(name),
            c.arg(isCurrent),
            c.arg(mastery),
            c.arg(term),
            c.arg(description),
            c.arg(maxPeople))
        }
      )
    )
  )

  val roleSchema = Schema(roleQueryType, Some(roleMutationType))
  val roleInstanceSchema =
    Schema(roleInstanceQueryType, Some(roleInstanceMutationType))
}
