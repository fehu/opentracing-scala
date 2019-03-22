package com.gihub.fehu.opentracing

import io.opentracing.{ Scope, ScopeManager, Span, Tracer }
import io.opentracing.util.GlobalTracer


object Implicits {
  implicit def defaultTracerOpt: Option[Tracer] = Option(GlobalTracer.get())
  implicit def activeSpanOpt(implicit tracerOpt: Option[Tracer]): Option[Span] = tracerOpt.flatMap(Option apply _.activeSpan())

  implicit def scopeManagerOpt(implicit tracerOpt: Option[Tracer]): Option[ScopeManager] = tracerOpt.flatMap(Option apply _.scopeManager())
  implicit def activeScope(implicit managerOpt: Option[ScopeManager]): Option[Scope] = managerOpt.flatMap(Option apply _.active())
}



object NullableImplicits {
  object Tracer extends LowPriorityNullableTracerImplicits {
    implicit def nullableTracerFromOption(implicit opt: Option[Tracer]): Tracer = opt.orNull
  }
  trait LowPriorityNullableTracerImplicits {
    implicit def defaultNullableTracer: Tracer = Implicits.defaultTracerOpt.orNull
  }


  object Span extends LowPriorityNullableSpanImplicits {
    implicit def activeNullableSpan(implicit tracer: Tracer): Span = Implicits.activeSpanOpt(Option(tracer)).orNull
  }
  trait LowPriorityNullableSpanImplicits {
    implicit def defaultActiveNullableSpan: Span = Implicits.activeSpanOpt(Implicits.defaultTracerOpt).orNull
  }

  object ScopeManager extends LowPriorityNullableScopeManagerImplicits {
    implicit def nullableScopeManager(implicit tracer: Tracer): ScopeManager = Implicits.scopeManagerOpt(Option(tracer)).orNull
  }
  trait LowPriorityNullableScopeManagerImplicits {
    implicit def defaultNullableScopeManager: ScopeManager = Implicits.scopeManagerOpt(Implicits.defaultTracerOpt).orNull
  }

  object Scope extends LowPriorityNullableScopeImplicits {
    implicit def activeNullableScope(implicit manager: ScopeManager): Scope = Implicits.activeScope(Option(manager)).orNull
  }
  trait LowPriorityNullableScopeImplicits {
    implicit def defaultActiveNullableScope: Scope = Implicits.activeScope(Implicits.scopeManagerOpt(Implicits.defaultTracerOpt)).orNull
  }
}
