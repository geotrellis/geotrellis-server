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

package geotrellis.server.stac

import cats.syntax.either._
import cats.implicits._
import io.circe._

case class StacLink(
    href: String,
    rel: StacLinkType,
    _type: Option[StacMediaType],
    title: Option[String],
    labelExtAssets: List[String]
)

object StacLink {
  implicit val encStacLink: Encoder[StacLink] = Encoder.forProduct5(
    "href",
    "rel",
    "type",
    "title",
    "label:assets"
  )(link => (link.href, link.rel, link._type, link.title, link.labelExtAssets))

  implicit val decStacLink: Decoder[StacLink] = new Decoder[StacLink] {
    final def apply(c: HCursor) =
      (
        c.downField("href").as[String],
        c.downField("rel").as[StacLinkType],
        c.get[Option[StacMediaType]]("type"),
        c.get[Option[String]]("title"),
        c.get[Option[List[String]]]("label:assets")
      ).mapN(
        (
            href: String,
            rel: StacLinkType,
            _type: Option[StacMediaType],
            title: Option[String],
            assets: Option[List[String]]
        ) => StacLink(href, rel, _type, title, assets getOrElse List.empty)
      )
  }
}
