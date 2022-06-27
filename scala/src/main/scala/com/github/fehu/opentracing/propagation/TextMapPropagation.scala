package com.github.fehu.opentracing.propagation

import java.util.{ Iterator => JIterator, Map => JMap }

import scala.collection.mutable
import scala.collection.JavaConverters._

import io.opentracing.propagation.{ Format, TextMap }

object TextMapPropagation extends Propagation {
  type Underlying = TextMap
  type Repr = Map[String, String]

  def format: Format[TextMap] = Format.Builtin.TEXT_MAP

  def apply(): TextMapPropagation.Carrier = new CarrierImpl(mutable.SortedMap.empty)
  def apply(repr: Map[String, String]): TextMapPropagation.Carrier = new CarrierImpl(mutable.SortedMap(repr.toSeq: _*))

  private class CarrierImpl(map: mutable.SortedMap[String, String]) extends Carrier {
    def underlying: TextMap = new TextMap {
      def iterator(): JIterator[JMap.Entry[String, String]] = map.asJava.entrySet().iterator()
      def put(key: String, value: String): Unit = map.update(key, value)
    }
    def repr: Map[String, String] = map.toMap
  }
}
