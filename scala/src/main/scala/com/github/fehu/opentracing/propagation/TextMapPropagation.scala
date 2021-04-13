package com.github.fehu.opentracing.propagation

import java.util.{ Iterator => JIterator, Map => JMap }

import scala.collection.mutable
import scala.collection.JavaConverters._

import io.opentracing.propagation.{ Format, TextMap }

final class TextMapPropagation private (map: mutable.SortedMap[String, String]) extends Propagation {
  type Underlying = TextMap

  val underlying: TextMap = new TextMap {
    def iterator(): JIterator[JMap.Entry[String, String]] = map.asJava.entrySet().iterator()

    def put(key: String, value: String): Unit = map.update(key, value)
  }

  def format: Format[TextMap] = TextMapPropagation.format

  type Repr = Map[String, String]
  def repr: Map[String, String] = map.toMap
}

object TextMapPropagation extends PropagationCompanion[TextMapPropagation] {
  def apply(): TextMapPropagation = new TextMapPropagation(mutable.SortedMap.empty)
  def apply(repr: Map[String, String]): TextMapPropagation = new TextMapPropagation(mutable.SortedMap(repr.toSeq: _*))

  def format: Format[TextMap] = Format.Builtin.TEXT_MAP
}
