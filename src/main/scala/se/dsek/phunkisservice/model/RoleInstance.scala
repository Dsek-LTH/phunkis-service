package se.dsek.phunkisservice.model

import java.util.Date

final case class RoleInstance(role: Long, user: String, startDate: Date, endDate: Date)
