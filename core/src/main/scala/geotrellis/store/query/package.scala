package geotrellis.store

import geotrellis.vector.Geometry
import higherkindness.droste.data.Fix

import java.time.ZonedDateTime

package object query {
  type Query = Fix[QueryF]

  implicit class QueryOps(self: Query) {
    def or(r: Query): Query = QueryF.or(self, r)
    def and(r: Query): Query = QueryF.and(self, r)
  }

  def or(l: Query, r: Query): Query        = QueryF.or(l, r)
  def and(l: Query, r: Query): Query       = QueryF.and(l, r)
  def withName(name: String): Query        = QueryF.withName(name)
  def withNames(names: Set[String]): Query = QueryF.withNames(names)
  def intersects(g: Geometry): Query       = QueryF.intersects(g)
  def contains(g: Geometry): Query         = QueryF.contains(g)
  def covers(g: Geometry): Query           = QueryF.covers(g)
  def at(t: ZonedDateTime, fieldName: Symbol = 'time): Query                          = QueryF.at(t, fieldName)
  def between(t1: ZonedDateTime, t2: ZonedDateTime, fieldName: Symbol = 'time): Query = QueryF.between(t1, t2, fieldName)
}
