package scalaz
package data

import scalaz.core.EqClass
import scalaz.algebra.{ MonoidClass, SemigroupClass }
import scalaz.ct._
import scalaz.debug.DebugClass

trait ValidationModule {
  type Validation[A, B]

  object Invalid {
    def unapply[A, B](vab: Validation[A, B]): Option[A] = fold(vab)(Some(_), _ => None)
  }

  object Valid {
    def unapply[A, B](vab: Validation[A, B]): Option[B] = fold(vab)(_ => None, Some(_))
  }

  /* functions */
  def fold[A, B, C](vab: Validation[A, B])(ia: A => C, vb: B => C): C
  def fromDisjunction[A, B](oa: A \/ B): Validation[A, B]
  def invalid[L, R](value: L): Validation[L, R]
  def valid[L, R](value: R): Validation[L, R]

  /* typeclass instances */
  def applicative[L: Semigroup]: Applicative[Validation[L, ?]]
  val bifunctor: Bifunctor[Validation]
  def eq[L: Eq, R: Eq]: Eq[Validation[L, R]]
  def debug[L: Debug, R: Debug]: Debug[Validation[L, R]]
  def semigroup[L: Semigroup, R: Semigroup]: Semigroup[Validation[L, R]]
  def monoid[L: Semigroup, R: Monoid]: Monoid[Validation[L, R]]
}

object ValidationModule extends ValidationSyntax {
  implicit def applicativeValidation[L: Semigroup]: Applicative[Validation[L, ?]]           = Validation.applicative
  implicit val bifunctorValidation: Bifunctor[Validation]                                   = Validation.bifunctor
  implicit def eqValidation[L: Eq, R: Eq]: Eq[Validation[L, R]]                             = Validation.eq
  implicit def debugValidation[L: Debug, R: Debug]: Debug[Validation[L, R]]                 = Validation.debug
  implicit def semigroupValidation[L: Semigroup, R: Semigroup]: Semigroup[Validation[L, R]] = Validation.semigroup
  implicit def monoidValidation[L: Semigroup, R: Monoid]: Monoid[Validation[L, R]]          = Validation.monoid
}

private[scalaz] object ValidationImpl extends ValidationModule {
  type Validation[A, B] = A \/ B

  final def fold[A, B, C](vab: Validation[A, B])(ia: A => C, vb: B => C): C = vab match {
    case Invalid(a) => ia(a)
    case Valid(b)   => vb(b)
  }

  @inline def fromDisjunction[L, R](disj: L \/ R): Validation[L, R] = disj

  @inline def invalid[L, R](value: L): Validation[L, R] = -\/(value)
  @inline def valid[L, R](value: R): Validation[L, R]   = \/-(value)

  def applicative[L](implicit L: Semigroup[L]): Applicative[Validation[L, ?]] =
    instanceOf(new ApplicativeClass[Validation[L, ?]] {

      override def map[A, B](ma: Validation[L, A])(f: A => B): Validation[L, B] =
        fold[L, A, Validation[L, B]](ma)(
          l => invalid(l),
          r => valid(f(r))
        )

      override def ap[A, B](ma: Validation[L, A])(mf: Validation[L, (A => B)]): Validation[L, B] =
        fold[L, A, Validation[L, B]](ma)(
          l => invalid[L, B](fold(mf)(L.append(l, _), _ => l)),
          a => map[(A => B), B](mf)(f => f(a))
        )

      override def pure[A](a: A): Validation[L, A] =
        \/-[L, A](a)
    })

  def debug[L, R](implicit L: Debug[L], R: Debug[R]): Debug[Validation[L, R]] =
    instanceOf[DebugClass[Validation[L, R]]] {
      case Invalid(left) => s"""Invalid(${L.debug(left)})"""
      case Valid(right)  => s"""Valid(${R.debug(right)})"""
    }

  val bifunctor: Bifunctor[Disjunction] =
    instanceOf(new BifunctorClass[Disjunction] with BifunctorClass.DeriveLmapRmap[Disjunction] {
      def bimap[A, B, S, T](fab: Validation[A, B])(as: A => S, bt: B => T): Validation[S, T] = fab match {
        case Invalid(a) => invalid(as(a))
        case Valid(b)   => valid(bt(b))
      }
    })

  def eq[A, B](implicit A: Eq[A], B: Eq[B]): Eq[Validation[A, B]] =
    instanceOf[EqClass[Validation[A, B]]] {
      case (Invalid(a1), Invalid(a2)) => A.equal(a1, a2)
      case (Valid(b1), Valid(b2))     => B.equal(b1, b2)
      case _                          => false
    }

  def monoid[L, R](implicit L: Semigroup[L], R: Monoid[R]): Monoid[Validation[L, R]] =
    instanceOf(new MonoidClass[Validation[L, R]] {
      def empty: Validation[L, R] = valid(R.empty)
      def append(a: Validation[L, R], b: => Validation[L, R]): Validation[L, R] =
        (a, b) match {
          case (Invalid(a), Invalid(b)) => invalid(L.append(a, b))
          case (Invalid(a), Valid(_))   => invalid(a)
          case (Valid(_), Invalid(b))   => invalid(b)
          case (Valid(a), Valid(b))     => valid(R.append(a, b))
        }
    })

  def semigroup[L, R](implicit L: Semigroup[L], R: Semigroup[R]): Semigroup[Validation[L, R]] =
    instanceOf(new SemigroupClass[Validation[L, R]] {
      def append(a: Validation[L, R], b: => Validation[L, R]): Validation[L, R] =
        (a, b) match {
          case (Invalid(a), Invalid(b)) => invalid(L.append(a, b))
          case (Invalid(a), Valid(_))   => invalid(a)
          case (Valid(_), Invalid(b))   => invalid(b)
          case (Valid(a), Valid(b))     => valid(R.append(a, b))
        }
    })
}

trait ValidationSyntax {
  implicit final class DisjunctionAsValidation[A, B](oa: A \/ B) {
    def asValidation: Validation[A, B] = Validation.fromDisjunction(oa)
  }

  implicit final class ToValidationOps[A](a: A) {
    def valid[B]: Validation[B, A]   = Validation.valid(a)
    def invalid[B]: Validation[A, B] = Validation.invalid(a)
  }
}
