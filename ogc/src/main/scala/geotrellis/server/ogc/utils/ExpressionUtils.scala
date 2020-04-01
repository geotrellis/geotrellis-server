/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.ogc.utils

import geotrellis.raster.TargetCell
import com.azavea.maml.ast.Expression
import cats.syntax.option._

object ExpressionUtils {
  def bindExpression(expr: Expression, fun: Expression => Expression): Expression = {
    def deepMap(expression: Expression, f: Expression => Expression): Expression =
      f(expression).withChildren(expression.children.map(deepMap(_, f)))

    deepMap(expr, fun)
  }

  def targetCell(str: String): Option[TargetCell] = str match {
    case "nodata" => TargetCell.NoData.some
    case "data"   => TargetCell.Data.some
    case "all"    => TargetCell.All.some
    case _        => None
  }
}
