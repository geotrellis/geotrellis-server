package geotrellis.server.extent

import geotrellis.vector.Extent
import geotrellis.raster._

import com.typesafe.scalalogging.LazyLogging
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.implicits._
import cats.syntax.either._


object SampleUtils extends LazyLogging {

  /** Sample imagery within the provided extent */
  final def sampleRasterExtent(uberExtent: Extent, cs: CellSize, maxCells: Int): (Extent, Extent, Extent, Extent) = {
    logger.trace(s"Finding sample extent for UberExtent $uberExtent for $cs with a maximum sample of $maxCells cells")
    val sampleWidth = math.sqrt(maxCells.toDouble) * cs.width
    val sampleHeight = math.sqrt(maxCells.toDouble) * cs.height

    logger.trace(s"orig height: ${uberExtent.height}, width: ${uberExtent.width}")
    logger.trace(s"ideal sample height: $sampleHeight, ideal sample width: $sampleWidth")
    // Sanity check here - if the desired sample extent is larger than the source extent, just use the source extent
    val widthDelta = (if (sampleWidth > uberExtent.width) uberExtent.width else sampleWidth) / 2
    val heightDelta = (if (sampleHeight > uberExtent.height) uberExtent.height else sampleHeight) / 2

    val tl = Extent(uberExtent.xmin,              uberExtent.ymax - heightDelta, uberExtent.xmin + widthDelta, uberExtent.ymax)
    val tr = Extent(uberExtent.xmax - widthDelta, uberExtent.ymax - heightDelta, uberExtent.xmax,              uberExtent.ymax)
    val bl = Extent(uberExtent.xmin,              uberExtent.ymin,               uberExtent.xmin + widthDelta, uberExtent.ymin + heightDelta)
    val br = Extent(uberExtent.xmax - widthDelta, uberExtent.ymin,               uberExtent.xmax,              uberExtent.ymin + heightDelta)
    logger.trace(s"The sample extent covers ${((tl.area + tr.area + bl.area + br.area) / uberExtent.area) * 100}% of the source extent")
    (tl, tr, bl, br)
  }

  /** Choose the largest cellsize */
  final def chooseLargestCellSize(nativeCellSizes: NEL[CellSize]): CellSize =
    nativeCellSizes
      .reduceLeft({ (chosenCS: CellSize, nextCS: CellSize) =>
        val chosenSize = chosenCS.height * chosenCS.width
        val nextSize = nextCS.height * nextCS.width

        if (nextSize > chosenSize)
          nextCS
        else
          chosenCS
      })

  /** Choose the smallest cellsize */
  final def chooseSmallestCellSize(nativeCellSizes: NEL[CellSize]): CellSize =
    nativeCellSizes
      .reduceLeft({ (chosenCS: CellSize, nextCS: CellSize) =>
        val chosenSize = chosenCS.height * chosenCS.width
        val nextSize = nextCS.height * nextCS.width

        if (nextSize < chosenSize)
          nextCS
        else
          chosenCS
      })

  final def intersectExtents(extents: NEL[Extent]): Option[Extent] = {
    extents.tail.foldLeft(Option(extents.head))({
      case (Some(ex1), ex2) => ex1 intersection ex2
      case _ => None
    })
  }

  final def unionExtents(extents: NEL[Extent]): Option[Extent] =
    Some(extents.tail.foldLeft(extents.head)({ (ex1, ex2) => ex1 combine ex2 }))


}

