package com.github.tkqubo.akka_open_graph_fetcher

import java.nio.charset.Charset

import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpCharsets, HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Parses `HttpResponse` into [[OpenGraph]] object
  * @param toStrictDuration Duration for entity collection
  */
class OpenGraphParser(
  val toStrictDuration: FiniteDuration = OpenGraphParser.defaultToStrictDuration
) {
  private val utf8: Charset = HttpCharsets.`UTF-8`.nioCharset()

  /**
    * Parses `HttpCharsets` into [[OpenGraph]] object
    * @param request
    * @param response
    * @param ec
    * @param materializer
    * @return
    */
  def parse(
    request: HttpRequest, response: HttpResponse
  )(implicit ec: ExecutionContext, materializer: ActorMaterializer): Future[OpenGraph] =
    response
      .header[`Content-Type`]
      .map(_.contentType.mediaType) match {
      case Some(mediaType) if mediaType.isImage =>
        val url: String = request.uri.toString()
        response.discardEntityBytes()
        Future.successful(OpenGraph(url = url, image = Some(url)))
      case _ =>
        responseToOpenGraph(request, response)
    }

  protected def responseToOpenGraph(
    request: HttpRequest,
    response: HttpResponse
  )(implicit ec: ExecutionContext, materializer: ActorMaterializer): Future[OpenGraph] =
    for {
      entity: Strict <- response.entity.toStrict(toStrictDuration)
      document: Document = byteStringToDocument(entity.data, response)
      openGraph: OpenGraph = documentToOpenGraph(document, request, response)
    } yield openGraph

  private def byteStringToDocument(data: ByteString, response: HttpResponse): Document = {
    val document: Document = Jsoup.parse(data.utf8String)
    // might be more safe google for example can send
    val maybeCharset: Option[Charset] = (
      maybeCharsetFromHttpResponseHeader(response) orElse
      maybeCharsetFromHttpEntity(response) orElse
      maybeCharsetFromDocumentCharsetMetaTag(document) orElse
      maybeCharsetFromDocumentHttpEquivMetaTag(document)
    ).filter(_ != utf8)
    maybeCharset
      .map(charset => Jsoup.parse(data.decodeString(charset)))
      .getOrElse(document)
  }

  protected def documentToOpenGraph(document: Document, request: HttpRequest, response: HttpResponse): OpenGraph =
    OpenGraph(
      url = document.metaValue("og:url") getOrElse request.uri.toString(),
      title = document.metaValue("og:title"),
      description = document.metaValue("og:description"),
      image = document.metaValue("og:image"),
      error = Error.maybeFromStatusCode(response.status)
    )

  private def maybeCharsetFromDocumentHttpEquivMetaTag(document: Document): Option[Charset] = {
    for {
      charsetStatement <- document
        .select("meta[http-equiv=Content-Type]")
        .attr("content")
        .toLowerCase
        .split(";")
        .find(_.trim.startsWith("charset="))
      charsetName <- charsetStatement.split("=").drop(1).lastOption
      charset <- charsetForNameOption(charsetName)
    } yield charset
  }

  private def maybeCharsetFromDocumentCharsetMetaTag(document: Document): Option[Charset] =
    charsetForNameOption(document.select("meta[charset]").attr("charset"))

  private def maybeCharsetFromHttpResponseHeader(response: HttpResponse): Option[Charset] =
    for {
      header <- response.header[`Content-Type`]
      charsetName <- header.contentType.charsetOption.map(_.value)
      charset <- charsetForNameOption(charsetName)
    } yield charset

  private def maybeCharsetFromHttpEntity(response: HttpResponse): Option[Charset] =
    for {
      httpCharset <- response.entity.contentType.charsetOption
      charset <- charsetForNameOption(httpCharset.value)
    } yield charset

  private def charsetForNameOption(charsetName: String): Option[Charset] =
    try Some(Charset.forName(charsetName)) catch { case e: Throwable => None }

  implicit protected class DocumentOps(val document: Document) {
    def metaValue(property: String): Option[String] =
      Option(document.select(s"meta[property=$property]").attr("content")).filter(_.nonEmpty)
  }
}

object OpenGraphParser {
  /** Default duration for [[OpenGraphParser.toStrictDuration]] */
  val defaultToStrictDuration: FiniteDuration = 3 seconds
}
