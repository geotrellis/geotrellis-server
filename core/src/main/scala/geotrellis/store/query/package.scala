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

  def or(l: Query, r: Query): Query = Fix(QueryF.Or(l, r))
  def and(l: Query, r: Query): Query = Fix(QueryF.And(l, r))
  def intersects(g: Geometry): Query = Fix[QueryF](QueryF.Intersects(g))
  def contains(g: Geometry): Query = Fix[QueryF](QueryF.Contains(g))
  def covers(g: Geometry): Query = Fix[QueryF](QueryF.Covers(g))
  def at(t: ZonedDateTime, fieldName: Symbol = 'time): Query = Fix[QueryF](QueryF.At(t, fieldName))
  def between(t1: ZonedDateTime, t2: ZonedDateTime, fieldName: Symbol = 'time): Query = Fix[QueryF](QueryF.Between(t1, t2, fieldName))
}
