package se.dsek.phunkisservice.model

import java.util.Date

case class RoleChange(role: Long, date: Date, user: String, inOrOut: Boolean)
