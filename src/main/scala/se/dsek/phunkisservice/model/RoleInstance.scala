package se.dsek.phunkisservice.model

import java.util.Date

case class RoleInstance(role: Long, user: String, startDate: Date, endDate: Date)
