package se.dsek.phunkisservice.model

final case class Role(uid: Long,
                      name: String,
                      isCurrent: Boolean,
                      mastery: String,
                      term: String,
                      description: String,
                      maxPeople: Option[Int])
